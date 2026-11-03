package mz.ac.isutc.scad.no.transaccoes;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import mz.ac.isutc.scad.common.relogio.RelogioCorrigido;
import mz.ac.isutc.scad.common.relogio.RelogioLamport;
import mz.ac.isutc.scad.no.bd.FragmentosBd;
import mz.ac.isutc.scad.no.transaccoes.Cronologia2PC.Decisao;
import mz.ac.isutc.scad.no.transaccoes.Cronologia2PC.Voto;

/**
 * Coordenador do protocolo Two-Phase Commit sobre os fragmentos PostgreSQL.
 *
 * <ol>
 *   <li><b>Fase 1 (votação)</b>: em paralelo, cada participante executa o seu trabalho e faz
 *       {@code PREPARE TRANSACTION 'gid'} = vota SIM. Uma excepção ou a falta de resposta em
 *       {@value #TIMEOUT_VOTO_S} s conta como NÃO.</li>
 *   <li><b>Decisão</b>: COMMIT só se todos votarem SIM. A decisão é gravada no log do coordenador
 *       ({@code log_2pc}) <b>antes</b> de ser comunicada.</li>
 *   <li><b>Fase 2</b>: {@code COMMIT PREPARED} / {@code ROLLBACK PREPARED} em cada participante. Se um
 *       participante não estiver acessível, a {@link Recuperacao2PC} conclui mais tarde.</li>
 * </ol>
 * O identificador global ("gid") é {@code coordenador|transacao|participante}, o que permite à
 * recuperação saber de quem é cada transacção preparada.
 */
@Component
public class Coordenador2PC {

    private static final Logger log = LoggerFactory.getLogger(Coordenador2PC.class);
    static final int TIMEOUT_VOTO_S = 5;
    private static final int MAX_CRONOLOGIAS = 40;
    private static final Pattern ID_VALIDO = Pattern.compile("[A-Z0-9-]+");

    private final FragmentosBd fragmentos;
    private final Log2PC log2pc;
    private final RelogioLamport lamport;
    private final RelogioCorrigido relogio;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "2pc-participante");
        t.setDaemon(true);
        return t;
    });
    private final Deque<Cronologia2PC> cronologias = new ConcurrentLinkedDeque<>();

    /** Só para a demonstração: pausa entre a decisão e a fase 2 (para "matar" o coordenador). */
    private volatile long pausaAntesFase2Ms;

    public Coordenador2PC(FragmentosBd fragmentos, Log2PC log2pc, RelogioLamport lamport, RelogioCorrigido relogio) {
        this.fragmentos = fragmentos;
        this.log2pc = log2pc;
        this.lamport = lamport;
        this.relogio = relogio;
    }

    public static String gid(String coordenador, String transacaoId, String participante) {
        return coordenador + "|" + transacaoId + "|" + participante;
    }

    public Decisao executar(String transacaoId, String tipo, Map<String, TrabalhoParticipante> trabalho) {
        if (!ID_VALIDO.matcher(transacaoId).matches()) {
            throw new IllegalArgumentException("Identificador de transacção inválido: " + transacaoId);
        }
        String eu = fragmentos.regiaoLocal();
        List<String> participantes = new ArrayList<>(trabalho.keySet());
        Cronologia2PC c = new Cronologia2PC(transacaoId, tipo, eu, participantes, relogio.agora());
        guardar(c);
        c.evento(relogio.agora(), "Início (" + tipo + "), coordenador " + eu + ", participantes " + participantes);

        // ---------- Fase 1: votação ----------
        Map<String, Future<?>> pedidos = new LinkedHashMap<>();
        trabalho.forEach((p, t) -> pedidos.put(p, executor.submit(() -> {
            preparar(p, gid(eu, transacaoId, p), t);
            return null;
        })));
        boolean todosSim = true;
        for (var e : pedidos.entrySet()) {
            String p = e.getKey();
            try {
                e.getValue().get(TIMEOUT_VOTO_S, TimeUnit.SECONDS);
                c.voto(p, Voto.SIM, null);
                c.evento(relogio.agora(), p + " votou SIM (PREPARE TRANSACTION)");
            } catch (TimeoutException ex) {
                todosSim = false;
                c.voto(p, Voto.SEM_RESPOSTA, "sem resposta em " + TIMEOUT_VOTO_S + " s");
                c.evento(relogio.agora(), p + " não respondeu: conta como NÃO");
            } catch (Exception ex) {
                todosSim = false;
                String motivo = causa(ex);
                c.voto(p, Voto.NAO, motivo);
                c.evento(relogio.agora(), p + " votou NÃO: " + motivo);
            }
        }

        // ---------- Decisão (gravada antes de ser comunicada) ----------
        Decisao decisao = todosSim ? Decisao.COMMIT : Decisao.ABORT;
        log2pc.gravarDecisao(transacaoId, tipo, participantes, decisao, lamport.tick(), relogio.agora());
        c.decisao(decisao);
        c.evento(relogio.agora(), "Decisão " + decisao + " gravada no log do coordenador");
        log.info("2PC {} ({}): decisão {}", transacaoId, tipo, decisao);

        pausaDeDemonstracao(c);

        // ---------- Fase 2: conclusão ----------
        boolean tudoConcluido = true;
        for (String p : participantes) {
            boolean ok = concluir(p, gid(eu, transacaoId, p), decisao);
            c.concluido(p, ok);
            c.evento(relogio.agora(), p + (ok ? ": " + decisao + " PREPARED concluído"
                    : ": inacessível, fica para a recuperação"));
            tudoConcluido &= ok;
        }
        if (tudoConcluido) {
            log2pc.marcarConcluida(transacaoId);
        }
        c.fim(relogio.agora());
        return decisao;
    }

    private void preparar(String participante, String gid, TrabalhoParticipante trabalho) throws SQLException {
        try (Connection con = fragmentos.dataSource(participante).getConnection()) {
            con.setAutoCommit(false);
            try {
                trabalho.executar(con);
                try (Statement st = con.createStatement()) {
                    st.execute("PREPARE TRANSACTION '" + gid + "'");
                }
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    /**
     * Fase 2 num participante. Idempotente: se a transacção preparada já não existir
     * (já concluída antes, ou nunca chegou a ser preparada), não há nada a fazer.
     *
     * @return false se o participante estiver inacessível (a recuperação tenta mais tarde)
     */
    boolean concluir(String participante, String gid, Decisao decisao) {
        String sql = (decisao == Decisao.COMMIT ? "COMMIT PREPARED '" : "ROLLBACK PREPARED '") + gid + "'";
        try (Connection con = fragmentos.dataSource(participante).getConnection();
             Statement st = con.createStatement()) {
            con.setAutoCommit(true);
            st.execute(sql);
            return true;
        } catch (SQLException e) {
            if ("42704".equals(e.getSQLState())) {   // undefined_object: gid já não existe
                return true;
            }
            log.warn("Fase 2 de {} em {} falhou: {}", gid, participante, e.getMessage());
            return false;
        }
    }

    private void pausaDeDemonstracao(Cronologia2PC c) {
        long pausa = pausaAntesFase2Ms;
        if (pausa > 0) {
            c.evento(relogio.agora(), "PAUSA de demonstração de " + pausa + " ms antes da fase 2");
            log.warn("2PC {}: pausa de demonstração de {} ms antes da fase 2", c.transacaoId(), pausa);
            try {
                Thread.sleep(pausa);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void definirPausaAntesFase2(long ms) {
        this.pausaAntesFase2Ms = Math.max(0, ms);
    }

    public long pausaAntesFase2Ms() {
        return pausaAntesFase2Ms;
    }

    private void guardar(Cronologia2PC c) {
        cronologias.addFirst(c);
        while (cronologias.size() > MAX_CRONOLOGIAS) {
            cronologias.removeLast();
        }
    }

    void registarCronologia(Cronologia2PC c) {
        guardar(c);
    }

    public List<Cronologia2PC.Vista> cronologias() {
        return cronologias.stream().map(Cronologia2PC::vista).toList();
    }

    private static String causa(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    @PreDestroy
    void parar() {
        executor.shutdownNow();
    }
}

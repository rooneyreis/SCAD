package mz.ac.isutc.scad.no.exclusao;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mz.ac.isutc.scad.common.relogio.RelogioCorrigido;
import mz.ac.isutc.scad.common.relogio.RelogioLamport;

/**
 * Exclusão mútua distribuída de Ricart-Agrawala, <b>por recurso</b>, com carimbos de Lamport.
 *
 * <ul>
 *   <li><b>Pedir</b>: {@code ts = lamport.tick()}; envia PEDIDO(recurso, ts, eu) a todos os nós vivos e
 *       espera um OK de cada um.</li>
 *   <li><b>Receber PEDIDO(ts_j, j)</b>: responde OK já, excepto se este nó estiver DENTRO da secção
 *       crítica, ou a PEDIR com prioridade maior: {@code (ts_eu, eu) < (ts_j, j)} (desempate pelo id
 *       do nó). Nesse caso adia o OK.</li>
 *   <li><b>Sair</b>: envia os OKs adiados.</li>
 * </ul>
 * <b>Tolerância a falhas:</b> quando o detector dá um nó como morto, deixa de se esperar pelo seu OK
 * (e de lhe guardar OKs adiados), por isso o algoritmo nunca bloqueia à espera de um nó morto. Os
 * pedidos sem resposta são reenviados a cada 2 s (a recepção é idempotente). Um nó que volta entra nos
 * pedidos seguintes.
 */
public class RicartAgrawala implements ExclusaoMutua {

    private static final Logger log = LoggerFactory.getLogger(RicartAgrawala.class);
    private static final long REENVIO_MS = 2_000;
    private static final int MAX_EVENTOS = 80;

    public enum Estado { LIVRE, PEDINDO, DENTRO }

    private final String eu;
    private final Membros membros;
    private final TransporteRA transporte;
    private final RelogioLamport lamport;
    private final RelogioCorrigido relogio;
    private final ExecutorService envios;
    private final long timeoutMs;

    private final Map<String, EstadoRecurso> recursos = new TreeMap<>();      // protegido por "this"
    private final Map<String, ReentrantLock> filaLocal = new ConcurrentHashMap<>();
    private final Deque<String> eventos = new ConcurrentLinkedDeque<>();

    public RicartAgrawala(String eu, Membros membros, TransporteRA transporte, RelogioLamport lamport,
                          RelogioCorrigido relogio, ExecutorService envios, long timeoutMs) {
        this.eu = eu;
        this.membros = membros;
        this.transporte = transporte;
        this.lamport = lamport;
        this.relogio = relogio;
        this.envios = envios;
        this.timeoutMs = timeoutMs;
        membros.aoMorrer(this::noMorto);
    }

    @Override
    public <T> T executar(String recurso, Supplier<T> seccaoCritica) {
        // Pedidos do próprio nó para o mesmo recurso são servidos um de cada vez
        ReentrantLock local = filaLocal.computeIfAbsent(recurso, r -> new ReentrantLock(true));
        local.lock();
        try {
            entrar(recurso);
            try {
                return seccaoCritica.get();
            } finally {
                sair(recurso);
            }
        } finally {
            local.unlock();
        }
    }

    // ------------------------------------------------------------------ pedir / entrar

    private void entrar(String recurso) {
        long ts;
        Set<String> destinos;
        synchronized (this) {
            EstadoRecurso e = estado(recurso);
            e.estado = Estado.PEDINDO;
            e.meuTs = lamport.tick();
            e.aguarda = new HashSet<>(membros.vivos());
            e.aguarda.remove(eu);
            ts = e.meuTs;
            destinos = Set.copyOf(e.aguarda);
        }
        evento("PEDIDO " + recurso + " (ts=" + ts + ") enviado a " + destinos);
        destinos.forEach(d -> enviarPedido(d, recurso, ts));

        long limite = System.currentTimeMillis() + timeoutMs;
        long proximoReenvio = System.currentTimeMillis() + REENVIO_MS;
        synchronized (this) {
            EstadoRecurso e = estado(recurso);
            while (!e.aguarda.isEmpty()) {
                long agora = System.currentTimeMillis();
                if (agora >= limite) {
                    desistir(recurso, e);
                    throw new ExclusaoIndisponivelException("Sem OK de " + e.aguarda + " para " + recurso
                            + " em " + timeoutMs + " ms");
                }
                if (agora >= proximoReenvio) {
                    Set.copyOf(e.aguarda).forEach(d -> enviarPedido(d, recurso, ts));
                    proximoReenvio = agora + REENVIO_MS;
                }
                try {
                    wait(Math.max(1, Math.min(limite, proximoReenvio) - agora));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    desistir(recurso, e);
                    throw new ExclusaoIndisponivelException("Interrompido à espera de " + recurso);
                }
            }
            e.estado = Estado.DENTRO;
        }
        evento("ENTRA na secção crítica de " + recurso + " (ts=" + ts + ")");
    }

    private void enviarPedido(String destino, String recurso, long ts) {
        envios.execute(() -> {
            try {
                if (transporte.enviarPedido(destino, recurso, ts, eu)) {
                    receberOk(recurso, destino, 0);
                }
            } catch (RuntimeException ex) {
                // destino inacessível: reenvia-se; se estiver morto, o detector de falhas liberta-nos
                log.debug("PEDIDO {} para {} falhou: {}", recurso, destino, ex.getMessage());
            }
        });
    }

    // ------------------------------------------------------------------ sair

    private void sair(String recurso) {
        List<String> aLibertar;
        synchronized (this) {
            EstadoRecurso e = estado(recurso);
            e.estado = Estado.LIVRE;
            aLibertar = new ArrayList<>(e.adiados);
            e.adiados.clear();
        }
        evento("SAI da secção crítica de " + recurso + (aLibertar.isEmpty() ? "" : "; OK adiado enviado a " + aLibertar));
        aLibertar.forEach(d -> enviarOk(d, recurso));
    }

    private void desistir(String recurso, EstadoRecurso e) {
        e.estado = Estado.LIVRE;
        List<String> aLibertar = new ArrayList<>(e.adiados);
        e.adiados.clear();
        evento("DESISTE de " + recurso + " (timeout)");
        aLibertar.forEach(d -> enviarOk(d, recurso));
    }

    private void enviarOk(String destino, String recurso) {
        long ts = lamport.tick();
        envios.execute(() -> {
            try {
                transporte.enviarOk(destino, recurso, ts, eu);
            } catch (RuntimeException ex) {
                log.debug("OK {} para {} falhou: {}", recurso, destino, ex.getMessage());
            }
        });
    }

    // ------------------------------------------------------------------ mensagens recebidas

    /** @return true = OK imediato; false = OK adiado (será enviado ao sair da secção crítica) */
    public boolean receberPedido(String recurso, long tsPedido, String origem) {
        lamport.receber(tsPedido);
        boolean adiar;
        synchronized (this) {
            EstadoRecurso e = estado(recurso);
            boolean euTenhoPrioridade = e.meuTs < tsPedido || (e.meuTs == tsPedido && eu.compareTo(origem) < 0);
            adiar = e.estado == Estado.DENTRO || (e.estado == Estado.PEDINDO && euTenhoPrioridade);
            if (adiar) {
                e.adiados.add(origem);
            }
        }
        evento("Recebe PEDIDO " + recurso + " de " + origem + " (ts=" + tsPedido + "): "
                + (adiar ? "OK ADIADO" : "responde OK"));
        return !adiar;
    }

    public void receberOk(String recurso, String origem, long ts) {
        if (ts > 0) {
            lamport.receber(ts);
        }
        synchronized (this) {
            EstadoRecurso e = estado(recurso);
            if (e.estado == Estado.PEDINDO && e.aguarda.remove(origem)) {
                notifyAll();
            }
        }
    }

    private void noMorto(String no) {
        boolean libertou = false;
        synchronized (this) {
            for (EstadoRecurso e : recursos.values()) {
                libertou |= e.aguarda.remove(no);
                e.adiados.remove(no);
            }
            notifyAll();
        }
        evento("Detector de falhas: " + no + " MORTO" + (libertou ? " -> deixa de se esperar pelo seu OK" : ""));
    }

    // ------------------------------------------------------------------ estado para o painel

    public synchronized List<VistaRecurso> estado() {
        List<VistaRecurso> lista = new ArrayList<>();
        recursos.forEach((r, e) -> lista.add(new VistaRecurso(r, e.estado, e.meuTs,
                List.copyOf(e.aguarda), List.copyOf(e.adiados))));
        return lista;
    }

    public List<String> eventos() {
        return new ArrayList<>(eventos);
    }

    private EstadoRecurso estado(String recurso) {
        return recursos.computeIfAbsent(recurso, r -> new EstadoRecurso());
    }

    private void evento(String texto) {
        log.info("[RA] {}", texto);
        Instant agora = relogio.agora();
        eventos.addFirst(agora + "  [L=" + lamport.actual() + "] " + texto);
        while (eventos.size() > MAX_EVENTOS) {
            eventos.removeLast();
        }
    }

    private static final class EstadoRecurso {
        Estado estado = Estado.LIVRE;
        long meuTs = Long.MAX_VALUE;
        Set<String> aguarda = new HashSet<>();
        final Set<String> adiados = new LinkedHashSet<>();
    }

    public record VistaRecurso(String recurso, Estado estado, long meuTs, List<String> aguardaOkDe,
                               List<String> okAdiadosPara) {
    }
}

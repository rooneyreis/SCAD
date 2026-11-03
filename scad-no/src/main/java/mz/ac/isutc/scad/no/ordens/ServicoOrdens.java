package mz.ac.isutc.scad.no.ordens;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import mz.ac.isutc.scad.common.relogio.RelogioCorrigido;
import mz.ac.isutc.scad.common.relogio.RelogioLamport;
import mz.ac.isutc.scad.no.bd.FragmentosBd;
import mz.ac.isutc.scad.no.ordens.OrdemCompra.Estado;
import mz.ac.isutc.scad.no.transaccoes.Coordenador2PC;
import mz.ac.isutc.scad.no.transaccoes.Cronologia2PC.Decisao;
import mz.ac.isutc.scad.no.transaccoes.TrabalhoParticipante;

/**
 * Operações sobre ordens de compra que tocam vários fragmentos, todas atómicas via 2PC:
 * criar a ordem consolidada, marcá-la como enviada e receber a entrega.
 */
@Service
public class ServicoOrdens {

    private final FragmentosBd fragmentos;
    private final Coordenador2PC coordenador;
    private final RepositorioOrdens ordens;
    private final RelogioLamport lamport;
    private final RelogioCorrigido relogio;

    public ServicoOrdens(FragmentosBd fragmentos, Coordenador2PC coordenador, RepositorioOrdens ordens,
                         RelogioLamport lamport, RelogioCorrigido relogio) {
        this.fragmentos = fragmentos;
        this.coordenador = coordenador;
        this.ordens = ordens;
        this.lamport = lamport;
        this.relogio = relogio;
    }

    /**
     * Passo 5 do fluxo: cria a ordem (cabeçalho em cada fragmento participante, linha e
     * {@code em_encomenda} no fragmento de cada armazém) numa única transacção distribuída.
     *
     * @return o id da ordem, ou null se o 2PC abortou
     */
    public String criar(Catalogo.Produto produto, String armazemOrigem, Map<String, Integer> reparticao) {
        long ts = lamport.tick();
        Instant hora = relogio.agora();
        String ordemId = String.format("OC-%s-%06d", fragmentos.regiaoLocal(), ts);
        int total = reparticao.values().stream().mapToInt(Integer::intValue).sum();

        Map<String, TrabalhoParticipante> trabalho = new LinkedHashMap<>();
        reparticao.forEach((armazem, quantidade) -> trabalho.put(armazem, con -> {
            actualizar(con, """
                    INSERT INTO ordem_compra (id, fornecedor_id, produto_id, quantidade_total, estado, armazem_origem,
                                              coordenador, ts_lamport, ts_corrigido)
                    VALUES (?, ?, ?, ?, 'PENDENTE_ENVIO', ?, ?, ?, ?)
                    """, 1, ordemId, produto.fornecedorId(), produto.id(), total, armazemOrigem,
                    fragmentos.regiaoLocal(), ts, Timestamp.from(hora));
            actualizar(con, "INSERT INTO linha_ordem (ordem_id, armazem_id, quantidade) VALUES (?, ?, ?)",
                    1, ordemId, armazem, quantidade);
            actualizar(con, """
                    UPDATE stock SET em_encomenda = em_encomenda + ?, actualizado_em = now()
                     WHERE armazem_id = ? AND produto_id = ?
                    """, 1, quantidade, armazem, produto.id());
        }));

        return coordenador.executar(ordemId, "CRIAR_ORDEM", trabalho) == Decisao.COMMIT ? ordemId : null;
    }

    /** Passo 7: o fornecedor confirmou a ordem. Idempotente (um reenvio não altera nada). */
    public Decisao marcarEnviada(String ordemId, String confirmacao, LocalDate prazoEntrega) {
        OrdemCompra ordem = ordens.procurar(ordemId)
                .orElseThrow(() -> new OrdemInexistenteException(ordemId));
        Map<String, TrabalhoParticipante> trabalho = new LinkedHashMap<>();
        ordem.linhas().keySet().forEach(armazem -> trabalho.put(armazem, con ->
                actualizar(con, """
                        UPDATE ordem_compra SET estado = 'ENVIADA', confirmacao = ?, prazo_entrega = ?, actualizado_em = now()
                         WHERE id = ? AND estado = 'PENDENTE_ENVIO'
                        """, -1, confirmacao, prazoEntrega == null ? null : Date.valueOf(prazoEntrega), ordemId)));
        return coordenador.executar("ENV-" + ordemId, "MARCAR_ENVIADA", trabalho);
    }

    /** "Receber entrega": cada armazém soma a sua linha à quantidade e retira-a do em_encomenda. */
    public Decisao receberEntrega(String ordemId) {
        OrdemCompra ordem = ordens.procurar(ordemId)
                .orElseThrow(() -> new OrdemInexistenteException(ordemId));
        if (ordem.estado() != Estado.ENVIADA) {
            throw new EstadoInvalidoException(ordemId, ordem.estado());
        }
        long ts = lamport.tick();
        Timestamp hora = Timestamp.from(relogio.agora());
        String origem = fragmentos.regiaoLocal();

        Map<String, TrabalhoParticipante> trabalho = new LinkedHashMap<>();
        ordem.linhas().forEach((armazem, quantidade) -> trabalho.put(armazem, con -> {
            actualizar(con, """
                    UPDATE ordem_compra SET estado = 'ENTREGUE', actualizado_em = now()
                     WHERE id = ? AND estado = 'ENVIADA'
                    """, 1, ordemId);
            actualizar(con, """
                    UPDATE stock SET quantidade = quantidade + ?, em_encomenda = em_encomenda - ?, actualizado_em = now()
                     WHERE armazem_id = ? AND produto_id = ?
                    """, 1, quantidade, quantidade, armazem, ordem.produtoId());
            actualizar(con, """
                    INSERT INTO movimento_stock (armazem_id, produto_id, tipo, motivo, quantidade, ts_lamport, ts_corrigido, origem)
                    VALUES (?, ?, 'ENTRADA', ?, ?, ?, ?, ?)
                    """, 1, armazem, ordem.produtoId(), "Entrega " + ordemId, quantidade, ts, hora, origem);
        }));
        return coordenador.executar("ENT-" + ordemId, "RECEBER_ENTREGA", trabalho);
    }

    /**
     * Executa uma actualização e confirma o número de linhas afectadas
     * ({@code esperado < 0} = qualquer). Se não bater certo, o participante vota NÃO.
     */
    private static void actualizar(Connection con, String sql, int esperado, Object... args) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            int n = ps.executeUpdate();
            if (esperado >= 0 && n != esperado) {
                throw new SQLException("Esperava " + esperado + " linha(s) afectada(s), foram " + n + ": "
                        + sql.strip().lines().findFirst().orElse(sql));
            }
        }
    }

    public static class OrdemInexistenteException extends RuntimeException {
        public OrdemInexistenteException(String ordemId) {
            super("Ordem " + ordemId + " não existe");
        }
    }

    public static class EstadoInvalidoException extends RuntimeException {
        public EstadoInvalidoException(String ordemId, Estado estado) {
            super("A ordem " + ordemId + " está " + estado + "; só se recebe a entrega de ordens ENVIADAS");
        }
    }
}

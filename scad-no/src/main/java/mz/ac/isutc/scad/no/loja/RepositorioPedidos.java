package mz.ac.isutc.scad.no.loja;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import mz.ac.isutc.scad.no.bd.FragmentosBd;
import mz.ac.isutc.scad.no.loja.PedidoCliente.Estado;
import mz.ac.isutc.scad.no.loja.PedidoCliente.Linha;

/** Acesso às tabelas {@code pedido_cliente} e {@code linha_pedido_cliente} (fragmentadas por região). */
@Repository
public class RepositorioPedidos {

    private static final RowMapper<PedidoCliente> CABECALHO = (rs, i) -> {
        Timestamp decidido = rs.getTimestamp("decidido_em");
        return new PedidoCliente(rs.getString("id"), rs.getString("armazem_id"), rs.getString("cliente_nome"),
                rs.getString("cliente_telefone"), Estado.valueOf(rs.getString("estado")), rs.getBigDecimal("total"),
                rs.getString("pagamento_ref"), rs.getString("motivo_recusa"), rs.getString("recibo"),
                rs.getLong("ts_lamport"), rs.getTimestamp("criado_em").toInstant(),
                decidido == null ? null : decidido.toInstant(), List.of());
    };

    private final FragmentosBd fragmentos;

    public RepositorioPedidos(FragmentosBd fragmentos) {
        this.fragmentos = fragmentos;
    }

    /** Próximo identificador do fragmento local: {@code PED-<REGIAO>-000001}. */
    public String novoId() {
        long n = fragmentos.jdbcLocal().queryForObject("SELECT nextval('seq_pedido_cliente')", Long.class);
        return String.format("PED-%s-%06d", fragmentos.regiaoLocal(), n);
    }

    /** Preço de venda e nome de um produto do catálogo (replicado). */
    public Optional<Linha> precoDeVenda(String produtoId, int quantidade) {
        return fragmentos.jdbcLocal().query("SELECT nome, preco_venda FROM produto WHERE id = ?",
                (rs, i) -> new Linha(produtoId, rs.getString("nome"), quantidade, rs.getBigDecimal("preco_venda")),
                produtoId).stream().findFirst();
    }

    public void inserir(PedidoCliente p) {
        JdbcTemplate jdbc = fragmentos.jdbcLocal();
        jdbc.update("""
                INSERT INTO pedido_cliente (id, armazem_id, cliente_nome, cliente_telefone, estado, total,
                                            pagamento_ref, ts_lamport, criado_em)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, p.id(), p.armazemId(), p.clienteNome(), p.clienteTelefone(), p.estado().name(), p.total(),
                p.pagamentoRef(), p.tsLamport(), Timestamp.from(p.criadoEm()));
        for (Linha l : p.linhas()) {
            jdbc.update("""
                    INSERT INTO linha_pedido_cliente (pedido_id, armazem_id, produto_id, quantidade, preco_unitario)
                    VALUES (?, ?, ?, ?, ?)
                    """, p.id(), p.armazemId(), l.produtoId(), l.quantidade(), l.precoUnitario());
        }
    }

    /**
     * Passa um pedido de PENDENTE para {@code novo}, de forma atómica (só um autorizar/recusar ganha).
     *
     * @return false se o pedido não existir ou já não estiver PENDENTE
     */
    public boolean decidir(String pedidoId, Estado novo, String motivoRecusa, String recibo, Instant quando) {
        return fragmentos.jdbcLocal().update("""
                UPDATE pedido_cliente
                   SET estado = ?, motivo_recusa = ?, recibo = ?, decidido_em = ?
                 WHERE id = ? AND estado = 'PENDENTE'
                """, novo.name(), motivoRecusa, recibo, Timestamp.from(quando), pedidoId) == 1;
    }

    /** Pedido de qualquer região (lido do fragmento dessa região: funciona mesmo com o nó dela em baixo). */
    public Optional<PedidoCliente> procurar(String pedidoId) {
        JdbcTemplate jdbc = fragmentos.jdbc(PedidoCliente.regiaoDoId(pedidoId));
        return jdbc.query("SELECT * FROM pedido_cliente WHERE id = ?", CABECALHO, pedidoId).stream().findFirst()
                .map(p -> p.comLinhas(linhas(jdbc, List.of(pedidoId)).getOrDefault(pedidoId, List.of())));
    }

    /** Pedidos do armazém local, mais recentes primeiro (todos, ou só os de um estado). */
    public List<PedidoCliente> listar(Estado estado, int limite) {
        JdbcTemplate jdbc = fragmentos.jdbcLocal();
        List<PedidoCliente> cabecalhos = estado == null
                ? jdbc.query("SELECT * FROM pedido_cliente ORDER BY ts_lamport DESC LIMIT ?", CABECALHO, limite)
                : jdbc.query("SELECT * FROM pedido_cliente WHERE estado = ? ORDER BY ts_lamport DESC LIMIT ?",
                        CABECALHO, estado.name(), limite);
        Map<String, List<Linha>> linhas = linhas(jdbc, cabecalhos.stream().map(PedidoCliente::id).toList());
        return cabecalhos.stream().map(p -> p.comLinhas(linhas.getOrDefault(p.id(), List.of()))).toList();
    }

    /** Número de pedidos pendentes e total vendido (autorizado) desde {@code desde}. */
    public Resumo resumo(Instant desde) {
        return fragmentos.jdbcLocal().queryForObject("""
                SELECT count(*) FILTER (WHERE estado = 'PENDENTE') AS pendentes,
                       COALESCE(sum(total) FILTER (WHERE estado = 'AUTORIZADO' AND decidido_em >= ?), 0) AS vendas
                  FROM pedido_cliente
                """, (rs, i) -> new Resumo(rs.getInt("pendentes"), rs.getBigDecimal("vendas")), Timestamp.from(desde));
    }

    private static Map<String, List<Linha>> linhas(JdbcTemplate jdbc, List<String> ids) {
        Map<String, List<Linha>> porPedido = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return porPedido;
        }
        jdbc.query("""
                SELECT l.pedido_id, l.produto_id, p.nome, l.quantidade, l.preco_unitario
                  FROM linha_pedido_cliente l JOIN produto p ON p.id = l.produto_id
                 WHERE l.pedido_id = ANY (string_to_array(?, ','))
                 ORDER BY p.nome
                """, rs -> {
            porPedido.computeIfAbsent(rs.getString("pedido_id"), k -> new ArrayList<>()).add(new Linha(
                    rs.getString("produto_id"), rs.getString("nome"), rs.getInt("quantidade"),
                    rs.getBigDecimal("preco_unitario")));
        }, String.join(",", ids));
        return porPedido;
    }

    public record Resumo(int pendentes, BigDecimal vendasHoje) {
    }
}

package mz.ac.isutc.scad.no.stock;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import mz.ac.isutc.scad.no.bd.FragmentosBd;

/** Acesso à tabela {@code stock} do fragmento local. */
@Repository
public class RepositorioStock {

    private static final RowMapper<Stock> MAPEADOR = (rs, i) -> new Stock(
            rs.getString("armazem_id"), rs.getString("produto_id"), rs.getInt("quantidade"),
            rs.getInt("minimo"), rs.getInt("maximo"), rs.getInt("em_encomenda"));

    private final FragmentosBd fragmentos;

    public RepositorioStock(FragmentosBd fragmentos) {
        this.fragmentos = fragmentos;
    }

    public List<Stock> listar() {
        return fragmentos.jdbcLocal().query(
                "SELECT * FROM stock ORDER BY produto_id", MAPEADOR);
    }

    public Optional<Stock> procurar(String produtoId) {
        return procurarEm(fragmentos.regiaoLocal(), produtoId);
    }

    /** Stock do produto no fragmento de outra região (leitura distribuída). */
    public Optional<Stock> procurarEm(String regiao, String produtoId) {
        return fragmentos.jdbc(regiao).query(
                "SELECT * FROM stock WHERE produto_id = ?", MAPEADOR, produtoId).stream().findFirst();
    }

    /**
     * Retira {@code quantidade} de forma atómica: a condição {@code quantidade >= ?} e a
     * actualização são uma só instrução, por isso duas saídas concorrentes nunca deixam o
     * stock negativo. Devolve vazio se o produto não existir ou o stock não chegar.
     */
    public Optional<Stock> retirar(String produtoId, int quantidade) {
        return fragmentos.jdbcLocal().query("""
                UPDATE stock
                   SET quantidade = quantidade - ?, actualizado_em = now()
                 WHERE produto_id = ? AND quantidade >= ?
                RETURNING *
                """, MAPEADOR, quantidade, produtoId, quantidade).stream().findFirst();
    }

    public List<StockDetalhado> listarDetalhado() {
        return listarDetalhado(fragmentos.regiaoLocal());
    }

    public List<StockDetalhado> listarDetalhado(String regiao) {
        return fragmentos.jdbc(regiao).query("""
                SELECT s.*, p.nome AS produto_nome, p.unidade, p.lote,
                       f.id AS fornecedor_id, f.nome AS fornecedor_nome, f.protocolo
                  FROM stock s
                  JOIN produto p    ON p.id = s.produto_id
                  JOIN fornecedor f ON f.id = p.fornecedor_id
                 ORDER BY p.nome
                """, (rs, i) -> {
            Stock s = MAPEADOR.mapRow(rs, i);
            return new StockDetalhado(s.armazemId(), s.produtoId(), rs.getString("produto_nome"),
                    rs.getString("unidade"), rs.getString("fornecedor_id"), rs.getString("fornecedor_nome"),
                    rs.getString("protocolo"), rs.getInt("lote"),
                    s.quantidade(), s.minimo(), s.maximo(), s.emEncomenda(), s.nivel());
        });
    }
}

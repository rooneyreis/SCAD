package mz.ac.isutc.scad.no.stock;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import mz.ac.isutc.scad.no.bd.FragmentosBd;
import mz.ac.isutc.scad.no.stock.Movimento.TipoMovimento;

/** Acesso à tabela {@code movimento_stock} do fragmento local. */
@Repository
public class RepositorioMovimentos {

    private static final RowMapper<Movimento> MAPEADOR = (rs, i) -> new Movimento(
            rs.getLong("id"), rs.getString("armazem_id"), rs.getString("produto_id"),
            TipoMovimento.valueOf(rs.getString("tipo")), rs.getString("motivo"), rs.getInt("quantidade"),
            rs.getLong("ts_lamport"), rs.getTimestamp("ts_corrigido").toInstant(), rs.getString("origem"));

    private final FragmentosBd fragmentos;

    public RepositorioMovimentos(FragmentosBd fragmentos) {
        this.fragmentos = fragmentos;
    }

    /** Insere o movimento e devolve-o com o id gerado. */
    public Movimento inserir(Movimento m) {
        long id = fragmentos.jdbcLocal().queryForObject("""
                INSERT INTO movimento_stock
                       (armazem_id, produto_id, tipo, motivo, quantidade, ts_lamport, ts_corrigido, origem)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, m.armazemId(), m.produtoId(), m.tipo().name(), m.motivo(), m.quantidade(),
                m.tsLamport(), Timestamp.from(m.tsCorrigido()), m.origem());
        return new Movimento(id, m.armazemId(), m.produtoId(), m.tipo(), m.motivo(), m.quantidade(),
                m.tsLamport(), m.tsCorrigido(), m.origem());
    }

    public List<Movimento> recentes(int limite) {
        return recentes(fragmentos.regiaoLocal(), limite);
    }

    public List<Movimento> recentes(String regiao, int limite) {
        return fragmentos.jdbc(regiao).query(
                "SELECT * FROM movimento_stock ORDER BY ts_lamport DESC, id DESC LIMIT ?", MAPEADOR, limite);
    }

    /** Maior carimbo de Lamport já gravado neste fragmento (para o relógio não recuar após reinício). */
    public long maiorLamport() {
        Long max = fragmentos.jdbcLocal().queryForObject(
                "SELECT max(ts_lamport) FROM movimento_stock", Long.class);
        return max == null ? 0 : max;
    }
}

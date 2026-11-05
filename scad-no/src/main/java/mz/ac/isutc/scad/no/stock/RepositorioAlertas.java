package mz.ac.isutc.scad.no.stock;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import mz.ac.isutc.scad.no.bd.FragmentosBd;

/** Acesso à tabela {@code alerta} do fragmento local (também é a fila local de envio). */
@Repository
public class RepositorioAlertas {

    private static final RowMapper<Alerta> MAPEADOR = (rs, i) -> {
        Timestamp enviado = rs.getTimestamp("enviado_em");
        return new Alerta(rs.getObject("id", UUID.class), rs.getString("armazem_id"), rs.getString("produto_id"),
                rs.getInt("quantidade_actual"), rs.getInt("em_encomenda"), rs.getInt("minimo"),
                NivelStock.valueOf(rs.getString("nivel")), rs.getLong("ts_lamport"),
                rs.getTimestamp("ts_corrigido").toInstant(), enviado == null ? null : enviado.toInstant());
    };

    private final FragmentosBd fragmentos;

    public RepositorioAlertas(FragmentosBd fragmentos) {
        this.fragmentos = fragmentos;
    }

    public void inserir(Alerta a) {
        fragmentos.jdbcLocal().update("""
                INSERT INTO alerta (id, armazem_id, produto_id, quantidade_actual, em_encomenda, minimo,
                                    nivel, ts_lamport, ts_corrigido)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, a.id(), a.armazemId(), a.produtoId(), a.quantidadeActual(), a.emEncomenda(), a.minimo(),
                a.nivel().name(), a.tsLamport(), Timestamp.from(a.tsCorrigido()));
    }

    /** Alertas ainda não entregues à Central, pela ordem em que foram emitidos. */
    public List<Alerta> pendentes(int limite) {
        return fragmentos.jdbcLocal().query(
                "SELECT * FROM alerta WHERE enviado_em IS NULL ORDER BY ts_lamport LIMIT ?", MAPEADOR, limite);
    }

    public void marcarEnviado(UUID id, Instant quando) {
        fragmentos.jdbcLocal().update(
                "UPDATE alerta SET enviado_em = ? WHERE id = ?", Timestamp.from(quando), id);
    }

    public List<Alerta> recentes(int limite) {
        return fragmentos.jdbcLocal().query(
                "SELECT * FROM alerta ORDER BY ts_lamport DESC LIMIT ?", MAPEADOR, limite);
    }

    /**
     * true se o último alerta deste produto já descreve exactamente este estado
     * (mesma quantidade disponível e mesmo em_encomenda): evita alertas repetidos.
     */
    public boolean ultimoAlertaDescreve(Stock s) {
        List<Boolean> r = fragmentos.jdbcLocal().query("""
                SELECT quantidade_actual = ? AND em_encomenda = ?
                  FROM alerta
                 WHERE produto_id = ?
                 ORDER BY ts_lamport DESC
                 LIMIT 1
                """, (rs, i) -> rs.getBoolean(1), s.disponivel(), s.emEncomenda(), s.produtoId());
        return !r.isEmpty() && r.get(0);
    }

    public long maiorLamport() {
        Long max = fragmentos.jdbcLocal().queryForObject("SELECT max(ts_lamport) FROM alerta", Long.class);
        return max == null ? 0 : max;
    }
}

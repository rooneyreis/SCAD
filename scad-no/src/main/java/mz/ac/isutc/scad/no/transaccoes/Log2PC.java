package mz.ac.isutc.scad.no.transaccoes;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Repository;

import mz.ac.isutc.scad.no.bd.FragmentosBd;
import mz.ac.isutc.scad.no.transaccoes.Cronologia2PC.Decisao;

/** Log do coordenador do 2PC (tabela {@code log_2pc} no fragmento local do coordenador). */
@Repository
public class Log2PC {

    private final FragmentosBd fragmentos;

    public Log2PC(FragmentosBd fragmentos) {
        this.fragmentos = fragmentos;
    }

    /** Grava a decisão (em autocommit, fora das transacções preparadas). */
    public void gravarDecisao(String transacaoId, String tipo, List<String> participantes, Decisao decisao,
                              long tsLamport, Instant tsCorrigido) {
        fragmentos.jdbcLocal().update("""
                INSERT INTO log_2pc (transacao_id, tipo, coordenador, participantes, decisao, ts_lamport, ts_corrigido)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (transacao_id) DO NOTHING
                """, transacaoId, tipo, fragmentos.regiaoLocal(), String.join(",", participantes),
                decisao.name(), tsLamport, Timestamp.from(tsCorrigido));
    }

    public void marcarConcluida(String transacaoId) {
        fragmentos.jdbcLocal().update("UPDATE log_2pc SET concluida = true WHERE transacao_id = ?", transacaoId);
    }

    public List<Entrada> porConcluir() {
        return fragmentos.jdbcLocal().query(
                "SELECT * FROM log_2pc WHERE NOT concluida ORDER BY ts_lamport",
                (rs, i) -> new Entrada(rs.getString("transacao_id"), rs.getString("tipo"),
                        Arrays.asList(rs.getString("participantes").split(",")),
                        Decisao.valueOf(rs.getString("decisao"))));
    }

    public boolean existe(String transacaoId) {
        Integer n = fragmentos.jdbcLocal().queryForObject(
                "SELECT count(*) FROM log_2pc WHERE transacao_id = ?", Integer.class, transacaoId);
        return n != null && n > 0;
    }

    public record Entrada(String transacaoId, String tipo, List<String> participantes, Decisao decisao) {
    }
}

package mz.ac.isutc.scad.no.ordens;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import mz.ac.isutc.scad.no.bd.FragmentosBd;

/** Leitura do catálogo (replicado em todos os fragmentos; lê-se do fragmento local). */
@Repository
public class Catalogo {

    private final FragmentosBd fragmentos;

    public Catalogo(FragmentosBd fragmentos) {
        this.fragmentos = fragmentos;
    }

    public Optional<Produto> produto(String produtoId) {
        return fragmentos.jdbcLocal().query("""
                SELECT p.id, p.nome, p.lote, p.preco_custo, f.id AS fornecedor_id, f.nome AS fornecedor_nome, f.protocolo, f.endpoint
                  FROM produto p JOIN fornecedor f ON f.id = p.fornecedor_id
                 WHERE p.id = ?
                """, (rs, i) -> new Produto(rs.getString("id"), rs.getString("nome"), rs.getInt("lote"),
                rs.getString("fornecedor_id"), rs.getString("fornecedor_nome"), rs.getString("protocolo"),
                rs.getString("endpoint"), rs.getBigDecimal("preco_custo")), produtoId).stream().findFirst();
    }

    public record Produto(String id, String nome, int lote, String fornecedorId, String fornecedorNome,
                          String protocolo, String endpoint, BigDecimal precoCusto) {

        /** Recurso da exclusão mútua: um lote de um produto a um fornecedor. */
        public String recurso() {
            return fornecedorId + "/" + id;
        }
    }
}

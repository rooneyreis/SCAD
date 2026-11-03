package mz.ac.isutc.scad.no.ordens;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import mz.ac.isutc.scad.no.bd.FragmentosBd;
import mz.ac.isutc.scad.no.ordens.OrdemCompra.Estado;

/**
 * Leitura distribuída das ordens de compra.
 *
 * <p>O cabeçalho de uma ordem existe em cada fragmento que participa nela e cada linha só no
 * fragmento do seu armazém. Para reconstruir a ordem completa juntam-se os cabeçalhos e as
 * linhas de todos os fragmentos acessíveis (incluindo réplicas promovidas).
 */
@Repository
public class RepositorioOrdens {

    private static final RowMapper<OrdemCompra> CABECALHO = (rs, i) -> {
        Date prazo = rs.getDate("prazo_entrega");
        Timestamp actualizado = rs.getTimestamp("actualizado_em");
        return new OrdemCompra(rs.getString("id"), rs.getString("fornecedor_id"), rs.getString("fornecedor_nome"),
                rs.getString("protocolo"), rs.getString("produto_id"), rs.getString("produto_nome"),
                rs.getInt("quantidade_total"), Estado.valueOf(rs.getString("estado")), rs.getString("armazem_origem"),
                rs.getString("coordenador"), rs.getString("confirmacao"), prazo == null ? null : prazo.toLocalDate(),
                rs.getLong("ts_lamport"), rs.getTimestamp("ts_corrigido").toInstant(),
                actualizado == null ? null : actualizado.toInstant(), Map.of());
    };

    private static final String SELECT_CABECALHO = """
            SELECT o.*, f.nome AS fornecedor_nome, f.protocolo, p.nome AS produto_nome
              FROM ordem_compra o
              JOIN fornecedor f ON f.id = o.fornecedor_id
              JOIN produto p    ON p.id = o.produto_id
            """;

    private final FragmentosBd fragmentos;

    public RepositorioOrdens(FragmentosBd fragmentos) {
        this.fragmentos = fragmentos;
    }

    /** Todas as ordens (mais recentes primeiro), reconstruídas a partir de todos os fragmentos. */
    public List<OrdemCompra> listar(int limite) {
        Map<String, OrdemCompra> cabecalhos = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> linhas = new LinkedHashMap<>();
        for (String regiao : fragmentos.regioes()) {
            try {
                for (OrdemCompra o : fragmentos.jdbc(regiao).query(
                        SELECT_CABECALHO + " ORDER BY o.ts_lamport DESC LIMIT ?", CABECALHO, limite)) {
                    cabecalhos.merge(o.id(), o, RepositorioOrdens::maisAvancada);
                }
                fragmentos.jdbc(regiao).query("SELECT ordem_id, armazem_id, quantidade FROM linha_ordem", rs -> {
                    linhas.computeIfAbsent(rs.getString(1), k -> new TreeMap<>()).put(rs.getString(2), rs.getInt(3));
                });
            } catch (DataAccessException e) {
                // fragmento inacessível: mostra-se o que existe nos restantes
            }
        }
        List<OrdemCompra> resultado = new ArrayList<>();
        cabecalhos.values().forEach(o -> resultado.add(o.comLinhas(linhas.getOrDefault(o.id(), Map.of()))));
        resultado.sort(Comparator.comparing(OrdemCompra::tsCorrigido).reversed());
        return resultado.size() > limite ? resultado.subList(0, limite) : resultado;
    }

    public Optional<OrdemCompra> procurar(String ordemId) {
        OrdemCompra ordem = null;
        Map<String, Integer> linhas = new TreeMap<>();
        for (String regiao : fragmentos.regioes()) {
            try {
                for (OrdemCompra o : fragmentos.jdbc(regiao).query(SELECT_CABECALHO + " WHERE o.id = ?", CABECALHO, ordemId)) {
                    ordem = ordem == null ? o : maisAvancada(ordem, o);
                }
                fragmentos.jdbc(regiao).query("SELECT armazem_id, quantidade FROM linha_ordem WHERE ordem_id = ?",
                        rs -> {
                            linhas.put(rs.getString(1), rs.getInt(2));
                        }, ordemId);
            } catch (DataAccessException e) {
                // fragmento inacessível
            }
        }
        return Optional.ofNullable(ordem).map(o -> o.comLinhas(linhas));
    }

    /** Ordens à espera de envio ao fornecedor há mais de {@code segundos}. */
    public List<String> pendentesDeEnvio(int segundos) {
        return fragmentos.jdbcLocal().queryForList("""
                SELECT id FROM ordem_compra
                 WHERE estado = 'PENDENTE_ENVIO' AND actualizado_em < now() - make_interval(secs => ?)
                 ORDER BY ts_lamport
                """, String.class, segundos);
    }

    /** Durante um 2PC os fragmentos podem estar momentaneamente diferentes: mostra-se o estado mais avançado. */
    private static OrdemCompra maisAvancada(OrdemCompra a, OrdemCompra b) {
        return b.estado().ordinal() > a.estado().ordinal() ? b : a;
    }
}

package mz.ac.isutc.scad.no.loja;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import mz.ac.isutc.scad.no.bd.FragmentosBd;
import mz.ac.isutc.scad.no.exclusao.Membros;

/**
 * Catálogo da loja: produtos, preço e quantidade disponível ({@code quantidade − reservado}) em cada
 * cidade, lido directamente do fragmento de cada região.
 *
 * <p>Uma cidade fica "indisponível de momento" se o nó do seu armazém estiver em baixo (detector de
 * falhas) ou se o seu fragmento da BD não estiver acessível: as compras são feitas no nó da região.
 */
@Component
public class CatalogoLoja {

    private final FragmentosBd fragmentos;
    private final Membros membros;

    public CatalogoLoja(FragmentosBd fragmentos, Membros membros) {
        this.fragmentos = fragmentos;
        this.membros = membros;
    }

    public List<Cidade> cidades() {
        List<Cidade> cidades = new ArrayList<>();
        for (String regiao : fragmentos.regioes()) {
            boolean noVivo = regiao.equals(fragmentos.regiaoLocal()) || membros.vivos().contains(regiao);
            try {
                String cidade = fragmentos.jdbc(regiao).queryForObject(
                        "SELECT cidade FROM armazem WHERE id = ?", String.class, regiao);
                List<Produto> produtos = fragmentos.jdbc(regiao).query("""
                        SELECT p.id, p.nome, p.unidade, p.preco_venda, s.quantidade - s.reservado AS disponivel
                          FROM stock s JOIN produto p ON p.id = s.produto_id
                         ORDER BY p.nome
                        """, (rs, i) -> new Produto(rs.getString("id"), rs.getString("nome"), rs.getString("unidade"),
                        rs.getBigDecimal("preco_venda"), rs.getInt("disponivel")));
                cidades.add(new Cidade(regiao, cidade, noVivo, noVivo ? null : "Nó do armazém em baixo", produtos));
            } catch (DataAccessException e) {
                cidades.add(new Cidade(regiao, null, false, "Base de dados do armazém inacessível", List.of()));
            }
        }
        return cidades;
    }

    /** @param aberta false = "indisponível de momento" (ver {@code motivo}) */
    public record Cidade(String armazemId, String cidade, boolean aberta, String motivo, List<Produto> produtos) {
    }

    public record Produto(String id, String nome, String unidade, BigDecimal precoVenda, int disponivel) {
    }
}

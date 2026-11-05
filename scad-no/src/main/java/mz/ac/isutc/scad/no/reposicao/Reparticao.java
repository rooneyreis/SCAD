package mz.ac.isutc.scad.no.reposicao;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import mz.ac.isutc.scad.no.stock.Stock;

/**
 * Repartição de uma ordem consolidada (sempre exactamente 1 lote) pelos armazéns.
 *
 * <p>{@code necessidade = maximo − posicao}, com {@code posicao = (quantidade − reservado) + em_encomenda}
 * (o stock reservado por pedidos da loja já não conta como disponível).
 * <ol>
 *   <li><b>Prioridade 1</b>: o armazém que disparou recebe a sua necessidade (ou o lote todo, se não chegar).</li>
 *   <li><b>Prioridade 2</b>: armazéns "perto do mínimo" ({@code posicao ≤ 1,2 × min}), do mais vazio para
 *       o menos vazio (menor {@code posicao / min} primeiro); cada um recebe a sua necessidade até acabar o lote.</li>
 *   <li><b>Prioridade 3</b>: o que sobrar reparte-se pelos restantes, proporcionalmente à necessidade,
 *       sem passar do máximo; arredondamento para baixo, e o resto da divisão vai para o que disparou.</li>
 *   <li>Se todos ficarem no máximo e ainda sobrar lote, o excesso vai para o que disparou (mesmo passando o máximo).</li>
 * </ol>
 */
public final class Reparticao {

    private Reparticao() {
    }

    /**
     * @param lote           quantidade total da ordem
     * @param regiaoDisparo  armazém que disparou a reposição
     * @param stocks         stock do produto em cada armazém disponível (inclui o que disparou)
     * @return quantidade por armazém (só armazéns com quantidade &gt; 0), pela ordem de atribuição
     */
    public static Map<String, Integer> calcular(int lote, String regiaoDisparo, Map<String, Stock> stocks) {
        if (lote <= 0) {
            throw new IllegalArgumentException("O lote tem de ser positivo");
        }
        Stock disparo = stocks.get(regiaoDisparo);
        if (disparo == null) {
            throw new IllegalArgumentException("Falta o stock do armazém que disparou: " + regiaoDisparo);
        }

        Map<String, Integer> resultado = new LinkedHashMap<>();
        int restante = lote;

        // Prioridade 1: quem disparou
        int p1 = Math.min(disparo.necessidade(), restante);
        resultado.put(regiaoDisparo, p1);
        restante -= p1;

        List<Stock> outros = new ArrayList<>(stocks.values());
        outros.removeIf(s -> s.armazemId().equals(regiaoDisparo));

        // Prioridade 2: perto do mínimo, do mais vazio para o menos vazio
        List<Stock> pertoDoMinimo = outros.stream()
                .filter(Stock::pertoDoMinimo)
                .sorted(Comparator.comparing(Reparticao::ocupacaoRelativa).thenComparing(Stock::armazemId))
                .toList();
        for (Stock s : pertoDoMinimo) {
            int q = Math.min(s.necessidade(), restante);
            if (q > 0) {
                resultado.put(s.armazemId(), q);
                restante -= q;
            }
        }

        // Prioridade 3: restantes, proporcional à necessidade, sem passar do máximo
        List<Stock> restantes = outros.stream()
                .filter(s -> !s.pertoDoMinimo() && s.necessidade() > 0)
                .sorted(Comparator.comparing(Stock::armazemId))
                .toList();
        long necessidadeTotal = restantes.stream().mapToLong(Stock::necessidade).sum();
        if (restante > 0 && necessidadeTotal > 0) {
            int aRepartir = restante;
            for (Stock s : restantes) {
                int q = necessidadeTotal <= aRepartir
                        ? s.necessidade()                                              // chega para todos
                        : (int) ((long) aRepartir * s.necessidade() / necessidadeTotal); // proporcional, arredonda para baixo
                if (q > 0) {
                    resultado.put(s.armazemId(), q);
                    restante -= q;
                }
            }
        }

        // Resto da divisão e eventual excesso: para quem disparou
        if (restante > 0) {
            resultado.merge(regiaoDisparo, restante, Integer::sum);
        }
        resultado.values().removeIf(q -> q == 0);
        return resultado;
    }

    /** posicao / min (quanto menor, mais vazio está o armazém). */
    private static double ocupacaoRelativa(Stock s) {
        return s.minimo() == 0 ? Double.MAX_VALUE : (double) s.posicao() / s.minimo();
    }
}

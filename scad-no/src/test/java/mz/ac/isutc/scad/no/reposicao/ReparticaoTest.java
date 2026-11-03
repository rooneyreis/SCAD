package mz.ac.isutc.scad.no.reposicao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import mz.ac.isutc.scad.no.stock.Stock;

class ReparticaoTest {

    private static Stock s(String armazem, int q, int min, int max, int enc) {
        return new Stock(armazem, "P", q, min, max, enc);
    }

    private static Map<String, Stock> stocks(Stock... lista) {
        Map<String, Stock> m = new LinkedHashMap<>();
        for (Stock x : lista) {
            m.put(x.armazemId(), x);
        }
        return m;
    }

    private static int total(Map<String, Integer> r) {
        return r.values().stream().mapToInt(Integer::intValue).sum();
    }

    @Test
    void loteNaoChegaParaQuemDisparou_ficaComTudo() {
        var r = Reparticao.calcular(100, "NORTE", stocks(
                s("NORTE", 10, 100, 400, 0), s("CENTRO", 90, 100, 400, 0)));
        assertEquals(Map.of("NORTE", 100), r);
    }

    @Test
    void prioridade2_pertoDoMinimoRecebeDepoisDeQuemDisparou() {
        // Cenário FARINHA-25: SUL dispara (70/80, max 300), CENTRO perto do mínimo (90 <= 96), NORTE normal
        var r = Reparticao.calcular(400, "SUL", stocks(
                s("NORTE", 200, 80, 300, 0), s("CENTRO", 90, 80, 300, 0), s("SUL", 70, 80, 300, 0)));
        assertEquals(230, r.get("SUL"));        // necessidade 300 - 70
        assertEquals(170, r.get("CENTRO"));     // o que sobra (necessidade era 210)
        assertEquals(null, r.get("NORTE"));
        assertEquals(400, total(r));
    }

    @Test
    void prioridade2_maisVazioPrimeiro() {
        // CENTRO: 85/80 = 1,06 ; SUL: 95/80 = 1,19 -> CENTRO primeiro
        var r = Reparticao.calcular(450, "NORTE", stocks(
                s("NORTE", 50, 80, 300, 0), s("SUL", 95, 80, 300, 0), s("CENTRO", 85, 80, 300, 0)));
        assertEquals(250, r.get("NORTE"));
        assertEquals(200, r.get("CENTRO"));     // 450-250 = 200 < necessidade 215
        assertEquals(null, r.get("SUL"));
        assertEquals(List.of("NORTE", "CENTRO"), List.copyOf(r.keySet()));
    }

    @Test
    void prioridade2_contaComOQueJaEstaEncomendado() {
        // CENTRO 50 + 50 encomendado = 100 > 96: não está perto do mínimo
        var r = Reparticao.calcular(300, "NORTE", stocks(
                s("NORTE", 50, 80, 300, 0), s("CENTRO", 50, 80, 300, 50)));
        assertEquals(250, r.get("NORTE"));
        assertEquals(50, r.get("CENTRO"));      // vem da prioridade 3 (necessidade 200)
    }

    @Test
    void prioridade3_proporcionalANecessidade_restoParaQuemDisparou() {
        // NORTE dispara (55/60, max 250 -> nec. 195); CENTRO nec. 100, SUL nec. 110; sobram 105 para repartir
        var r = Reparticao.calcular(300, "NORTE", stocks(
                s("NORTE", 55, 60, 250, 0), s("CENTRO", 150, 60, 250, 0), s("SUL", 140, 60, 250, 0)));
        // 105 * 100/210 = 50 ; 105 * 110/210 = 55 -> 105, sem resto
        assertEquals(195, r.get("NORTE"));
        assertEquals(50, r.get("CENTRO"));
        assertEquals(55, r.get("SUL"));
        assertEquals(300, total(r));
    }

    @Test
    void prioridade3_arredondamentoParaBaixo_restoDaDivisaoParaQuemDisparou() {
        // Sobram 10 para necessidades 100 e 200: 10*100/300 = 3 ; 10*200/300 = 6 -> 9, resto 1 para NORTE
        var r = Reparticao.calcular(110, "NORTE", stocks(
                s("NORTE", 0, 50, 100, 0), s("CENTRO", 100, 50, 200, 0), s("SUL", 100, 50, 300, 0)));
        assertEquals(101, r.get("NORTE"));
        assertEquals(3, r.get("CENTRO"));
        assertEquals(6, r.get("SUL"));
        assertEquals(110, total(r));
    }

    @Test
    void excesso_todosNoMaximo_vaiParaQuemDisparou() {
        // Cenário OLEO-5: lote 600; NORTE 55/60 (nec. 195); CENTRO nec. 100; SUL nec. 110 -> sobram 195
        var r = Reparticao.calcular(600, "NORTE", stocks(
                s("NORTE", 55, 60, 250, 0), s("CENTRO", 150, 60, 250, 0), s("SUL", 140, 60, 250, 0)));
        assertEquals(195 + 195, r.get("NORTE"));  // passa o máximo
        assertEquals(100, r.get("CENTRO"));
        assertEquals(110, r.get("SUL"));
        assertEquals(600, total(r));
    }

    @Test
    void picoSimultaneo_arroz() {
        // NORTE dispara (95/100, max 400 -> nec. 305), CENTRO 94/100 (perto) nec. 306, SUL normal
        var r = Reparticao.calcular(500, "NORTE", stocks(
                s("NORTE", 95, 100, 400, 0), s("CENTRO", 94, 100, 400, 0), s("SUL", 300, 100, 400, 0)));
        assertEquals(305, r.get("NORTE"));
        assertEquals(195, r.get("CENTRO"));
        assertEquals(null, r.get("SUL"));
    }

    @Test
    void armazemNoMaximoNaoRecebe_eSemOutrosArmazens() {
        var r = Reparticao.calcular(500, "SUL", stocks(
                s("SUL", 10, 100, 200, 0), s("NORTE", 400, 100, 400, 0)));
        assertEquals(Map.of("SUL", 500), r);       // 190 de necessidade + 310 de excesso
    }

    @Test
    void validaEntradas() {
        assertThrows(IllegalArgumentException.class, () -> Reparticao.calcular(0, "NORTE",
                stocks(s("NORTE", 1, 10, 20, 0))));
        assertThrows(IllegalArgumentException.class, () -> Reparticao.calcular(10, "SUL",
                stocks(s("NORTE", 1, 10, 20, 0))));
    }
}

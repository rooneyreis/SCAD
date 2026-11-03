package mz.ac.isutc.scad.no.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StockTest {

    @ParameterizedTest(name = "q={0} enc={1} min={2} -> {3}")
    @CsvSource({
            "0,   0,   100, RUPTURA",
            "0,   500, 100, RUPTURA",   // prateleira vazia, mesmo com encomenda a caminho
            "99,  0,   100, CRITICO",
            "50,  49,  100, CRITICO",
            "50,  50,  100, AVISO",     // disponível = mínimo: não é crítico
            "120, 0,   100, AVISO",     // exactamente 1,2 x mínimo
            "121, 0,   100, NORMAL",
            "85,  0,   80,  AVISO",     // 85 <= 96
            "97,  0,   80,  NORMAL"     // 97 > 96
    })
    void classificaNivel(int quantidade, int emEncomenda, int minimo, NivelStock esperado) {
        assertEquals(esperado, NivelStock.classificar(quantidade, emEncomenda, minimo));
    }

    @Test
    void condicaoDeReposicaoContaComOQueEstaEncomendado() {
        assertTrue(new Stock("NORTE", "ARROZ-25", 95, 100, 400, 0).precisaReposicao());
        assertFalse(new Stock("NORTE", "ARROZ-25", 95, 100, 400, 200).precisaReposicao());
    }

    @Test
    void necessidadeEhOQueFaltaParaOMaximo() {
        assertEquals(305, new Stock("NORTE", "ARROZ-25", 95, 100, 400, 0).necessidade());
        assertEquals(105, new Stock("NORTE", "ARROZ-25", 95, 100, 400, 200).necessidade());
        assertEquals(0, new Stock("NORTE", "ARROZ-25", 450, 100, 400, 0).necessidade());
    }
}

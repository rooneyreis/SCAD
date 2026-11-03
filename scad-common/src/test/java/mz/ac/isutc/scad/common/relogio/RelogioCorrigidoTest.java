package mz.ac.isutc.scad.common.relogio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class RelogioCorrigidoTest {

    private final Clock fixo = Clock.fixed(Instant.ofEpochMilli(1_000_000), ZoneOffset.UTC);

    @Test
    void aplicaDesvioSimulado() {
        RelogioCorrigido relogio = new RelogioCorrigido(fixo, 2_500);
        assertEquals(1_002_500, relogio.agoraMs());
        assertEquals(2_500, relogio.desvioEfectivoMs());
    }

    @Test
    void ajustesBerkeleyAcumulamECorrigemODesvio() {
        RelogioCorrigido relogio = new RelogioCorrigido(fixo, 2_500);
        relogio.ajustar(-2_000);
        relogio.ajustar(-450);
        assertEquals(1_000_050, relogio.agoraMs());
        assertEquals(50, relogio.desvioEfectivoMs());
        assertEquals(-2_450, relogio.ajusteAcumuladoMs());
    }
}

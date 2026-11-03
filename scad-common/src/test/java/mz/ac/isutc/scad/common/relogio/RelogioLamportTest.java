package mz.ac.isutc.scad.common.relogio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class RelogioLamportTest {

    @Test
    void eventoLocalIncrementa() {
        RelogioLamport relogio = new RelogioLamport();
        assertEquals(1, relogio.tick());
        assertEquals(2, relogio.tick());
    }

    @Test
    void recepcaoUsaMaximoMaisUm() {
        RelogioLamport relogio = new RelogioLamport();
        relogio.tick();                         // C = 1
        assertEquals(11, relogio.receber(10));  // max(1, 10) + 1
        assertEquals(12, relogio.receber(3));   // carimbo antigo: max(11, 3) + 1
    }

    @Test
    void envioERecepcaoRespeitamCausalidade() {
        RelogioLamport a = new RelogioLamport();
        RelogioLamport b = new RelogioLamport();
        b.tick(); b.tick(); b.tick();

        long envio = a.tick();
        long recepcao = b.receber(envio);
        assertTrue(recepcao > envio, "a recepção tem de ter carimbo maior do que o envio");
    }

    @Test
    void ticksConcorrentesNuncaRepetemValores() throws InterruptedException {
        RelogioLamport relogio = new RelogioLamport();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 10_000; i++) {
            pool.submit(relogio::tick);
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(10_000, relogio.actual());
    }

    @Test
    void garantirMinimoNuncaRecua() {
        RelogioLamport relogio = new RelogioLamport();
        relogio.garantirMinimo(50);
        relogio.garantirMinimo(20);
        assertEquals(51, relogio.tick());
    }
}

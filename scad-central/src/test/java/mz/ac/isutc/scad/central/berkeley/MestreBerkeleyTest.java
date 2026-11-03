package mz.ac.isutc.scad.central.berkeley;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class MestreBerkeleyTest {

    @Test
    void medianaParaDescartarValoresAbsurdos() {
        // Com um relógio absurdo (+900 s), a mediana continua "no meio" dos valores normais
        assertEquals(700, MestreBerkeley.mediana(List.of(0L, 2500L, -1800L, 700L, 900_000L)));
        assertEquals(350, MestreBerkeley.mediana(List.of(0L, 2500L, -1800L, 700L)));
    }
}

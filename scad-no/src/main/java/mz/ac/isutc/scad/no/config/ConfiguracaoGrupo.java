package mz.ac.isutc.scad.no.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import mz.ac.isutc.scad.common.relogio.RelogioCorrigido;
import mz.ac.isutc.scad.common.relogio.RelogioLamport;
import mz.ac.isutc.scad.no.exclusao.ClienteInterno;
import mz.ac.isutc.scad.no.exclusao.DetectorFalhas;
import mz.ac.isutc.scad.no.exclusao.RicartAgrawala;

/**
 * Grupo de nós: endereços dos pares (variável {@code SCAD_PARES}, ex.:
 * {@code NORTE=http://no-norte:8080,CENTRO=http://no-centro:8080,SUL=http://no-sul:8080}),
 * detector de falhas e exclusão mútua de Ricart-Agrawala.
 */
@Configuration
public class ConfiguracaoGrupo {

    /** Região -> URL base de cada nó do grupo (incluindo este). */
    public record ParesGrupo(Map<String, String> urls) {
    }

    @Bean
    ParesGrupo paresGrupo(@Value("${scad.pares}") String pares) {
        Map<String, String> mapa = new LinkedHashMap<>();
        for (String par : pares.split(",")) {
            String[] partes = par.trim().split("=", 2);
            if (partes.length == 2 && !partes[0].isBlank()) {
                mapa.put(partes[0].trim(), partes[1].trim());
            }
        }
        return new ParesGrupo(Map.copyOf(mapa));
    }

    @Bean
    ClienteInterno clienteInterno(ParesGrupo pares) {
        return new ClienteInterno(pares.urls());
    }

    @Bean
    DetectorFalhas detectorFalhas(PropriedadesNo props, ParesGrupo pares, ClienteInterno cliente) {
        return new DetectorFalhas(props.regiao(), new java.util.TreeMap<>(pares.urls()), cliente);
    }

    @Bean(destroyMethod = "shutdownNow")
    ExecutorService enviosRA() {
        return Executors.newFixedThreadPool(6, r -> {
            Thread t = new Thread(r, "ra-envio");
            t.setDaemon(true);
            return t;
        });
    }

    @Bean
    RicartAgrawala ricartAgrawala(PropriedadesNo props, DetectorFalhas detector, ClienteInterno cliente,
                                  RelogioLamport lamport, RelogioCorrigido relogio, ExecutorService enviosRA,
                                  @Value("${scad.exclusao.timeout-ms:30000}") long timeoutMs) {
        return new RicartAgrawala(props.regiao(), detector, cliente, lamport, relogio, enviosRA, timeoutMs);
    }
}

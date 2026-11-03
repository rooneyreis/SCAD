package mz.ac.isutc.scad.central.berkeley;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import mz.ac.isutc.scad.central.corba.CentralAuditoriaImpl;
import mz.ac.isutc.scad.common.relogio.RelogioCorrigido;
import mz.ac.isutc.scad.corba.AgenteStock;

/**
 * Mestre do algoritmo de Berkeley (corre na Central de Auditoria).
 *
 * <p>Em cada ronda:
 * <ol>
 *   <li>pede a hora a cada agente ({@code obterRelogio}, CORBA) e mede o tempo de ida e volta (RTT);
 *       estima a diferença de cada relógio em relação ao mestre: {@code d = (t_agente + RTT/2) − t_mestre};</li>
 *   <li>calcula a média das diferenças (incluindo a do mestre, 0), <b>descartando valores absurdos</b>:
 *       diferenças a mais de {@code limite} da mediana;</li>
 *   <li>envia a cada agente o seu ajuste ({@code media − d}, CORBA {@code ajustarRelogio}) e ajusta o
 *       próprio relógio em {@code media}.</li>
 * </ol>
 * O resultado é que todos os relógios ficam de acordo entre si (não necessariamente com a hora "real").
 */
@Component
public class MestreBerkeley {

    private static final Logger log = LoggerFactory.getLogger(MestreBerkeley.class);

    private final CentralAuditoriaImpl central;
    private final RelogioCorrigido relogio;
    private final long limiteMs;

    public MestreBerkeley(CentralAuditoriaImpl central, RelogioCorrigido relogio,
                          @Value("${scad.berkeley.limite-ms:10000}") long limiteMs) {
        this.central = central;
        this.relogio = relogio;
        this.limiteMs = limiteMs;
    }

    @Scheduled(initialDelayString = "${scad.berkeley.intervalo-ms:20000}",
               fixedDelayString = "${scad.berkeley.intervalo-ms:20000}")
    public void ronda() {
        Map<String, AgenteStock> agentes = new LinkedHashMap<>(central.agentes());
        if (agentes.isEmpty()) {
            return;
        }

        // 1. Leitura dos relógios
        Map<String, Long> diferencas = new LinkedHashMap<>();
        agentes.forEach((regiao, agente) -> {
            try {
                long t0 = relogio.agoraMs();
                long tAgente = agente.obterRelogio();
                long t1 = relogio.agoraMs();
                long estimativa = tAgente + (t1 - t0) / 2;
                diferencas.put(regiao, estimativa - t1);
            } catch (RuntimeException e) {
                log.warn("Berkeley: agente {} não respondeu ({}); fica fora desta ronda", regiao, e.getClass().getSimpleName());
            }
        });
        if (diferencas.isEmpty()) {
            return;
        }

        // 2. Média sem valores absurdos (o mestre conta com diferença 0)
        List<Long> todas = new ArrayList<>(diferencas.values());
        todas.add(0L);
        long mediana = mediana(todas);
        List<Long> aceites = todas.stream().filter(d -> Math.abs(d - mediana) <= limiteMs).toList();
        long media = Math.round(aceites.stream().mapToLong(Long::longValue).average().orElse(0));

        // 3. Ajustes
        StringBuilder resumo = new StringBuilder();
        diferencas.forEach((regiao, d) -> {
            long ajuste = media - d;
            boolean descartado = Math.abs(d - mediana) > limiteMs;
            try {
                agentes.get(regiao).ajustarRelogio(ajuste);
                resumo.append(String.format(" %s: %+d ms -> ajuste %+d%s;", regiao, d, ajuste,
                        descartado ? " (descartado da média)" : ""));
            } catch (RuntimeException e) {
                resumo.append(String.format(" %s: ajuste falhou;", regiao));
            }
        });
        relogio.ajustar(media);
        log.info("Berkeley: média {} ms (mestre ajusta {} ms) |{}", media, media, resumo);
    }

    static long mediana(List<Long> valores) {
        List<Long> ordenados = valores.stream().sorted().toList();
        int n = ordenados.size();
        return n % 2 == 1 ? ordenados.get(n / 2) : (ordenados.get(n / 2 - 1) + ordenados.get(n / 2)) / 2;
    }
}

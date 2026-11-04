package mz.ac.isutc.scad.no.failover;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import mz.ac.isutc.scad.common.relogio.RelogioCorrigido;
import mz.ac.isutc.scad.no.bd.FragmentosBd;
import mz.ac.isutc.scad.no.bd.FragmentosBd.Fragmento;
import mz.ac.isutc.scad.no.bd.FragmentosBd.Ligacao;
import mz.ac.isutc.scad.no.documentos.RegistoErros;

/**
 * Failover dos fragmentos PostgreSQL (replicação mestre-escravo).
 *
 * <p>A cada 2 s, para cada fragmento ainda no primário:
 * <ol>
 *   <li>Se a réplica já <b>não</b> está em recuperação ({@code pg_is_in_recovery() = false}), outro
 *       nó já a promoveu: este nó passa também a usá-la.</li>
 *   <li>Se o primário falha {@value #FALHAS_PARA_PROMOVER} verificações seguidas (~6 s) e a réplica
 *       responde, este nó promove-a ({@code SELECT pg_promote()}) e passa a usá-la.</li>
 * </ol>
 * Qualquer nó pode promover (a operação é idempotente), por isso o failover não depende de um nó
 * específico estar vivo. Uma réplica promovida é sempre a autoridade: quando o primário antigo volta,
 * nunca é reutilizado (evita <i>split-brain</i>); tem de ser reintegrado manualmente como réplica.
 */
@Component
public class MonitorFailover {

    private static final Logger log = LoggerFactory.getLogger(MonitorFailover.class);
    static final int FALHAS_PARA_PROMOVER = 3;
    static final int ESPERA_EXTRA_NAO_RESPONSAVEL = 2;
    private static final int MAX_EVENTOS = 50;

    private final FragmentosBd fragmentos;
    private final RelogioCorrigido relogio;
    private final RegistoErros erros;
    private final Map<String, Integer> falhasSeguidas = new ConcurrentHashMap<>();
    private final Deque<EventoFailover> eventos = new ConcurrentLinkedDeque<>();

    public MonitorFailover(FragmentosBd fragmentos, RelogioCorrigido relogio, RegistoErros erros) {
        this.fragmentos = fragmentos;
        this.relogio = relogio;
        this.erros = erros;
    }

    @Scheduled(initialDelay = 1_000, fixedDelay = 2_000)
    public void verificar() {
        for (String regiao : fragmentos.regioes()) {
            Fragmento f = fragmentos.fragmento(regiao);
            if (f.replica() == null || f.emFailover()) {
                continue;
            }
            verificarFragmento(f);
        }
    }

    private void verificarFragmento(Fragmento f) {
        String regiao = f.regiao();
        Boolean replicaEmRecuperacao = emRecuperacao(f.replica());

        if (Boolean.FALSE.equals(replicaEmRecuperacao)) {
            f.usarReplica();
            registar(regiao, "Réplica " + f.replica().endereco() + " já é primária (promovida por outro nó); fragmento "
                    + regiao + " passa a usá-la");
            return;
        }

        if (responde(f.primario())) {
            falhasSeguidas.remove(regiao);
            return;
        }

        int falhas = falhasSeguidas.merge(regiao, 1, Integer::sum);
        int necessarias = FALHAS_PARA_PROMOVER + (responsavelPelaPromocao(regiao) ? 0 : ESPERA_EXTRA_NAO_RESPONSAVEL);
        log.warn("Primário do fragmento {} ({}) não responde ({}/{})", regiao, f.primario().endereco(),
                falhas, necessarias);
        if (falhas < necessarias) {
            return;
        }
        if (replicaEmRecuperacao == null) {
            registar(regiao, "Primário e réplica do fragmento " + regiao + " em baixo: fragmento indisponível");
            falhasSeguidas.put(regiao, 0);
            return;
        }
        promover(f);
    }

    /**
     * A réplica de cada fragmento está na máquina da região seguinte do anel (NORTE→CENTRO→SUL→NORTE),
     * por isso é o nó dessa região que promove primeiro. Os outros só promovem se ele também
     * não o fizer (ex.: a máquina inteira caiu), o que mantém o failover sem ponto único de falha.
     */
    private boolean responsavelPelaPromocao(String regiaoFragmento) {
        List<String> anel = new ArrayList<>(fragmentos.regioes());
        int i = anel.indexOf(regiaoFragmento);
        return anel.get((i + 1) % anel.size()).equals(fragmentos.regiaoLocal());
    }

    private void promover(Fragmento f) {
        if (Boolean.FALSE.equals(emRecuperacao(f.replica()))) {
            return;   // outro nó promoveu entretanto: o próximo ciclo passa a usá-la
        }
        try {
            f.replica().jdbc().queryForObject("SELECT pg_promote(true, 15)", Boolean.class);
        } catch (DataAccessException e) {
            // Outro nó pode ter promovido entretanto ("recovery is not in progress"): confirmamos abaixo
            log.info("pg_promote em {}: {}", f.replica().endereco(), e.getMostSpecificCause().getMessage());
        }
        if (Boolean.FALSE.equals(emRecuperacao(f.replica()))) {
            f.usarReplica();
            falhasSeguidas.remove(f.regiao());
            registar(f.regiao(), "Primário " + f.primario().endereco() + " caiu: réplica " + f.replica().endereco()
                    + " PROMOVIDA a primário por este nó");
        }
    }

    /** true/false = estado de recuperação; null = servidor inacessível. */
    private static Boolean emRecuperacao(Ligacao ligacao) {
        try {
            return ligacao.jdbc().queryForObject("SELECT pg_is_in_recovery()", Boolean.class);
        } catch (DataAccessException e) {
            return null;
        }
    }

    private static boolean responde(Ligacao ligacao) {
        try {
            ligacao.jdbc().queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (DataAccessException e) {
            return false;
        }
    }

    private void registar(String regiao, String descricao) {
        log.warn("FAILOVER: {}", descricao);
        erros.erro("FAILOVER", descricao);
        eventos.addFirst(new EventoFailover(relogio.agora(), regiao, descricao));
        while (eventos.size() > MAX_EVENTOS) {
            eventos.removeLast();
        }
    }

    public List<EventoFailover> eventos() {
        return new ArrayList<>(eventos);
    }

    public record EventoFailover(Instant hora, String regiao, String descricao) {
    }
}

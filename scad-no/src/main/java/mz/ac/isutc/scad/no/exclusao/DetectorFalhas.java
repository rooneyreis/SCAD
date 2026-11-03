package mz.ac.isutc.scad.no.exclusao;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Detector de falhas por heartbeats: a cada 1 s pergunta a cada par se está vivo
 * ({@code GET /interno/heartbeat}). Um par sem resposta há mais de {@value #SUSPEITA_MS} ms é
 * declarado MORTO (sai da lista de participantes); quando volta a responder é reintegrado.
 */
public class DetectorFalhas implements Membros {

    private static final Logger log = LoggerFactory.getLogger(DetectorFalhas.class);
    static final long SUSPEITA_MS = 3_000;

    private final String eu;
    private final Map<String, String> pares;          // região -> URL base
    private final ClienteInterno cliente;
    private final Map<String, Instant> ultimoContacto = new ConcurrentHashMap<>();
    private final Set<String> vivos = ConcurrentHashMap.newKeySet();
    private final List<Consumer<String>> aoMorrer = new CopyOnWriteArrayList<>();

    public DetectorFalhas(String eu, Map<String, String> pares, ClienteInterno cliente) {
        this.eu = eu;
        this.pares = pares;
        this.cliente = cliente;
        // No arranque, todos os pares são considerados vivos até prova em contrário
        pares.keySet().stream().filter(p -> !p.equals(eu)).forEach(p -> {
            vivos.add(p);
            ultimoContacto.put(p, Instant.now());
        });
    }

    @Scheduled(initialDelay = 1_000, fixedDelay = 1_000)
    public void verificar() {
        pares.forEach((no, url) -> {
            if (no.equals(eu)) {
                return;
            }
            boolean respondeu = cliente.heartbeat(url);
            Instant agora = Instant.now();
            if (respondeu) {
                ultimoContacto.put(no, agora);
                if (vivos.add(no)) {
                    log.warn("Detector de falhas: {} VOLTOU (reintegrado)", no);
                }
            } else if (vivos.contains(no)
                    && agora.toEpochMilli() - ultimoContacto.getOrDefault(no, agora).toEpochMilli() > SUSPEITA_MS) {
                vivos.remove(no);
                log.warn("Detector de falhas: {} MORTO (sem heartbeat há > {} ms)", no, SUSPEITA_MS);
                aoMorrer.forEach(a -> a.accept(no));
            }
        });
    }

    @Override
    public Set<String> vivos() {
        return Set.copyOf(vivos);
    }

    @Override
    public void aoMorrer(Consumer<String> accao) {
        aoMorrer.add(accao);
    }

    /** Estado de cada par (para o painel). */
    public Map<String, String> estado() {
        Map<String, String> m = new TreeMap<>();
        pares.keySet().forEach(p -> m.put(p, p.equals(eu) ? "EU" : vivos.contains(p) ? "VIVO" : "MORTO"));
        return m;
    }

    @Override
    public String toString() {
        return pares.keySet().stream().map(p -> p + "=" + estado().get(p)).collect(Collectors.joining(", "));
    }

    public List<String> pares() {
        return new ArrayList<>(pares.keySet());
    }
}

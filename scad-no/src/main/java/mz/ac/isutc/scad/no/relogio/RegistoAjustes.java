package mz.ac.isutc.scad.no.relogio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.stereotype.Component;

import mz.ac.isutc.scad.common.relogio.RelogioCorrigido;

/**
 * Histórico do relógio corrigido deste nó: ajustes do mestre Berkeley (com o desvio antes e
 * depois de cada ronda) e perturbações manuais feitas na demonstração.
 */
@Component
public class RegistoAjustes {

    private static final int MAX = 30;

    private final RelogioCorrigido relogio;
    private final Deque<Ajuste> historico = new ConcurrentLinkedDeque<>();

    public RegistoAjustes(RelogioCorrigido relogio) {
        this.relogio = relogio;
    }

    public synchronized Ajuste aplicar(long deltaMs, String origem) {
        long antes = relogio.desvioEfectivoMs();
        relogio.ajustar(deltaMs);
        Ajuste a = new Ajuste(Instant.now(), origem, antes, deltaMs, relogio.desvioEfectivoMs());
        historico.addFirst(a);
        while (historico.size() > MAX) {
            historico.removeLast();
        }
        return a;
    }

    public List<Ajuste> historico() {
        return new ArrayList<>(historico);
    }

    /** @param antesMs / depoisMs desvio em relação ao relógio do sistema, antes e depois do ajuste */
    public record Ajuste(Instant hora, String origem, long antesMs, long deltaMs, long depoisMs) {
    }
}

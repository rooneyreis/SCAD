package mz.ac.isutc.scad.common.relogio;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Relógio físico próprio do nó, sincronizado pelo algoritmo de Berkeley.
 *
 * <p>Os relógios reais dos PCs quase não se desviam, por isso cada nó simula um desvio
 * configurável ({@code CLOCK_SKEW_MS}). O relógio do sistema operativo nunca é alterado:
 * <pre>
 *   agora = relógio do sistema + desvio simulado + ajuste acumulado (Berkeley)
 * </pre>
 * Se a Central (mestre Berkeley) cair, o último ajuste mantém-se.
 */
public final class RelogioCorrigido {

    private final Clock sistema;
    private final long desvioSimuladoMs;
    private final AtomicLong ajusteMs = new AtomicLong();

    public RelogioCorrigido(long desvioSimuladoMs) {
        this(Clock.systemUTC(), desvioSimuladoMs);
    }

    public RelogioCorrigido(Clock sistema, long desvioSimuladoMs) {
        this.sistema = sistema;
        this.desvioSimuladoMs = desvioSimuladoMs;
    }

    /** Hora corrigida em milissegundos desde a época. */
    public long agoraMs() {
        return sistema.millis() + desvioSimuladoMs + ajusteMs.get();
    }

    public Instant agora() {
        return Instant.ofEpochMilli(agoraMs());
    }

    /** Aplica o ajuste enviado pelo mestre Berkeley (acumula com os anteriores). */
    public void ajustar(long deltaMs) {
        ajusteMs.addAndGet(deltaMs);
    }

    /** Diferença actual em relação ao relógio do sistema (desvio simulado + ajustes). */
    public long desvioEfectivoMs() {
        return desvioSimuladoMs + ajusteMs.get();
    }

    public long desvioSimuladoMs() {
        return desvioSimuladoMs;
    }

    public long ajusteAcumuladoMs() {
        return ajusteMs.get();
    }
}

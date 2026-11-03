package mz.ac.isutc.scad.common.relogio;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Relógio lógico de Lamport.
 *
 * <ul>
 *   <li>Evento local ou envio de mensagem: {@code C = C + 1}</li>
 *   <li>Recepção de mensagem com carimbo T: {@code C = max(C, T) + 1}</li>
 * </ul>
 * Garante que, se a → b (a "aconteceu antes" de b), então C(a) &lt; C(b).
 */
public final class RelogioLamport {

    private final AtomicLong contador = new AtomicLong();

    /** Regista um evento local (ou um envio) e devolve o novo carimbo. */
    public long tick() {
        return contador.incrementAndGet();
    }

    /** Regista a recepção de uma mensagem com o carimbo {@code recebido} e devolve o novo carimbo. */
    public long receber(long recebido) {
        return contador.updateAndGet(actual -> Math.max(actual, recebido) + 1);
    }

    /** Valor actual, sem avançar o relógio. */
    public long actual() {
        return contador.get();
    }

    /** Garante que o relógio nunca recua abaixo de {@code valor} (ex.: depois de reiniciar o nó). */
    public void garantirMinimo(long valor) {
        contador.accumulateAndGet(valor, Math::max);
    }
}

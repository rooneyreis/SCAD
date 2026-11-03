package mz.ac.isutc.scad.no.stock;

import java.time.Instant;

/** Movimento de stock, com carimbo de Lamport e hora corrigida (Berkeley). */
public record Movimento(long id, String armazemId, String produtoId, TipoMovimento tipo, String motivo,
                        int quantidade, long tsLamport, Instant tsCorrigido, String origem) {

    public enum TipoMovimento { SAIDA, ENTRADA }
}

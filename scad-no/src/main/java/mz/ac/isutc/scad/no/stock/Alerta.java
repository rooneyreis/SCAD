package mz.ac.isutc.scad.no.stock;

import java.time.Instant;
import java.util.UUID;

/**
 * Alerta de stock emitido pelo agente deste armazém.
 *
 * @param quantidadeActual quantidade disponível (quantidade − reservado) no momento do alerta
 * @param enviadoEm null enquanto o alerta estiver na fila local à espera de ser entregue à Central
 */
public record Alerta(UUID id, String armazemId, String produtoId, int quantidadeActual, int emEncomenda,
                     int minimo, NivelStock nivel, long tsLamport, Instant tsCorrigido, Instant enviadoEm) {
}

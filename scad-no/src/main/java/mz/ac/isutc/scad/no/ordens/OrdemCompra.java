package mz.ac.isutc.scad.no.ordens;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * Ordem de compra consolidada (cabeçalho + repartição por armazém).
 *
 * @param linhas quantidade por armazém (vem dos fragmentos de cada região)
 */
public record OrdemCompra(String id, String fornecedorId, String fornecedorNome, String protocolo,
                          String produtoId, String produtoNome, int quantidadeTotal, Estado estado,
                          String armazemOrigem, String coordenador, String confirmacao, LocalDate prazoEntrega,
                          long tsLamport, Instant tsCorrigido, Instant actualizadoEm, Map<String, Integer> linhas) {

    public enum Estado { PENDENTE_ENVIO, ENVIADA, ENTREGUE }

    public OrdemCompra comLinhas(Map<String, Integer> novas) {
        return new OrdemCompra(id, fornecedorId, fornecedorNome, protocolo, produtoId, produtoNome, quantidadeTotal,
                estado, armazemOrigem, coordenador, confirmacao, prazoEntrega, tsLamport, tsCorrigido, actualizadoEm,
                novas);
    }
}

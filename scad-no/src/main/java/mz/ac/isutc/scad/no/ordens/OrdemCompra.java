package mz.ac.isutc.scad.no.ordens;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ordem de compra consolidada (cabeçalho + repartição por armazém).
 *
 * @param precoUnitario preço de custo unitário (MZN) fixado quando a ordem foi criada
 * @param linhas quantidade por armazém (vem dos fragmentos de cada região)
 */
public record OrdemCompra(String id, String fornecedorId, String fornecedorNome, String protocolo,
                          String produtoId, String produtoNome, int quantidadeTotal, BigDecimal precoUnitario, Estado estado,
                          String armazemOrigem, String coordenador, String confirmacao, LocalDate prazoEntrega,
                          long tsLamport, Instant tsCorrigido, Instant actualizadoEm, Map<String, Integer> linhas) {

    public enum Estado { PENDENTE_ENVIO, ENVIADA, ENTREGUE }

    /** Valor total da ordem: quantidade × preço de custo. */
    @JsonProperty
    public BigDecimal total() {
        return precoUnitario.multiply(BigDecimal.valueOf(quantidadeTotal));
    }

    public OrdemCompra comLinhas(Map<String, Integer> novas) {
        return new OrdemCompra(id, fornecedorId, fornecedorNome, protocolo, produtoId, produtoNome, quantidadeTotal,
                precoUnitario, estado, armazemOrigem, coordenador, confirmacao, prazoEntrega, tsLamport, tsCorrigido, actualizadoEm,
                novas);
    }
}

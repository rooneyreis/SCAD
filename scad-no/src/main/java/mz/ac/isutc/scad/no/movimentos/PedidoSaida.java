package mz.ac.isutc.scad.no.movimentos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Pedido de saída de stock (vendas, quebras, transferências...). */
public record PedidoSaida(
        @NotBlank String armazemId,
        @NotBlank String produtoId,
        @Positive @Max(100_000) int quantidade,
        @NotBlank @Size(max = 200) String motivo) {
}

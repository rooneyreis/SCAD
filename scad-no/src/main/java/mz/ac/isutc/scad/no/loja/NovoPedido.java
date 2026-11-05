package mz.ac.isutc.scad.no.loja;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Compra feita na loja: armazém (cidade), cliente e produtos. Sem contas nem pagamento real. */
public record NovoPedido(
        @NotBlank String armazemId,
        @NotBlank @Size(max = 80) String clienteNome,
        @NotBlank @Pattern(regexp = "\\+?[0-9 ]{9,16}", message = "telefone inválido") String clienteTelefone,
        @NotEmpty @Size(max = 20) List<@Valid Item> itens) {

    public record Item(@NotBlank String produtoId, @Positive @Max(1_000) int quantidade) {
    }
}

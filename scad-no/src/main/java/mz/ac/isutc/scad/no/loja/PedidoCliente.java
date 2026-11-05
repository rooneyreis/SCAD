package mz.ac.isutc.scad.no.loja;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Pedido de um cliente da loja a um armazém (tabela {@code pedido_cliente}, no fragmento da região).
 *
 * <p>Ciclo de vida: {@code PENDENTE} (stock reservado) → {@code AUTORIZADO} (saída de stock) ou
 * {@code RECUSADO} (reserva libertada).
 *
 * @param pagamentoRef referência do pagamento simulado
 * @param recibo       caminho do recibo PDF no HDFS (só nos pedidos autorizados)
 * @param criadoEm     hora corrigida (Berkeley) da criação
 */
public record PedidoCliente(String id, String armazemId, String clienteNome, String clienteTelefone, Estado estado,
                            BigDecimal total, String pagamentoRef, String motivoRecusa, String recibo,
                            long tsLamport, Instant criadoEm, Instant decididoEm, List<Linha> linhas) {

    public enum Estado { PENDENTE, AUTORIZADO, RECUSADO }

    private static final DateTimeFormatter ANO_MES = DateTimeFormatter.ofPattern("yyyy/MM").withZone(ZoneId.of("Africa/Maputo"));

    /** Uma linha do pedido; o preço de venda fica fixado no momento da compra. */
    public record Linha(String produtoId, String produtoNome, int quantidade, BigDecimal precoUnitario) {

        @JsonProperty
        public BigDecimal subtotal() {
            return precoUnitario.multiply(BigDecimal.valueOf(quantidade));
        }
    }

    /** Região de um pedido a partir do id ({@code PED-SUL-000001} → {@code SUL}). */
    public static String regiaoDoId(String pedidoId) {
        String[] partes = pedidoId == null ? new String[0] : pedidoId.split("-");
        if (partes.length != 3 || !partes[0].equals("PED")) {
            throw new IllegalArgumentException("Identificador de pedido inválido: " + pedidoId);
        }
        return partes[1];
    }

    /** Caminho do recibo no HDFS: {@code /scad/recibos/AAAA/MM/<id>.pdf} (mês da autorização). */
    public static String caminhoRecibo(String pedidoId, Instant autorizadoEm) {
        return "/scad/recibos/" + ANO_MES.format(autorizadoEm) + "/" + pedidoId + ".pdf";
    }

    public PedidoCliente comLinhas(List<Linha> novas) {
        return new PedidoCliente(id, armazemId, clienteNome, clienteTelefone, estado, total, pagamentoRef,
                motivoRecusa, recibo, tsLamport, criadoEm, decididoEm, novas);
    }
}

package mz.ac.isutc.scad.no.loja;

import mz.ac.isutc.scad.no.loja.PedidoCliente.Estado;

/** Erros de negócio da loja e da autorização de pedidos. */
public final class ErrosLoja {

    private ErrosLoja() {
    }

    /** Outro cliente reservou primeiro (ou o armazém retirou o stock entretanto). */
    public static class StockIndisponivel extends RuntimeException {
        private final String produtoId;
        private final int disponivel;

        public StockIndisponivel(String produtoId, String produtoNome, int pedido, int disponivel) {
            super(disponivel == 0
                    ? produtoNome + " esgotou entretanto: outro cliente comprou as últimas unidades."
                    : "Já só há " + disponivel + " de " + produtoNome + " disponíveis (pediu " + pedido + ").");
            this.produtoId = produtoId;
            this.disponivel = disponivel;
        }

        public String produtoId() {
            return produtoId;
        }

        public int disponivel() {
            return disponivel;
        }
    }

    public static class PedidoInexistente extends RuntimeException {
        public PedidoInexistente(String pedidoId) {
            super("Pedido " + pedidoId + " não existe");
        }
    }

    /** Autorizar/recusar só se aplica a pedidos PENDENTES (evita decidir duas vezes). */
    public static class PedidoJaDecidido extends RuntimeException {
        public PedidoJaDecidido(String pedidoId, Estado estado) {
            super("O pedido " + pedidoId + " já está " + estado);
        }
    }
}

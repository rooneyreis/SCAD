package mz.ac.isutc.scad.no.movimentos;

/** Erros de negócio ao registar movimentos de stock. */
public final class ErrosMovimento {

    private ErrosMovimento() {
    }

    public static class ProdutoNaoEncontrado extends RuntimeException {
        public ProdutoNaoEncontrado(String produtoId, String armazemId) {
            super("Produto " + produtoId + " não existe no armazém " + armazemId);
        }
    }

    public static class StockInsuficiente extends RuntimeException {
        public StockInsuficiente(String produtoId, int pedido, int disponivel) {
            super("Stock insuficiente de " + produtoId + ": pedido " + pedido + ", disponível " + disponivel);
        }
    }

    /** A fragmentação é por região: cada nó só escreve no armazém do seu fragmento. */
    public static class ArmazemForaDoFragmento extends RuntimeException {
        public ArmazemForaDoFragmento(String armazemId, String regiaoLocal) {
            super("O armazém " + armazemId + " não pertence a este nó (fragmento " + regiaoLocal
                    + "); envie o pedido ao nó " + armazemId);
        }
    }
}

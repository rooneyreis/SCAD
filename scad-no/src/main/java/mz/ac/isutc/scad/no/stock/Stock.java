package mz.ac.isutc.scad.no.stock;

/**
 * Saldo de um produto num armazém (linha da tabela {@code stock}).
 *
 * @param reservado unidades reservadas por pedidos da loja ainda por autorizar
 */
public record Stock(String armazemId, String produtoId, int quantidade, int minimo, int maximo, int emEncomenda,
                    int reservado) {

    /** Stock sem reservas. */
    public Stock(String armazemId, String produtoId, int quantidade, int minimo, int maximo, int emEncomenda) {
        this(armazemId, produtoId, quantidade, minimo, maximo, emEncomenda, 0);
    }

    /** O que ainda se pode vender ou retirar: quantidade − reservado. */
    public int disponivel() {
        return quantidade - reservado;
    }

    /** Posição de stock: disponível + em_encomenda (o que conta para decidir a reposição). */
    public int posicao() {
        return disponivel() + emEncomenda;
    }

    /** Condição de reposição: (quantidade − reservado) + em_encomenda &lt; minimo. */
    public boolean precisaReposicao() {
        return posicao() < minimo;
    }

    /** "Perto do mínimo": posição &le; 1,2 × minimo. */
    public boolean pertoDoMinimo() {
        return NivelStock.pertoDoMinimo(posicao(), minimo);
    }

    /** Quanto falta para a posição chegar ao máximo (nunca negativo). */
    public int necessidade() {
        return Math.max(0, maximo - posicao());
    }

    public NivelStock nivel() {
        return NivelStock.classificar(quantidade, posicao(), minimo);
    }
}

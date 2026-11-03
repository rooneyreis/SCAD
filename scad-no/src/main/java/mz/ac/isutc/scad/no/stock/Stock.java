package mz.ac.isutc.scad.no.stock;

/** Saldo de um produto num armazém (linha da tabela {@code stock}). */
public record Stock(String armazemId, String produtoId, int quantidade, int minimo, int maximo, int emEncomenda) {

    /** quantidade + em_encomenda */
    public int disponivel() {
        return quantidade + emEncomenda;
    }

    /** Condição de reposição: quantidade + em_encomenda &lt; minimo. */
    public boolean precisaReposicao() {
        return disponivel() < minimo;
    }

    /** "Perto do mínimo": quantidade + em_encomenda &le; 1,2 × minimo. */
    public boolean pertoDoMinimo() {
        return NivelStock.pertoDoMinimo(disponivel(), minimo);
    }

    /** Quanto falta para chegar ao máximo (nunca negativo). */
    public int necessidade() {
        return Math.max(0, maximo - disponivel());
    }

    public NivelStock nivel() {
        return NivelStock.classificar(quantidade, emEncomenda, minimo);
    }
}

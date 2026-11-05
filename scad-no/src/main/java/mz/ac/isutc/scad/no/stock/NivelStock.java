package mz.ac.isutc.scad.no.stock;

/**
 * Nível de um stock, do mais grave para o menos grave.
 *
 * <p>Conta com a <b>posição</b> de stock: {@code posicao = (quantidade − reservado) + em_encomenda}
 * (ver {@link Stock#posicao()}).
 */
public enum NivelStock {

    /** Prateleira vazia (quantidade = 0). */
    RUPTURA,
    /** posicao &lt; minimo: é preciso repor. */
    CRITICO,
    /** posicao &le; 1,2 × minimo: "perto do mínimo". */
    AVISO,
    NORMAL;

    public static NivelStock classificar(int quantidade, int posicao, int minimo) {
        if (quantidade == 0) {
            return RUPTURA;
        }
        if (posicao < minimo) {
            return CRITICO;
        }
        if (pertoDoMinimo(posicao, minimo)) {
            return AVISO;
        }
        return NORMAL;
    }

    /** posicao &le; 1,2 × minimo, em aritmética inteira: 5·posicao &le; 6·minimo. */
    static boolean pertoDoMinimo(int posicao, int minimo) {
        return 5L * posicao <= 6L * minimo;
    }

    public boolean requerAlerta() {
        return this != NORMAL;
    }
}

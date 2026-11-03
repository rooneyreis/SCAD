package mz.ac.isutc.scad.no.stock;

/**
 * Nível de um stock, do mais grave para o menos grave.
 *
 * <p>O "nível disponível" conta também o que já está encomendado:
 * {@code disponivel = quantidade + em_encomenda}.
 */
public enum NivelStock {

    /** Prateleira vazia (quantidade = 0). */
    RUPTURA,
    /** disponivel &lt; minimo: é preciso repor. */
    CRITICO,
    /** disponivel &le; 1,2 × minimo: "perto do mínimo". */
    AVISO,
    NORMAL;

    public static NivelStock classificar(int quantidade, int emEncomenda, int minimo) {
        int disponivel = quantidade + emEncomenda;
        if (quantidade == 0) {
            return RUPTURA;
        }
        if (disponivel < minimo) {
            return CRITICO;
        }
        if (pertoDoMinimo(disponivel, minimo)) {
            return AVISO;
        }
        return NORMAL;
    }

    /** disponivel &le; 1,2 × minimo, em aritmética inteira: 5·disponivel &le; 6·minimo. */
    static boolean pertoDoMinimo(int disponivel, int minimo) {
        return 5L * disponivel <= 6L * minimo;
    }

    public boolean requerAlerta() {
        return this != NORMAL;
    }
}

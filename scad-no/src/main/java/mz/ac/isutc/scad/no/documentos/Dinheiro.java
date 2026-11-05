package mz.ac.isutc.scad.no.documentos;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/** Formatação de valores em meticais para os documentos: "1 450,00 MZN". */
public final class Dinheiro {

    private Dinheiro() {
    }

    public static String formatar(BigDecimal valor) {
        if (valor == null) {
            return "-";
        }
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols();
        simbolos.setDecimalSeparator(',');
        simbolos.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0.00", simbolos).format(valor) + " MZN";
    }
}

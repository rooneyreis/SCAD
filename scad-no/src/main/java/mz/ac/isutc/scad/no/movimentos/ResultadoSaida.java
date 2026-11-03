package mz.ac.isutc.scad.no.movimentos;

import mz.ac.isutc.scad.no.stock.Alerta;
import mz.ac.isutc.scad.no.stock.Movimento;
import mz.ac.isutc.scad.no.stock.NivelStock;
import mz.ac.isutc.scad.no.stock.Stock;

/**
 * @param alerta alerta emitido por esta saída (null se o nível não o exigir)
 */
public record ResultadoSaida(Movimento movimento, Stock stockApos, NivelStock nivel,
                             boolean precisaReposicao, Alerta alerta) {
}

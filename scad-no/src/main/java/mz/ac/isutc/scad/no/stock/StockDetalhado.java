package mz.ac.isutc.scad.no.stock;

/** Stock com os dados do catálogo, para mostrar no painel. */
public record StockDetalhado(String armazemId, String produtoId, String produtoNome, String unidade,
                             String fornecedorId, String fornecedorNome, String protocolo, int lote,
                             int quantidade, int minimo, int maximo, int emEncomenda, NivelStock nivel) {
}

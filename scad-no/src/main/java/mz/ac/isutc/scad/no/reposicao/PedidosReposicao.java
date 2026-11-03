package mz.ac.isutc.scad.no.reposicao;

/** Ponto de entrada para pedir a reposição de um produto do armazém local (assíncrono). */
public interface PedidosReposicao {

    void solicitar(String produtoId);
}

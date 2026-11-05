package mz.ac.isutc.scad.no.loja;

/** Gera o recibo PDF de um pedido autorizado e grava-o no HDFS (em segundo plano, via fila local). */
public interface ArquivoRecibos {

    void arquivarRecibo(PedidoCliente pedido);
}

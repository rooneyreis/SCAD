package mz.ac.isutc.scad.no.fornecedores;

import mz.ac.isutc.scad.fornecedor.contrato.ConfirmacaoOrdem;
import mz.ac.isutc.scad.fornecedor.contrato.PedidoOrdem;

/**
 * Adapter (padrão de desenho) para a integração com fornecedores: o gateway trabalha com esta
 * interface e o campo {@code fornecedor.protocolo} decide qual a implementação (REST ou SOAP).
 */
public interface ClienteFornecedor {

    /** "REST" ou "SOAP". */
    String protocolo();

    /**
     * Submete a ordem. O {@code ordemId} do pedido é a chave de idempotência.
     *
     * @throws FalhaEnvio com {@code temporaria=true} se valer a pena tentar outra vez
     */
    ConfirmacaoOrdem submeter(String url, PedidoOrdem pedido) throws FalhaEnvio;

    class FalhaEnvio extends Exception {
        private final boolean temporaria;

        public FalhaEnvio(String mensagem, boolean temporaria, Throwable causa) {
            super(mensagem, causa);
            this.temporaria = temporaria;
        }

        public boolean temporaria() {
            return temporaria;
        }
    }
}

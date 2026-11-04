package mz.ac.isutc.scad.fornecedor.contrato;

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;

/**
 * Serviço SOAP de um fornecedor (sistema empresarial "legado").
 * O WSDL é gerado a partir desta interface e publicado em {@code /ws/fornecedor?wsdl}.
 */
@WebService(name = "ServicoFornecedor", targetNamespace = ServicoFornecedor.NAMESPACE)
public interface ServicoFornecedor {

    String NAMESPACE = "http://scad.isutc.ac.mz/fornecedor";

    /**
     * Submete uma ordem de compra. Idempotente: o {@code ordemId} é a chave de idempotência, por isso
     * submeter a mesma ordem várias vezes devolve sempre a mesma confirmação.
     *
     * @throws FalhaFornecedor (SOAP Fault) dados inválidos ou fornecedor indisponível
     */
    @WebMethod(operationName = "SubmeterOrdemCompra")
    @WebResult(name = "confirmacao")
    ConfirmacaoOrdem submeterOrdemCompra(@WebParam(name = "ordem") PedidoOrdem ordem) throws FalhaFornecedor;
}

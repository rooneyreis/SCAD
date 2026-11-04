package mz.ac.isutc.scad.fornecedor.mock;

import org.apache.cxf.Bus;
import org.apache.cxf.jaxws.EndpointImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.jws.WebService;
import jakarta.xml.ws.Endpoint;
import mz.ac.isutc.scad.fornecedor.contrato.ConfirmacaoOrdem;
import mz.ac.isutc.scad.fornecedor.contrato.FalhaFornecedor;
import mz.ac.isutc.scad.fornecedor.contrato.PedidoOrdem;
import mz.ac.isutc.scad.fornecedor.contrato.ServicoFornecedor;

/**
 * Rota SOAP/XML (fornecedores "legados"), publicada pelo Apache CXF em {@code /ws/fornecedor}
 * (WSDL em {@code /ws/fornecedor?wsdl}). Os erros chegam ao cliente como SOAP Fault.
 */
@WebService(serviceName = "ServicoFornecedorService", portName = "ServicoFornecedorPort",
        targetNamespace = ServicoFornecedor.NAMESPACE,
        endpointInterface = "mz.ac.isutc.scad.fornecedor.contrato.ServicoFornecedor")
public class ServicoFornecedorSoap implements ServicoFornecedor {

    private final RecepcaoOrdens recepcao;

    public ServicoFornecedorSoap(RecepcaoOrdens recepcao) {
        this.recepcao = recepcao;
    }

    @Override
    public ConfirmacaoOrdem submeterOrdemCompra(PedidoOrdem ordem) throws FalhaFornecedor {
        return recepcao.receber(ordem, "SOAP");
    }

    @Configuration
    static class Publicacao {
        @Bean
        Endpoint endpointFornecedor(Bus bus, RecepcaoOrdens recepcao) {
            EndpointImpl endpoint = new EndpointImpl(bus, new ServicoFornecedorSoap(recepcao));
            endpoint.publish("/fornecedor");
            return endpoint;
        }
    }
}

package mz.ac.isutc.scad.no.fornecedores;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.springframework.stereotype.Component;

import jakarta.xml.ws.WebServiceException;
import mz.ac.isutc.scad.fornecedor.contrato.ConfirmacaoOrdem;
import mz.ac.isutc.scad.fornecedor.contrato.FalhaFornecedor;
import mz.ac.isutc.scad.fornecedor.contrato.PedidoOrdem;
import mz.ac.isutc.scad.fornecedor.contrato.ServicoFornecedor;

/**
 * Cliente SOAP/XML (JAX-WS, Apache CXF), gerado a partir da interface do contrato.
 * Os SOAP Faults chegam como {@link FalhaFornecedor} com o detalhe estruturado.
 */
@Component
public class ClienteSoap implements ClienteFornecedor {

    private final Map<String, ServicoFornecedor> portas = new ConcurrentHashMap<>();

    @Override
    public String protocolo() {
        return "SOAP";
    }

    @Override
    public ConfirmacaoOrdem submeter(String url, PedidoOrdem pedido) throws FalhaEnvio {
        try {
            return porta(url).submeterOrdemCompra(pedido);
        } catch (FalhaFornecedor f) {
            boolean temporaria = f.getFaultInfo() == null || f.getFaultInfo().isTemporaria();
            String codigo = f.getFaultInfo() == null ? "?" : f.getFaultInfo().getCodigo();
            throw new FalhaEnvio("SOAP Fault " + codigo + ": " + f.getMessage(), temporaria, f);
        } catch (WebServiceException e) {
            throw new FalhaEnvio("Sem resposta do fornecedor SOAP (timeout/ligação): " + e.getMessage(), true, e);
        }
    }

    private ServicoFornecedor porta(String url) {
        return portas.computeIfAbsent(url, u -> {
            JaxWsProxyFactoryBean fabrica = new JaxWsProxyFactoryBean();
            fabrica.setServiceClass(ServicoFornecedor.class);
            fabrica.setAddress(u);
            ServicoFornecedor p = (ServicoFornecedor) fabrica.create();
            Client cliente = ClientProxy.getClient(p);
            HTTPClientPolicy politica = new HTTPClientPolicy();
            politica.setConnectionTimeout(ClienteRest.TIMEOUT_LIGACAO_MS);
            politica.setReceiveTimeout(ClienteRest.TIMEOUT_RESPOSTA_MS);
            ((HTTPConduit) cliente.getConduit()).setClient(politica);
            return p;
        });
    }
}

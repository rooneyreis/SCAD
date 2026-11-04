package mz.ac.isutc.scad.no.fornecedores;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import mz.ac.isutc.scad.fornecedor.contrato.ConfirmacaoOrdem;
import mz.ac.isutc.scad.fornecedor.contrato.PedidoOrdem;

/** Cliente REST/JSON: {@code POST {url}} com o cabeçalho {@code Idempotency-Key}. */
@Component
public class ClienteRest implements ClienteFornecedor {

    static final int TIMEOUT_LIGACAO_MS = 2_000;
    static final int TIMEOUT_RESPOSTA_MS = 4_000;

    private final RestClient http;

    public ClienteRest() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(TIMEOUT_LIGACAO_MS);
        fabrica.setReadTimeout(TIMEOUT_RESPOSTA_MS);
        this.http = RestClient.builder().requestFactory(fabrica).build();
    }

    @Override
    public String protocolo() {
        return "REST";
    }

    @Override
    public ConfirmacaoOrdem submeter(String url, PedidoOrdem pedido) throws FalhaEnvio {
        try {
            return http.post().uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", pedido.getOrdemId())
                    .body(pedido)
                    .retrieve()
                    .body(ConfirmacaoOrdem.class);
        } catch (RestClientResponseException e) {
            HttpStatusCode s = e.getStatusCode();
            // 4xx = pedido inválido (não adianta repetir), excepto timeout/limite de pedidos
            boolean temporaria = s.is5xxServerError() || s.value() == 408 || s.value() == 429;
            throw new FalhaEnvio("HTTP " + s.value() + ": " + e.getResponseBodyAsString(), temporaria, e);
        } catch (ResourceAccessException e) {
            throw new FalhaEnvio("Sem resposta do fornecedor (timeout/ligação): " + e.getMostSpecificCause().getMessage(),
                    true, e);
        } catch (RestClientException e) {
            // ex.: timeout de leitura a meio da resposta
            throw new FalhaEnvio("Resposta do fornecedor incompleta: " + e.getMostSpecificCause().getMessage(), true, e);
        }
    }
}

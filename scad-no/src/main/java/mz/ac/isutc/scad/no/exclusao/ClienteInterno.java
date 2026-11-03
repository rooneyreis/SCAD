package mz.ac.isutc.scad.no.exclusao;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Comunicação HTTP entre nós (heartbeats e mensagens do Ricart-Agrawala), com timeouts curtos
 * para que um nó morto nunca bloqueie o algoritmo.
 */
public class ClienteInterno implements TransporteRA {

    private final Map<String, String> pares;   // região -> URL base
    private final RestClient http;

    public ClienteInterno(Map<String, String> pares) {
        this.pares = pares;
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(800);
        fabrica.setReadTimeout(2_000);
        this.http = RestClient.builder().requestFactory(fabrica).build();
    }

    public boolean heartbeat(String urlBase) {
        try {
            http.get().uri(urlBase + "/interno/heartbeat").retrieve().toBodilessEntity();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public boolean enviarPedido(String destino, String recurso, long tsLamport, String origem) {
        RespostaPedido r = http.post().uri(url(destino) + "/interno/ra/pedido")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new MensagemRA(recurso, tsLamport, origem))
                .retrieve().body(RespostaPedido.class);
        return r != null && r.ok();
    }

    @Override
    public void enviarOk(String destino, String recurso, long tsLamport, String origem) {
        http.post().uri(url(destino) + "/interno/ra/ok")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new MensagemRA(recurso, tsLamport, origem))
                .retrieve().toBodilessEntity();
    }

    private String url(String destino) {
        String u = pares.get(destino);
        if (u == null) {
            throw new IllegalArgumentException("Nó desconhecido: " + destino);
        }
        return u;
    }

    public record MensagemRA(String recurso, long ts, String origem) {
    }

    public record RespostaPedido(boolean ok) {
    }
}

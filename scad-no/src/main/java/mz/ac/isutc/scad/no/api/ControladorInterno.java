package mz.ac.isutc.scad.no.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import mz.ac.isutc.scad.no.exclusao.ClienteInterno.MensagemRA;
import mz.ac.isutc.scad.no.exclusao.ClienteInterno.RespostaPedido;
import mz.ac.isutc.scad.no.exclusao.RicartAgrawala;

/**
 * Endpoints usados só entre nós (heartbeats e Ricart-Agrawala).
 * Não são expostos pelo proxy do painel.
 */
@RestController
@RequestMapping("/interno")
public class ControladorInterno {

    private final RicartAgrawala ra;

    public ControladorInterno(RicartAgrawala ra) {
        this.ra = ra;
    }

    @GetMapping("/heartbeat")
    public Map<String, Boolean> heartbeat() {
        return Map.of("vivo", true);
    }

    @PostMapping("/ra/pedido")
    public RespostaPedido pedido(@RequestBody MensagemRA m) {
        return new RespostaPedido(ra.receberPedido(m.recurso(), m.ts(), m.origem()));
    }

    @PostMapping("/ra/ok")
    public void ok(@RequestBody MensagemRA m) {
        ra.receberOk(m.recurso(), m.origem(), m.ts());
    }
}

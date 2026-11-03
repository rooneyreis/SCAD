package mz.ac.isutc.scad.no.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import mz.ac.isutc.scad.common.relogio.RelogioCorrigido;
import mz.ac.isutc.scad.common.relogio.RelogioLamport;
import mz.ac.isutc.scad.no.exclusao.DetectorFalhas;
import mz.ac.isutc.scad.no.exclusao.RicartAgrawala;
import mz.ac.isutc.scad.no.relogio.RegistoAjustes;

/** Estado da sincronização (Ricart-Agrawala, detector de falhas, relógios) para o painel. */
@RestController
@RequestMapping("/api")
public class ControladorSincronizacao {

    private final RicartAgrawala ra;
    private final DetectorFalhas detector;
    private final RelogioCorrigido relogio;
    private final RelogioLamport lamport;
    private final RegistoAjustes ajustes;

    public ControladorSincronizacao(RicartAgrawala ra, DetectorFalhas detector, RelogioCorrigido relogio,
                                    RelogioLamport lamport, RegistoAjustes ajustes) {
        this.ra = ra;
        this.detector = detector;
        this.relogio = relogio;
        this.lamport = lamport;
        this.ajustes = ajustes;
    }

    @GetMapping("/exclusao")
    public Map<String, Object> exclusao() {
        return Map.of("membros", detector.estado(), "recursos", ra.estado(), "eventos", ra.eventos());
    }

    @GetMapping("/relogio")
    public Map<String, Object> relogio() {
        return Map.of("horaCorrigida", relogio.agora(), "desvioSimuladoMs", relogio.desvioSimuladoMs(),
                "desvioEfectivoMs", relogio.desvioEfectivoMs(), "lamport", lamport.actual(),
                "historico", ajustes.historico());
    }

    /** Demonstração: desregula o relógio deste nó (a próxima ronda de Berkeley corrige). */
    @PostMapping("/relogio/perturbar")
    public RegistoAjustes.Ajuste perturbar(@RequestParam long ms) {
        return ajustes.aplicar(ms, "perturbação manual");
    }
}

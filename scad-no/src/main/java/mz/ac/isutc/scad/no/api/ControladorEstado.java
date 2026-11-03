package mz.ac.isutc.scad.no.api;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import mz.ac.isutc.scad.common.relogio.RelogioCorrigido;
import mz.ac.isutc.scad.common.relogio.RelogioLamport;
import mz.ac.isutc.scad.no.config.PropriedadesNo;
import mz.ac.isutc.scad.no.corba.LigacaoCentral;

/** Estado do nó e das suas dependências (usado pela faixa de estado do painel). */
@RestController
@RequestMapping("/api/estado")
public class ControladorEstado {

    private final PropriedadesNo props;
    private final LigacaoCentral ligacaoCentral;
    private final MonitorHdfs hdfs;
    private final RelogioCorrigido relogio;
    private final RelogioLamport lamport;

    public ControladorEstado(PropriedadesNo props, LigacaoCentral ligacaoCentral, MonitorHdfs hdfs,
                             RelogioCorrigido relogio, RelogioLamport lamport) {
        this.props = props;
        this.ligacaoCentral = ligacaoCentral;
        this.hdfs = hdfs;
        this.relogio = relogio;
        this.lamport = lamport;
    }

    @GetMapping
    public EstadoNo estado() {
        return new EstadoNo(props.regiao(), Instant.now(), relogio.agora(), relogio.desvioEfectivoMs(),
                lamport.actual(), ligacaoCentral.centralDisponivel(), ligacaoCentral.ultimoRegisto(),
                hdfs.disponivel());
    }

    public record EstadoNo(String regiao, Instant horaSistema, Instant horaCorrigida, long desvioMs,
                           long lamport, boolean centralDisponivel, Instant ultimoRegistoCentral,
                           boolean hdfsDisponivel) {
    }
}

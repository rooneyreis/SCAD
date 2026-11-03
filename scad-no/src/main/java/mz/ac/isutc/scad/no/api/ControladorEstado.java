package mz.ac.isutc.scad.no.api;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import mz.ac.isutc.scad.common.dfs.ClienteHdfs;
import mz.ac.isutc.scad.no.config.PropriedadesNo;
import mz.ac.isutc.scad.no.corba.LigacaoCentral;

/** Estado do nó e das suas dependências (usado pela faixa de estado do painel). */
@RestController
@RequestMapping("/api/estado")
public class ControladorEstado {

    private final PropriedadesNo props;
    private final LigacaoCentral ligacaoCentral;
    private final ClienteHdfs hdfs;

    public ControladorEstado(PropriedadesNo props, LigacaoCentral ligacaoCentral, ClienteHdfs hdfs) {
        this.props = props;
        this.ligacaoCentral = ligacaoCentral;
        this.hdfs = hdfs;
    }

    @GetMapping
    public EstadoNo estado() {
        return new EstadoNo(props.regiao(), Instant.now(),
                ligacaoCentral.centralDisponivel(), ligacaoCentral.ultimoRegisto(), hdfs.disponivel());
    }

    public record EstadoNo(String regiao, Instant hora, boolean centralDisponivel,
                           Instant ultimoRegistoCentral, boolean hdfsDisponivel) {
    }
}

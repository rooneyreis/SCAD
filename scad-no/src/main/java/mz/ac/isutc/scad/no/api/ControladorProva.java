package mz.ac.isutc.scad.no.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import mz.ac.isutc.scad.common.dfs.ClienteHdfs;
import mz.ac.isutc.scad.corba.AlertaStock;
import mz.ac.isutc.scad.corba.NivelAlerta;
import mz.ac.isutc.scad.no.config.PropriedadesNo;
import mz.ac.isutc.scad.no.corba.LigacaoCentral;

/**
 * Endpoints de prova técnica da Fase 1 (CORBA e HDFS ponta-a-ponta).
 * Serão removidos quando o fluxo real (Fases 2 e 6) os substituir.
 */
@RestController
@RequestMapping("/api/prova")
public class ControladorProva {

    private final PropriedadesNo props;
    private final LigacaoCentral ligacaoCentral;
    private final ClienteHdfs hdfs;

    public ControladorProva(PropriedadesNo props, LigacaoCentral ligacaoCentral, ClienteHdfs hdfs) {
        this.props = props;
        this.ligacaoCentral = ligacaoCentral;
        this.hdfs = hdfs;
    }

    /** Envia um alerta CORBA de teste para a Central. */
    @PostMapping("/alerta")
    public ResponseEntity<Map<String, Object>> alerta() {
        AlertaStock alerta = new AlertaStock(UUID.randomUUID().toString(), "PROVA", props.regiao(),
                5, 10, NivelAlerta.CRITICO, System.currentTimeMillis());
        boolean enviado = ligacaoCentral.enviarAlerta(alerta);
        return ResponseEntity.status(enviado ? 200 : 503)
                .body(Map.of("alertaId", alerta.alertaId, "enviado", enviado));
    }

    /** Escreve um ficheiro no HDFS e lê-o de volta. */
    @PostMapping("/hdfs")
    public ResponseEntity<Map<String, Object>> hdfs() {
        String caminho = "/scad/prova/" + props.regiao() + ".txt";
        String conteudo = "Ola do no " + props.regiao() + " em " + Instant.now();
        try {
            hdfs.escrever(caminho, conteudo.getBytes(StandardCharsets.UTF_8));
            String lido = new String(hdfs.ler(caminho), StandardCharsets.UTF_8);
            return ResponseEntity.ok(Map.of("caminho", caminho, "lido", lido,
                    "ficheiros", hdfs.listar("/scad/prova")));
        } catch (IOException e) {
            return ResponseEntity.status(503).body(Map.of("erro", e.toString()));
        }
    }
}

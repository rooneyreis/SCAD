package mz.ac.isutc.scad.no.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import mz.ac.isutc.scad.common.dfs.ClienteHdfs;
import mz.ac.isutc.scad.no.config.PropriedadesNo;

/**
 * Prova técnica do HDFS ponta-a-ponta (usada também no teste de fumo com 2 máquinas).
 * Será removida na Fase 6, quando as facturas e a auditoria forem gravadas no HDFS.
 */
@RestController
@RequestMapping("/api/prova")
public class ControladorProva {

    private final PropriedadesNo props;
    private final ClienteHdfs hdfs;

    public ControladorProva(PropriedadesNo props, ClienteHdfs hdfs) {
        this.props = props;
        this.hdfs = hdfs;
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

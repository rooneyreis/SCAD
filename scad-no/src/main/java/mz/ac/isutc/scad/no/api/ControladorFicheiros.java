package mz.ac.isutc.scad.no.api;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import mz.ac.isutc.scad.common.dfs.ClienteHdfs;
import mz.ac.isutc.scad.common.dfs.FilaHdfs;

/** Ficheiros no HDFS (listagem e download) e estado da fila local. */
@RestController
@RequestMapping("/api/ficheiros")
public class ControladorFicheiros {

    private static final String RAIZ = "/scad";

    private final ClienteHdfs hdfs;
    private final FilaHdfs fila;
    private final MonitorHdfs monitor;

    public ControladorFicheiros(ClienteHdfs hdfs, FilaHdfs fila, MonitorHdfs monitor) {
        this.hdfs = hdfs;
        this.fila = fila;
        this.monitor = monitor;
    }

    @GetMapping
    public Map<String, Object> listar(@RequestParam(defaultValue = RAIZ) String dir) {
        validar(dir);
        List<ClienteHdfs.Entrada> ficheiros = List.of();
        boolean disponivel = monitor.disponivel();
        if (disponivel) {
            try {
                ficheiros = hdfs.listarRecursivo(dir).stream()
                        .sorted(Comparator.comparingLong(ClienteHdfs.Entrada::modificadoEm).reversed()).toList();
            } catch (IOException e) {
                disponivel = false;
            }
        }
        return Map.of("hdfsDisponivel", disponivel, "filaLocalPendentes", fila.pendentes(), "ficheiros", ficheiros);
    }

    @GetMapping("/conteudo")
    public ResponseEntity<byte[]> conteudo(@RequestParam String caminho) throws IOException {
        validar(caminho);
        byte[] dados = hdfs.ler(caminho);
        String nome = caminho.substring(caminho.lastIndexOf('/') + 1);
        MediaType tipo = nome.endsWith(".pdf") ? MediaType.APPLICATION_PDF
                : nome.endsWith(".json") ? MediaType.APPLICATION_JSON
                : new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(nome).build().toString())
                .body(dados);
    }

    /** Só se expõe a árvore do SCAD (evita "../" e caminhos arbitrários). */
    private static void validar(String caminho) {
        if (caminho == null || !caminho.startsWith(RAIZ) || caminho.contains("..")) {
            throw new IllegalArgumentException("Caminho inválido: " + caminho);
        }
    }
}

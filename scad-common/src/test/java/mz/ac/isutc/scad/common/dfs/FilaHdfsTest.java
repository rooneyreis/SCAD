package mz.ac.isutc.scad.common.dfs;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilaHdfsTest {

    @TempDir
    Path dir;

    private boolean hdfsDisponivel;
    private final Map<String, byte[]> hdfs = new LinkedHashMap<>();

    private final FilaHdfs.Destino destino = (caminho, dados) -> {
        if (!hdfsDisponivel) {
            throw new IOException("HDFS em baixo");
        }
        hdfs.put(caminho, dados);
    };

    @Test
    void comHdfsEmBaixoNadaSePerdeEOsFicheirosSaoEnviadosPorOrdemQuandoVolta() {
        FilaHdfs fila = new FilaHdfs(dir, destino);
        fila.guardar("/scad/facturas/2026/11/OC-1.pdf", "pdf1".getBytes(StandardCharsets.UTF_8));
        fila.guardar("/scad/2pc/OC-1.json", "{}".getBytes(StandardCharsets.UTF_8));

        assertEquals(0, fila.esvaziar());
        assertEquals(2, fila.pendentes());

        // Um nó que reinicia volta a ler a fila do disco
        FilaHdfs depoisDeReiniciar = new FilaHdfs(dir, destino);
        hdfsDisponivel = true;
        assertEquals(2, depoisDeReiniciar.esvaziar());
        assertEquals(0, depoisDeReiniciar.pendentes());
        assertEquals(List.of("/scad/facturas/2026/11/OC-1.pdf", "/scad/2pc/OC-1.json"), List.copyOf(hdfs.keySet()));
        assertArrayEquals("pdf1".getBytes(StandardCharsets.UTF_8), hdfs.get("/scad/facturas/2026/11/OC-1.pdf"));
    }
}

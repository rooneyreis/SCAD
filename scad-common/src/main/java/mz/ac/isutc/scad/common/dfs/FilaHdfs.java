package mz.ac.isutc.scad.common.dfs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fila local para escritas no HDFS ("store and forward").
 *
 * <p>Cada ficheiro é primeiro gravado no disco local do nó (volume Docker) e só depois enviado
 * para o HDFS. Se o HDFS estiver indisponível, os ficheiros ficam na fila e são enviados, por ordem,
 * quando ele voltar. Nada se perde, mesmo que o nó reinicie entretanto.
 *
 * <p>Formato: para cada ficheiro, {@code <seq>.dados} (conteúdo) e {@code <seq>.caminho} (caminho no HDFS).
 * O {@code .caminho} é escrito por último (com rename atómico), por isso uma entrada só é visível
 * quando está completa.
 */
public final class FilaHdfs {

    private static final Logger log = LoggerFactory.getLogger(FilaHdfs.class);

    /** Destino final (em produção: {@code ClienteHdfs::escrever}). */
    @FunctionalInterface
    public interface Destino {
        void escrever(String caminho, byte[] dados) throws IOException;
    }

    private final Path directorio;
    private final Destino hdfs;
    private final AtomicLong sequencia = new AtomicLong(System.currentTimeMillis() * 1000);
    private volatile long enviados;

    public FilaHdfs(Path directorio, Destino hdfs) {
        this.directorio = directorio;
        this.hdfs = hdfs;
        try {
            Files.createDirectories(directorio);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível criar a fila local " + directorio, e);
        }
    }

    /** Guarda o ficheiro na fila local (nunca falha por o HDFS estar em baixo). */
    public void guardar(String caminhoHdfs, byte[] dados) {
        String base = String.format("%019d-%s", sequencia.incrementAndGet(), UUID.randomUUID().toString().substring(0, 8));
        try {
            Files.write(directorio.resolve(base + ".dados"), dados);
            Path tmp = directorio.resolve(base + ".tmp");
            Files.writeString(tmp, caminhoHdfs, StandardCharsets.UTF_8);
            Files.move(tmp, directorio.resolve(base + ".caminho"), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar na fila local: " + caminhoHdfs, e);
        }
    }

    /**
     * Envia para o HDFS tudo o que está na fila, por ordem. Pára no primeiro erro (HDFS em baixo).
     *
     * @return número de ficheiros enviados
     */
    public synchronized int esvaziar() {
        int n = 0;
        for (Path meta : entradas()) {
            String base = meta.getFileName().toString().replace(".caminho", "");
            Path dados = directorio.resolve(base + ".dados");
            try {
                String caminho = Files.readString(meta, StandardCharsets.UTF_8);
                hdfs.escrever(caminho, Files.readAllBytes(dados));
                Files.deleteIfExists(meta);
                Files.deleteIfExists(dados);
                n++;
            } catch (IOException | RuntimeException e) {
                log.debug("HDFS indisponível; {} ficheiro(s) ficam na fila local: {}", pendentes(), e.getMessage());
                break;
            }
        }
        enviados += n;
        if (n > 0) {
            log.info("Fila HDFS: {} ficheiro(s) enviado(s); {} pendente(s)", n, pendentes());
        }
        return n;
    }

    public int pendentes() {
        return entradas().size();
    }

    public long enviados() {
        return enviados;
    }

    private List<Path> entradas() {
        try (Stream<Path> s = Files.list(directorio)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".caminho")).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}

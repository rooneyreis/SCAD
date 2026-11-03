package mz.ac.isutc.scad.common.dfs;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

/**
 * Cliente mínimo do HDFS (escrever, ler, listar).
 *
 * <p>Configurado para o cenário Docker: os DataNodes anunciam um hostname/IP alcançável
 * (e não o IP interno do container), por isso o cliente tem de ligar-se por hostname.
 */
public final class ClienteHdfs implements Closeable {

    private final FileSystem fs;

    public ClienteHdfs(String uri, String utilizador, int replicacao) {
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", uri);
        conf.setBoolean("dfs.client.use.datanode.hostname", true);
        conf.setInt("dfs.replication", replicacao);
        // Com apenas 3 DataNodes não há substituto no pipeline: se um cair, continua com os restantes
        conf.set("dfs.client.block.write.replace-datanode-on-failure.policy", "NEVER");
        // Falhar depressa quando o NameNode está em baixo (a fila local trata do resto)
        conf.setInt("ipc.client.connect.timeout", 3000);
        conf.setInt("ipc.client.connect.max.retries", 1);
        conf.setInt("ipc.client.connect.max.retries.on.timeouts", 1);
        try {
            this.fs = FileSystem.newInstance(URI.create(uri), conf, utilizador);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Não foi possível criar o cliente HDFS para " + uri, e);
        }
    }

    public void escrever(String caminho, byte[] dados) throws IOException {
        try (OutputStream out = fs.create(new Path(caminho), true)) {
            out.write(dados);
        }
    }

    public byte[] ler(String caminho) throws IOException {
        try (InputStream in = fs.open(new Path(caminho))) {
            return in.readAllBytes();
        }
    }

    public List<String> listar(String directorio) throws IOException {
        List<String> caminhos = new ArrayList<>();
        Path dir = new Path(directorio);
        if (!fs.exists(dir)) {
            return caminhos;
        }
        for (FileStatus estado : fs.listStatus(dir)) {
            caminhos.add(estado.getPath().toUri().getPath());
        }
        return caminhos;
    }

    /** true se o NameNode responde. */
    public boolean disponivel() {
        try {
            fs.getFileStatus(new Path("/"));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public void close() throws IOException {
        fs.close();
    }
}

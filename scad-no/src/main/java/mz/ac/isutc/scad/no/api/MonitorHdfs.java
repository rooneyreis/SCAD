package mz.ac.isutc.scad.no.api;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import mz.ac.isutc.scad.common.dfs.ClienteHdfs;

/**
 * Verifica em segundo plano se o HDFS responde.
 *
 * <p>Com o NameNode inacessível, uma verificação demora vários segundos (timeouts de ligação).
 * O painel consulta o estado a cada segundo, por isso devolve-se sempre o último resultado
 * conhecido em vez de verificar dentro do pedido HTTP.
 */
@Component
public class MonitorHdfs {

    private final ClienteHdfs hdfs;
    private volatile boolean disponivel;

    public MonitorHdfs(ClienteHdfs hdfs) {
        this.hdfs = hdfs;
    }

    @Scheduled(initialDelay = 0, fixedDelay = 5_000)
    void verificar() {
        disponivel = hdfs.disponivel();
    }

    public boolean disponivel() {
        return disponivel;
    }
}

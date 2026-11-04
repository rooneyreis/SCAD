package mz.ac.isutc.scad.no.documentos;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import mz.ac.isutc.scad.common.dfs.FilaHdfs;
import mz.ac.isutc.scad.common.relogio.RelogioCorrigido;
import mz.ac.isutc.scad.common.relogio.RelogioLamport;
import mz.ac.isutc.scad.no.config.PropriedadesNo;

/** Log de erros do sistema de compras, guardado no HDFS em {@code /scad/logs/erros/AAAA-MM-DD/}. */
@Component
public class RegistoErros {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HHmmss-SSS").withZone(ZoneOffset.UTC);

    private final FilaHdfs fila;
    private final RelogioCorrigido relogio;
    private final RelogioLamport lamport;
    private final String regiao;
    private final AtomicInteger seq = new AtomicInteger();

    public RegistoErros(FilaHdfs fila, RelogioCorrigido relogio, RelogioLamport lamport, PropriedadesNo props) {
        this.fila = fila;
        this.relogio = relogio;
        this.lamport = lamport;
        this.regiao = props.regiao();
    }

    public void erro(String origem, String mensagem) {
        var agora = relogio.agora();
        String texto = agora + " [L=" + lamport.actual() + "] [" + regiao + "] [" + origem + "] " + mensagem + "\n";
        String caminho = "/scad/logs/erros/" + DIA.format(agora) + "/" + regiao + "-" + HORA.format(agora)
                + "-" + seq.incrementAndGet() + ".log";
        fila.guardar(caminho, texto.getBytes(StandardCharsets.UTF_8));
    }
}

package mz.ac.isutc.scad.central.auditoria;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import mz.ac.isutc.scad.central.corba.CentralAuditoriaImpl;
import mz.ac.isutc.scad.common.dfs.FilaHdfs;
import mz.ac.isutc.scad.common.relogio.RelogioCorrigido;
import mz.ac.isutc.scad.corba.AgenteStock;
import mz.ac.isutc.scad.corba.SaldoProduto;

/**
 * Auditoria periódica: a Central pede a cada agente todos os saldos ({@code consultarTodos}, CORBA)
 * e grava um relatório no HDFS ({@code /scad/auditoria/AAAA-MM-DD/auditoria-HHmmss.txt}), com os
 * alertas recebidos no período. Usa a fila local se o HDFS estiver em baixo.
 */
@Component
public class AuditoriaPeriodica {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaPeriodica.class);
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HHmmss").withZone(ZoneOffset.UTC);

    private final CentralAuditoriaImpl central;
    private final FilaHdfs fila;
    private final RelogioCorrigido relogio;

    public AuditoriaPeriodica(CentralAuditoriaImpl central, FilaHdfs fila, RelogioCorrigido relogio) {
        this.central = central;
        this.fila = fila;
        this.relogio = relogio;
    }

    @Scheduled(initialDelayString = "${scad.auditoria.intervalo-ms:60000}",
               fixedDelayString = "${scad.auditoria.intervalo-ms:60000}")
    public void auditar() {
        Instant agora = relogio.agora();
        StringBuilder r = new StringBuilder();
        r.append("RELATÓRIO DE AUDITORIA SCAD — ").append(agora).append(" (hora corrigida da Central)\n\n");

        Map<String, AgenteStock> agentes = new TreeMap<>(central.agentes());
        for (var e : agentes.entrySet()) {
            r.append("== Armazém ").append(e.getKey()).append(" ==\n");
            try {
                SaldoProduto[] saldos = e.getValue().consultarTodos();
                r.append(String.format("  %-12s %10s %8s %12s  %s%n", "produto", "quantidade", "minimo", "em_encomenda", "situação"));
                for (SaldoProduto s : saldos) {
                    String situacao = s.quantidade == 0 ? "RUPTURA"
                            : s.quantidade + s.emEncomenda < s.minimo ? "ABAIXO DO MÍNIMO" : "ok";
                    r.append(String.format("  %-12s %10d %8d %12d  %s%n", s.produtoId, s.quantidade, s.minimo,
                            s.emEncomenda, situacao));
                }
            } catch (RuntimeException ex) {
                r.append("  agente inacessível (").append(ex.getClass().getSimpleName()).append(")\n");
            }
            r.append('\n');
        }

        List<String> alertas = central.retirarAlertasDoPeriodo();
        r.append("== Alertas recebidos no período (").append(alertas.size()).append(") ==\n");
        alertas.forEach(a -> r.append("  ").append(a).append('\n'));

        String caminho = "/scad/auditoria/" + DIA.format(agora) + "/auditoria-" + HORA.format(agora) + ".txt";
        fila.guardar(caminho, r.toString().getBytes(StandardCharsets.UTF_8));
        fila.esvaziar();
        log.info("Auditoria de {} agente(s) gravada em {}", agentes.size(), caminho);
    }

    @Scheduled(initialDelay = 5_000, fixedDelay = 5_000)
    public void enviarFila() {
        fila.esvaziar();
    }
}

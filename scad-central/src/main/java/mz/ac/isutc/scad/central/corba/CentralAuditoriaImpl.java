package mz.ac.isutc.scad.central.corba;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import mz.ac.isutc.scad.corba.AgenteStock;
import mz.ac.isutc.scad.corba.AlertaStock;
import mz.ac.isutc.scad.corba.CentralAuditoriaPOA;

/**
 * Servant da Central de Auditoria.
 *
 * <p>Fase 1: guarda as referências dos agentes e, a cada registo, faz uma chamada de volta
 * (callback) ao agente para provar a comunicação nos dois sentidos entre containers.
 */
@Component
public class CentralAuditoriaImpl extends CentralAuditoriaPOA {

    private static final Logger log = LoggerFactory.getLogger(CentralAuditoriaImpl.class);

    private final Map<String, AgenteStock> agentes = new ConcurrentHashMap<>();
    // As chamadas de volta não podem bloquear a thread do ORB que atende o registo
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void registarAgente(String armazemId, AgenteStock agente) {
        AgenteStock anterior = agentes.put(armazemId, agente);
        if (anterior == null || !anterior._is_equivalent(agente)) {
            log.info("Agente {} registado (novo IOR)", armazemId);
            executor.submit(() -> verificarAgente(armazemId, agente));
        }
    }

    @Override
    public void dispararAlerta(AlertaStock alerta) {
        log.info("ALERTA {} recebido de {}: produto={} quantidade={} minimo={} nivel={}",
                alerta.alertaId, alerta.armazemId, alerta.produtoId,
                alerta.quantidadeActual, alerta.minimo, alerta.nivel.value());
    }

    public Map<String, AgenteStock> agentes() {
        return agentes;
    }

    private void verificarAgente(String armazemId, AgenteStock agente) {
        try {
            long relogio = agente.obterRelogio();
            int saldos = agente.consultarTodos().length;
            log.info("Callback OK para {}: relogio={} desvio={} ms, saldos={}",
                    armazemId, relogio, relogio - System.currentTimeMillis(), saldos);
        } catch (RuntimeException e) {
            log.warn("Callback para {} falhou: {}", armazemId, e.toString());
        }
    }

    @PreDestroy
    void parar() {
        executor.shutdownNow();
    }
}

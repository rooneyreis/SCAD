package mz.ac.isutc.scad.central.corba;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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
import mz.ac.isutc.scad.corba.NivelAlerta;

/**
 * Servant da Central de Auditoria.
 *
 * <p>Guarda as referências dos agentes e, a cada novo registo, faz uma chamada de volta
 * (callback) ao agente, provando a comunicação nos dois sentidos.
 *
 * <p>Os agentes reenviam alertas se não tiverem a certeza de que foram entregues, por isso a
 * Central descarta duplicados pelo {@code alertaId} (entrega "pelo menos uma vez" + idempotência).
 */
@Component
public class CentralAuditoriaImpl extends CentralAuditoriaPOA {

    private static final Logger log = LoggerFactory.getLogger(CentralAuditoriaImpl.class);
    private static final int MEMORIA_ALERTAS = 10_000;

    private final Map<String, AgenteStock> agentes = new ConcurrentHashMap<>();
    // Ids dos alertas já recebidos (os mais antigos são esquecidos quando se excede o limite)
    private final Set<String> alertasRecebidos = Collections.newSetFromMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> maisAntigo) {
            return size() > MEMORIA_ALERTAS;
        }
    });
    // As chamadas de volta não podem bloquear a thread do ORB que atende o registo
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void registarAgente(String armazemId, AgenteStock agente) {
        AgenteStock anterior = agentes.put(armazemId, agente);
        if (anterior == null || !anterior._is_equivalent(agente)) {
            log.info("Agente {} registado", armazemId);
            executor.submit(() -> verificarAgente(armazemId, agente));
        }
    }

    @Override
    public void dispararAlerta(AlertaStock alerta) {
        synchronized (alertasRecebidos) {
            if (!alertasRecebidos.add(alerta.alertaId)) {
                log.debug("Alerta duplicado {} ignorado", alerta.alertaId);
                return;
            }
        }
        log.info("ALERTA {} de {}: produto={} quantidade={} minimo={} (id={}, hora do nó={})",
                nomeNivel(alerta.nivel), alerta.armazemId, alerta.produtoId,
                alerta.quantidadeActual, alerta.minimo, alerta.alertaId, Instant.ofEpochMilli(alerta.timestamp));
    }

    private static String nomeNivel(NivelAlerta nivel) {
        return switch (nivel.value()) {
            case NivelAlerta._AVISO -> "AVISO";
            case NivelAlerta._CRITICO -> "CRITICO";
            case NivelAlerta._RUPTURA -> "RUPTURA";
            default -> "DESCONHECIDO(" + nivel.value() + ")";
        };
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

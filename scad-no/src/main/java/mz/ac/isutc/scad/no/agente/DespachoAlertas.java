package mz.ac.isutc.scad.no.agente;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import mz.ac.isutc.scad.common.relogio.RelogioCorrigido;
import mz.ac.isutc.scad.corba.AlertaStock;
import mz.ac.isutc.scad.corba.NivelAlerta;
import mz.ac.isutc.scad.no.stock.Alerta;
import mz.ac.isutc.scad.no.stock.RepositorioAlertas;

/**
 * Entrega os alertas da fila local à Central de Auditoria (CORBA {@code dispararAlerta}).
 *
 * <p>Padrão "outbox": o alerta é gravado na BD na mesma transacção do movimento e só é
 * marcado como enviado depois de a chamada CORBA ter sucesso. Se a Central estiver em
 * baixo, os alertas ficam pendentes e são reenviados por ordem quando ela voltar.
 * Os envios correm numa única thread, fora do pedido HTTP, para nunca atrasar uma saída de stock.
 */
@Component
public class DespachoAlertas {

    private static final Logger log = LoggerFactory.getLogger(DespachoAlertas.class);
    private static final int LOTE_ENVIO = 50;

    private final RepositorioAlertas alertas;
    private final CanalAlertas central;
    private final RelogioCorrigido relogio;

    private final Executor executor;
    private final AtomicBoolean envioAgendado = new AtomicBoolean();

    @Autowired
    public DespachoAlertas(RepositorioAlertas alertas, CanalAlertas central, RelogioCorrigido relogio) {
        this(alertas, central, relogio, Executors.newSingleThreadExecutor(r -> new Thread(r, "despacho-alertas")));
    }

    /** @param executor onde correm os envios (os testes usam um executor síncrono) */
    public DespachoAlertas(RepositorioAlertas alertas, CanalAlertas central, RelogioCorrigido relogio,
                           Executor executor) {
        this.alertas = alertas;
        this.central = central;
        this.relogio = relogio;
        this.executor = executor;
    }

    /** Pede um envio assíncrono dos pendentes (pedidos repetidos enquanto um está em espera juntam-se). */
    public void solicitarEnvio() {
        if (envioAgendado.compareAndSet(false, true)) {
            executor.execute(() -> {
                envioAgendado.set(false);
                enviarPendentes();
            });
        }
    }

    /** Reenvio periódico: cobre o caso de a Central ter estado em baixo. */
    @Scheduled(initialDelay = 5_000, fixedDelay = 5_000)
    public void reenviarPendentes() {
        solicitarEnvio();
    }

    /** Envia já (de forma síncrona) os alertas pendentes; devolve quantos foram entregues. */
    public int enviarPendentes() {
        try {
            List<Alerta> pendentes = alertas.pendentes(LOTE_ENVIO);
            int enviados = 0;
            for (Alerta a : pendentes) {
                if (!central.enviarAlerta(paraIdl(a))) {
                    break;  // Central indisponível: mantém a ordem e tenta mais tarde
                }
                alertas.marcarEnviado(a.id(), relogio.agora());
                enviados++;
            }
            if (enviados > 0) {
                log.info("{} alerta(s) entregue(s) à Central ({} pendente(s) antes)", enviados, pendentes.size());
            }
            return enviados;
        } catch (DataAccessException e) {
            log.warn("Não foi possível ler a fila de alertas: {}", e.getMessage());
            return 0;
        }
    }

    static AlertaStock paraIdl(Alerta a) {
        NivelAlerta nivel = switch (a.nivel()) {
            case RUPTURA -> NivelAlerta.RUPTURA;
            case CRITICO -> NivelAlerta.CRITICO;
            case AVISO -> NivelAlerta.AVISO;
            case NORMAL -> throw new IllegalArgumentException("Nível NORMAL não gera alerta");
        };
        return new AlertaStock(a.id().toString(), a.produtoId(), a.armazemId(), a.quantidadeActual(),
                a.minimo(), nivel, a.tsCorrigido().toEpochMilli());
    }

    @PreDestroy
    void parar() {
        if (executor instanceof ExecutorService servico) {
            servico.shutdownNow();
        }
    }
}

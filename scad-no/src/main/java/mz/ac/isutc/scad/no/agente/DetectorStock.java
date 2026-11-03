package mz.ac.isutc.scad.no.agente;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import mz.ac.isutc.scad.common.relogio.RelogioCorrigido;
import mz.ac.isutc.scad.common.relogio.RelogioLamport;
import mz.ac.isutc.scad.no.bd.FragmentosBd;
import mz.ac.isutc.scad.no.reposicao.PedidosReposicao;
import mz.ac.isutc.scad.no.stock.Alerta;
import mz.ac.isutc.scad.no.stock.RepositorioAlertas;
import mz.ac.isutc.scad.no.stock.RepositorioStock;
import mz.ac.isutc.scad.no.stock.Stock;

/**
 * Agente de stock: detecta níveis que exigem alerta e coloca o alerta na fila local.
 *
 * <p>A detecção corre (1) logo a seguir a cada saída de stock, na mesma transacção do movimento,
 * e (2) periodicamente, para apanhar estados que já existiam no arranque (e voltar a pedir a
 * reposição de produtos que continuem abaixo do mínimo, por exemplo depois de um 2PC abortado). Um estado só gera
 * alerta uma vez (ver {@link RepositorioAlertas#ultimoAlertaDescreve}).
 */
@Component
public class DetectorStock {

    private static final Logger log = LoggerFactory.getLogger(DetectorStock.class);

    private final RepositorioStock stock;
    private final RepositorioAlertas alertas;
    private final FragmentosBd fragmentos;
    private final RelogioLamport lamport;
    private final RelogioCorrigido relogio;
    private final DespachoAlertas despacho;
    private final PedidosReposicao reposicao;

    public DetectorStock(RepositorioStock stock, RepositorioAlertas alertas, FragmentosBd fragmentos,
                         RelogioLamport lamport, RelogioCorrigido relogio, DespachoAlertas despacho,
                         PedidosReposicao reposicao) {
        this.stock = stock;
        this.alertas = alertas;
        this.fragmentos = fragmentos;
        this.lamport = lamport;
        this.relogio = relogio;
        this.despacho = despacho;
        this.reposicao = reposicao;
    }

    /**
     * Avalia um stock e, se o nível o exigir, grava um alerta na fila local.
     * Deve ser chamado dentro da transacção que alterou o stock.
     */
    public Optional<Alerta> avaliar(Stock s) {
        if (!s.nivel().requerAlerta() || alertas.ultimoAlertaDescreve(s)) {
            return Optional.empty();
        }
        Alerta alerta = new Alerta(UUID.randomUUID(), s.armazemId(), s.produtoId(), s.quantidade(),
                s.emEncomenda(), s.minimo(), s.nivel(), lamport.tick(), relogio.agora(), null);
        alertas.inserir(alerta);
        log.info("Alerta {} para {}/{}: quantidade={} em_encomenda={} minimo={}",
                alerta.nivel(), s.armazemId(), s.produtoId(), s.quantidade(), s.emEncomenda(), s.minimo());
        return Optional.of(alerta);
    }

    @Scheduled(initialDelay = 3_000, fixedDelay = 30_000)
    public void verificarTodos() {
        try {
            boolean novos = false;
            for (Stock s : stock.listar()) {
                Optional<Alerta> a = fragmentos.transaccaoLocal().execute(estado -> avaliar(s));
                novos |= a != null && a.isPresent();
                if (s.precisaReposicao()) {
                    reposicao.solicitar(s.produtoId());   // ex.: produto já abaixo do mínimo no arranque
                }
            }
            if (novos) {
                despacho.solicitarEnvio();
            }
        } catch (DataAccessException e) {
            log.warn("Verificação periódica do stock falhou (fragmento local indisponível?): {}", e.getMessage());
        }
    }
}

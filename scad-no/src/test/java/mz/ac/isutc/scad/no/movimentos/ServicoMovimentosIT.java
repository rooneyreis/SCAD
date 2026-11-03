package mz.ac.isutc.scad.no.movimentos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import mz.ac.isutc.scad.common.relogio.RelogioCorrigido;
import mz.ac.isutc.scad.common.relogio.RelogioLamport;
import mz.ac.isutc.scad.corba.AlertaStock;
import mz.ac.isutc.scad.no.FragmentoPostgresTeste;
import mz.ac.isutc.scad.no.agente.CanalAlertas;
import mz.ac.isutc.scad.no.agente.DespachoAlertas;
import mz.ac.isutc.scad.no.agente.DetectorStock;
import mz.ac.isutc.scad.no.bd.FragmentosBd;
import mz.ac.isutc.scad.no.movimentos.ErrosMovimento.ArmazemForaDoFragmento;
import mz.ac.isutc.scad.no.movimentos.ErrosMovimento.ProdutoNaoEncontrado;
import mz.ac.isutc.scad.no.movimentos.ErrosMovimento.StockInsuficiente;
import mz.ac.isutc.scad.no.stock.Alerta;
import mz.ac.isutc.scad.no.stock.NivelStock;
import mz.ac.isutc.scad.no.stock.RepositorioAlertas;
import mz.ac.isutc.scad.no.stock.RepositorioMovimentos;
import mz.ac.isutc.scad.no.stock.RepositorioStock;

/**
 * Fluxo base (passos 1 e 2) contra um fragmento PostgreSQL real (NORTE),
 * inicializado com os scripts de produção.
 */
@Testcontainers
class ServicoMovimentosIT {

    private static final long DESVIO_MS = 1_500;

    @Container
    static final PostgreSQLContainer<?> PG = FragmentoPostgresTeste.criar("NORTE");

    private static FragmentosBd fragmentos;

    private final CentralFalsa central = new CentralFalsa();
    private final RelogioLamport lamport = new RelogioLamport();
    private final RelogioCorrigido relogio = new RelogioCorrigido(
            Clock.fixed(Instant.parse("2026-11-04T10:00:00Z"), ZoneOffset.UTC), DESVIO_MS);

    private RepositorioStock stock;
    private RepositorioAlertas alertas;
    private DespachoAlertas despacho;
    private DetectorStock detector;
    private ServicoMovimentos servico;

    @BeforeAll
    static void ligar() {
        fragmentos = FragmentoPostgresTeste.fragmentos("NORTE", PG);
    }

    @AfterAll
    static void desligar() {
        fragmentos.destroy();
    }

    @BeforeEach
    void preparar() {
        // Cada teste parte do stock inicial de produção
        fragmentos.jdbcLocal().execute("TRUNCATE alerta, movimento_stock");
        fragmentos.jdbcLocal().execute("""
                UPDATE stock s SET quantidade = v.q, em_encomenda = 0
                  FROM (VALUES ('ARROZ-25',105), ('FARINHA-25',200), ('FEIJAO-10',150),
                               ('ACUCAR-10',180), ('OLEO-5',100), ('SABAO-BARRA',400)) AS v(p, q)
                 WHERE s.produto_id = v.p
                """);

        stock = new RepositorioStock(fragmentos);
        alertas = new RepositorioAlertas(fragmentos);
        despacho = new DespachoAlertas(alertas, central, relogio, Runnable::run);  // envios síncronos: testes deterministas
        detector = new DetectorStock(stock, alertas, fragmentos, lamport, relogio, despacho);
        servico = new ServicoMovimentos(fragmentos, stock, new RepositorioMovimentos(fragmentos),
                detector, despacho, lamport, relogio);
    }

    @Test
    void saidaRetiraStockEGravaMovimentoComCarimbos() {
        ResultadoSaida r = servico.registarSaida(new PedidoSaida("NORTE", "FEIJAO-10", 30, "Venda balcão"));

        assertEquals(120, r.stockApos().quantidade());
        assertEquals(NivelStock.NORMAL, r.nivel());
        assertNull(r.alerta());
        assertTrue(r.movimento().id() > 0);
        assertEquals(1, r.movimento().tsLamport());
        assertEquals(Instant.parse("2026-11-04T10:00:01.500Z"), r.movimento().tsCorrigido(),
                "a hora gravada é a do relógio corrigido (com o desvio simulado)");
        assertEquals("NORTE", r.movimento().origem());
    }

    @Test
    void saidaAbaixoDoMinimoGeraAlertaCriticoEntregueACentral() {
        ResultadoSaida r = servico.registarSaida(new PedidoSaida("NORTE", "ARROZ-25", 10, "Venda"));

        assertEquals(95, r.stockApos().quantidade());
        assertTrue(r.precisaReposicao());
        assertNotNull(r.alerta());
        assertEquals(NivelStock.CRITICO, r.alerta().nivel());
        assertTrue(r.alerta().tsLamport() > r.movimento().tsLamport(), "o alerta acontece depois do movimento");

        // Entregue à Central e retirado da fila local
        assertEquals(1, central.recebidos.size());
        assertEquals(r.alerta().id().toString(), central.recebidos.get(0).alertaId);
        assertEquals(0, alertas.pendentes(10).size());
    }

    @Test
    void alertasFicamNaFilaEnquantoACentralEstaEmBaixoESaoReenviadosPorOrdem() {
        central.disponivel = false;
        servico.registarSaida(new PedidoSaida("NORTE", "ARROZ-25", 10, "Venda"));     // CRITICO
        servico.registarSaida(new PedidoSaida("NORTE", "ARROZ-25", 95, "Quebra"));    // RUPTURA

        assertEquals(0, despacho.enviarPendentes());
        assertEquals(2, alertas.pendentes(10).size());

        central.disponivel = true;
        assertEquals(2, despacho.enviarPendentes());
        assertEquals(0, alertas.pendentes(10).size());
        assertEquals(List.of(95, 0), central.recebidos.stream().map(a -> a.quantidadeActual).toList(),
                "os alertas chegam pela ordem em que foram emitidos");
    }

    @Test
    void stockInsuficienteNaoAlteraNada() {
        assertThrows(StockInsuficiente.class,
                () -> servico.registarSaida(new PedidoSaida("NORTE", "OLEO-5", 101, "Venda")));
        assertEquals(100, stock.procurar("OLEO-5").orElseThrow().quantidade());
        assertTrue(new RepositorioMovimentos(fragmentos).recentes(10).isEmpty());
    }

    @Test
    void produtoInexistenteEArmazemDeOutroFragmentoSaoRejeitados() {
        assertThrows(ProdutoNaoEncontrado.class,
                () -> servico.registarSaida(new PedidoSaida("NORTE", "CAFE-1", 1, "Venda")));
        assertThrows(ArmazemForaDoFragmento.class,
                () -> servico.registarSaida(new PedidoSaida("SUL", "ARROZ-25", 1, "Venda")));
    }

    @Test
    void fragmentoRecusaLinhasDeOutraRegiao() {
        // Predicado da fragmentação horizontal imposto pela própria BD (CHECK armazem_id = 'NORTE')
        assertThrows(DataIntegrityViolationException.class, () -> fragmentos.jdbcLocal().update(
                "INSERT INTO stock (armazem_id, produto_id, quantidade, minimo, maximo) VALUES ('SUL','CAFE',1,1,2)"));
        assertThrows(DataIntegrityViolationException.class, () -> fragmentos.jdbcLocal().update(
                "UPDATE stock SET armazem_id = 'CENTRO' WHERE produto_id = 'ARROZ-25'"));
    }

    @Test
    void saidasConcorrentesNuncaDeixamStockNegativo() throws Exception {
        // 20 pedidos de 10 unidades sobre 105: exactamente 10 têm sucesso
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Callable<Boolean>> tarefas = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tarefas.add(() -> {
                try {
                    servico.registarSaida(new PedidoSaida("NORTE", "ARROZ-25", 10, "Pico"));
                    return true;
                } catch (StockInsuficiente e) {
                    return false;
                }
            });
        }
        int sucessos = 0;
        for (Future<Boolean> f : pool.invokeAll(tarefas)) {
            sucessos += f.get() ? 1 : 0;
        }
        pool.shutdown();

        assertEquals(10, sucessos);
        assertEquals(5, stock.procurar("ARROZ-25").orElseThrow().quantidade());
        assertEquals(10, new RepositorioMovimentos(fragmentos).recentes(100).size());
    }

    @Test
    void verificacaoPeriodicaNaoRepeteAlertasParaOMesmoEstado() {
        detector.verificarTodos();
        detector.verificarTodos();

        // Estado inicial do NORTE: só o arroz (105 <= 120) está "perto do mínimo"
        List<Alerta> emitidos = alertas.recentes(10);
        assertEquals(1, emitidos.size());
        assertEquals("ARROZ-25", emitidos.get(0).produtoId());
        assertEquals(NivelStock.AVISO, emitidos.get(0).nivel());
    }

    /** Central de Auditoria simulada: regista os alertas recebidos ou finge estar em baixo. */
    private static final class CentralFalsa implements CanalAlertas {
        volatile boolean disponivel = true;
        final List<AlertaStock> recebidos = new ArrayList<>();

        @Override
        public synchronized boolean enviarAlerta(AlertaStock alerta) {
            if (disponivel) {
                recebidos.add(alerta);
            }
            return disponivel;
        }
    }
}

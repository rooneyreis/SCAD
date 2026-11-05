package mz.ac.isutc.scad.no.loja;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import mz.ac.isutc.scad.common.relogio.RelogioCorrigido;
import mz.ac.isutc.scad.common.relogio.RelogioLamport;
import mz.ac.isutc.scad.no.FragmentoPostgresTeste;
import mz.ac.isutc.scad.no.agente.DespachoAlertas;
import mz.ac.isutc.scad.no.agente.DetectorStock;
import mz.ac.isutc.scad.no.bd.FragmentosBd;
import mz.ac.isutc.scad.no.documentos.GeradorRecibo;
import mz.ac.isutc.scad.no.loja.ErrosLoja.PedidoJaDecidido;
import mz.ac.isutc.scad.no.loja.ErrosLoja.StockIndisponivel;
import mz.ac.isutc.scad.no.loja.NovoPedido.Item;
import mz.ac.isutc.scad.no.loja.PedidoCliente.Estado;
import mz.ac.isutc.scad.no.movimentos.ErrosMovimento.ArmazemForaDoFragmento;
import mz.ac.isutc.scad.no.movimentos.ErrosMovimento.StockInsuficiente;
import mz.ac.isutc.scad.no.movimentos.PedidoSaida;
import mz.ac.isutc.scad.no.movimentos.ServicoMovimentos;
import mz.ac.isutc.scad.no.stock.RepositorioAlertas;
import mz.ac.isutc.scad.no.stock.RepositorioMovimentos;
import mz.ac.isutc.scad.no.stock.RepositorioStock;
import mz.ac.isutc.scad.no.stock.Stock;

/**
 * Loja contra um fragmento PostgreSQL real (NORTE), inicializado com os scripts de produção
 * (incluindo a migração da loja): reserva, concorrência na última unidade, autorizar, recusar
 * e a nova condição de reposição.
 */
@Testcontainers
class ServicoLojaIT {

    @Container
    static final PostgreSQLContainer<?> PG = FragmentoPostgresTeste.criar("NORTE");

    private static FragmentosBd fragmentos;

    private final List<String> reposicoes = new CopyOnWriteArrayList<>();
    private final List<PedidoCliente> recibos = new CopyOnWriteArrayList<>();
    private final RelogioLamport lamport = new RelogioLamport();
    private final RelogioCorrigido relogio = new RelogioCorrigido(
            Clock.fixed(Instant.parse("2026-11-04T10:00:00Z"), ZoneOffset.UTC), 0);

    private RepositorioStock stock;
    private RepositorioPedidos pedidos;
    private ServicoLoja loja;
    private ServicoMovimentos movimentos;

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
        // Cada teste parte do stock inicial de produção, sem pedidos nem reservas
        fragmentos.jdbcLocal().execute("TRUNCATE alerta, movimento_stock, linha_pedido_cliente, pedido_cliente");
        fragmentos.jdbcLocal().execute("""
                UPDATE stock s SET quantidade = v.q, reservado = 0, em_encomenda = 0
                  FROM (VALUES ('ARROZ-25',105), ('FARINHA-25',200), ('FEIJAO-10',150),
                               ('ACUCAR-10',180), ('OLEO-5',100), ('SABAO-BARRA',400)) AS v(p, q)
                 WHERE s.produto_id = v.p
                """);

        stock = new RepositorioStock(fragmentos);
        pedidos = new RepositorioPedidos(fragmentos);
        RepositorioAlertas alertas = new RepositorioAlertas(fragmentos);
        DespachoAlertas despacho = new DespachoAlertas(alertas, alerta -> true, relogio, Runnable::run);
        DetectorStock detector = new DetectorStock(stock, alertas, fragmentos, lamport, relogio, despacho, reposicoes::add);
        RepositorioMovimentos repMovimentos = new RepositorioMovimentos(fragmentos);
        loja = new ServicoLoja(fragmentos, stock, pedidos, repMovimentos, detector, despacho, reposicoes::add,
                recibos::add, lamport, relogio);
        movimentos = new ServicoMovimentos(fragmentos, stock, repMovimentos, detector, despacho, lamport, relogio,
                reposicoes::add);
    }

    // ------------------------------------------------------------------ reserva

    @Test
    void compraCriaPedidoPendenteEReservaOStock() {
        PedidoCliente p = comprar(new Item("OLEO-5", 2), new Item("ARROZ-25", 1));

        assertTrue(p.id().matches("PED-NORTE-\\d{6}"), p.id());
        assertEquals(Estado.PENDENTE, p.estado());
        assertEquals(new BigDecimal("3350.00"), p.total());           // 2 × 750 + 1 × 1850
        assertTrue(p.pagamentoRef().startsWith("SIM-"));

        Stock oleo = stock.procurar("OLEO-5").orElseThrow();
        assertEquals(100, oleo.quantidade(), "a reserva não mexe na quantidade");
        assertEquals(2, oleo.reservado());
        assertEquals(98, oleo.disponivel());

        PedidoCliente lido = pedidos.procurar(p.id()).orElseThrow();
        assertEquals(2, lido.linhas().size());
        assertEquals(new BigDecimal("750.00"), lido.linhas().stream()
                .filter(l -> l.produtoId().equals("OLEO-5")).findFirst().orElseThrow().precoUnitario());
    }

    @Test
    void naoDeixaComprarMaisQueODisponivelENadaFicaReservado() {
        StockIndisponivel e = assertThrows(StockIndisponivel.class,
                () -> comprar(new Item("OLEO-5", 2), new Item("FEIJAO-10", 151)));

        assertEquals("FEIJAO-10", e.produtoId());
        assertEquals(150, e.disponivel());
        assertEquals(0, stock.procurar("OLEO-5").orElseThrow().reservado(), "a transacção inteira é desfeita");
        assertEquals(0, pedidos.listar(null, 10).size());
    }

    @Test
    void saidaManualNaoPodeRetirarStockReservado() {
        comprar(new Item("OLEO-5", 98));

        StockInsuficiente e = assertThrows(StockInsuficiente.class,
                () -> movimentos.registarSaida(new PedidoSaida("NORTE", "OLEO-5", 5, "Quebra")));
        assertTrue(e.getMessage().contains("disponível 2"), e.getMessage());
    }

    @Test
    void compraNoutroArmazemEhRecusadaPorEsteNo() {
        assertThrows(ArmazemForaDoFragmento.class, () -> loja.comprar(
                new NovoPedido("SUL", "Ana", "841234567", List.of(new Item("OLEO-5", 1)))));
    }

    /**
     * Vários clientes compram a ÚLTIMA unidade ao mesmo tempo: só um consegue, os outros recebem
     * StockIndisponivel e o stock nunca fica com mais reservado do que a quantidade.
     */
    @Test
    void concorrenciaNaUltimaUnidadeSoUmClienteConsegue() throws Exception {
        fragmentos.jdbcLocal().update("UPDATE stock SET quantidade = 1 WHERE produto_id = 'ACUCAR-10'");
        int clientes = 8;
        CountDownLatch partida = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(clientes);
        try {
            List<Future<PedidoCliente>> compras = new ArrayList<>();
            for (int i = 0; i < clientes; i++) {
                String nome = "Cliente " + i;
                compras.add(pool.submit(() -> {
                    partida.await();
                    return loja.comprar(new NovoPedido("NORTE", nome, "840000000", List.of(new Item("ACUCAR-10", 1))));
                }));
            }
            partida.countDown();

            int sucessos = 0;
            List<String> mensagens = new ArrayList<>();
            for (Future<PedidoCliente> f : compras) {
                try {
                    f.get();
                    sucessos++;
                } catch (java.util.concurrent.ExecutionException e) {
                    StockIndisponivel s = (StockIndisponivel) e.getCause();
                    assertEquals(0, s.disponivel());
                    mensagens.add(s.getMessage());
                }
            }
            assertEquals(1, sucessos);
            assertEquals(clientes - 1, mensagens.size());
            assertTrue(mensagens.get(0).contains("esgotou"), mensagens.get(0));
        } finally {
            pool.shutdownNow();
        }
        Stock acucar = stock.procurar("ACUCAR-10").orElseThrow();
        assertEquals(1, acucar.reservado());
        assertEquals(0, acucar.disponivel());
        assertEquals(1, pedidos.listar(Estado.PENDENTE, 20).size());
    }

    // ------------------------------------------------------------------ autorizar / recusar

    @Test
    void autorizarBaixaQuantidadeEReservadoRegistaVendaEGeraRecibo() {
        PedidoCliente p = comprar(new Item("OLEO-5", 3));

        PedidoCliente autorizado = loja.autorizar(p.id());

        assertEquals(Estado.AUTORIZADO, autorizado.estado());
        assertEquals("/scad/recibos/2026/11/" + p.id() + ".pdf", autorizado.recibo());
        Stock oleo = stock.procurar("OLEO-5").orElseThrow();
        assertEquals(97, oleo.quantidade());
        assertEquals(0, oleo.reservado());

        Map<String, Object> mov = fragmentos.jdbcLocal().queryForMap(
                "SELECT tipo, motivo, origem, quantidade, ts_lamport FROM movimento_stock");
        assertEquals("SAIDA", mov.get("tipo"));
        assertEquals("VENDA", mov.get("motivo"));
        assertEquals("LOJA", mov.get("origem"));
        assertEquals(3, mov.get("quantidade"));
        assertTrue((Long) mov.get("ts_lamport") > p.tsLamport(), "carimbo de Lamport posterior ao da compra");

        assertEquals(List.of(p.id()), recibos.stream().map(PedidoCliente::id).toList());
        byte[] pdf = GeradorRecibo.gerar(autorizado, "Nampula", "NORTE", relogio.agora());
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
    }

    @Test
    void recusarLibertaAReserva() {
        PedidoCliente p = comprar(new Item("OLEO-5", 3));

        PedidoCliente recusado = loja.recusar(p.id(), "Sem transporte para a entrega");

        assertEquals(Estado.RECUSADO, recusado.estado());
        assertEquals("Sem transporte para a entrega", recusado.motivoRecusa());
        assertNull(recusado.recibo());
        Stock oleo = stock.procurar("OLEO-5").orElseThrow();
        assertEquals(100, oleo.quantidade());
        assertEquals(0, oleo.reservado());
        assertEquals(0, fragmentos.jdbcLocal().queryForObject("SELECT count(*) FROM movimento_stock", Integer.class));
    }

    @Test
    void umPedidoSoSeDecideUmaVez() {
        PedidoCliente p = comprar(new Item("OLEO-5", 3));
        loja.autorizar(p.id());

        assertThrows(PedidoJaDecidido.class, () -> loja.recusar(p.id(), "tarde demais"));
        assertThrows(PedidoJaDecidido.class, () -> loja.autorizar(p.id()));
        assertEquals(97, stock.procurar("OLEO-5").orElseThrow().quantidade(), "não sai stock duas vezes");
    }

    @Test
    void resumoContaPendentesEVendasAutorizadas() {
        PedidoCliente a = comprar(new Item("OLEO-5", 2));     // 1500
        comprar(new Item("SABAO-BARRA", 10));                  // 600, fica pendente
        loja.autorizar(a.id());

        RepositorioPedidos.Resumo r = pedidos.resumo(Instant.parse("2026-11-03T22:00:00Z"));
        assertEquals(1, r.pendentes());
        assertEquals(new BigDecimal("1500.00"), r.vendasHoje());
    }

    // ------------------------------------------------------------------ reposição

    /** ARROZ-25: quantidade 105, mínimo 100. Reservar 6 deixa (105 − 6) + 0 = 99 < 100. */
    @Test
    void reservaQueDeixaODisponivelAbaixoDoMinimoDisparaAReposicao() {
        comprar(new Item("ARROZ-25", 5));
        assertTrue(reposicoes.isEmpty(), "(105 − 5) + 0 = 100: ainda não");

        comprar(new Item("ARROZ-25", 1));
        assertEquals(List.of("ARROZ-25"), reposicoes);
        assertEquals(105, stock.procurar("ARROZ-25").orElseThrow().quantidade(), "a prateleira ainda não mudou");
    }

    @Test
    void reservaCobertaPelaEncomendaNaoDisparaAReposicao() {
        fragmentos.jdbcLocal().update("UPDATE stock SET em_encomenda = 300 WHERE produto_id = 'ARROZ-25'");
        comprar(new Item("ARROZ-25", 50));
        assertTrue(reposicoes.isEmpty(), "(105 − 50) + 300 >= 100");
    }

    private PedidoCliente comprar(Item... itens) {
        return loja.comprar(new NovoPedido("NORTE", "Maria Cossa", "+258 84 123 4567", List.of(itens)));
    }
}

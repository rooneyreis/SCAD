package mz.ac.isutc.scad.no.transaccoes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import mz.ac.isutc.scad.no.bd.FragmentosBd;
import mz.ac.isutc.scad.no.ordens.Catalogo;
import mz.ac.isutc.scad.no.ordens.OrdemCompra;
import mz.ac.isutc.scad.no.ordens.OrdemCompra.Estado;
import mz.ac.isutc.scad.no.ordens.RepositorioOrdens;
import mz.ac.isutc.scad.no.ordens.ServicoOrdens;
import mz.ac.isutc.scad.no.stock.RepositorioStock;
import mz.ac.isutc.scad.no.transaccoes.Cronologia2PC.Decisao;

/**
 * 2PC real sobre 3 fragmentos PostgreSQL (NORTE, CENTRO, SUL), inicializados com os scripts
 * de produção. O nó coordenador é o NORTE.
 */
@Testcontainers
class DoisFasesIT {

    @Container
    static final PostgreSQLContainer<?> NORTE = FragmentoPostgresTeste.criar("NORTE");
    @Container
    static final PostgreSQLContainer<?> CENTRO = FragmentoPostgresTeste.criar("CENTRO");
    @Container
    static final PostgreSQLContainer<?> SUL = FragmentoPostgresTeste.criar("SUL");

    private static FragmentosBd fragmentos;

    private final RelogioLamport lamport = new RelogioLamport();
    private final RelogioCorrigido relogio = new RelogioCorrigido(0);
    private Coordenador2PC coordenador;
    private Log2PC log2pc;
    private RepositorioOrdens ordens;
    private ServicoOrdens servico;
    private RepositorioStock stock;
    private Catalogo.Produto arroz;

    @BeforeAll
    static void ligar() {
        Map<String, PostgreSQLContainer<?>> pgs = new LinkedHashMap<>();
        pgs.put("NORTE", NORTE);
        pgs.put("CENTRO", CENTRO);
        pgs.put("SUL", SUL);
        fragmentos = FragmentoPostgresTeste.fragmentos("NORTE", pgs);
    }

    @AfterAll
    static void desligar() {
        fragmentos.destroy();
    }

    @BeforeEach
    void preparar() {
        for (String r : fragmentos.regioes()) {
            var jdbc = fragmentos.jdbc(r);
            jdbc.queryForList("SELECT gid FROM pg_prepared_xacts", String.class)
                    .forEach(g -> jdbc.execute("ROLLBACK PREPARED '" + g + "'"));
            jdbc.execute("TRUNCATE linha_ordem, ordem_compra, log_2pc, movimento_stock, alerta");
            jdbc.execute("UPDATE stock SET em_encomenda = 0");
        }
        log2pc = new Log2PC(fragmentos);
        coordenador = new Coordenador2PC(fragmentos, log2pc, lamport, relogio);
        ordens = new RepositorioOrdens(fragmentos);
        servico = new ServicoOrdens(fragmentos, coordenador, ordens, lamport, relogio);
        stock = new RepositorioStock(fragmentos);
        arroz = new Catalogo(fragmentos).produto("ARROZ-25").orElseThrow();
    }

    private int emEncomenda(String regiao) {
        return stock.procurarEm(regiao, "ARROZ-25").orElseThrow().emEncomenda();
    }

    private int quantidade(String regiao) {
        return stock.procurarEm(regiao, "ARROZ-25").orElseThrow().quantidade();
    }

    private int preparadas() {
        int n = 0;
        for (String r : fragmentos.regioes()) {
            n += fragmentos.jdbc(r).queryForObject("SELECT count(*) FROM pg_prepared_xacts", Integer.class);
        }
        return n;
    }

    @Test
    void commit_criaOrdemConsolidadaEmTodosOsFragmentosParticipantes() {
        Map<String, Integer> rep = new LinkedHashMap<>();
        rep.put("NORTE", 305);
        rep.put("CENTRO", 195);

        String id = servico.criar(arroz, "NORTE", rep);

        assertNotNull(id);
        assertEquals(305, emEncomenda("NORTE"));
        assertEquals(195, emEncomenda("CENTRO"));
        assertEquals(0, emEncomenda("SUL"), "o SUL não participa");
        OrdemCompra o = ordens.procurar(id).orElseThrow();
        assertEquals(Estado.PENDENTE_ENVIO, o.estado());
        assertEquals(500, o.quantidadeTotal());
        assertEquals(Map.of("NORTE", 305, "CENTRO", 195), o.linhas());
        assertEquals(0, fragmentos.jdbc("SUL").queryForObject("SELECT count(*) FROM ordem_compra", Integer.class));
        assertEquals(0, preparadas());
        assertTrue(log2pc.porConcluir().isEmpty(), "decisão gravada e marcada como concluída");

        Cronologia2PC.Vista c = coordenador.cronologias().get(0);
        assertEquals(Decisao.COMMIT, c.decisao());
        assertEquals(Map.of("NORTE", Cronologia2PC.Voto.SIM, "CENTRO", Cronologia2PC.Voto.SIM), c.votos());
    }

    @Test
    void umVotoNao_abortaEmTodosOsParticipantes() {
        Map<String, TrabalhoParticipante> trabalho = new LinkedHashMap<>();
        trabalho.put("NORTE", con -> {
            try (Statement st = con.createStatement()) {
                st.executeUpdate("UPDATE stock SET em_encomenda = 999 WHERE produto_id = 'ARROZ-25'");
            }
        });
        trabalho.put("CENTRO", con -> {
            throw new SQLException("simulação: o participante recusa");
        });

        Decisao d = coordenador.executar("TESTE-ABORT-1", "TESTE", trabalho);

        assertEquals(Decisao.ABORT, d);
        assertEquals(0, emEncomenda("NORTE"), "o trabalho preparado no NORTE foi desfeito (ROLLBACK PREPARED)");
        assertEquals(0, preparadas());
        assertEquals(Cronologia2PC.Voto.NAO, coordenador.cronologias().get(0).votos().get("CENTRO"));
    }

    @Test
    void recuperacao_concluiTransaccoesPreparadasComDecisaoNoLog() throws Exception {
        // Simula um coordenador que morreu depois de gravar COMMIT e antes da fase 2
        for (String r : List.of("NORTE", "SUL")) {
            try (var con = fragmentos.dataSource(r).getConnection(); var st = con.createStatement()) {
                con.setAutoCommit(false);
                st.executeUpdate("UPDATE stock SET em_encomenda = 77 WHERE produto_id = 'ARROZ-25'");
                st.execute("PREPARE TRANSACTION '" + Coordenador2PC.gid("NORTE", "TESTE-REC-1", r) + "'");
                con.setAutoCommit(true);
            }
        }
        log2pc.gravarDecisao("TESTE-REC-1", "TESTE", List.of("NORTE", "SUL"), Decisao.COMMIT, 1, relogio.agora());
        assertEquals(2, preparadas());
        assertEquals(0, emEncomenda("SUL"), "ainda não é visível");

        new Recuperacao2PC(fragmentos, log2pc, coordenador, relogio).recuperar();

        assertEquals(0, preparadas());
        assertEquals(77, emEncomenda("NORTE"));
        assertEquals(77, emEncomenda("SUL"));
        assertTrue(log2pc.porConcluir().isEmpty());
    }

    @Test
    void cicloCompleto_criarEnviarReceberEntrega() {
        Map<String, Integer> rep = new LinkedHashMap<>();
        rep.put("SUL", 230);
        rep.put("CENTRO", 170);
        Catalogo.Produto farinha = new Catalogo(fragmentos).produto("FARINHA-25").orElseThrow();
        String id = servico.criar(farinha, "SUL", rep);

        assertThrows(ServicoOrdens.EstadoInvalidoException.class, () -> servico.receberEntrega(id),
                "não se recebe uma ordem ainda não enviada");
        assertEquals(Decisao.COMMIT, servico.marcarEnviada(id, "CONF-123", LocalDate.of(2026, 11, 10)));
        assertEquals(Decisao.COMMIT, servico.marcarEnviada(id, "CONF-123", LocalDate.of(2026, 11, 10)),
                "idempotente: um reenvio não falha");
        assertEquals(Estado.ENVIADA, ordens.procurar(id).orElseThrow().estado());

        int sulAntes = stock.procurarEm("SUL", "FARINHA-25").orElseThrow().quantidade();
        assertEquals(Decisao.COMMIT, servico.receberEntrega(id));

        OrdemCompra o = ordens.procurar(id).orElseThrow();
        assertEquals(Estado.ENTREGUE, o.estado());
        assertEquals("CONF-123", o.confirmacao());
        var sul = stock.procurarEm("SUL", "FARINHA-25").orElseThrow();
        assertEquals(sulAntes + 230, sul.quantidade());
        assertEquals(0, sul.emEncomenda());
        assertEquals(0, stock.procurarEm("CENTRO", "FARINHA-25").orElseThrow().emEncomenda());
        assertEquals(1, fragmentos.jdbc("CENTRO").queryForObject(
                "SELECT count(*) FROM movimento_stock WHERE tipo = 'ENTRADA'", Integer.class));
        assertThrows(ServicoOrdens.EstadoInvalidoException.class, () -> servico.receberEntrega(id),
                "uma entrega não pode ser recebida duas vezes");
    }
}

package mz.ac.isutc.scad.no.exclusao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import mz.ac.isutc.scad.common.relogio.RelogioCorrigido;
import mz.ac.isutc.scad.common.relogio.RelogioLamport;

/** Ricart-Agrawala com 3 nós ligados por um transporte em memória. */
class RicartAgrawalaTest {

    private static final String R = "GRAO-DOURADO/ARROZ-25";

    private final Map<String, RicartAgrawala> nos = new ConcurrentHashMap<>();
    private final Map<String, GrupoFalso> grupos = new ConcurrentHashMap<>();
    private final Set<String> mortos = ConcurrentHashMap.newKeySet();
    private final ExecutorService envios = Executors.newFixedThreadPool(8);
    private final ExecutorService clientes = Executors.newFixedThreadPool(6);

    /** Entrega as mensagens directamente ao nó destino; nós "mortos" não respondem. */
    private final TransporteRA rede = new TransporteRA() {
        @Override
        public boolean enviarPedido(String destino, String recurso, long ts, String origem) {
            if (mortos.contains(destino)) {
                throw new IllegalStateException(destino + " inacessível");
            }
            return nos.get(destino).receberPedido(recurso, ts, origem);
        }

        @Override
        public void enviarOk(String destino, String recurso, long ts, String origem) {
            if (mortos.contains(destino)) {
                throw new IllegalStateException(destino + " inacessível");
            }
            nos.get(destino).receberOk(recurso, origem, ts);
        }
    };

    private void criar(String... ids) {
        for (String id : ids) {
            GrupoFalso g = new GrupoFalso(id, List.of(ids));
            grupos.put(id, g);
            nos.put(id, new RicartAgrawala(id, g, rede, new RelogioLamport(), new RelogioCorrigido(0), envios, 5_000));
        }
    }

    @AfterEach
    void parar() {
        envios.shutdownNow();
        clientes.shutdownNow();
    }

    @Test
    void nuncaHaDoisNosNaSeccaoCriticaAoMesmoTempo() throws Exception {
        criar("NORTE", "CENTRO", "SUL");
        AtomicInteger dentro = new AtomicInteger();
        AtomicInteger maximo = new AtomicInteger();
        AtomicInteger entradas = new AtomicInteger();

        List<Future<?>> tarefas = new ArrayList<>();
        for (String no : List.of("NORTE", "CENTRO", "SUL")) {
            for (int t = 0; t < 2; t++) {                  // 2 threads por nó
                tarefas.add(clientes.submit(() -> {
                    for (int i = 0; i < 10; i++) {
                        nos.get(no).executar(R, () -> {
                            maximo.accumulateAndGet(dentro.incrementAndGet(), Math::max);
                            sleep(2);
                            dentro.decrementAndGet();
                            return entradas.incrementAndGet();
                        });
                    }
                    return null;
                }));
            }
        }
        for (Future<?> f : tarefas) {
            f.get(60, TimeUnit.SECONDS);
        }
        assertEquals(1, maximo.get(), "exclusão mútua violada");
        assertEquals(60, entradas.get(), "todos os pedidos foram servidos (sem bloqueio)");
    }

    @Test
    void recursosDiferentesNaoSeBloqueiam() throws Exception {
        criar("NORTE", "CENTRO");
        CountDownLatch norteDentro = new CountDownLatch(1);
        CountDownLatch libertar = new CountDownLatch(1);
        Future<?> a = clientes.submit(() -> nos.get("NORTE").executar(R, () -> {
            norteDentro.countDown();
            await(libertar);
            return null;
        }));
        assertTrue(norteDentro.await(5, TimeUnit.SECONDS));
        // Outro recurso (outro fornecedor/produto) entra logo, mesmo com o NORTE dentro de R
        assertEquals("ok", nos.get("CENTRO").executar("DOCE-VALE/ACUCAR-10", () -> "ok"));
        libertar.countDown();
        a.get(5, TimeUnit.SECONDS);
    }

    @Test
    void okAdiadoQuemTemCarimboMaiorEsperaPeloQueEstaDentro() throws Exception {
        criar("NORTE", "CENTRO");
        CountDownLatch norteDentro = new CountDownLatch(1);
        CountDownLatch libertar = new CountDownLatch(1);
        List<String> ordem = new CopyOnWriteArrayList<>();

        Future<?> a = clientes.submit(() -> nos.get("NORTE").executar(R, () -> {
            ordem.add("NORTE entra");
            norteDentro.countDown();
            await(libertar);
            ordem.add("NORTE sai");
            return null;
        }));
        assertTrue(norteDentro.await(5, TimeUnit.SECONDS));
        Future<?> b = clientes.submit(() -> nos.get("CENTRO").executar(R, () -> ordem.add("CENTRO entra")));

        sleep(300);
        assertEquals(List.of("CENTRO"), nos.get("NORTE").estado().get(0).okAdiadosPara(),
                "o NORTE (dentro) adiou o OK ao CENTRO");
        assertFalse(ordem.contains("CENTRO entra"));

        libertar.countDown();
        a.get(5, TimeUnit.SECONDS);
        b.get(5, TimeUnit.SECONDS);
        assertEquals(List.of("NORTE entra", "NORTE sai", "CENTRO entra"), ordem);
    }

    @Test
    void noQueMorreDentroDaSeccaoCriticaNaoBloqueiaOsOutros() throws Exception {
        criar("NORTE", "CENTRO", "SUL");
        CountDownLatch centroDentro = new CountDownLatch(1);
        // O CENTRO entra e "morre" lá dentro (nunca sai)
        clientes.submit(() -> nos.get("CENTRO").executar(R, () -> {
            centroDentro.countDown();
            sleep(60_000);
            return null;
        }));
        assertTrue(centroDentro.await(5, TimeUnit.SECONDS));
        mortos.add("CENTRO");

        Future<String> norte = clientes.submit(() -> nos.get("NORTE").executar(R, () -> "NORTE entrou"));
        sleep(500);
        assertFalse(norte.isDone(), "ainda à espera do OK do CENTRO");

        // O detector de falhas declara o CENTRO morto nos restantes nós
        grupos.get("NORTE").morre("CENTRO");
        grupos.get("SUL").morre("CENTRO");

        assertEquals("NORTE entrou", norte.get(5, TimeUnit.SECONDS));
    }

    @Test
    void semRespostaDentroDoPrazoDesisteComErro() {
        criar("NORTE", "CENTRO");
        mortos.add("CENTRO");     // inacessível, mas o detector ainda não o declarou morto
        RicartAgrawala norte = new RicartAgrawala("NORTE", grupos.get("NORTE"), rede, new RelogioLamport(),
                new RelogioCorrigido(0), envios, 800);
        assertThrows(ExclusaoMutua.ExclusaoIndisponivelException.class, () -> norte.executar(R, () -> "x"));
        assertEquals(RicartAgrawala.Estado.LIVRE, norte.estado().get(0).estado());
    }

    // ------------------------------------------------------------------ utilitários

    private static final class GrupoFalso implements Membros {
        private final String eu;
        private final Set<String> vivos = ConcurrentHashMap.newKeySet();
        private final List<Consumer<String>> aoMorrer = new CopyOnWriteArrayList<>();

        GrupoFalso(String eu, List<String> todos) {
            this.eu = eu;
            vivos.addAll(todos);
        }

        void morre(String no) {
            vivos.remove(no);
            aoMorrer.forEach(a -> a.accept(no));
        }

        @Override
        public Set<String> vivos() {
            Set<String> s = ConcurrentHashMap.newKeySet();
            s.addAll(vivos);
            s.remove(eu);
            return s;
        }

        @Override
        public void aoMorrer(Consumer<String> accao) {
            aoMorrer.add(accao);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(CountDownLatch l) {
        try {
            l.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

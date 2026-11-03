package mz.ac.isutc.scad.no.reposicao;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import mz.ac.isutc.scad.common.relogio.RelogioCorrigido;
import mz.ac.isutc.scad.no.bd.FragmentosBd;
import mz.ac.isutc.scad.no.exclusao.ExclusaoMutua;
import mz.ac.isutc.scad.no.ordens.Catalogo;
import mz.ac.isutc.scad.no.ordens.ServicoOrdens;
import mz.ac.isutc.scad.no.stock.RepositorioStock;
import mz.ac.isutc.scad.no.stock.Stock;

/**
 * Passos 3 a 6 do fluxo de compra:
 * <ol start="3">
 *   <li>pede a secção crítica para o recurso (fornecedor, produto);</li>
 *   <li>dentro dela <b>volta a verificar</b> a condição (outro nó pode já ter criado uma ordem que
 *       aumentou o {@code em_encomenda} deste armazém); se já não for preciso, sai;</li>
 *   <li>calcula a repartição do lote e executa o 2PC;</li>
 *   <li>liberta a secção crítica.</li>
 * </ol>
 * O envio ao fornecedor (passo 7) acontece depois, fora da secção crítica.
 */
@Service
public class ServicoReposicao implements PedidosReposicao {

    private static final Logger log = LoggerFactory.getLogger(ServicoReposicao.class);
    private static final int MAX_EVENTOS = 50;

    private final FragmentosBd fragmentos;
    private final RepositorioStock stock;
    private final Catalogo catalogo;
    private final ServicoOrdens ordens;
    private final ExclusaoMutua exclusao;
    private final RelogioCorrigido relogio;
    private final long demoraSeccaoCriticaMs;

    private final ExecutorService executor = Executors.newFixedThreadPool(4, r -> new Thread(r, "reposicao"));
    private final Set<String> emCurso = ConcurrentHashMap.newKeySet();
    private final Deque<String> eventos = new ConcurrentLinkedDeque<>();
    private final List<Consumer<String>> aoCriarOrdem = new ArrayList<>();

    public ServicoReposicao(FragmentosBd fragmentos, RepositorioStock stock, Catalogo catalogo, ServicoOrdens ordens,
                            ExclusaoMutua exclusao, RelogioCorrigido relogio,
                            @Value("${scad.demo.demora-seccao-critica-ms:1500}") long demoraSeccaoCriticaMs) {
        this.fragmentos = fragmentos;
        this.stock = stock;
        this.catalogo = catalogo;
        this.ordens = ordens;
        this.exclusao = exclusao;
        this.relogio = relogio;
        this.demoraSeccaoCriticaMs = demoraSeccaoCriticaMs;
    }

    /** Regista quem deve ser avisado quando uma ordem é criada (ex.: envio ao fornecedor). */
    public void aoCriarOrdem(Consumer<String> accao) {
        aoCriarOrdem.add(accao);
    }

    /** Pede uma reposição em segundo plano (pedidos repetidos para o mesmo produto juntam-se). */
    @Override
    public void solicitar(String produtoId) {
        if (emCurso.add(produtoId)) {
            executor.execute(() -> {
                try {
                    repor(produtoId);
                } catch (RuntimeException e) {
                    evento("Reposição de " + produtoId + " falhou: " + e.getMessage());
                    log.warn("Reposição de {} falhou", produtoId, e);
                } finally {
                    emCurso.remove(produtoId);
                }
            });
        }
    }

    /** @return id da ordem criada, ou null se já não era precisa ou o 2PC abortou */
    public String repor(String produtoId) {
        Catalogo.Produto produto = catalogo.produto(produtoId)
                .orElseThrow(() -> new IllegalArgumentException("Produto desconhecido: " + produtoId));
        evento("Pede secção crítica para " + produto.recurso());
        String ordemId = exclusao.executar(produto.recurso(), () -> seccaoCritica(produto));
        evento("Liberta secção crítica de " + produto.recurso());
        if (ordemId != null) {
            aoCriarOrdem.forEach(a -> a.accept(ordemId));
        }
        return ordemId;
    }

    private String seccaoCritica(Catalogo.Produto produto) {
        evento("DENTRO da secção crítica de " + produto.recurso());
        pausaDeDemonstracao();

        // Passo 4: voltar a verificar (outro nó pode já ter encomendado para este armazém)
        String regiao = fragmentos.regiaoLocal();
        Stock local = stock.procurar(produto.id()).orElseThrow();
        if (!local.precisaReposicao()) {
            evento("Re-verificação: " + produto.id() + " já não precisa de reposição (quantidade=" + local.quantidade()
                    + ", em encomenda=" + local.emEncomenda() + ") -> não cria ordem");
            return null;
        }

        // Passo 5: repartição com o stock de todos os armazéns acessíveis, e 2PC
        Map<String, Stock> stocks = new LinkedHashMap<>();
        for (String r : fragmentos.regioes()) {
            try {
                stock.procurarEm(r, produto.id()).ifPresent(s -> stocks.put(r, s));
            } catch (DataAccessException e) {
                evento("Fragmento " + r + " inacessível: fica fora desta ordem");
            }
        }
        Map<String, Integer> reparticao = Reparticao.calcular(produto.lote(), regiao, stocks);
        evento("Repartição do lote de " + produto.lote() + ": " + reparticao);
        String ordemId = ordens.criar(produto, regiao, reparticao);
        evento(ordemId != null ? "Ordem " + ordemId + " criada (2PC COMMIT)" : "2PC ABORT: ordem não criada");
        return ordemId;
    }

    private void pausaDeDemonstracao() {
        if (demoraSeccaoCriticaMs > 0) {
            try {
                Thread.sleep(demoraSeccaoCriticaMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void evento(String texto) {
        log.info("[reposição] {}", texto);
        Instant agora = relogio.agora();
        eventos.addFirst(agora + "  " + texto);
        while (eventos.size() > MAX_EVENTOS) {
            eventos.removeLast();
        }
    }

    public List<String> eventos() {
        return new ArrayList<>(eventos);
    }

    @PreDestroy
    void parar() {
        executor.shutdownNow();
    }
}

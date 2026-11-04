package mz.ac.isutc.scad.no.fornecedores;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import mz.ac.isutc.scad.common.relogio.RelogioCorrigido;
import mz.ac.isutc.scad.fornecedor.contrato.ConfirmacaoOrdem;
import mz.ac.isutc.scad.fornecedor.contrato.PedidoOrdem;
import mz.ac.isutc.scad.no.fornecedores.ClienteFornecedor.FalhaEnvio;
import mz.ac.isutc.scad.no.ordens.Catalogo;
import mz.ac.isutc.scad.no.ordens.OrdemCompra;
import mz.ac.isutc.scad.no.ordens.RepositorioOrdens;
import mz.ac.isutc.scad.no.ordens.ServicoOrdens;
import mz.ac.isutc.scad.no.reposicao.ServicoReposicao;

/**
 * Passo 7 do fluxo: envia a ordem ao fornecedor, fora da secção crítica.
 *
 * <ul>
 *   <li><b>Adapter</b>: escolhe o {@link ClienteFornecedor} (REST ou SOAP) pelo protocolo do fornecedor.</li>
 *   <li><b>Timeouts</b> (2 s de ligação, 4 s de resposta) e até {@value #MAX_TENTATIVAS} tentativas com espera
 *       exponencial (0,5 s, 1 s, ...). Erros permanentes (dados inválidos) não se repetem.</li>
 *   <li><b>Idempotência</b>: o id da ordem é a chave; um reenvio nunca duplica a compra no fornecedor.</li>
 *   <li>Se falhar, a ordem fica {@code PENDENTE_ENVIO} e é reenviada pelo processo periódico.</li>
 *   <li>Com sucesso, passa a {@code ENVIADA} (2PC em todos os fragmentos participantes).</li>
 * </ul>
 */
@Service
public class GatewayFornecedores {

    private static final Logger log = LoggerFactory.getLogger(GatewayFornecedores.class);
    static final int MAX_TENTATIVAS = 3;
    private static final long ESPERA_INICIAL_MS = 500;

    private final Map<String, ClienteFornecedor> clientes;
    private final RepositorioOrdens ordens;
    private final ServicoOrdens servicoOrdens;
    private final Catalogo catalogo;
    private final RelogioCorrigido relogio;
    private final String urlBase;

    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> new Thread(r, "gateway-fornecedores"));
    private final Set<String> emEnvio = ConcurrentHashMap.newKeySet();
    private final Deque<String> eventos = new ConcurrentLinkedDeque<>();
    private final List<Consumer<String>> aoEnviar = new CopyOnWriteArrayList<>();

    public GatewayFornecedores(List<ClienteFornecedor> clientes, RepositorioOrdens ordens, ServicoOrdens servicoOrdens,
                               Catalogo catalogo, RelogioCorrigido relogio, ServicoReposicao reposicao,
                               @Value("${scad.fornecedores.url-base}") String urlBase) {
        this.clientes = clientes.stream().collect(Collectors.toMap(ClienteFornecedor::protocolo, Function.identity()));
        this.ordens = ordens;
        this.servicoOrdens = servicoOrdens;
        this.catalogo = catalogo;
        this.relogio = relogio;
        this.urlBase = urlBase;
        reposicao.aoCriarOrdem(this::enviarEmSegundoPlano);
    }

    /** Regista quem deve ser avisado quando uma ordem fica ENVIADA (ex.: factura em PDF). */
    public void aoEnviar(Consumer<String> accao) {
        aoEnviar.add(accao);
    }

    public void enviarEmSegundoPlano(String ordemId) {
        executor.execute(() -> enviar(ordemId));
    }

    /** Reenvio periódico das ordens que ficaram por enviar. */
    @Scheduled(initialDelay = 15_000, fixedDelay = 15_000)
    public void reenviarPendentes() {
        try {
            ordens.pendentesDeEnvio().forEach(this::enviarEmSegundoPlano);
        } catch (DataAccessException e) {
            log.debug("Reenvio adiado: {}", e.getMessage());
        }
    }

    /** @return true se a ordem ficou ENVIADA */
    public boolean enviar(String ordemId) {
        if (!emEnvio.add(ordemId)) {
            return false;   // já há um envio desta ordem em curso
        }
        try {
            OrdemCompra ordem = ordens.procurar(ordemId).orElse(null);
            if (ordem == null || ordem.estado() != OrdemCompra.Estado.PENDENTE_ENVIO) {
                return false;
            }
            int somaLinhas = ordem.linhas().values().stream().mapToInt(Integer::intValue).sum();
            if (somaLinhas != ordem.quantidadeTotal()) {
                evento(ordemId + ": há fragmentos inacessíveis (linhas incompletas); o envio fica para depois");
                return false;
            }
            Catalogo.Produto produto = catalogo.produto(ordem.produtoId()).orElseThrow();
            ClienteFornecedor cliente = clientes.get(produto.protocolo());
            String url = urlBase + produto.endpoint();
            PedidoOrdem pedido = paraPedido(ordem, produto);

            for (int tentativa = 1; tentativa <= MAX_TENTATIVAS; tentativa++) {
                try {
                    ConfirmacaoOrdem c = cliente.submeter(url, pedido);
                    servicoOrdens.marcarEnviada(ordemId, c.getNumeroConfirmacao(),
                            c.getPrazoEntrega() == null ? null : LocalDate.parse(c.getPrazoEntrega()));
                    evento(ordemId + " ENVIADA por " + cliente.protocolo() + " (tentativa " + tentativa + "): confirmação "
                            + c.getNumeroConfirmacao() + (c.isDuplicado() ? " [duplicado reconhecido pelo fornecedor]" : "")
                            + ", entrega até " + c.getPrazoEntrega());
                    aoEnviar.forEach(a -> a.accept(ordemId));
                    return true;
                } catch (FalhaEnvio f) {
                    evento(ordemId + " tentativa " + tentativa + "/" + MAX_TENTATIVAS + " por " + cliente.protocolo()
                            + " falhou: " + f.getMessage());
                    if (!f.temporaria()) {
                        evento(ordemId + ": erro permanente, não se repete");
                        break;
                    }
                    if (tentativa < MAX_TENTATIVAS) {
                        dormir(ESPERA_INICIAL_MS << (tentativa - 1));   // espera exponencial
                    }
                }
            }
            evento(ordemId + " fica PENDENTE_ENVIO; o reenvio automático tenta de novo dentro de 15 s");
            return false;
        } catch (RuntimeException e) {
            evento(ordemId + ": envio interrompido (" + e.getMessage() + ")");
            return false;
        } finally {
            emEnvio.remove(ordemId);
        }
    }

    private PedidoOrdem paraPedido(OrdemCompra o, Catalogo.Produto produto) {
        List<PedidoOrdem.LinhaEntrega> linhas = o.linhas().entrySet().stream()
                .map(e -> new PedidoOrdem.LinhaEntrega(e.getKey(), e.getValue())).toList();
        return new PedidoOrdem(o.id(), produto.fornecedorId(), produto.id(), produto.nome(), o.quantidadeTotal(),
                linhas, o.tsCorrigido().toString());
    }

    private void evento(String texto) {
        log.info("[fornecedores] {}", texto);
        Instant agora = relogio.agora();
        eventos.addFirst(agora + "  " + texto);
        while (eventos.size() > 60) {
            eventos.removeLast();
        }
    }

    public List<String> eventos() {
        return new ArrayList<>(eventos);
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    void parar() {
        executor.shutdownNow();
    }
}

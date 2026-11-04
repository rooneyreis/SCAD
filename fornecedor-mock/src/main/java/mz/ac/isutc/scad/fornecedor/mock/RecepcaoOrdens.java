package mz.ac.isutc.scad.fornecedor.mock;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import mz.ac.isutc.scad.fornecedor.contrato.ConfirmacaoOrdem;
import mz.ac.isutc.scad.fornecedor.contrato.FalhaFornecedor;
import mz.ac.isutc.scad.fornecedor.contrato.PedidoOrdem;

/**
 * Lógica comum às duas rotas (REST e SOAP): validação, idempotência e o "interruptor" de falhas.
 *
 * <p>Idempotência: o {@code ordemId} é a chave. Uma ordem repetida (reenvio do cliente depois de um
 * timeout, por exemplo) devolve a confirmação original e <b>nunca</b> gera uma segunda compra.
 */
@Service
public class RecepcaoOrdens {

    private static final Logger log = LoggerFactory.getLogger(RecepcaoOrdens.class);
    static final long DEMORA_MODO_LENTO_MS = 8_000;

    public enum Modo { NORMAL, EM_BAIXO, LENTO, ERRO }

    private volatile Modo modo = Modo.NORMAL;
    private final Map<String, ConfirmacaoOrdem> ordens = new ConcurrentHashMap<>();
    private final AtomicInteger sequencia = new AtomicInteger(1000);
    private final AtomicInteger pedidos = new AtomicInteger();
    private final AtomicInteger duplicados = new AtomicInteger();
    private final AtomicInteger rejeitados = new AtomicInteger();
    private final Deque<String> registo = new ConcurrentLinkedDeque<>();

    public ConfirmacaoOrdem receber(PedidoOrdem p, String canal) throws FalhaFornecedor {
        pedidos.incrementAndGet();
        Modo m = modo;
        switch (m) {
            case EM_BAIXO -> {
                rejeitados.incrementAndGet();
                registar(canal + " " + id(p) + " REJEITADO: fornecedor em baixo");
                throw FalhaFornecedor.de("INDISPONIVEL", "Fornecedor temporariamente indisponível", true);
            }
            case ERRO -> {
                rejeitados.incrementAndGet();
                registar(canal + " " + id(p) + " REJEITADO: erro interno simulado");
                throw FalhaFornecedor.de("ERRO_INTERNO", "Erro interno no sistema do fornecedor", true);
            }
            case LENTO -> {
                registar(canal + " " + id(p) + " modo LENTO: responde daqui a " + DEMORA_MODO_LENTO_MS + " ms");
                dormir(DEMORA_MODO_LENTO_MS);
            }
            case NORMAL -> { }
        }

        validar(p);
        ConfirmacaoOrdem existente = ordens.get(p.getOrdemId());
        if (existente != null) {
            duplicados.incrementAndGet();
            registar(canal + " " + p.getOrdemId() + " DUPLICADO: devolve a confirmação " + existente.getNumeroConfirmacao());
            return existente.comoDuplicado();
        }
        ConfirmacaoOrdem nova = new ConfirmacaoOrdem(p.getOrdemId(), "CONF-" + sequencia.incrementAndGet(),
                LocalDate.now().plusDays(3).toString(), false);
        ConfirmacaoOrdem corrida = ordens.putIfAbsent(p.getOrdemId(), nova);
        if (corrida != null) {    // dois pedidos iguais em simultâneo: só um cria
            duplicados.incrementAndGet();
            return corrida.comoDuplicado();
        }
        registar(canal + " " + p.getOrdemId() + " ACEITE: " + p.getQuantidade() + " x " + p.getProdutoId()
                + " -> " + nova.getNumeroConfirmacao());
        return nova;
    }

    private void validar(PedidoOrdem p) throws FalhaFornecedor {
        List<String> erros = new ArrayList<>();
        if (p == null) {
            throw FalhaFornecedor.de("PEDIDO_VAZIO", "Pedido vazio", false);
        }
        if (vazio(p.getOrdemId())) {
            erros.add("ordemId é obrigatório");
        }
        if (vazio(p.getProdutoId())) {
            erros.add("produtoId é obrigatório");
        }
        if (vazio(p.getFornecedorId())) {
            erros.add("fornecedorId é obrigatório");
        }
        if (p.getQuantidade() <= 0) {
            erros.add("quantidade tem de ser positiva");
        }
        int soma = p.getLinhas() == null ? 0 : p.getLinhas().stream().mapToInt(PedidoOrdem.LinhaEntrega::getQuantidade).sum();
        if (soma != p.getQuantidade()) {
            erros.add("a soma das linhas (" + soma + ") difere da quantidade (" + p.getQuantidade() + ")");
        }
        if (!erros.isEmpty()) {
            rejeitados.incrementAndGet();
            registar("REJEITADO " + id(p) + ": " + String.join("; ", erros));
            throw FalhaFornecedor.de("DADOS_INVALIDOS", String.join("; ", erros), false);
        }
    }

    public Modo modo() {
        return modo;
    }

    public void definirModo(Modo novo) {
        log.warn("Modo do fornecedor: {} -> {}", modo, novo);
        registar("MODO alterado para " + novo);
        this.modo = novo;
    }

    public Map<String, Object> estatisticas() {
        return Map.of("modo", modo, "pedidosRecebidos", pedidos.get(), "ordensUnicas", ordens.size(),
                "duplicadosIgnorados", duplicados.get(), "rejeitados", rejeitados.get(),
                "registo", new ArrayList<>(registo));
    }

    private void registar(String texto) {
        log.info(texto);
        registo.addFirst(Instant.now() + "  " + texto);
        while (registo.size() > 50) {
            registo.removeLast();
        }
    }

    private static String id(PedidoOrdem p) {
        return p == null ? "?" : p.getOrdemId();
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

package mz.ac.isutc.scad.no.loja;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.stereotype.Service;

import mz.ac.isutc.scad.common.relogio.RelogioCorrigido;
import mz.ac.isutc.scad.common.relogio.RelogioLamport;
import mz.ac.isutc.scad.no.agente.DespachoAlertas;
import mz.ac.isutc.scad.no.agente.DetectorStock;
import mz.ac.isutc.scad.no.bd.FragmentosBd;
import mz.ac.isutc.scad.no.loja.ErrosLoja.PedidoInexistente;
import mz.ac.isutc.scad.no.loja.ErrosLoja.PedidoJaDecidido;
import mz.ac.isutc.scad.no.loja.ErrosLoja.StockIndisponivel;
import mz.ac.isutc.scad.no.loja.PedidoCliente.Estado;
import mz.ac.isutc.scad.no.loja.PedidoCliente.Linha;
import mz.ac.isutc.scad.no.movimentos.ErrosMovimento.ArmazemForaDoFragmento;
import mz.ac.isutc.scad.no.movimentos.ErrosMovimento.ProdutoNaoEncontrado;
import mz.ac.isutc.scad.no.reposicao.PedidosReposicao;
import mz.ac.isutc.scad.no.stock.Movimento;
import mz.ac.isutc.scad.no.stock.Movimento.TipoMovimento;
import mz.ac.isutc.scad.no.stock.RepositorioMovimentos;
import mz.ac.isutc.scad.no.stock.RepositorioStock;
import mz.ac.isutc.scad.no.stock.Stock;

/**
 * Pedidos de clientes da loja: compra (com reserva de stock) e autorização/recusa no armazém.
 *
 * <p>Cada pedido pertence a um só armazém, por isso tudo é uma <b>transacção local</b> no fragmento
 * dessa região (não é preciso 2PC nem exclusão mútua distribuída): a reserva é um UPDATE condicional
 * ({@code quantidade − reservado >= pedido}) e o PostgreSQL serializa as reservas concorrentes da
 * mesma linha de stock.
 */
@Service
public class ServicoLoja {

    /** Motivo e origem dos movimentos de stock gerados pela loja. */
    public static final String MOTIVO_VENDA = "VENDA";
    public static final String ORIGEM_LOJA = "LOJA";

    private final FragmentosBd fragmentos;
    private final RepositorioStock stock;
    private final RepositorioPedidos pedidos;
    private final RepositorioMovimentos movimentos;
    private final DetectorStock detector;
    private final DespachoAlertas despacho;
    private final PedidosReposicao reposicao;
    private final ArquivoRecibos recibos;
    private final RelogioLamport lamport;
    private final RelogioCorrigido relogio;

    public ServicoLoja(FragmentosBd fragmentos, RepositorioStock stock, RepositorioPedidos pedidos,
                       RepositorioMovimentos movimentos, DetectorStock detector, DespachoAlertas despacho,
                       PedidosReposicao reposicao, ArquivoRecibos recibos, RelogioLamport lamport,
                       RelogioCorrigido relogio) {
        this.fragmentos = fragmentos;
        this.stock = stock;
        this.pedidos = pedidos;
        this.movimentos = movimentos;
        this.detector = detector;
        this.despacho = despacho;
        this.reposicao = reposicao;
        this.recibos = recibos;
        this.lamport = lamport;
        this.relogio = relogio;
    }

    /**
     * Compra na loja: cria o pedido PENDENTE e reserva o stock, numa só transacção local.
     * Se algum produto já não tiver disponível suficiente, nada fica reservado.
     *
     * <p>A reserva baixa a posição de stock, por isso é aqui que a reposição pode disparar.
     */
    public PedidoCliente comprar(NovoPedido novo) {
        String regiao = verificarRegiao(novo.armazemId());
        // Produtos repetidos juntam-se; a ordem fixa (por id) evita deadlocks entre compras concorrentes
        Map<String, Integer> quantidades = new TreeMap<>();
        novo.itens().forEach(i -> quantidades.merge(i.produtoId(), i.quantidade(), Integer::sum));

        List<Stock> aposReserva = new ArrayList<>();
        Resultado<PedidoCliente> r = fragmentos.transaccaoLocal().execute(estado -> {
            List<Linha> linhas = new ArrayList<>();
            boolean alertas = false;
            for (Map.Entry<String, Integer> e : quantidades.entrySet()) {
                Linha linha = pedidos.precoDeVenda(e.getKey(), e.getValue())
                        .orElseThrow(() -> new ProdutoNaoEncontrado(e.getKey(), regiao));
                Stock s = stock.reservar(e.getKey(), e.getValue())
                        .orElseThrow(() -> semDisponivel(linha));
                linhas.add(linha);
                aposReserva.add(s);
                alertas |= detector.avaliar(s).isPresent();
            }
            BigDecimal total = linhas.stream().map(Linha::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            PedidoCliente pedido = new PedidoCliente(pedidos.novoId(), regiao, novo.clienteNome().trim(),
                    novo.clienteTelefone().trim(), Estado.PENDENTE, total, referenciaPagamento(), null, null,
                    lamport.tick(), relogio.agora(), null, linhas);
            pedidos.inserir(pedido);
            return new Resultado<>(pedido, alertas);
        });

        if (r.alertas()) {
            despacho.solicitarEnvio();
        }
        aposReserva.stream().filter(Stock::precisaReposicao).forEach(s -> reposicao.solicitar(s.produtoId()));
        return r.valor();
    }

    /**
     * Autorização no painel do armazém: o mesmo que "Registar saída" (movimento SAIDA, motivo VENDA,
     * origem LOJA, com Lamport e hora corrigida), mas retirando do reservado. Gera o recibo PDF.
     */
    public PedidoCliente autorizar(String pedidoId) {
        String regiao = verificarRegiao(PedidoCliente.regiaoDoId(pedidoId));
        Instant agora = relogio.agora();
        String recibo = PedidoCliente.caminhoRecibo(pedidoId, agora);

        boolean alertas = Boolean.TRUE.equals(fragmentos.transaccaoLocal().execute(estado -> {
            PedidoCliente pedido = decidir(pedidoId, Estado.AUTORIZADO, null, recibo, agora);
            boolean novos = false;
            for (Linha l : pedido.linhas()) {
                Stock apos = stock.retirarReservado(l.produtoId(), l.quantidade())
                        .orElseThrow(() -> new IllegalStateException("Reserva de " + l.produtoId()
                                + " em falta para o pedido " + pedidoId));
                movimentos.inserir(new Movimento(0, regiao, l.produtoId(), TipoMovimento.SAIDA, MOTIVO_VENDA,
                        l.quantidade(), lamport.tick(), relogio.agora(), ORIGEM_LOJA));
                novos |= detector.avaliar(apos).isPresent();
            }
            return novos;
        }));

        if (alertas) {
            despacho.solicitarEnvio();
        }
        PedidoCliente autorizado = pedidos.procurar(pedidoId).orElseThrow();
        recibos.arquivarRecibo(autorizado);
        return autorizado;
    }

    /** Recusa no painel do armazém: liberta a reserva. */
    public PedidoCliente recusar(String pedidoId, String motivo) {
        verificarRegiao(PedidoCliente.regiaoDoId(pedidoId));
        fragmentos.transaccaoLocal().executeWithoutResult(estado -> {
            PedidoCliente pedido = decidir(pedidoId, Estado.RECUSADO, motivo.trim(), null, relogio.agora());
            for (Linha l : pedido.linhas()) {
                stock.libertarReserva(l.produtoId(), l.quantidade())
                        .orElseThrow(() -> new IllegalStateException("Reserva de " + l.produtoId()
                                + " em falta para o pedido " + pedidoId));
            }
        });
        return pedidos.procurar(pedidoId).orElseThrow();
    }

    /** Muda o estado (só a partir de PENDENTE) e devolve o pedido com as linhas. */
    private PedidoCliente decidir(String pedidoId, Estado novo, String motivo, String recibo, Instant quando) {
        PedidoCliente pedido = pedidos.procurar(pedidoId).orElseThrow(() -> new PedidoInexistente(pedidoId));
        if (!pedidos.decidir(pedidoId, novo, motivo, recibo, quando)) {
            // outro operador decidiu entretanto: devolve o estado actual
            throw new PedidoJaDecidido(pedidoId, pedidos.procurar(pedidoId).map(PedidoCliente::estado).orElse(novo));
        }
        return pedido;
    }

    /** Cada nó só escreve no fragmento do seu armazém. */
    private String verificarRegiao(String armazemId) {
        String regiao = fragmentos.regiaoLocal();
        if (!regiao.equals(armazemId)) {
            throw new ArmazemForaDoFragmento(armazemId, regiao);
        }
        return regiao;
    }

    private StockIndisponivel semDisponivel(Linha linha) {
        int disponivel = stock.procurar(linha.produtoId()).map(Stock::disponivel).orElse(0);
        return new StockIndisponivel(linha.produtoId(), linha.produtoNome(), linha.quantidade(), disponivel);
    }

    /** Pagamento simulado: só se gera uma referência. */
    private static String referenciaPagamento() {
        return "SIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private record Resultado<T>(T valor, boolean alertas) {
    }
}

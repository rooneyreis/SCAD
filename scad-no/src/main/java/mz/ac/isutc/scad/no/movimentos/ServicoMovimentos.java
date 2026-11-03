package mz.ac.isutc.scad.no.movimentos;

import org.springframework.stereotype.Service;

import mz.ac.isutc.scad.common.relogio.RelogioCorrigido;
import mz.ac.isutc.scad.common.relogio.RelogioLamport;
import mz.ac.isutc.scad.no.agente.DespachoAlertas;
import mz.ac.isutc.scad.no.agente.DetectorStock;
import mz.ac.isutc.scad.no.bd.FragmentosBd;
import mz.ac.isutc.scad.no.movimentos.ErrosMovimento.ArmazemForaDoFragmento;
import mz.ac.isutc.scad.no.movimentos.ErrosMovimento.ProdutoNaoEncontrado;
import mz.ac.isutc.scad.no.movimentos.ErrosMovimento.StockInsuficiente;
import mz.ac.isutc.scad.no.stock.Alerta;
import mz.ac.isutc.scad.no.stock.Movimento;
import mz.ac.isutc.scad.no.stock.Movimento.TipoMovimento;
import mz.ac.isutc.scad.no.stock.RepositorioMovimentos;
import mz.ac.isutc.scad.no.stock.RepositorioStock;
import mz.ac.isutc.scad.no.stock.Stock;

/**
 * Passo 1 e 2 do fluxo de compra: regista a saída de stock e, se for o caso, emite o alerta.
 *
 * <p>Numa única transacção local: retira o stock (de forma atómica), grava o movimento com
 * carimbo de Lamport e hora corrigida, e grava o alerta na fila local. O envio do alerta à
 * Central (CORBA) acontece depois do commit, de forma assíncrona.
 */
@Service
public class ServicoMovimentos {

    private final FragmentosBd fragmentos;
    private final RepositorioStock stock;
    private final RepositorioMovimentos movimentos;
    private final DetectorStock detector;
    private final DespachoAlertas despacho;
    private final RelogioLamport lamport;
    private final RelogioCorrigido relogio;

    public ServicoMovimentos(FragmentosBd fragmentos, RepositorioStock stock, RepositorioMovimentos movimentos,
                             DetectorStock detector, DespachoAlertas despacho,
                             RelogioLamport lamport, RelogioCorrigido relogio) {
        this.fragmentos = fragmentos;
        this.stock = stock;
        this.movimentos = movimentos;
        this.detector = detector;
        this.despacho = despacho;
        this.lamport = lamport;
        this.relogio = relogio;
    }

    public ResultadoSaida registarSaida(PedidoSaida pedido) {
        String regiao = fragmentos.regiaoLocal();
        if (!regiao.equals(pedido.armazemId())) {
            throw new ArmazemForaDoFragmento(pedido.armazemId(), regiao);
        }

        ResultadoSaida resultado = fragmentos.transaccaoLocal().execute(estado -> {
            Stock apos = stock.retirar(pedido.produtoId(), pedido.quantidade())
                    .orElseThrow(() -> motivoFalha(pedido));
            Movimento movimento = movimentos.inserir(new Movimento(0, regiao, pedido.produtoId(),
                    TipoMovimento.SAIDA, pedido.motivo().trim(), pedido.quantidade(),
                    lamport.tick(), relogio.agora(), regiao));
            Alerta alerta = detector.avaliar(apos).orElse(null);
            return new ResultadoSaida(movimento, apos, apos.nivel(), apos.precisaReposicao(), alerta);
        });

        if (resultado.alerta() != null) {
            despacho.solicitarEnvio();
        }
        return resultado;
    }

    private RuntimeException motivoFalha(PedidoSaida pedido) {
        return stock.procurar(pedido.produtoId())
                .<RuntimeException>map(s -> new StockInsuficiente(pedido.produtoId(), pedido.quantidade(), s.quantidade()))
                .orElseGet(() -> new ProdutoNaoEncontrado(pedido.produtoId(), pedido.armazemId()));
    }
}

package mz.ac.isutc.scad.no.corba;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import mz.ac.isutc.scad.common.relogio.RelogioCorrigido;
import mz.ac.isutc.scad.corba.AgenteStockPOA;
import mz.ac.isutc.scad.corba.ProdutoInexistente;
import mz.ac.isutc.scad.corba.SaldoProduto;
import mz.ac.isutc.scad.no.stock.RepositorioStock;
import mz.ac.isutc.scad.no.stock.Stock;

/**
 * Servant CORBA do agente de stock deste nó: expõe à Central os saldos do fragmento local
 * e o relógio corrigido (usado pelo mestre Berkeley).
 */
@Component
public class AgenteStockImpl extends AgenteStockPOA {

    private static final Logger log = LoggerFactory.getLogger(AgenteStockImpl.class);

    private final RepositorioStock stock;
    private final RelogioCorrigido relogio;

    public AgenteStockImpl(RepositorioStock stock, RelogioCorrigido relogio) {
        this.stock = stock;
        this.relogio = relogio;
    }

    @Override
    public SaldoProduto consultarSaldo(String produtoId) throws ProdutoInexistente {
        Stock s = stock.procurar(produtoId).orElseThrow(() -> new ProdutoInexistente(produtoId));
        return paraIdl(s);
    }

    @Override
    public SaldoProduto[] consultarTodos() {
        return stock.listar().stream().map(this::paraIdl).toArray(SaldoProduto[]::new);
    }

    @Override
    public long obterRelogio() {
        return relogio.agoraMs();
    }

    @Override
    public void ajustarRelogio(long delta) {
        relogio.ajustar(delta);
        log.info("Relógio ajustado em {} ms pelo mestre Berkeley (desvio efectivo: {} ms)",
                delta, relogio.desvioEfectivoMs());
    }

    private SaldoProduto paraIdl(Stock s) {
        return new SaldoProduto(s.produtoId(), s.armazemId(), s.quantidade(), s.minimo(), s.emEncomenda(),
                relogio.agoraMs());
    }
}

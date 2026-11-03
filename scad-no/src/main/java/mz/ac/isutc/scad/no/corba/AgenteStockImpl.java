package mz.ac.isutc.scad.no.corba;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import mz.ac.isutc.scad.corba.AgenteStockPOA;
import mz.ac.isutc.scad.corba.ProdutoInexistente;
import mz.ac.isutc.scad.corba.SaldoProduto;
import mz.ac.isutc.scad.no.config.PropriedadesNo;

/**
 * Servant CORBA do agente de stock deste nó.
 *
 * <p>Fase 1: ainda sem base de dados (devolve uma lista vazia) e com o relógio do sistema.
 * A Fase 2 liga-o ao fragmento PostgreSQL e a Fase 4 ao relógio corrigido (Berkeley).
 */
@Component
public class AgenteStockImpl extends AgenteStockPOA {

    private static final Logger log = LoggerFactory.getLogger(AgenteStockImpl.class);

    private final String regiao;

    public AgenteStockImpl(PropriedadesNo props) {
        this.regiao = props.regiao();
    }

    @Override
    public SaldoProduto consultarSaldo(String produtoId) throws ProdutoInexistente {
        throw new ProdutoInexistente(produtoId);
    }

    @Override
    public SaldoProduto[] consultarTodos() {
        log.info("[{}] consultarTodos() pedido pela Central", regiao);
        return new SaldoProduto[0];
    }

    @Override
    public long obterRelogio() {
        return System.currentTimeMillis();
    }

    @Override
    public void ajustarRelogio(long delta) {
        log.info("[{}] ajustarRelogio({} ms) recebido", regiao, delta);
    }
}

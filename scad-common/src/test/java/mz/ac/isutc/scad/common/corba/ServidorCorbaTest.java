package mz.ac.isutc.scad.common.corba;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import mz.ac.isutc.scad.corba.AgenteStock;
import mz.ac.isutc.scad.corba.AgenteStockHelper;
import mz.ac.isutc.scad.corba.AgenteStockPOA;
import mz.ac.isutc.scad.corba.ProdutoInexistente;
import mz.ac.isutc.scad.corba.SaldoProduto;

/**
 * Prova mínima de que o JacORB funciona em Java 17: uma chamada remota real (IIOP sobre TCP)
 * entre dois ORBs no mesmo processo, incluindo a propagação de uma excepção do IDL.
 */
class ServidorCorbaTest {

    private static ServidorCorba servidor;
    private static ServidorCorba cliente;

    @BeforeAll
    static void arrancar() {
        servidor = new ServidorCorba(new ConfiguracaoOrb("teste-servidor", "127.0.0.1", 38701, null));
        cliente = new ServidorCorba(new ConfiguracaoOrb("teste-cliente", "127.0.0.1", 38702, null));
    }

    @AfterAll
    static void parar() {
        cliente.parar();
        servidor.parar();
    }

    @Test
    void chamadaRemotaDevolveSaldoEPropagaExcepcao() throws Exception {
        String ior = servidor.orb().object_to_string(servidor.activar(new AgenteFalso()));

        // O cliente só conhece o IOR em texto, como aconteceria entre containers
        AgenteStock agente = AgenteStockHelper.narrow(cliente.orb().string_to_object(ior));

        SaldoProduto saldo = agente.consultarSaldo("ARROZ-25KG");
        assertEquals("ARROZ-25KG", saldo.produtoId);
        assertEquals(120, saldo.quantidade);
        assertEquals(42L, agente.obterRelogio());

        ProdutoInexistente erro = assertThrows(ProdutoInexistente.class, () -> agente.consultarSaldo("NAO-EXISTE"));
        assertEquals("NAO-EXISTE", erro.produtoId);
    }

    private static final class AgenteFalso extends AgenteStockPOA {
        @Override
        public SaldoProduto consultarSaldo(String produtoId) throws ProdutoInexistente {
            if (!"ARROZ-25KG".equals(produtoId)) {
                throw new ProdutoInexistente(produtoId);
            }
            return new SaldoProduto(produtoId, "NORTE", 120, 100, 0, 42L);
        }

        @Override
        public SaldoProduto[] consultarTodos() {
            return new SaldoProduto[0];
        }

        @Override
        public long obterRelogio() {
            return 42L;
        }

        @Override
        public void ajustarRelogio(long delta) {
        }
    }
}

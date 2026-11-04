package mz.ac.isutc.scad.fornecedor.mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import mz.ac.isutc.scad.fornecedor.contrato.ConfirmacaoOrdem;
import mz.ac.isutc.scad.fornecedor.contrato.FalhaFornecedor;
import mz.ac.isutc.scad.fornecedor.contrato.PedidoOrdem;
import mz.ac.isutc.scad.fornecedor.contrato.PedidoOrdem.LinhaEntrega;

class RecepcaoOrdensTest {

    private final RecepcaoOrdens recepcao = new RecepcaoOrdens();

    private static PedidoOrdem pedido(String id) {
        return new PedidoOrdem(id, "GRAO-DOURADO", "ARROZ-25", "Arroz 25 kg", 500,
                List.of(new LinhaEntrega("NORTE", 305), new LinhaEntrega("CENTRO", 195)), "2026-11-04T10:00:00Z");
    }

    @Test
    void reenvioDaMesmaOrdemNuncaDuplicaACompra() throws Exception {
        ConfirmacaoOrdem a = recepcao.receber(pedido("OC-NORTE-000001"), "REST");
        ConfirmacaoOrdem b = recepcao.receber(pedido("OC-NORTE-000001"), "SOAP");
        assertFalse(a.isDuplicado());
        assertTrue(b.isDuplicado());
        assertEquals(a.getNumeroConfirmacao(), b.getNumeroConfirmacao());
        assertEquals(1, recepcao.estatisticas().get("ordensUnicas"));
        assertEquals(1, recepcao.estatisticas().get("duplicadosIgnorados"));
    }

    @Test
    void dadosInvalidosSaoRejeitadosComoFalhaPermanente() {
        PedidoOrdem p = pedido("OC-NORTE-000002");
        p.setQuantidade(400);   // linhas somam 500
        FalhaFornecedor f = assertThrows(FalhaFornecedor.class, () -> recepcao.receber(p, "REST"));
        assertEquals("DADOS_INVALIDOS", f.getFaultInfo().getCodigo());
        assertFalse(f.getFaultInfo().isTemporaria());
    }

    @Test
    void emBaixoEErroSaoFalhasTemporarias() {
        recepcao.definirModo(RecepcaoOrdens.Modo.EM_BAIXO);
        assertTrue(assertThrows(FalhaFornecedor.class, () -> recepcao.receber(pedido("X-1"), "REST"))
                .getFaultInfo().isTemporaria());
        recepcao.definirModo(RecepcaoOrdens.Modo.ERRO);
        assertEquals("ERRO_INTERNO", assertThrows(FalhaFornecedor.class, () -> recepcao.receber(pedido("X-1"), "SOAP"))
                .getFaultInfo().getCodigo());
        assertEquals(0, recepcao.estatisticas().get("ordensUnicas"), "nada foi comprado");
    }
}

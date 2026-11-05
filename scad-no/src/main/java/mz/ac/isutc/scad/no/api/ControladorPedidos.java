package mz.ac.isutc.scad.no.api;

import java.time.ZoneId;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import mz.ac.isutc.scad.common.relogio.RelogioCorrigido;
import mz.ac.isutc.scad.no.loja.PedidoCliente;
import mz.ac.isutc.scad.no.loja.RepositorioPedidos;
import mz.ac.isutc.scad.no.loja.ServicoLoja;

/** Pedidos de clientes do armazém deste nó, para o painel: listar, autorizar e recusar. */
@RestController
@RequestMapping("/api/pedidos")
public class ControladorPedidos {

    private static final ZoneId MAPUTO = ZoneId.of("Africa/Maputo");

    private final RepositorioPedidos pedidos;
    private final ServicoLoja loja;
    private final RelogioCorrigido relogio;

    public ControladorPedidos(RepositorioPedidos pedidos, ServicoLoja loja, RelogioCorrigido relogio) {
        this.pedidos = pedidos;
        this.loja = loja;
        this.relogio = relogio;
    }

    /** @param estado PENDENTE, AUTORIZADO ou RECUSADO (omisso = todos) */
    @GetMapping
    public List<PedidoCliente> listar(@RequestParam(required = false) PedidoCliente.Estado estado,
                                      @RequestParam(defaultValue = "50") int limite) {
        return pedidos.listar(estado, Math.max(1, Math.min(limite, 200)));
    }

    /** Pedidos pendentes e vendas autorizadas hoje (dia de Maputo, pela hora corrigida). */
    @GetMapping("/resumo")
    public RepositorioPedidos.Resumo resumo() {
        return pedidos.resumo(relogio.agora().atZone(MAPUTO).toLocalDate().atStartOfDay(MAPUTO).toInstant());
    }

    @PostMapping("/{id}/autorizar")
    public PedidoCliente autorizar(@PathVariable String id) {
        return loja.autorizar(id);
    }

    @PostMapping("/{id}/recusar")
    public PedidoCliente recusar(@PathVariable String id, @Valid @RequestBody Recusa recusa) {
        return loja.recusar(id, recusa.motivo());
    }

    public record Recusa(@NotBlank @Size(max = 200) String motivo) {
    }
}

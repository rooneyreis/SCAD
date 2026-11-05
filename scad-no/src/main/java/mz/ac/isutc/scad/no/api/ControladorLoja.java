package mz.ac.isutc.scad.no.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import mz.ac.isutc.scad.no.loja.CatalogoLoja;
import mz.ac.isutc.scad.no.loja.ErrosLoja.PedidoInexistente;
import mz.ac.isutc.scad.no.loja.NovoPedido;
import mz.ac.isutc.scad.no.loja.PedidoCliente;
import mz.ac.isutc.scad.no.loja.RepositorioPedidos;
import mz.ac.isutc.scad.no.loja.ServicoLoja;

/**
 * API pública da loja (clientes). A compra tem de ser enviada ao nó da cidade escolhida;
 * o catálogo e a consulta de um pedido funcionam em qualquer nó.
 */
@RestController
@RequestMapping("/api/loja")
public class ControladorLoja {

    private final CatalogoLoja catalogo;
    private final ServicoLoja loja;
    private final RepositorioPedidos pedidos;

    public ControladorLoja(CatalogoLoja catalogo, ServicoLoja loja, RepositorioPedidos pedidos) {
        this.catalogo = catalogo;
        this.loja = loja;
        this.pedidos = pedidos;
    }

    /** Produtos, preços e disponível em cada cidade (e quais estão indisponíveis de momento). */
    @GetMapping("/catalogo")
    public List<CatalogoLoja.Cidade> catalogo() {
        return catalogo.cidades();
    }

    @PostMapping("/pedidos")
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoCliente comprar(@Valid @RequestBody NovoPedido pedido) {
        return loja.comprar(pedido);
    }

    /** Estado de um pedido ("o meu pedido"): PENDENTE → AUTORIZADO / RECUSADO. */
    @GetMapping("/pedidos/{id}")
    public PedidoCliente pedido(@PathVariable String id) {
        return pedidos.procurar(id).orElseThrow(() -> new PedidoInexistente(id));
    }
}

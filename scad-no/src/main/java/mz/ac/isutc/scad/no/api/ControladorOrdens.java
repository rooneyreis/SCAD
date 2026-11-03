package mz.ac.isutc.scad.no.api;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import mz.ac.isutc.scad.no.ordens.OrdemCompra;
import mz.ac.isutc.scad.no.ordens.RepositorioOrdens;
import mz.ac.isutc.scad.no.ordens.ServicoOrdens;
import mz.ac.isutc.scad.no.ordens.ServicoOrdens.OrdemInexistenteException;
import mz.ac.isutc.scad.no.transaccoes.Cronologia2PC.Decisao;

/** Ordens de compra (leitura distribuída) e "Receber entrega" (2PC). */
@RestController
@RequestMapping("/api/ordens")
public class ControladorOrdens {

    private final RepositorioOrdens ordens;
    private final ServicoOrdens servico;

    public ControladorOrdens(RepositorioOrdens ordens, ServicoOrdens servico) {
        this.ordens = ordens;
        this.servico = servico;
    }

    @GetMapping
    public List<OrdemCompra> listar(@RequestParam(defaultValue = "50") int limite) {
        return ordens.listar(Math.max(1, Math.min(limite, 200)));
    }

    @GetMapping("/{id}")
    public OrdemCompra obter(@PathVariable String id) {
        return ordens.procurar(id).orElseThrow(() -> new OrdemInexistenteException(id));
    }

    @PostMapping("/{id}/entrega")
    public Map<String, Object> receberEntrega(@PathVariable String id) {
        Decisao decisao = servico.receberEntrega(id);
        return Map.of("ordemId", id, "decisao2pc", decisao, "ordem", obter(id));
    }
}

package mz.ac.isutc.scad.no.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import mz.ac.isutc.scad.no.bd.FragmentosBd;
import mz.ac.isutc.scad.no.stock.Movimento;
import mz.ac.isutc.scad.no.stock.RepositorioMovimentos;
import mz.ac.isutc.scad.no.stock.RepositorioStock;
import mz.ac.isutc.scad.no.stock.StockDetalhado;

/**
 * Vistas globais (todos os armazéns), lidas directamente dos fragmentos de cada região.
 * Funcionam mesmo que o nó de uma região esteja em baixo, desde que o seu fragmento
 * (primário ou réplica promovida) esteja acessível.
 */
@RestController
@RequestMapping("/api/global")
public class ControladorGlobal {

    private final FragmentosBd fragmentos;
    private final RepositorioStock stock;
    private final RepositorioMovimentos movimentos;

    public ControladorGlobal(FragmentosBd fragmentos, RepositorioStock stock, RepositorioMovimentos movimentos) {
        this.fragmentos = fragmentos;
        this.stock = stock;
        this.movimentos = movimentos;
    }

    @GetMapping("/stock")
    public StockGlobal stock() {
        List<StockDetalhado> todos = new ArrayList<>();
        List<String> indisponiveis = new ArrayList<>();
        for (String regiao : fragmentos.regioes()) {
            try {
                todos.addAll(stock.listarDetalhado(regiao));
            } catch (DataAccessException e) {
                indisponiveis.add(regiao);
            }
        }
        return new StockGlobal(todos, indisponiveis);
    }

    @GetMapping("/movimentos")
    public List<Movimento> movimentos(@RequestParam(defaultValue = "40") int limite) {
        int n = Math.max(1, Math.min(limite, 300));
        List<Movimento> todos = new ArrayList<>();
        for (String regiao : fragmentos.regioes()) {
            try {
                todos.addAll(movimentos.recentes(regiao, n));
            } catch (DataAccessException e) {
                // fragmento inacessível
            }
        }
        todos.sort(Comparator.comparing(Movimento::tsCorrigido).reversed());
        return todos.size() > n ? todos.subList(0, n) : todos;
    }

    public record StockGlobal(List<StockDetalhado> stock, List<String> fragmentosIndisponiveis) {
    }
}

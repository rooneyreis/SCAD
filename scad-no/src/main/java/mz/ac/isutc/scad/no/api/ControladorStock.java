package mz.ac.isutc.scad.no.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import mz.ac.isutc.scad.no.movimentos.PedidoSaida;
import mz.ac.isutc.scad.no.movimentos.ResultadoSaida;
import mz.ac.isutc.scad.no.movimentos.ServicoMovimentos;
import mz.ac.isutc.scad.no.stock.Alerta;
import mz.ac.isutc.scad.no.stock.Movimento;
import mz.ac.isutc.scad.no.stock.RepositorioAlertas;
import mz.ac.isutc.scad.no.stock.RepositorioMovimentos;
import mz.ac.isutc.scad.no.stock.RepositorioStock;
import mz.ac.isutc.scad.no.stock.StockDetalhado;

/** API REST do armazém deste nó: stock, movimentos e alertas. */
@RestController
@RequestMapping("/api")
public class ControladorStock {

    private static final int LIMITE_MAXIMO = 500;

    private final RepositorioStock stock;
    private final RepositorioMovimentos movimentos;
    private final RepositorioAlertas alertas;
    private final ServicoMovimentos servicoMovimentos;

    public ControladorStock(RepositorioStock stock, RepositorioMovimentos movimentos,
                            RepositorioAlertas alertas, ServicoMovimentos servicoMovimentos) {
        this.stock = stock;
        this.movimentos = movimentos;
        this.alertas = alertas;
        this.servicoMovimentos = servicoMovimentos;
    }

    @GetMapping("/stock")
    public List<StockDetalhado> stock() {
        return stock.listarDetalhado();
    }

    @PostMapping("/movimentos/saida")
    @ResponseStatus(HttpStatus.CREATED)
    public ResultadoSaida registarSaida(@Valid @RequestBody PedidoSaida pedido) {
        return servicoMovimentos.registarSaida(pedido);
    }

    @GetMapping("/movimentos")
    public List<Movimento> movimentos(@RequestParam(defaultValue = "50") int limite) {
        return movimentos.recentes(limitar(limite));
    }

    @GetMapping("/alertas")
    public List<Alerta> alertas(@RequestParam(defaultValue = "50") int limite) {
        return alertas.recentes(limitar(limite));
    }

    private static int limitar(int limite) {
        return Math.max(1, Math.min(limite, LIMITE_MAXIMO));
    }
}

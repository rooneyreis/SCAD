package mz.ac.isutc.scad.no.api;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import mz.ac.isutc.scad.no.bd.FragmentosBd;
import mz.ac.isutc.scad.no.failover.MonitorFailover;
import mz.ac.isutc.scad.no.fornecedores.GatewayFornecedores;
import mz.ac.isutc.scad.no.reposicao.ServicoReposicao;
import mz.ac.isutc.scad.no.transaccoes.Coordenador2PC;
import mz.ac.isutc.scad.no.transaccoes.Cronologia2PC;

/** Dados do "Monitor distribuído" do painel: 2PC, fragmentos/failover e reposições deste nó. */
@RestController
@RequestMapping("/api")
public class ControladorMonitor {

    private final Coordenador2PC coordenador;
    private final FragmentosBd fragmentos;
    private final MonitorFailover failover;
    private final ServicoReposicao reposicao;
    private final GatewayFornecedores gateway;

    public ControladorMonitor(Coordenador2PC coordenador, FragmentosBd fragmentos, MonitorFailover failover,
                              ServicoReposicao reposicao, GatewayFornecedores gateway) {
        this.coordenador = coordenador;
        this.fragmentos = fragmentos;
        this.failover = failover;
        this.reposicao = reposicao;
        this.gateway = gateway;
    }

    /** Tentativas de envio aos fornecedores feitas por este nó. */
    @GetMapping("/fornecedores/eventos")
    public List<String> eventosFornecedores() {
        return gateway.eventos();
    }

    /** Cronologias dos 2PC coordenados por este nó (mais recentes primeiro). */
    @GetMapping("/2pc")
    public List<Cronologia2PC.Vista> transaccoes() {
        return coordenador.cronologias();
    }

    /** Qual servidor (primário/réplica) cada fragmento está a usar, e eventos de failover. */
    @GetMapping("/fragmentos")
    public Map<String, Object> fragmentos() {
        return Map.of("fragmentos", fragmentos.estado(), "eventos", failover.eventos());
    }

    @GetMapping("/reposicao/eventos")
    public List<String> eventosReposicao() {
        return reposicao.eventos();
    }

    /** Demonstração: pausa entre a decisão e a fase 2 do 2PC (para parar o coordenador a meio). */
    @PostMapping("/demo/2pc-pausa")
    public Map<String, Object> pausa2pc(@RequestParam long ms) {
        coordenador.definirPausaAntesFase2(ms);
        return Map.of("pausaAntesFase2Ms", coordenador.pausaAntesFase2Ms());
    }
}

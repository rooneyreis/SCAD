package mz.ac.isutc.scad.no.transaccoes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cronologia de uma transacção 2PC, para o painel: participantes, votos, decisão e conclusão.
 * Os métodos são sincronizados porque os votos chegam em paralelo.
 */
public final class Cronologia2PC {

    public enum Voto { SIM, NAO, SEM_RESPOSTA }

    public enum Decisao { COMMIT, ABORT }

    private final String transacaoId;
    private final String tipo;
    private final String coordenador;
    private final List<String> participantes;
    private final Instant inicio;
    private final Map<String, Voto> votos = new LinkedHashMap<>();
    private final Map<String, String> motivos = new LinkedHashMap<>();
    private final Map<String, Boolean> concluidos = new LinkedHashMap<>();
    private final List<String> eventos = new ArrayList<>();
    private Decisao decisao;
    private Instant fim;

    public Cronologia2PC(String transacaoId, String tipo, String coordenador, List<String> participantes, Instant inicio) {
        this.transacaoId = transacaoId;
        this.tipo = tipo;
        this.coordenador = coordenador;
        this.participantes = List.copyOf(participantes);
        this.inicio = inicio;
    }

    public synchronized void evento(Instant hora, String texto) {
        eventos.add(hora + "  " + texto);
    }

    public synchronized void voto(String participante, Voto voto, String motivo) {
        votos.put(participante, voto);
        if (motivo != null) {
            motivos.put(participante, motivo);
        }
    }

    public synchronized void decisao(Decisao d) {
        this.decisao = d;
    }

    public synchronized void concluido(String participante, boolean ok) {
        concluidos.put(participante, ok);
    }

    public synchronized void fim(Instant hora) {
        this.fim = hora;
    }

    public synchronized Vista vista() {
        return new Vista(transacaoId, tipo, coordenador, participantes, inicio, fim,
                new LinkedHashMap<>(votos), new LinkedHashMap<>(motivos), decisao,
                new LinkedHashMap<>(concluidos), new ArrayList<>(eventos));
    }

    public String transacaoId() {
        return transacaoId;
    }

    /** Fotografia imutável, serializada para JSON. */
    public record Vista(String transacaoId, String tipo, String coordenador, List<String> participantes,
                        Instant inicio, Instant fim, Map<String, Voto> votos, Map<String, String> motivosNao,
                        Decisao decisao, Map<String, Boolean> fase2Concluida, List<String> eventos) {
    }
}

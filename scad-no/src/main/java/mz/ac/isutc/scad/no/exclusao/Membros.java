package mz.ac.isutc.scad.no.exclusao;

import java.util.Set;
import java.util.function.Consumer;

/** Participantes vivos do grupo (fornecido pelo detector de falhas). */
public interface Membros {

    /** Nós vivos, sem incluir este. */
    Set<String> vivos();

    /** Regista uma acção a executar quando um nó é dado como morto. */
    void aoMorrer(Consumer<String> accao);
}

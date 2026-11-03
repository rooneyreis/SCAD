package mz.ac.isutc.scad.no.transaccoes;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Trabalho que um participante do 2PC executa no seu fragmento, dentro de uma transacção
 * (a ligação já vem com autoCommit desligado). Lançar uma excepção = votar NÃO.
 */
@FunctionalInterface
public interface TrabalhoParticipante {

    void executar(Connection ligacao) throws SQLException;
}

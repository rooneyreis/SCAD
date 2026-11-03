package mz.ac.isutc.scad.no.agente;

import mz.ac.isutc.scad.corba.AlertaStock;

/** Destino dos alertas de stock (em produção: a Central de Auditoria via CORBA). */
public interface CanalAlertas {

    /** @return true se o alerta foi entregue; false se o destino está indisponível */
    boolean enviarAlerta(AlertaStock alerta);
}

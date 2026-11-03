package mz.ac.isutc.scad.central.corba;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import mz.ac.isutc.scad.common.corba.ServicoNomes;
import mz.ac.isutc.scad.common.corba.ServidorCorba;

/**
 * Publica a Central no Naming Service e repete a publicação periodicamente,
 * para sobreviver a um reinício do Naming Service.
 */
@Component
public class PublicacaoCentral {

    private static final Logger log = LoggerFactory.getLogger(PublicacaoCentral.class);

    private final ServidorCorba servidor;
    private final org.omg.CORBA.Object referencia;
    private volatile boolean publicada;

    public PublicacaoCentral(ServidorCorba servidor, CentralAuditoriaImpl central) {
        this.servidor = servidor;
        this.referencia = servidor.activar(central);
    }

    @Scheduled(initialDelay = 0, fixedDelay = 10_000)
    public void publicar() {
        try {
            servidor.nomes().registar(ServicoNomes.CENTRAL_AUDITORIA, referencia);
            if (!publicada) {
                log.info("Central de Auditoria publicada no Naming Service como '{}'", ServicoNomes.CENTRAL_AUDITORIA);
            }
            publicada = true;
        } catch (RuntimeException e) {
            if (publicada) {
                log.warn("Naming Service indisponível: {}", e.toString());
            }
            publicada = false;
        }
    }
}

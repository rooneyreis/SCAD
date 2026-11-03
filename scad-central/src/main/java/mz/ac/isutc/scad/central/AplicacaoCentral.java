package mz.ac.isutc.scad.central;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Central de Auditoria: recebe alertas dos agentes de stock e audita-os via CORBA. */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class AplicacaoCentral {

    public static void main(String[] args) {
        SpringApplication.run(AplicacaoCentral.class, args);
    }
}

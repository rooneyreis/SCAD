package mz.ac.isutc.scad.central.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scad")
public record PropriedadesCentral(Corba corba) {

    public record Corba(String hostAnunciado, int porta, String naming) {
    }
}

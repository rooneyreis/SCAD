package mz.ac.isutc.scad.central.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import mz.ac.isutc.scad.common.corba.ConfiguracaoOrb;
import mz.ac.isutc.scad.common.corba.ServidorCorba;
import mz.ac.isutc.scad.common.relogio.RelogioCorrigido;

@Configuration
public class ConfiguracaoCorba {

    @Bean(destroyMethod = "parar")
    ServidorCorba servidorCorba(PropriedadesCentral props) {
        PropriedadesCentral.Corba corba = props.corba();
        return new ServidorCorba(new ConfiguracaoOrb(
                "central", corba.hostAnunciado(), corba.porta(), corba.naming()));
    }

    /** Relógio do mestre Berkeley (também com desvio simulado configurável). */
    @Bean
    RelogioCorrigido relogioCorrigido(@org.springframework.beans.factory.annotation.Value("${scad.relogio.desvio-simulado-ms:0}") long desvio) {
        return new RelogioCorrigido(desvio);
    }
}

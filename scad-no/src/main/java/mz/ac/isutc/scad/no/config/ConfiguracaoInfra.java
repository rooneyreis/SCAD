package mz.ac.isutc.scad.no.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import mz.ac.isutc.scad.common.corba.ConfiguracaoOrb;
import mz.ac.isutc.scad.common.corba.ServidorCorba;
import mz.ac.isutc.scad.common.dfs.ClienteHdfs;

/** Beans de infra-estrutura distribuída: ORB e cliente HDFS. */
@Configuration
public class ConfiguracaoInfra {

    @Bean(destroyMethod = "parar")
    ServidorCorba servidorCorba(PropriedadesNo props) {
        PropriedadesNo.Corba corba = props.corba();
        return new ServidorCorba(new ConfiguracaoOrb(
                "no-" + props.regiao(), corba.hostAnunciado(), corba.porta(), corba.naming()));
    }

    @Bean(destroyMethod = "close")
    ClienteHdfs clienteHdfs(PropriedadesNo props) {
        return new ClienteHdfs(props.hdfs().uri(), props.hdfs().utilizador(), props.hdfs().replicacao());
    }
}

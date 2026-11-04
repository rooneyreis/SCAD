package mz.ac.isutc.scad.no.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import mz.ac.isutc.scad.common.corba.ConfiguracaoOrb;
import mz.ac.isutc.scad.common.corba.ServidorCorba;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;

import mz.ac.isutc.scad.common.dfs.ClienteHdfs;
import mz.ac.isutc.scad.common.dfs.FilaHdfs;

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

    /** Fila local (volume Docker) para os ficheiros à espera de ir para o HDFS. */
    @Bean
    FilaHdfs filaHdfs(ClienteHdfs hdfs, @Value("${scad.hdfs.fila:/dados/fila-hdfs}") String directorio) {
        return new FilaHdfs(Path.of(directorio), hdfs::escrever);
    }
}

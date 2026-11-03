package mz.ac.isutc.scad.no.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do nó, vinda de variáveis de ambiente (ver application.yml).
 * O mesmo código corre em modo local e distribuído: só estes valores mudam.
 */
@ConfigurationProperties(prefix = "scad")
public record PropriedadesNo(String regiao, Corba corba, Hdfs hdfs) {

    /**
     * @param hostAnunciado IP da máquina Windows (distribuído) ou nome do serviço Docker (local)
     * @param naming        "host:porta" do Naming Service
     */
    public record Corba(String hostAnunciado, int porta, String naming) {
    }

    public record Hdfs(String uri, String utilizador) {
    }
}

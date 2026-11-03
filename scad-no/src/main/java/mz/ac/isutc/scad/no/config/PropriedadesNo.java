package mz.ac.isutc.scad.no.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do nó, vinda de variáveis de ambiente (ver application.yml).
 * O mesmo código corre em modo local e distribuído: só estes valores mudam.
 *
 * @param regiao     região deste nó (NORTE, CENTRO ou SUL) = armazém = fragmento local
 * @param fragmentos endereços ("host:porta") do primário e da réplica de cada fragmento
 */
@ConfigurationProperties(prefix = "scad")
public record PropriedadesNo(String regiao, Corba corba, Hdfs hdfs, Bd bd,
                             Map<String, Fragmento> fragmentos, Relogio relogio) {

    /**
     * @param hostAnunciado IP da máquina Windows (distribuído) ou nome do serviço Docker (local)
     * @param naming        "host:porta" do Naming Service
     */
    public record Corba(String hostAnunciado, int porta, String naming) {
    }

    /** @param replicacao factor de replicação dos ficheiros (3 com 3 DataNodes) */
    public record Hdfs(String uri, String utilizador, int replicacao) {
    }

    public record Bd(String nome, String utilizador, String password) {
    }

    public record Fragmento(String primario, String replica) {
    }

    /** @param desvioSimuladoMs desvio artificial do relógio deste nó (CLOCK_SKEW_MS) */
    public record Relogio(long desvioSimuladoMs) {
    }
}

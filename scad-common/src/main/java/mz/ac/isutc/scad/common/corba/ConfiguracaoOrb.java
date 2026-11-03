package mz.ac.isutc.scad.common.corba;

/**
 * Parâmetros de rede de um ORB.
 *
 * @param nomeImplementacao identificador do processo (aparece nos logs do JacORB)
 * @param hostAnunciado     endereço que vai dentro do IOR (IP da máquina Windows ou nome do serviço)
 * @param porta             porta fixa onde o ORB escuta e que é publicada pelo Docker
 * @param enderecoNaming    "host:porta" do Naming Service (pode ser nulo para o próprio Naming)
 */
public record ConfiguracaoOrb(String nomeImplementacao, String hostAnunciado, int porta, String enderecoNaming) {

    public ConfiguracaoOrb {
        if (hostAnunciado == null || hostAnunciado.isBlank()) {
            throw new IllegalArgumentException("hostAnunciado é obrigatório (nunca usar localhost)");
        }
        if (porta <= 0) {
            throw new IllegalArgumentException("porta CORBA inválida: " + porta);
        }
    }
}

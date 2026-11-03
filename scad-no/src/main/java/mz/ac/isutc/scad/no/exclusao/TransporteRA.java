package mz.ac.isutc.scad.no.exclusao;

/** Envio de mensagens do Ricart-Agrawala para outro nó (HTTP em produção, memória nos testes). */
public interface TransporteRA {

    /**
     * Envia um PEDIDO de acesso.
     *
     * @return true se o destino respondeu logo OK; false se adiou a resposta (o OK chega mais tarde)
     * @throws RuntimeException se o destino não estiver acessível
     */
    boolean enviarPedido(String destino, String recurso, long tsLamport, String origem);

    /** Envia um OK (adiado) ao destino. */
    void enviarOk(String destino, String recurso, long tsLamport, String origem);
}

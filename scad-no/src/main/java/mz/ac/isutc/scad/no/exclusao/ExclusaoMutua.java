package mz.ac.isutc.scad.no.exclusao;

import java.util.function.Supplier;

/** Exclusão mútua distribuída por recurso (ex.: "GRAO-DOURADO/ARROZ-25"). */
public interface ExclusaoMutua {

    /**
     * Executa a secção crítica com acesso exclusivo ao recurso em todo o sistema.
     *
     * @throws ExclusaoIndisponivelException se não for possível obter o acesso
     */
    <T> T executar(String recurso, Supplier<T> seccaoCritica);

    class ExclusaoIndisponivelException extends RuntimeException {
        public ExclusaoIndisponivelException(String mensagem) {
            super(mensagem);
        }
    }
}

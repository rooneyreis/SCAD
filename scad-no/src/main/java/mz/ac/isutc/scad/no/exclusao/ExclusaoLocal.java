package mz.ac.isutc.scad.no.exclusao;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

/** Exclusão mútua apenas dentro deste processo (provisória até ao Ricart-Agrawala). */
@Component
public class ExclusaoLocal implements ExclusaoMutua {

    private final Map<String, ReentrantLock> trincos = new ConcurrentHashMap<>();

    @Override
    public <T> T executar(String recurso, Supplier<T> seccaoCritica) {
        ReentrantLock trinco = trincos.computeIfAbsent(recurso, r -> new ReentrantLock(true));
        trinco.lock();
        try {
            return seccaoCritica.get();
        } finally {
            trinco.unlock();
        }
    }
}

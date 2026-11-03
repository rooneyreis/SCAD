package mz.ac.isutc.scad.no.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;

import mz.ac.isutc.scad.common.relogio.RelogioCorrigido;
import mz.ac.isutc.scad.common.relogio.RelogioLamport;
import mz.ac.isutc.scad.no.bd.FragmentosBd;

/** Relógios do nó: lógico (Lamport) e físico corrigido (Berkeley, com desvio simulado). */
@Configuration
public class ConfiguracaoRelogios {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracaoRelogios.class);

    @Bean
    RelogioCorrigido relogioCorrigido(PropriedadesNo props) {
        long desvio = props.relogio().desvioSimuladoMs();
        log.info("Relógio corrigido com desvio simulado de {} ms", desvio);
        return new RelogioCorrigido(desvio);
    }

    /**
     * Ao reiniciar, o relógio de Lamport continua a partir do maior carimbo já gravado no fragmento
     * (os ids das ordens usam o carimbo, por isso nunca se podem repetir).
     */
    @Bean
    RelogioLamport relogioLamport(FragmentosBd fragmentos) {
        RelogioLamport relogio = new RelogioLamport();
        try {
            Long maior = fragmentos.jdbcLocal().queryForObject("""
                    SELECT greatest((SELECT max(ts_lamport) FROM movimento_stock),
                                    (SELECT max(ts_lamport) FROM alerta),
                                    (SELECT max(ts_lamport) FROM ordem_compra WHERE coordenador = ?),
                                    (SELECT max(ts_lamport) FROM log_2pc))
                    """, Long.class, fragmentos.regiaoLocal());
            relogio.garantirMinimo(maior == null ? 0 : maior);
            log.info("Relógio de Lamport retomado em {}", relogio.actual());
        } catch (DataAccessException e) {
            log.warn("Fragmento local indisponível no arranque; Lamport começa em 0: {}", e.getMessage());
        }
        return relogio;
    }
}

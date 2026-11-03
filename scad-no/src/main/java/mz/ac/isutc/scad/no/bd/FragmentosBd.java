package mz.ac.isutc.scad.no.bd;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import mz.ac.isutc.scad.no.config.PropriedadesNo;

/**
 * Acesso aos fragmentos da base de dados distribuída.
 *
 * <p>Cada região (NORTE, CENTRO, SUL) é um fragmento num PostgreSQL diferente. O nó tem um
 * pool de ligações por fragmento: o seu (escrita local de movimentos) e os dos outros
 * (necessários para o 2PC das ordens consolidadas, Fase 3).
 */
@Component
public class FragmentosBd implements DisposableBean {

    private final String regiaoLocal;
    private final Map<String, Fragmento> fragmentos = new LinkedHashMap<>();

    public FragmentosBd(PropriedadesNo props) {
        this.regiaoLocal = props.regiao();
        props.fragmentos().forEach((regiao, cfg) -> {
            // Um fragmento sem endereço não existe nesta instalação (ex.: teste com 2 máquinas)
            if (cfg != null && cfg.primario() != null && !cfg.primario().isBlank()) {
                fragmentos.put(regiao, new Fragmento(criarPool(regiao, cfg.primario(), props.bd())));
            }
        });
        if (!fragmentos.containsKey(regiaoLocal)) {
            throw new IllegalStateException("Fragmento local '" + regiaoLocal + "' não está configurado");
        }
    }

    public String regiaoLocal() {
        return regiaoLocal;
    }

    public Set<String> regioes() {
        return fragmentos.keySet();
    }

    public JdbcTemplate jdbc(String regiao) {
        return fragmento(regiao).jdbc;
    }

    public TransactionTemplate transaccao(String regiao) {
        return fragmento(regiao).transaccao;
    }

    public JdbcTemplate jdbcLocal() {
        return jdbc(regiaoLocal);
    }

    public TransactionTemplate transaccaoLocal() {
        return transaccao(regiaoLocal);
    }

    private Fragmento fragmento(String regiao) {
        Fragmento f = fragmentos.get(regiao);
        if (f == null) {
            throw new IllegalArgumentException("Fragmento desconhecido: " + regiao);
        }
        return f;
    }

    private static HikariDataSource criarPool(String regiao, String endereco, PropriedadesNo.Bd bd) {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("pg-" + regiao.toLowerCase());
        cfg.setJdbcUrl("jdbc:postgresql://" + endereco + "/" + bd.nome());
        cfg.setUsername(bd.utilizador());
        cfg.setPassword(bd.password());
        cfg.setMaximumPoolSize(5);
        cfg.setMinimumIdle(1);
        cfg.setConnectionTimeout(3_000);
        // O nó arranca mesmo que o fragmento esteja em baixo (tolerância a falhas)
        cfg.setInitializationFailTimeout(-1);
        return new HikariDataSource(cfg);
    }

    @Override
    public void destroy() {
        fragmentos.values().forEach(f -> f.pool.close());
    }

    private static final class Fragmento {
        final HikariDataSource pool;
        final JdbcTemplate jdbc;
        final TransactionTemplate transaccao;

        Fragmento(HikariDataSource pool) {
            this.pool = pool;
            this.jdbc = new JdbcTemplate(pool);
            this.transaccao = new TransactionTemplate(new DataSourceTransactionManager(pool));
        }
    }
}

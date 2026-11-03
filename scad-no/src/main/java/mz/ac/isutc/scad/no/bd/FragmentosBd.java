package mz.ac.isutc.scad.no.bd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

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
 * <p>Cada região (NORTE, CENTRO, SUL) é um fragmento com um PostgreSQL primário e uma réplica
 * (streaming replication). O nó tem um pool de ligações para cada um. Todas as operações usam
 * a ligação <b>activa</b> do fragmento: o primário, ou a réplica depois de um failover
 * (ver {@link mz.ac.isutc.scad.no.failover.MonitorFailover}).
 */
@Component
public class FragmentosBd implements DisposableBean {

    private final String regiaoLocal;
    private final Map<String, Fragmento> fragmentos = new LinkedHashMap<>();

    public FragmentosBd(PropriedadesNo props) {
        this.regiaoLocal = props.regiao();
        props.fragmentos().forEach((regiao, cfg) -> {
            // Um fragmento sem endereço não existe nesta instalação
            if (cfg != null && temValor(cfg.primario())) {
                Ligacao primario = new Ligacao(cfg.primario(), criarPool(regiao + "-primario", cfg.primario(), props.bd()));
                Ligacao replica = temValor(cfg.replica())
                        ? new Ligacao(cfg.replica(), criarPool(regiao + "-replica", cfg.replica(), props.bd()))
                        : null;
                fragmentos.put(regiao, new Fragmento(regiao, primario, replica));
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
        return fragmento(regiao).activo.jdbc;
    }

    public TransactionTemplate transaccao(String regiao) {
        return fragmento(regiao).activo.transaccao;
    }

    /** DataSource activa do fragmento (usada pelo 2PC, que precisa de ligações concretas). */
    public DataSource dataSource(String regiao) {
        return fragmento(regiao).activo.pool;
    }

    public JdbcTemplate jdbcLocal() {
        return jdbc(regiaoLocal);
    }

    public TransactionTemplate transaccaoLocal() {
        return transaccao(regiaoLocal);
    }

    public Fragmento fragmento(String regiao) {
        Fragmento f = fragmentos.get(regiao);
        if (f == null) {
            throw new IllegalArgumentException("Fragmento desconhecido: " + regiao);
        }
        return f;
    }

    public List<EstadoFragmento> estado() {
        List<EstadoFragmento> lista = new ArrayList<>();
        fragmentos.values().forEach(f -> lista.add(new EstadoFragmento(f.regiao, f.primario.endereco,
                f.replica == null ? null : f.replica.endereco, f.emFailover() ? "REPLICA" : "PRIMARIO",
                f.activo.endereco)));
        return lista;
    }

    private static boolean temValor(String s) {
        return s != null && !s.isBlank();
    }

    private static HikariDataSource criarPool(String nome, String endereco, PropriedadesNo.Bd bd) {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("pg-" + nome.toLowerCase());
        cfg.setJdbcUrl("jdbc:postgresql://" + endereco + "/" + bd.nome() + "?connectTimeout=2&socketTimeout=15");
        cfg.setUsername(bd.utilizador());
        cfg.setPassword(bd.password());
        cfg.setMaximumPoolSize(6);
        cfg.setMinimumIdle(0);
        cfg.setConnectionTimeout(2_500);
        cfg.setValidationTimeout(1_000);
        // O nó arranca mesmo que o fragmento esteja em baixo (tolerância a falhas)
        cfg.setInitializationFailTimeout(-1);
        return new HikariDataSource(cfg);
    }

    @Override
    public void destroy() {
        fragmentos.values().forEach(f -> {
            f.primario.pool.close();
            if (f.replica != null) {
                f.replica.pool.close();
            }
        });
    }

    /** Uma ligação (pool) a um servidor PostgreSQL concreto. */
    public static final class Ligacao {
        final String endereco;
        final HikariDataSource pool;
        final JdbcTemplate jdbc;
        final TransactionTemplate transaccao;

        Ligacao(String endereco, HikariDataSource pool) {
            this.endereco = endereco;
            this.pool = pool;
            this.jdbc = new JdbcTemplate(pool);
            this.jdbc.setQueryTimeout(10);
            this.transaccao = new TransactionTemplate(new DataSourceTransactionManager(pool));
        }

        public String endereco() {
            return endereco;
        }

        public JdbcTemplate jdbc() {
            return jdbc;
        }
    }

    /** Primário + réplica de uma região, e qual dos dois está activo. */
    public static final class Fragmento {
        private final String regiao;
        private final Ligacao primario;
        private final Ligacao replica;
        private volatile Ligacao activo;

        Fragmento(String regiao, Ligacao primario, Ligacao replica) {
            this.regiao = regiao;
            this.primario = primario;
            this.replica = replica;
            this.activo = primario;
        }

        public String regiao() {
            return regiao;
        }

        public Ligacao primario() {
            return primario;
        }

        public Ligacao replica() {
            return replica;
        }

        public boolean emFailover() {
            return activo == replica;
        }

        /** Passa a usar a réplica (já promovida a primário). Não há regresso automático. */
        public void usarReplica() {
            if (replica == null) {
                throw new IllegalStateException("O fragmento " + regiao + " não tem réplica");
            }
            activo = replica;
        }
    }

    public record EstadoFragmento(String regiao, String primario, String replica, String emUso, String enderecoActivo) {
    }
}

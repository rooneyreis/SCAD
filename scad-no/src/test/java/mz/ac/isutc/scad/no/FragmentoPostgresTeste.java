package mz.ac.isutc.scad.no;

import java.nio.file.Path;
import java.util.Map;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import mz.ac.isutc.scad.no.bd.FragmentosBd;
import mz.ac.isutc.scad.no.config.PropriedadesNo;

/**
 * Fragmento PostgreSQL real para testes de integração, inicializado com os MESMOS scripts
 * usados em produção (db/init + db/dados).
 */
public final class FragmentoPostgresTeste {

    private static final Path RAIZ_BD = Path.of("..", "db").toAbsolutePath().normalize();

    private FragmentoPostgresTeste() {
    }

    public static PostgreSQLContainer<?> criar(String regiao) {
        return new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("scad")
                .withUsername("scad")
                .withPassword("scad")
                .withEnv("SCAD_REGIAO", regiao)
                .withEnv("REPLICACAO_PASSWORD", "replica")
                .withCommand("postgres", "-c", "max_prepared_transactions=50")
                .withCopyFileToContainer(MountableFile.forHostPath(RAIZ_BD.resolve("init")), "/docker-entrypoint-initdb.d/")
                .withCopyFileToContainer(MountableFile.forHostPath(RAIZ_BD.resolve("dados")), "/scad-dados/");
    }

    public static FragmentosBd fragmentos(String regiao, PostgreSQLContainer<?> pg) {
        String endereco = pg.getHost() + ":" + pg.getMappedPort(5432);
        PropriedadesNo props = new PropriedadesNo(regiao, null, null,
                new PropriedadesNo.Bd("scad", "scad", "scad"),
                Map.of(regiao, new PropriedadesNo.Fragmento(endereco, null)),
                new PropriedadesNo.Relogio(0));
        return new FragmentosBd(props);
    }
}

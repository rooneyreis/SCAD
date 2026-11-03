package mz.ac.isutc.scad.common.corba;

import java.util.Properties;

import org.omg.CORBA.ORB;

import mz.ac.isutc.scad.common.corba.rmi.CompatibilidadeJava17;

/**
 * Cria ORBs JacORB configurados para correr dentro de containers Docker.
 *
 * <p>Ponto crítico em Docker Desktop: o ORB escuta numa porta fixa dentro do container
 * ({@code OAPort}), mas o IOR publicado tem de conter o endereço pelo qual os <b>outros</b>
 * processos chegam até nós (IP da máquina Windows em modo distribuído, nome do serviço
 * em modo local). Para isso usamos {@code jacorb.ior_proxy_host/port}.
 */
public final class FabricaOrb {

    static {
        // O JDK 17 já não traz ORB; garante que qualquer ORB.init() (incluindo o singleton
        // usado internamente pelos Helpers gerados) usa o JacORB.
        System.setProperty("org.omg.CORBA.ORBClass", "org.jacorb.orb.ORB");
        System.setProperty("org.omg.CORBA.ORBSingletonClass", "org.jacorb.orb.ORBSingleton");
        CompatibilidadeJava17.activar();
    }

    private FabricaOrb() {
    }

    public static ORB criar(ConfiguracaoOrb cfg) {
        Properties p = new Properties();
        p.setProperty("org.omg.CORBA.ORBClass", "org.jacorb.orb.ORB");
        p.setProperty("org.omg.CORBA.ORBSingletonClass", "org.jacorb.orb.ORBSingleton");

        // Porta fixa (publicada no docker-compose) e endereço anunciado no IOR
        p.setProperty("OAPort", String.valueOf(cfg.porta()));
        p.setProperty("jacorb.ior_proxy_host", cfg.hostAnunciado());
        p.setProperty("jacorb.ior_proxy_port", String.valueOf(cfg.porta()));

        if (cfg.enderecoNaming() != null && !cfg.enderecoNaming().isBlank()) {
            p.setProperty("ORBInitRef.NameService", "corbaloc::" + cfg.enderecoNaming() + "/NameService");
        }

        // Nunca ficar pendurado à espera de um processo morto
        p.setProperty("jacorb.connection.client.connect_timeout", "3000");
        p.setProperty("jacorb.connection.client.pending_reply_timeout", "5000");
        p.setProperty("jacorb.retries", "1");
        p.setProperty("jacorb.retry_interval", "500");

        p.setProperty("jacorb.implname", cfg.nomeImplementacao());
        return ORB.init(new String[0], p);
    }
}

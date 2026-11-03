package mz.ac.isutc.scad.common.corba;

import org.omg.CORBA.ORB;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;
import org.omg.PortableServer.Servant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ciclo de vida de um ORB servidor: cria o ORB, activa o RootPOA e corre o ciclo de
 * atendimento de pedidos numa thread própria.
 */
public final class ServidorCorba {

    private static final Logger log = LoggerFactory.getLogger(ServidorCorba.class);

    private final ORB orb;
    private final POA poa;
    private final ServicoNomes servicoNomes;
    private final Thread threadOrb;

    public ServidorCorba(ConfiguracaoOrb cfg) {
        this.orb = FabricaOrb.criar(cfg);
        try {
            this.poa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
            poa.the_POAManager().activate();
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível activar o RootPOA", e);
        }
        this.servicoNomes = new ServicoNomes(orb);

        // Thread não-daemon: mantém o processo vivo enquanto o ORB estiver activo
        this.threadOrb = new Thread(orb::run, "orb-" + cfg.nomeImplementacao());
        threadOrb.start();
        log.info("ORB '{}' a escutar na porta {} (anunciado como {}:{})",
                cfg.nomeImplementacao(), cfg.porta(), cfg.hostAnunciado(), cfg.porta());
    }

    /** Activa um servant no RootPOA e devolve a referência (IOR) correspondente. */
    public org.omg.CORBA.Object activar(Servant servant) {
        try {
            return poa.servant_to_reference(servant);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao activar o servant " + servant.getClass().getSimpleName(), e);
        }
    }

    public ORB orb() {
        return orb;
    }

    public ServicoNomes nomes() {
        return servicoNomes;
    }

    public void parar() {
        log.info("A parar o ORB...");
        orb.shutdown(false);
    }
}

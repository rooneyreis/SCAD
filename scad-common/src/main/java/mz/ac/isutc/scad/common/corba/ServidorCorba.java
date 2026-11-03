package mz.ac.isutc.scad.common.corba;

import java.nio.charset.StandardCharsets;

import org.omg.CORBA.ORB;
import org.omg.CORBA.Policy;
import org.omg.PortableServer.IdAssignmentPolicyValue;
import org.omg.PortableServer.LifespanPolicyValue;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;
import org.omg.PortableServer.Servant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ciclo de vida de um ORB servidor: cria o ORB, o POA dos objectos SCAD e corre o ciclo
 * de atendimento de pedidos numa thread própria.
 *
 * <p>Os objectos são activados num POA <b>persistente</b> (LifespanPolicy PERSISTENT +
 * IdAssignmentPolicy USER_ID). Como a porta e o {@code jacorb.implname} também são fixos,
 * o IOR de um objecto é sempre o mesmo, mesmo depois de o processo reiniciar: quem guardou
 * a referência continua a poder usá-la. Com um POA transitório, uma referência antiga
 * apontaria para um objecto que já não existe, e numa chamada {@code oneway} essa falha
 * seria silenciosa.
 */
public final class ServidorCorba {

    private static final Logger log = LoggerFactory.getLogger(ServidorCorba.class);
    private static final String NOME_POA = "SCAD";

    private final ORB orb;
    private final POA poa;
    private final ServicoNomes servicoNomes;

    public ServidorCorba(ConfiguracaoOrb cfg) {
        this.orb = FabricaOrb.criar(cfg);
        try {
            POA raiz = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
            Policy[] politicas = {
                    raiz.create_lifespan_policy(LifespanPolicyValue.PERSISTENT),
                    raiz.create_id_assignment_policy(IdAssignmentPolicyValue.USER_ID)
            };
            this.poa = raiz.create_POA(NOME_POA, raiz.the_POAManager(), politicas);
            raiz.the_POAManager().activate();
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível criar o POA persistente", e);
        }
        this.servicoNomes = new ServicoNomes(orb);

        // Thread não-daemon: mantém o processo vivo enquanto o ORB estiver activo
        Thread threadOrb = new Thread(orb::run, "orb-" + cfg.nomeImplementacao());
        threadOrb.start();
        log.info("ORB '{}' a escutar na porta {} (anunciado como {}:{})",
                cfg.nomeImplementacao(), cfg.porta(), cfg.hostAnunciado(), cfg.porta());
    }

    /**
     * Activa um servant com um identificador fixo e devolve a sua referência (IOR),
     * que se mantém igual entre reinícios do processo.
     */
    public org.omg.CORBA.Object activar(String idObjecto, Servant servant) {
        byte[] id = idObjecto.getBytes(StandardCharsets.UTF_8);
        try {
            poa.activate_object_with_id(id, servant);
            return poa.id_to_reference(id);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao activar o objecto '" + idObjecto + "'", e);
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

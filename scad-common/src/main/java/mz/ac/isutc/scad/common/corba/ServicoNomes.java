package mz.ac.isutc.scad.common.corba;

import org.omg.CORBA.ORB;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;

/**
 * Acesso simplificado ao CORBA Naming Service (CosNaming).
 * Usa nomes "planos" (sem contextos aninhados) para simplificar o registo.
 */
public final class ServicoNomes {

    public static final String CENTRAL_AUDITORIA = "CentralAuditoria";

    private final ORB orb;

    ServicoNomes(ORB orb) {
        this.orb = orb;
    }

    public static String nomeAgente(String regiao) {
        return "AgenteStock-" + regiao;
    }

    /** Associa (ou substitui) o nome à referência. Idempotente: pode ser repetido periodicamente. */
    public void registar(String nome, org.omg.CORBA.Object referencia) {
        try {
            NamingContextExt contexto = contexto();
            contexto.rebind(contexto.to_name(nome), referencia);
        } catch (org.omg.CORBA.SystemException e) {
            throw e;
        } catch (Exception e) {
            throw new FalhaNamingException("Falha ao registar '" + nome + "' no Naming Service", e);
        }
    }

    public org.omg.CORBA.Object resolver(String nome) {
        try {
            return contexto().resolve_str(nome);
        } catch (org.omg.CORBA.SystemException e) {
            throw e;
        } catch (Exception e) {
            throw new FalhaNamingException("Nome '" + nome + "' não encontrado no Naming Service", e);
        }
    }

    private NamingContextExt contexto() throws org.omg.CORBA.ORBPackage.InvalidName {
        return NamingContextExtHelper.narrow(orb.resolve_initial_references("NameService"));
    }

    public static final class FalhaNamingException extends RuntimeException {
        public FalhaNamingException(String mensagem, Throwable causa) {
            super(mensagem, causa);
        }
    }
}

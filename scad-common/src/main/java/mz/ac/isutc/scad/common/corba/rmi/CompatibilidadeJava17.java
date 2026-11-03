package mz.ac.isutc.scad.common.corba.rmi;

/**
 * Permite correr o JacORB 3.9 em Java 17.
 *
 * <p>O Java 11 retirou do JDK o CORBA ({@code org.omg.*}) e o RMI-IIOP ({@code javax.rmi.CORBA}).
 * O {@code org.omg.*} vem do jar {@code jacorb-omgapi}; a API {@code javax.rmi.CORBA} vem do jar
 * {@code yoko-rmi-spec}. Mas a classe base das referências do JacORB ({@code org.jacorb.orb.Reference})
 * estende {@code javax.rmi.CORBA.Stub}, que exige implementações ("delegates") de {@code Util} e
 * {@code Stub}. No Java 8 eram fornecidas pelo JDK; aqui fornecemos versões mínimas, suficientes
 * porque o SCAD usa apenas IDL (structs, sequences, enums, excepções) e nunca RMI-IIOP.
 */
public final class CompatibilidadeJava17 {

    private CompatibilidadeJava17() {
    }

    /** Tem de ser chamado antes de qualquer referência CORBA ser criada. */
    public static void activar() {
        System.setProperty("javax.rmi.CORBA.UtilClass", UtilDelegateMinimo.class.getName());
        System.setProperty("javax.rmi.CORBA.StubClass", StubDelegateMinimo.class.getName());
    }
}

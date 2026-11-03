package mz.ac.isutc.scad.common.corba.rmi;

import java.rmi.NoSuchObjectException;
import java.rmi.Remote;
import java.rmi.RemoteException;

import javax.rmi.CORBA.Stub;
import javax.rmi.CORBA.Tie;
import javax.rmi.CORBA.UtilDelegate;
import javax.rmi.CORBA.ValueHandler;

import org.omg.CORBA.NO_IMPLEMENT;
import org.omg.CORBA.ORB;
import org.omg.CORBA.SystemException;
import org.omg.CORBA.portable.InputStream;
import org.omg.CORBA.portable.OutputStream;

/**
 * Compatibilidade JacORB + Java 17 (ver {@link CompatibilidadeJava17}).
 *
 * <p>Implementação mínima de {@code javax.rmi.CORBA.Util}: só o necessário para carregar classes
 * e mapear excepções. As operações de RMI-IIOP (valuetypes, Ties, cópia de objectos) não são
 * usadas pelo IDL do SCAD e falham explicitamente com NO_IMPLEMENT.
 */
public final class UtilDelegateMinimo implements UtilDelegate {

    @Override
    public RemoteException mapSystemException(SystemException e) {
        return new RemoteException("Excepção CORBA: " + e, e);
    }

    @Override
    public RemoteException wrapException(Throwable t) {
        return new RemoteException("Excepção remota: " + t, t);
    }

    @Override
    public Class<?> loadClass(String nome, String codebase, ClassLoader loader) throws ClassNotFoundException {
        ClassLoader cl = loader != null ? loader : Thread.currentThread().getContextClassLoader();
        return Class.forName(nome, false, cl);
    }

    @Override
    public boolean isLocal(Stub stub) {
        return false;
    }

    @Override
    public String getCodebase(Class clazz) {
        return null;
    }

    @Override
    public ValueHandler createValueHandler() {
        throw semRmiIiop();
    }

    @Override
    public Object copyObject(Object obj, ORB orb) {
        throw semRmiIiop();
    }

    @Override
    public Object[] copyObjects(Object[] obj, ORB orb) {
        throw semRmiIiop();
    }

    @Override
    public Tie getTie(Remote target) {
        return null;
    }

    @Override
    public void registerTarget(Tie tie, Remote target) {
        throw semRmiIiop();
    }

    @Override
    public void unexportObject(Remote target) throws NoSuchObjectException {
        throw new NoSuchObjectException("RMI-IIOP não é suportado no SCAD");
    }

    @Override
    public Object readAny(InputStream in) {
        throw semRmiIiop();
    }

    @Override
    public void writeAny(OutputStream out, Object obj) {
        throw semRmiIiop();
    }

    @Override
    public void writeRemoteObject(OutputStream out, Object obj) {
        throw semRmiIiop();
    }

    @Override
    public void writeAbstractObject(OutputStream out, Object obj) {
        throw semRmiIiop();
    }

    private static NO_IMPLEMENT semRmiIiop() {
        return new NO_IMPLEMENT("RMI-IIOP não é suportado no SCAD (apenas IDL)");
    }
}

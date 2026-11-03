package mz.ac.isutc.scad.common.corba.rmi;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.rmi.RemoteException;

import javax.rmi.CORBA.Stub;
import javax.rmi.CORBA.StubDelegate;

import org.omg.CORBA.BAD_OPERATION;
import org.omg.CORBA.ORB;

/**
 * Compatibilidade JacORB + Java 17 (ver {@link CompatibilidadeJava17}).
 *
 * <p>Delega equals/hashCode/toString no Delegate CORBA do próprio objecto, exactamente como
 * faz {@code org.omg.CORBA.portable.ObjectImpl}. As operações exclusivas de RMI-IIOP não são
 * suportadas (o SCAD só usa IDL puro).
 */
public final class StubDelegateMinimo implements StubDelegate {

    @Override
    public boolean equals(Stub self, Object outro) {
        if (self == outro) {
            return true;
        }
        try {
            return self._get_delegate().equals(self, outro);
        } catch (BAD_OPERATION semDelegate) {
            return false;
        }
    }

    @Override
    public int hashCode(Stub self) {
        try {
            return self._get_delegate().hashCode(self);
        } catch (BAD_OPERATION semDelegate) {
            return System.identityHashCode(self);
        }
    }

    @Override
    public String toString(Stub self) {
        try {
            return self._get_delegate().toString(self);
        } catch (BAD_OPERATION semDelegate) {
            return self.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(self));
        }
    }

    @Override
    public void connect(Stub self, ORB orb) throws RemoteException {
        throw new RemoteException("RMI-IIOP não é suportado no SCAD (apenas IDL)");
    }

    @Override
    public void readObject(Stub self, ObjectInputStream in) throws IOException {
        throw new NotSerializableException("Referências CORBA não são serializáveis por Java (use IOR)");
    }

    @Override
    public void writeObject(Stub self, ObjectOutputStream out) throws IOException {
        throw new NotSerializableException("Referências CORBA não são serializáveis por Java (use IOR)");
    }
}

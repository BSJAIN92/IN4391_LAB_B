package distributed.systems.das.services;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ReplicationHandler extends Remote{
	void broadcastBattleFieldUpdates() throws RemoteException;
	void onReceiveBattleFieldUpdates() throws RemoteException;
}

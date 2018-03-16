package distributed.systems.das.services;

import java.rmi.Remote;
import java.rmi.RemoteException;

import distributed.systems.das.common.Message;

public interface MessagingHandler extends Remote {
	Message onMessageReceived(Message message) throws RemoteException;
	void onSynchronizationMessageReceived(Message message) throws RemoteException;
}

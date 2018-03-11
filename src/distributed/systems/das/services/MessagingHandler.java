package distributed.systems.das.services;

import java.rmi.Remote;
import java.rmi.RemoteException;

import distributed.systems.das.Message;

public interface MessagingHandler extends Remote {
	void onMessageReceived(Message message) throws RemoteException;
}

package distributed.systems.das;

import java.rmi.RemoteException;

import distributed.systems.das.common.Message;
import distributed.systems.das.common.MessageType;
import distributed.systems.das.services.MessagingHandler;

public class SendMessages implements Runnable{
	Message msg;
	MessagingHandler handler;
	MessageType type;
	public SendMessages(Message msg, MessagingHandler h, MessageType messageType){
		this.msg = msg;
		this.handler = h;
		this.type = messageType;
	}
	public void run() {
		try {
			if(type.equals(MessageType.sync)) {
				handler.onSynchronizationMessageReceived(msg);
			}
			else if(type.equals(MessageType.changeServer)) {
				handler.onMessageReceived(msg);
			}
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}

package distributed.systems.das.services;

import java.rmi.RemoteException;
import java.util.HashMap;

import distributed.systems.das.GameState;
import distributed.systems.das.common.Message;
import distributed.systems.das.common.MessageType;

public class HeartbeatService implements Runnable {
	
	private MessagingHandler backupServerhandle;
	private String serverIp;
	private int frequency;
	private Thread runnerThread; 
	
	public HeartbeatService(MessagingHandler backupServerHandle, String ip) {
		this.backupServerhandle = backupServerHandle;
		this.serverIp = ip;
		this.frequency = 3000;
		runnerThread = new Thread(this);
		runnerThread.start();
	}
	public void run() {
		//periodically send heartbeat message to designated server	
		try {
			/* sleep for 3 seconds */
			Thread.currentThread().sleep(frequency);
			
			/* send heartbeat message*/
			Message hb = new Message();
			hb.put("serverName", serverIp);
			Message reply = null;
			try {
				reply = this.backupServerhandle.onHeartbeatReceived(hb);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			/* process the reply heartbeat*/
			String s = "Heartbeat reply received from server: "+reply.get("serverName");
			LoggingService.log(MessageType.heartbeat, s);
			
		} catch (InterruptedException e) {			
			e.printStackTrace();
		}
	}
}

package distributed.systems.das.services;

import java.rmi.RemoteException;
import java.util.HashMap;

import distributed.systems.das.GameState;
import distributed.systems.das.common.Message;
import distributed.systems.das.common.MessageType;

public class HeartbeatService implements Runnable {
	
	private MessagingHandler backupServerhandle;
	private String serverName;
	private int frequency;
	private Thread runnerThread; 
	
	public HeartbeatService(MessagingHandler backupServerHandle, String ip) {
		this.backupServerhandle = backupServerHandle;
		this.serverName = ip;
		this.frequency = 20000;
		runnerThread = new Thread(this);
		runnerThread.start();
	}
	public void run() {
		LoggingService.log(MessageType.setup, "HeartbeatService thread: "+ Thread.currentThread().getName() +" started for server "+serverName);
		//periodically send heartbeat message to designated server	
		try {
			Thread.currentThread();
			/* sleep for 3 seconds */
			Thread.sleep(frequency);
			
			/* send heartbeat message*/
			Message hb = new Message();
			hb.put("serverName", serverName);
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

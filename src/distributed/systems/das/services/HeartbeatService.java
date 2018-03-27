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
		this.frequency = 3000;
		runnerThread = new Thread(this);
		runnerThread.start();
	}
	public synchronized void run() {
		LoggingService.log(MessageType.setup, "["+serverName+"]"+"HeartbeatService thread: "+ Thread.currentThread().getName() +" started for server "+serverName);
		//periodically send heart beat message to designated server
		while(GameState.getRunningState()) {
			LoggingService.log(MessageType.setup, "["+serverName+"]"+"Heartbeat sending.");
			try {
				Thread.currentThread();
				/* sleep for 3 seconds */
				Thread.sleep(frequency);
				
				/* send heart beat message*/
				Message hb = new Message();
				hb.put("serverName", serverName);
				Message reply = null;
				reply = this.backupServerhandle.onHeartbeatReceived(hb);
				/* process the reply heart beat*/
				String s = "["+serverName+"]"+"Heartbeat reply received from server: "+reply.get("serverName");
				LoggingService.log(MessageType.heartbeat, s);
					
			}catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}				 
			catch (InterruptedException e) {			
				e.printStackTrace();
			}
		}
	}
}

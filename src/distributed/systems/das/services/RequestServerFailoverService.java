package distributed.systems.das.services;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

import distributed.systems.das.BackupRequestHandlingServer;
import distributed.systems.das.GameState;
import distributed.systems.das.common.MessageType;

public class RequestServerFailoverService implements Runnable{
	
	private BackupRequestHandlingServer serverState;
	Thread t;
	
	public RequestServerFailoverService(BackupRequestHandlingServer backupRequestHandlingServer) {
		this.serverState = backupRequestHandlingServer;
		t = new Thread(this);
		t.start();
	}
	
	@Override
	public synchronized void run() {
		
		LoggingService.log(MessageType.heartbeat, "[backupServerReq]"+"Started thread "+Thread.currentThread().getName() + " for failover listener.");
		//periodically check if all expected heartbeats have been received
		while(GameState.getRunningState()) {
			if(serverState.getTimeSinceHeartbeat()!= null && !serverState.getTimeSinceHeartbeat().isEmpty()) {
				Iterator it = serverState.getTimeSinceHeartbeat().entrySet().iterator();
				ArrayList<String> serversFailed = new ArrayList<String>();
				while(it.hasNext()) {
					Map.Entry pair = (Map.Entry) it.next();
					long diff = Math.abs(System.currentTimeMillis() - (long)pair.getValue());
					if(diff > 10000) { 
						/*exceed heart beat wait time. */
						serversFailed.add((String) pair.getKey());
						serverState.removeTimeSinceHeartbeatServer((String) pair.getKey());
						LoggingService.log(MessageType.changeServer, "[backupServerReq]"+"Time limit exceeded. Start failover for ["+(String) pair.getKey()+"]");	
					}
				}
				serversFailed.forEach(s -> {
					LoggingService.log(MessageType.changeServer, "[backupServerReq]"+"XXXXXXXXXXX "+serverState.getTimeSinceHeartbeat().isEmpty());
					(new Thread(){
						public void run() {
							serverState.processServerFailure(s);
						}
					}).start();
				});
			}
		}
	}
}

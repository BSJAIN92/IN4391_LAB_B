package distributed.systems.das.services;

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
	public void run() {
		
		LoggingService.log(MessageType.changeServer, "Started thread "+Thread.currentThread().getName() + " for failover listener.");
		//periodically check if all expected heartbeats have been received
		while(GameState.getRunningState()) {
			if(serverState.getTimeSinceHeartbeat()!= null && !serverState.getTimeSinceHeartbeat().isEmpty()) {
				Iterator it = serverState.getTimeSinceHeartbeat().entrySet().iterator();
				while(it.hasNext()) {
					Map.Entry pair = (Map.Entry) it.next();
					long diff = Math.abs(System.currentTimeMillis() - (long)pair.getValue());
					LoggingService.log(MessageType.changeServer, "Time difference is: "+diff + " for failover listener.");
					if(diff > 40000) { 
						/*exceed heartbeat wait time. assume helper server is dead.
						 * tell main game server and backup game server that this server is dead
						 * */
						LoggingService.log(MessageType.changeServer, "Time limit exceeded. Start failover process.");
						serverState.processServerFailure(pair.getKey().toString());
					}
				}
			}
		}
	}
}

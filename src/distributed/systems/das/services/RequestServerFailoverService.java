package distributed.systems.das.services;

import java.util.Iterator;
import java.util.Map;

import distributed.systems.das.BackupRequestHandlingServer;
import distributed.systems.das.GameState;

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
		//periodically check if all expected heartbeats have been received
		while(GameState.getRunningState()) {
			if(serverState.getTimeSinceHeartbeat()!= null && !serverState.getTimeSinceHeartbeat().isEmpty()) {
				Iterator it = serverState.getTimeSinceHeartbeat().entrySet().iterator();
				while(it.hasNext()) {
					Map.Entry pair = (Map.Entry) it.next();
					//Date d1 = df.parse(pair.getValue().toString());
					//Date d2 = df.parse(new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date()));
					long diff = Math.abs(System.currentTimeMillis() - (long)pair.getValue());
					if(diff > 5000) { 
						/*exceed heartbeat wait time. assume helper server is dead.
						 * tell main game server and backup game server that this server is dead
						 * */
						serverState.processServerFailure(pair.getKey().toString());
					}
				}
			}
		}
	}
}

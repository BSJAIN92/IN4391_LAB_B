package distributed.systems.das.units;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import distributed.systems.das.BackupRequestHandlingServer;
import distributed.systems.das.GameServer;
import distributed.systems.das.RequestHandlingServer;
import distributed.systems.das.common.Message;
import distributed.systems.das.common.MessageType;
import distributed.systems.das.services.LoggingService;
import distributed.systems.das.services.MessagingHandler;

class PlayerDetails {
	
	private int id;
	
	private double timestamp;
	
	private double duration;
	
	public PlayerDetails(int id, double timestamp, double duration) {
		this.id = id;
		this.timestamp = timestamp;
		this.duration = duration;
		
	}
	
	public int getId() {
		return this.id;
	}
	
	public double getTimestamp() {
		return this.timestamp;
	}
	
	public double getDuration() {
		return this.duration;
	}
}


public class PlayerConnectionSimulation implements Runnable{
	
	private static List<PlayerDetails> listPlayers;
	int noOfPlayers;
	String file = "SC2_Edge_Detailed";
	RequestHandlingServer requestHandlingServer;
	BackupRequestHandlingServer backupRequestHandlingServer;
	int serverNumber;
	
	Thread simThread;
	
	public PlayerConnectionSimulation(int noOfPlayers, RequestHandlingServer server, int serverNumber) {
		
		LoggingService.log(MessageType.spawnUnit, "in GTA");
		this.noOfPlayers = noOfPlayers;
		this.requestHandlingServer = server;
		this.serverNumber = serverNumber;
		
		listPlayers = getPlayersList(file);
		
		Collections.sort(listPlayers, new Comparator<PlayerDetails>() {
			public int compare(PlayerDetails p1, PlayerDetails p2) {
				if(p1.getTimestamp() > p2.getTimestamp()) {
					return 1;
				}
				else if(p1.getTimestamp() < p2.getTimestamp()) {
					return -1;
				}
				else {
					return 0;
				}
			}
		});		
		simThread = new Thread(this);
		simThread.start();
	}
	
	public PlayerConnectionSimulation(int noOfPlayers, BackupRequestHandlingServer server, int serverNumber) {
		
		LoggingService.log(MessageType.spawnUnit, "in GTA");
		this.noOfPlayers = noOfPlayers;
		this.backupRequestHandlingServer = server;
		this.serverNumber = serverNumber;
		
		listPlayers = getPlayersList(file);
		
		Collections.sort(listPlayers, new Comparator<PlayerDetails>() {
			public int compare(PlayerDetails p1, PlayerDetails p2) {
				if(p1.getTimestamp() > p2.getTimestamp()) {
					return 1;
				}
				else if(p1.getTimestamp() < p2.getTimestamp()) {
					return -1;
				}
				else {
					return 0;
				}
			}
		});		
		simThread = new Thread(this);
		simThread.start();
	}
	
	@Override
	public synchronized void run() {
		if(requestHandlingServer != null) {
			LoggingService.log(MessageType.spawnUnit, "[reqServer_"+ serverNumber +"]"+"in GTA run");
			int start = serverNumber*noOfPlayers;
			int end =  start + noOfPlayers;
			for (int i = start; i < end; i++) {
				int id = listPlayers.get(i-1).getId();
				
				LoggingService.log(MessageType.setup, "[reqServer_"+ serverNumber +"]"+"Player Number: " + id);
				long sleepTime = (long) (listPlayers.get(i).getTimestamp() - listPlayers.get(i-1).getTimestamp());
				LoggingService.log(MessageType.setup, "[reqServer_"+ serverNumber +"]"+"Sleep for: " + sleepTime);
				(new Thread() {
					public void run() {
						try {
							Thread.sleep(sleepTime);
							requestHandlingServer.spawnUnitRequest(id);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}) .start();
			}	
		}
		if(backupRequestHandlingServer != null) {
			int start = serverNumber*noOfPlayers;
			int end =  start + noOfPlayers;
			for (int i = start; i < end; i++) {
				int id = listPlayers.get(i-1).getId();
				long sleepTime = (long) (listPlayers.get(i).getTimestamp() - listPlayers.get(i-1).getTimestamp());
				(new Thread() {
					public void run() {
						try {
							Thread.sleep(sleepTime);
							backupRequestHandlingServer.spawnUnitRequest(id);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}) .start();
			}	
		}
	}
	
	private List<PlayerDetails> getPlayersList(String file){
		List <PlayerDetails> p = new ArrayList<PlayerDetails>();
		String record = null;
		int count = 0;
		BufferedReader br = null;
		
		try {
			br = new BufferedReader(new FileReader(file));
			
			record = br.readLine();
			
		}catch (Exception e) {
				e.printStackTrace();
		}
			
		while(record != null && count < 101) {
			try {
				record = br.readLine();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			
			String[] read = record.split(", ");
			
			try {
				if(read.length >= 3) {
					int id = 0;
					id = Integer.parseInt(read[0]);
					double timestamp = Double.parseDouble(read[1]);
					double duration = Double.parseDouble(read[2]);
					PlayerDetails player = new PlayerDetails(id, timestamp, duration);
					p.add(player);
					//System.out.println(id + " " + timestamp + " " + duration);
					count += 1;
				}
			}	catch(Exception e) {
				
			}
		}
		

		return p;
		
	}
}

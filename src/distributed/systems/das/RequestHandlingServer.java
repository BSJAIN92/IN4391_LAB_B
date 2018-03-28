package distributed.systems.das;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import distributed.systems.das.common.Message;
import distributed.systems.das.common.MessageType;
import distributed.systems.das.common.UnitState;
import distributed.systems.das.common.UnitType;
import distributed.systems.das.services.ClientServer;
import distributed.systems.das.services.HeartbeatService;
import distributed.systems.das.services.LoggingService;
import distributed.systems.das.services.MessagingHandler;
import distributed.systems.das.services.RequestServerFailoverService;
import distributed.systems.das.units.Dragon;
import distributed.systems.das.units.Player;
import distributed.systems.das.units.PlayerConnectionSimulation;

public class RequestHandlingServer implements MessagingHandler {
	public int MAP_WIDTH;
	public int MAP_HEIGHT;
	public UnitState[][] map;
	private int localMessageCounter = 0;
	private static RequestHandlingServer battlefield;
	//private static String gameServerIp = "localhost";
	//private static String backupServerIp = "localhost";
	private static String myServerName;
	private static int myServerNumber;
	private static int port = 1099; //we use default RMI Registry port
	private static MessagingHandler gameServerHandle;
	private static MessagingHandler backupReqServerHandle;
	private static HeartbeatService heartbeat;
	private static RequestServerFailoverService reqFailoverService;
	private static HashMap<String, String> serverIps;
	public static HashMap<String, Long> timeSinceHeartbeat;
	public static int numberOfPlayers;
	
	
	private RequestHandlingServer(int width, int height){
		MAP_WIDTH = width;
		MAP_HEIGHT = height;
		//read all IP addresses from config file and add them to hashmap
		createIpMap();	
	}
	private void createIpMap() {
		serverIps = new HashMap<String, String>();
		File ipFile = new File("/home/ec2-user/ipAddresses.txt");
		try {
			BufferedReader in = new BufferedReader(new FileReader(ipFile));
			String line = null;
			while((line = in.readLine()) != null) {
				String[] s = line.split(" ");
				if(!s[0].equals(myServerName)) {
					serverIps.put(s[0], s[1]);
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public static MessagingHandler getRequestHandlingServer(){
		if(battlefield == null) {
			battlefield = new RequestHandlingServer(25, 25);
			battlefield.map = new UnitState[25][25];
		}
		return battlefield;
	}
	
	public UnitState getUnit(int x, int y)
	{
		assert x >= 0 && x < map.length;
		assert y >= 0 && x < map[0].length;

		return map[x][y];
	}
	
	private synchronized UnitState spawnUnit(int x, int y, int id, UnitType unitType, String origin)
	{
		UnitState unit = null; 
		synchronized (this) {
			if (map[x][y] == null) {
				unit = new UnitState(x, y, id, unitType, origin);
				map[x][y] = unit;
				map[x][y].setPosition(x, y);
			}
		}
		return unit;
	}
	
	private synchronized UnitState moveUnit(UnitState unit, int newX, int newY)
	{
		synchronized(this) {
			if (newX >= 0 && newX < MAP_WIDTH)
				if (newY >= 0 && newY < MAP_HEIGHT)
					if (map[newX][newY] == null) {
						/*UnitState newUnit = new UnitState(newX, newY, unit.unitID, unit.unitType, unit.helperServerAddress);
						map[newX][newY] =  newUnit;
						removeUnit(unit.x, unit.y);
						return map[newX][newY];*/
						removeUnit(unit.x, unit.y);
						unit.x = newX;
						unit.y = newY;
						map[newX][newY] = unit;
						return map[newX][newY];
					}
			return null;
		}
	}
	
	private synchronized void removeUnit(int x, int y)
	{
		synchronized(this) {
			map[x][y] = null;
		}
	}
	
	public synchronized void moveUnitRequest(UnitState unit, int toX, int toY) {
		int a = unit.x;
		int b = unit.y;
		int uid = unit.unitID;
		Message moveMessage = new Message();
		int id = ++localMessageCounter;
		moveMessage.setMessageType(MessageType.moveUnit);
		moveMessage.put("request", MessageType.moveUnit);
		moveMessage.put("toX", toX);
		moveMessage.put("toY", toY);
		moveMessage.put("id", id);
		moveMessage.put("unit", unit);
		moveMessage.put("origin", unit.helperServerAddress);
		String text = "["+ myServerName+"]"+" Move unit X: "+ unit.x + " Y: "+ unit.y + " unitID: "+unit.unitID+ " to X:"+toX+" Y: "+toY;
		LoggingService.log(MessageType.moveUnit, text);
		try {
			Message reply = gameServerHandle.onMessageReceived(moveMessage);
			if((boolean)reply.get("moveSuccess")) {
				synchronized(this) {
					moveUnit(unit, toX, toY);
				}
				if(map[a][b] == null) {
					LoggingService.log(MessageType.moveUnit, "["+ myServerName+"]"+"moveRequest UnitId: "+uid
							+"move from ["+a +","+b+"] old map is updated to null.");
				}
				if(map[toX][toY] != null) {
					LoggingService.log(MessageType.moveUnit, "["+ myServerName+"]"+"moveRequest UnitId: "+uid
							+"move to ["+toX +","+toY+"] new map is not null. It is ["+map[toX][toY].x +","+map[toX][toY].y+"]");
				}
			}
		} catch (RemoteException e) { 
			e.printStackTrace();
		}
	}
	
	public synchronized void healDamageRequest(UnitState unit, int toX, int toY) {
		int id;
		Message healMessage;
		synchronized (this) {
			id = ++localMessageCounter;
			healMessage = new Message();
			healMessage.setMessageType(MessageType.healDamage);
			healMessage.put("request", MessageType.healDamage);
			healMessage.put("toX", toX);
			healMessage.put("toY", toY);
			healMessage.put("healedPoints", unit.attackPoints);
			healMessage.put("id", id);
			healMessage.put("origin", unit.helperServerAddress);
			String text = "["+ myServerName+"]"+ "Heal from unit X: "+ unit.x + " Y: "+ unit.y + " unitID: "+unit.unitID+ "to X:"+toX+" Y: "+toY;
			LoggingService.log(MessageType.healDamage, text);
			try {
				Message reply = gameServerHandle.onMessageReceived(healMessage);
				if(reply!= null) {
					UnitState toUnit = getUnit(toX, toY);
					synchronized(this) {
						toUnit.adjustHitPoints(unit.attackPoints);
					}
				}
				else {
					LoggingService.log(MessageType.healDamage, "Caoont heal damage because ["+toX+", "+toY+"] does not exist anymore.");
				}
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}	
	}
	
	public synchronized void dealDamageRequest(UnitState unit, int toX, int toY) {
		int id;
		Message dealMessage;
		synchronized(this) {
			id = ++localMessageCounter;
			dealMessage = new Message();
			dealMessage.setMessageType(MessageType.dealDamage);
			dealMessage.put("request", MessageType.dealDamage);
			dealMessage.put("toX", toX);
			dealMessage.put("toY", toY);
			dealMessage.put("damagePoints", unit.attackPoints);
			dealMessage.put("id", id);
			dealMessage.put("origin", unit.helperServerAddress);
			String text = "["+ myServerName+"]"+ "Deal unit X: "+ unit.x + " Y: "+ unit.y + " unitID: "+unit.unitID+ "to X:"+toX+" Y: "+toY;
			LoggingService.log(MessageType.dealDamage, text);
			try {
				Message reply = gameServerHandle.onMessageReceived(dealMessage);
				if(reply!= null) {
					UnitState toUnit = getUnit(toX, toY);
					synchronized(this) {
						toUnit.adjustHitPoints(-unit.attackPoints);
					}
				}
				else {
					LoggingService.log(MessageType.dealDamage, "Caoont deal damage because ["+toX+", "+toY+"] does not exist anymore.");
				}
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
	}
	
	public synchronized void spawnUnitRequest(int unitId) {
		int id;
		Message spawnMessage;
		synchronized(this) {
			id = ++localMessageCounter;
			spawnMessage = new Message();
			spawnMessage.setMessageType(MessageType.spawnUnit);
			spawnMessage.put("request", MessageType.spawnUnit);
			spawnMessage.put("id", id);
			spawnMessage.put("origin", myServerName);
			spawnMessage.put("unitId", unitId);
			String text = "["+ myServerName+"]"+ "Spawn new unit.";
			LoggingService.log(MessageType.spawnUnit, text);
			Message reply;
			UnitState spawnedUnit;
			try {
				reply = gameServerHandle.onMessageReceived(spawnMessage);
				if(reply!=null) {
					UnitState unit = (UnitState)reply.get("unit");
					synchronized(this) {
						spawnedUnit = spawnUnit(unit.x, unit.y, unit.unitID, unit.unitType, unit.helperServerAddress);
						if(spawnedUnit != null) {
							LoggingService.log(MessageType.spawnUnit, "["+ myServerName+"]"+ 
									"Spawned new unit at map ["+map[unit.x]+","+unit.y+"]");
							Player p = new Player(spawnedUnit, battlefield, myServerName);
						}
						else {
							LoggingService.log(MessageType.spawnUnit, "XXXXXXXXXXx");
						}
					}
				}
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public synchronized void removeUnitRequest(int x, int y)
	{
		int id;
		Message removeMessage;
		synchronized(this) {
			id = ++localMessageCounter;
			removeMessage = new Message();
			removeMessage.put("request", MessageType.removeUnit);
			removeMessage.put("x", x);
			removeMessage.put("y", y);
			removeMessage.put("id", id);
			Message reply;
			try {
				reply = gameServerHandle.onMessageReceived(removeMessage);
				if(reply != null) {
					synchronized(this) {
						removeUnit(x, y);
					}
				}
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void placeUnitOnMap(UnitState u) {
		try {
			map[u.x][u.y] = u;
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return;
	}
	public static void main(String args[]) throws NotBoundException {	
		String bkName = "backupServerReq";
		try {
			myServerName = args[0]; 
			myServerNumber = Integer.parseInt(myServerName.split("_")[1]);
			numberOfPlayers = Integer.parseInt(args[2]);
			LocateRegistry.createRegistry(1099);
			
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		MessagingHandler reqHandlingServer = getRequestHandlingServer();
        MessagingHandler reqHandlingServerStub;
		try {
			reqHandlingServerStub = (MessagingHandler) UnicastRemoteObject.exportObject(reqHandlingServer, 0);
			Registry registry = LocateRegistry.getRegistry();
	        registry.rebind(myServerName, reqHandlingServerStub);
	        Registry backupRegistry = LocateRegistry.getRegistry(serverIps.get(bkName));
	        LoggingService.log(MessageType.setup, "["+ myServerName+"]" +" remote object registered.");
	        backupReqServerHandle = (MessagingHandler) backupRegistry.lookup(bkName);
	        heartbeat = new HeartbeatService(backupReqServerHandle, myServerName);
	        LoggingService.log(MessageType.setup, "["+ myServerName+"]"+"Backup request server handle obtained.");
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public synchronized Message onMessageReceived(Message message) throws RemoteException {
		if(message.get("type").equals(MessageType.setup)) {
			LoggingService.log(MessageType.setup, "["+ myServerName+"]"+ " received player and dragon setup message from gameServer.");
			try {
				Registry remoteRegistry  = LocateRegistry.getRegistry(serverIps.get("gameServer"), port);
				gameServerHandle= (MessagingHandler) remoteRegistry.lookup("gameServer");
				LoggingService.log(MessageType.setup, "["+ myServerName+"]"+" gameServerHandle obtained."+gameServerHandle);
			} catch (RemoteException e) {
				e.printStackTrace();
			} catch (NotBoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			HashMap<String, ArrayList<UnitState>> setupDragons = (HashMap<String, ArrayList<UnitState>>) message.get("dragons");
			setupDragons.forEach((k,v)->{
				v.forEach(u->{
					placeUnitOnMap(u);
					if(u.helperServerAddress.equals(myServerName)) {
						Dragon d = new Dragon(u, battlefield, myServerName);
					}
				});
			});
			
			//start a new thread to simulate player connections
			PlayerConnectionSimulation sim = new PlayerConnectionSimulation(numberOfPlayers, battlefield, myServerNumber);
			
			/*HashMap<String, ArrayList<UnitState>> setupPlayers = (HashMap<String, ArrayList<UnitState>>) message.get("players");
			setupPlayers.forEach((k,v)->{
				v.forEach(u->{
					placeUnitOnMap(u);
					if(u.helperServerAddress.equals(myServerName)) {
						Player d = new Player(u, battlefield, myServerName);
					}
				});
			});*/
		}
		return null;
	}
	
	@Override
	public synchronized void onSynchronizationMessageReceived(Message message) throws RemoteException {
		if(message.get("request").equals(MessageType.sync)) {
			UnitState u;
			int x = 0, y = 0;
			MessageType messageType = (MessageType)message.get("type");
			String text = "["+ myServerName+"]"+"Received sync message for action "+messageType;
			LoggingService.log(MessageType.sync, text);
			switch(messageType) {
			case spawnUnit:
				u = (UnitState) message.get("unit");
				synchronized(this) {
					map[u.x][u.y] = u;
				}
				break;
			case dealDamage:
				x = (Integer)message.get("x");
				y = (Integer)message.get("y");
				Integer damagePoints = (Integer)message.get("damagePoints");
				u = (UnitState) message.get("unit");
				u = this.getUnit(x, y);
				if (u != null) {
					synchronized(this) {
						u.adjustHitPoints(-damagePoints );
					}
				}
				break;
			case healDamage:
				u = (UnitState) message.get("unit");
				x = (int)message.get("x");
				y = (int)message.get("y");
				u = this.getUnit(x, y);
				if (u != null)
					synchronized(this) {
						u.adjustHitPoints((Integer)message.get("healedPoints") );
					}
				break;
			case moveUnit:
				int fromX = Integer.parseInt(message.get("fromX").toString());;
				int fromY = Integer.parseInt(message.get("fromY").toString());;
				int toX = Integer.parseInt(message.get("toX").toString());
				int toY = Integer.parseInt(message.get("toY").toString());
				LoggingService.log(MessageType.moveUnit, "YYYYYYYYYYYYYYYY "+ fromX+" "+fromY+  " "+toX+ " "+toY);
				UnitState oldUnit = getUnit(fromX, fromY);
				if(oldUnit == null) {
					LoggingService.log(MessageType.moveUnit, "YYYYYYYYYYYYYYYY "+ fromX+" "+fromY+  " "+toX+ " "+toY);
				}
				synchronized(this) {
					moveUnit(oldUnit, toX, toY);
				}
				if(map[fromX][fromY] == null) {
					LoggingService.log(MessageType.moveUnit,"["+ myServerName+"]"+ "moveRequest UnitId: "+oldUnit.unitID
							+" move from ["+fromX +","+fromY+"] old map is updated to null.");
				}
				else {
					LoggingService.log(MessageType.moveUnit,"["+ myServerName+"]"+ "XXXXIt is ["+map[fromX][fromY].x +","+map[fromX][fromY].y+"]");
				}
				if(map[toX][toY] != null) {
					LoggingService.log(MessageType.moveUnit,"["+ myServerName+"]"+ "moveRequest UnitId: "+oldUnit.unitID
							+" move to ["+toX +","+toY+"] new map is not null. It is ["+map[toX][toY].x +","+map[toX][toY].y+"]");
				}
				break;
			case putUnit:
				break;
			case removeUnit:
				x = (Integer)message.get("removeX");
				y = (Integer)message.get("removeY");
				removeUnit(x,y);
				break;
			case getType:
				break;
			case getUnit:
				break;
			case setup:
				break;
			case sync:
				break;
			default:
				break;			
			}
		}		
	}
	
	public synchronized UnitState killPlayer(int x, int y) {
		UnitState u;
		u = getUnit(x, y);
		if(u.hitPoints <= 0) {
			synchronized(this) {
				removeUnit(x, y);
				u = null;
			}
		}
		return u;
	}
	public Message onHeartbeatReceived(Message msg) {		
		Message reply = null;	
		String name = msg.get("serverName").toString();
		String text = "["+ myServerName+"]" + " Received heartbeat from " +  name;
		LoggingService.log(MessageType.heartbeat, text);
		Date now = null;
		if(!timeSinceHeartbeat.containsKey(name)) {
			timeSinceHeartbeat.put(name, System.currentTimeMillis());
		}
		else {
			timeSinceHeartbeat.put(name, System.currentTimeMillis());
		}
		reply = new Message();
		reply.put("serverName", myServerName);
		return reply;
	}
}

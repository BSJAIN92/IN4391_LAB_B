package distributed.systems.das.units;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import distributed.systems.das.BattleField;
import distributed.systems.das.GameState;
import distributed.systems.das.Message;
import distributed.systems.das.MessageRequest;
import distributed.systems.das.communication.NodeAddress;


public class Unit implements Serializable{
	// Position of the unit
	protected int x, y;

	// Health
	private int maxHitPoints;
	protected int hitPoints;

	// Attack points
	protected int attackPoints;

	// Identifier of the unit
	private int unitID;

	// The communication socket between this client and the board
	protected Socket clientSocket = null;
	
	// Map messages from their ids
	private Map<Integer, Message> messageList;
	// Is used for mapping an unique id to a message sent by this unit
	private int localMessageCounter = 0;
	
	// If this is set to false, the unit will return its run()-method and disconnect from the server
	protected boolean running;

	/* The thread that is used to make the unit run in a separate thread.
	 * We need to remember this thread to make sure that Java exits cleanly.
	 * (See stopRunnerThread())
	 */
	protected Thread runnerThread;

	public enum Direction {
		up, right, down, left
	};
	
	public enum UnitType {
		player, dragon, undefined,
	};
	
	NodeAddress serverAddress;
	
	/**
	 * Create a new unit and specify the 
	 * number of hitpoints. Units hitpoints
	 * are initialized to the maxHitPoints. 
	 * 
	 * @param maxHealth is the maximum health of 
	 * this specific unit.
	 */
	public Unit(int maxHealth, int attackPoints, NodeAddress serverAddress) {

		messageList = new HashMap<Integer, Message>();
		// Initialize the max health and health
		hitPoints = maxHitPoints = maxHealth;
		// Initialize the attack points
		this.attackPoints = attackPoints;
		// Get a new unit id
		unitID = BattleField.getBattleField().getNewUnitID();
		this.serverAddress = serverAddress; 
		try {
			clientSocket = new Socket(serverAddress.ipAddress, serverAddress.port);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	protected Unit getUnit(int x, int y)
	{
		Message getMessage = new Message(), result;
		int id = localMessageCounter++;
		getMessage.put("request", MessageRequest.getUnit);
		getMessage.put("x", x);
		getMessage.put("y", y);
		getMessage.put("id", id);

		// Send the getUnit message
		try {
			PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
			out.print(getMessage);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();			
			getUnit(x, y);
		}		

		// Wait for the reply
		while(!messageList.containsKey(id)) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
			}

			// Quit if the game window has closed
			if (!GameState.getRunningState())
				return null;
		}

		result = messageList.get(id);
		messageList.put(id, null);

		return (Unit) result.get("unit");	
	}
	protected boolean spawn(int x, int y) {
		/* Create a new message, notifying the board
		 * the unit has actually spawned at the
		 * designated position. 
		 */
		int id = localMessageCounter++;
		Message spawnMessage = new Message();
		spawnMessage.put("request", MessageRequest.spawnUnit);
		spawnMessage.put("x", x);
		spawnMessage.put("y", y);
		spawnMessage.put("unit", this);
		spawnMessage.put("id", id);
		Unit spawnedUnit = null;
		// Send a spawn message
		try {
			PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
			out.print(spawnMessage);
			spawnedUnit = getUnit(x, y);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();			
		}	
		if(spawnedUnit.unitID == this.unitID) {
			return true;
		}
		else {
			return true;
		}
	}
	
}

package distributed.systems.das.units;

import java.io.Serializable;
import java.net.Socket;
import java.rmi.RemoteException;

import distributed.systems.das.GameServer;
import distributed.systems.das.GameState;
import distributed.systems.das.RequestHandlingServer;
import distributed.systems.das.common.Direction;
import distributed.systems.das.common.Message;
import distributed.systems.das.common.UnitState;
import distributed.systems.das.common.UnitType;
import distributed.systems.das.services.MessagingHandler;

public class Player implements Runnable, Serializable{
	
	UnitState unit;
	Thread runnerThread;
	boolean running;
	RequestHandlingServer battlefield;
	/* Reaction speed of the player
	 * This is the time needed for the player to take its next turn.
	 * Measured in half a seconds x GAME_SPEED.
	 */
	protected int timeBetweenTurns;
	public static final int MIN_TIME_BETWEEN_TURNS = 2;
	public static final int MAX_TIME_BETWEEN_TURNS = 7;
	public static final int MAP_SIZE = 25;
	
	private static final long serialVersionUID = -3507657523648823999L;
	
	public Player(UnitState unit) {
		this.unit = unit;
		/* Create a random delay */
		timeBetweenTurns = (int)(Math.random() * (MAX_TIME_BETWEEN_TURNS - MIN_TIME_BETWEEN_TURNS)) + MIN_TIME_BETWEEN_TURNS;
		battlefield = RequestHandlingServer.getRequestHandlingServer();
		runnerThread = new Thread(this);
		runnerThread.start();
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		Direction direction;
		UnitType adjacentUnitType;
		int targetX = 0, targetY = 0;
		
		running = true;

		while(GameState.getRunningState() && this.running) {
			try {			
				/* Sleep while the player is considering its next move */
				Thread.currentThread().sleep((int)(timeBetweenTurns * 500 * GameState.GAME_SPEED));

				/* Stop if the player runs out of hitpoints */
				if (unit.hitPoints <= 0)
					break;

				// Randomly choose one of the four wind directions to move to if there are no units present
				direction = Direction.values()[ (int)(Direction.values().length * Math.random()) ];
				adjacentUnitType = UnitType.Undefined;

				switch (direction) {
					case up:
						if (unit.y <= 0)
							// The player was at the edge of the map, so he can't move north and there are no units there
							continue;
						
						targetX = unit.x;
						targetY = unit.y - 1;
						break;
					case down:
						if (unit.y >= MAP_SIZE - 1)
							// The player was at the edge of the map, so he can't move south and there are no units there
							continue;

						targetX = unit.x;
						targetY = unit.y + 1;
						break;
					case left:
						if (unit.x <= 0)
							// The player was at the edge of the map, so he can't move west and there are no units there
							continue;

						targetX = unit.x - 1;
						targetY = unit.y;
						break;
					case right:
						if (unit.x >= GameServer.MAP_WIDTH - 1)
							// The player was at the edge of the map, so he can't move east and there are no units there
							continue;

						targetX = unit.x + 1;
						targetY = unit.y;
						break;
				}

				// Get what unit lies in the target square
				UnitState us = battlefield.getUnit(targetX, targetY);
				if(us!= null) {
					adjacentUnitType = us.unitType;
				}
				switch (adjacentUnitType) {
					case Undefined:
						// There is no unit in the square. Move the player to this square
						battlefield.moveUnit(unit, targetX, targetY);
						break;
					case Player:
						// There is a player in the square, attempt a healing
						battlefield.healDamage(unit, targetX, targetY);
						break;
					case Dragon:
						// There is a dragon in the square, attempt a dragon slaying
						battlefield.dealDamage(unit, targetX, targetY);
						break;
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}

package distributed.systems.das.units;

import java.util.ArrayList;

import distributed.systems.das.GameServer;
import distributed.systems.das.GameState;
import distributed.systems.das.RequestHandlingServer;
import distributed.systems.das.common.Direction;
import distributed.systems.das.common.UnitState;
import distributed.systems.das.common.UnitType;

public class Dragon implements Runnable{
	/* Reaction speed of the dragon
	 * This is the time needed for the dragon to take its next turn.
	 * Measured in half a seconds x GAME_SPEED.
	 */
	protected int timeBetweenTurns; 
	public static final int MIN_TIME_BETWEEN_TURNS = 2;
	public static final int MAX_TIME_BETWEEN_TURNS = 7;
	Thread runnerThread;
	RequestHandlingServer battlefield;
	UnitState unit;
	/**
	 * Spawn a new dragon, initialize the 
	 * reaction speed 
	 */
	public Dragon(UnitState unit) {
		/* Create a random delay */
		timeBetweenTurns = (int)(Math.random() * (MAX_TIME_BETWEEN_TURNS - MIN_TIME_BETWEEN_TURNS)) + MIN_TIME_BETWEEN_TURNS;
		battlefield = RequestHandlingServer.getRequestHandlingServer();
		/* Awaken the dragon */
		//new Thread(this).start();
		runnerThread = new Thread(this);
		runnerThread.start();
	}

	/**
	 * Roleplay the dragon. Make the dragon act once a while,
	 * only stopping when the dragon is actually dead or the 
	 * program has halted.
	 * 
	 * It checks if an enemy is near and, if so, it attacks that
	 * specific enemy.
	 */
	public void run() {
		ArrayList<Direction> adjacentPlayers = new ArrayList<Direction> ();		
		while(GameState.getRunningState()) {
			try {
				/* Sleep while the dragon is considering its next move */
				Thread.currentThread().sleep((int)(timeBetweenTurns * 500 * GameState.GAME_SPEED));

				/* Stop if the dragon runs out of hitpoints */
				if (unit.hitPoints <= 0)
					break;

				// Decide what players are near
				if (unit.y > 0)
					if ( battlefield.getUnit( unit.x, unit.y - 1 ).unitType == UnitType.Player )
						adjacentPlayers.add(Direction.up);
				if (unit.y < GameServer.MAP_WIDTH - 1)
					if ( battlefield.getUnit( unit.x, unit.y + 1 ).unitType == UnitType.Player )
						adjacentPlayers.add(Direction.down);
				if (unit.x > 0)
					if ( battlefield.getUnit( unit.x - 1, unit.y ).unitType == UnitType.Player )
						adjacentPlayers.add(Direction.left);
				if (unit.x < GameServer.MAP_WIDTH - 1)
					if ( battlefield.getUnit( unit.x + 1, unit.y ).unitType == UnitType.Player )
						adjacentPlayers.add(Direction.right);
				
				// Pick a random player to attack
				if (adjacentPlayers.size() == 0)
					continue; // There are no players to attack
				Direction playerToAttack = adjacentPlayers.get( (int)(Math.random() * adjacentPlayers.size()) );
				
				// Attack the player
				switch (playerToAttack) {
					case up:
						battlefield.dealDamage( unit, unit.x, unit.y - 1);
						break;
					case right:
						battlefield.dealDamage(unit,  unit.x + 1, unit.y);
						break;
					case down:
						battlefield.dealDamage( unit, unit.x, unit.y + 1);
						break;
					case left:
						battlefield.dealDamage( unit, unit.x - 1, unit.y);
						break;
				}
				
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}

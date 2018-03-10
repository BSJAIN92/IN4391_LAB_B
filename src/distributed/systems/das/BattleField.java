package distributed.systems.das;

import java.util.ArrayList;

import distributed.systems.das.units.Unit;

public class BattleField {
	/* The array of units */
	private Unit[][] map;

	/* The static singleton */
	private static BattleField battlefield;
	
	public final static int MAP_WIDTH = 25;
	public final static int MAP_HEIGHT = 25;
	private ArrayList <Unit> units; 
	/* The last id that was assigned to an unit. This variable is used to
	 * enforce that each unit has its own unique id.
	 */
	private int lastUnitID = 0;
	
	private BattleField() {
		map = new Unit[MAP_WIDTH][MAP_HEIGHT];
		units = new ArrayList<>();
	}
	
	public static BattleField getBattleField() {
		if(battlefield == null) {
			new BattleField();
		}
		return battlefield;
	}
	
	/**
	 * Returns a new unique unit ID.
	 * @return int: a new unique unit ID.
	 */
	public synchronized int getNewUnitID() {
		return ++lastUnitID;
	}
}

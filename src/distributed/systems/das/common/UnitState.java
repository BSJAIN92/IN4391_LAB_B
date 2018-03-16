package distributed.systems.das.common;

public class UnitState{
	
	public final int MIN_HITPOINTS = 20;
	public final int MAX_HITPOINTS = 10;
	public final int MIN_ATTACKPOINTS = 1;
	public final int MAX_ATTACKPOINTS = 10;
	
	// Position of the unit
	public int x, y;

	// Health
	public int hitPoints;

	// Attack points
	public int attackPoints;

	// Identifier of the unit
	public int unitID;
	public UnitType unitType;
	
	//Name of the helper server a unit is associated with
	String helperServerAddress;	
	
	public UnitState(int x, int y, int unitId, UnitType unitType, String helperServerAddress)
	{
		this.x = x;
		this.y = y;
		this.unitID = unitId;
		this.unitType = unitType;
		this.helperServerAddress = helperServerAddress;
		this.hitPoints = (int)(Math.random() * (MAX_HITPOINTS - MIN_HITPOINTS) + MIN_HITPOINTS);
		this.attackPoints = (int)(Math.random() * (MAX_ATTACKPOINTS - MIN_ATTACKPOINTS) + MIN_ATTACKPOINTS);
	}
	
	/**
	 * Set the position of the unit.
	 * @param x is the new x coordinate
	 * @param y is the new y coordinate
	 */
	public synchronized void setPosition(int x, int y) {
		this.x = x;
		this.y = y;
	}
	
	/**
	 * Adjust the hitpoints to a certain level. 
	 * Useful for healing or dying purposes.
	 * @param modifier is to be added to the
	 * hitpoint count.
	 */
	public synchronized int adjustHitPoints(int modifier) {
		hitPoints += modifier;
		if (hitPoints > MAX_HITPOINTS)
			hitPoints = MAX_HITPOINTS;
		
		return hitPoints;		
	}	
}


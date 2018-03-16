package distributed.systems.das.common;

/**
 * Different request types for the
 * nodes to send to the server.
 * 
 * @author Pieter Anemaet, Boaz Pat-El
 */
public enum MessageType {
	spawnUnit, getUnit, moveUnit, putUnit, removeUnit, getType, dealDamage, healDamage, setup, sync
}
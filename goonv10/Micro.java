package goonv10;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
@FunctionalInterface
public interface Micro {
    public void micro(Direction d, MapLocation dest) throws GameActionException;
}
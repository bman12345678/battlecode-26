package goonv5;

import battlecode.common.Direction;
import battlecode.common.MapLocation;
@FunctionalInterface
public interface Micro {
    public void micro(Direction d, MapLocation dest) throws Exception;
}
package goon;

import battlecode.common.*;
@FunctionalInterface
public interface Micro {
    public void micro(Direction d, MapLocation dest) throws Exception;
}
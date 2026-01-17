package goonv4;

import battlecode.common.*;

import java.util.Random;

//store global stuff that you dont want to pass around
//and doesnt fit anywhere else
public class G {
    public static RobotController rc;
    public static Random rng;
    public static MapLocation mapCenter;
    public static Team opponentTeam;


    public static final Direction[] DIRECTIONS = {
            Direction.SOUTHWEST,
            Direction.SOUTH,
            Direction.SOUTHEAST,
            Direction.WEST,
            Direction.EAST,
            Direction.NORTHWEST,
            Direction.NORTH,
            Direction.NORTHEAST,
    };

    public static final Direction[] ALL_DIRECTIONS = {
            Direction.SOUTHWEST,
            Direction.SOUTH,
            Direction.SOUTHEAST,
            Direction.WEST,
            Direction.EAST,
            Direction.NORTHWEST,
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.CENTER,
    };

    // stuff that changes
    public static StringBuilder indicatorString;
    public static MapLocation me;
    public static RobotInfo[] allyRobots;
    public static RobotInfo[] opponentRobots;
    public static MapInfo[] infos;
    public static MapInfo[][] mp = null;
    public static MapInfo[] mines = {};
}
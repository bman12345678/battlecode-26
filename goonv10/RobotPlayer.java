package goonv10;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

import java.util.Random;

public class RobotPlayer {
    public static void updateInfo() throws GameActionException {
        G.me = G.rc.getLocation();
        G.allyRobots = G.rc.senseNearbyRobots(-1, G.rc.getTeam());
        G.opponentRobots = G.rc.senseNearbyRobots(-1, POI.opponentTeam);
        G.infos = G.rc.senseNearbyMapInfos();
        //aPOI.updateInfo();
    }

    public static void run(RobotController rc) throws Exception {

        try {
            G.rc = rc;
            G.rng = new Random(G.rc.getID() + 2026);
            G.mapCenter = new MapLocation(G.rc.getMapWidth() / 2, G.rc.getMapHeight() / 2);
            G.opponentTeam = G.rc.getTeam().opponent();
            updateInfo();
            switch (G.rc.getType()) {
                case RAT_KING:
                case BABY_RAT:
                    Robot.init();
                    break;
            }
            while (true) {
                try {
                    G.indicatorString = new StringBuilder();
                    updateInfo();
                    switch (G.rc.getType()) {
                        case RAT_KING:
                        case BABY_RAT:

                            Robot.run();
                            break;
                    }
                    G.rc.setIndicatorString(G.indicatorString.toString());
                    Clock.yield();
                } catch (GameActionException e) {
                    System.out.println("Unexpected GameActionException");
                    e.printStackTrace();
                } catch (Exception e) {
                    System.out.println("Unexpected Exception");
                    e.printStackTrace();
                }
            }
        } catch (GameActionException e) {
            System.out.println("Unexpected GameActionException");
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("Unexpected Exception");
            e.printStackTrace();
        }
    }
}
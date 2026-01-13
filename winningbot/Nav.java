package winningbot;

import battlecode.common.*;

/**
 * Robust Bug Pathfinding (SAFE VERSION).
 * Guarded against GameActionException and hard crashes.
 */
public class Nav {

    private static final Direction[] DIRS = {
            Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
            Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
    };

    private static MapLocation currentTarget = null;
    private static MapLocation lastObstacle = null;
    private static boolean rotateRight = true;
    private static int minDistToTarget = 999999;
    private static int noProgressCount = 0;

    private static int[][] visitedState;
    private static int bugSessionId = 0;
    private static boolean initialized = false;

    private static int expcnt = 0;
    private static Direction expd;
    private static Direction ogd;
    private static MapLocation expt;

    public static int mx = 0;

    private static void init(RobotController rc) {
        if (!initialized) {
            visitedState = new int[rc.getMapWidth()][rc.getMapHeight()];
            initialized = true;
        }
    }

    /* -------------------- BASIC MOVEMENT -------------------- */

    public static void tryMoveAway(RobotController rc, MapLocation loc) throws GameActionException {
        if (!rc.isMovementReady() || loc == null) return;

        Direction d = rc.getLocation().directionTo(loc);
        if (d == Direction.CENTER) return;

        if (rc.canMove(d)) rc.move(d);
        else if (rc.canMove(d.rotateRight())) rc.move(d.rotateRight());
        else if (rc.canMove(d.rotateLeft())) rc.move(d.rotateLeft());
    }

    public static void move(RobotController rc, MapLocation target) throws GameActionException {
        if (!rc.isMovementReady() || target == null) return;
        init(rc);

        MapLocation myLoc = rc.getLocation();
        int dist = myLoc.distanceSquaredTo(target);

        // target reset logic
        if (currentTarget == null || !currentTarget.equals(target) || dist < minDistToTarget) {
            currentTarget = target;
            minDistToTarget = dist;
            lastObstacle = null;
            bugSessionId++;
            noProgressCount = 0;
        }

        // turn toward target
        Direction toTarget = myLoc.directionTo(target);
        if (toTarget != Direction.CENTER && rc.isTurningReady() && rc.canTurn()) {
            rc.turn(toTarget);
        }

        // greedy move
        if (lastObstacle == null) {
            if (canMove(rc, toTarget)) {
                rc.move(toTarget);
                return;
            }
            else {
                if (rc.canRemoveDirt(rc.getLocation().add(toTarget))) rc.removeDirt(rc.getLocation().add(toTarget));
                if (rc.isActionReady() && canMove(rc, toTarget)) {
                    rc.move(toTarget);
                    return;
                }
            }
            lastObstacle = myLoc.add(toTarget);
        }

        // wall-follow
        if (lastObstacle != null) {
            if (!isLooping(rc, myLoc, toTarget, rotateRight) && canMove(rc, toTarget)) {
                lastObstacle = null;
                rc.move(toTarget);
                return;
            }

            Direction dir = myLoc.directionTo(lastObstacle);
            for (int i = 0; i < 8; i++) {
                dir = rotateRight ? dir.rotateLeft() : dir.rotateRight();
                if (canMove(rc, dir)) {
                    if (!isLooping(rc, myLoc, dir, rotateRight)) {
                        rc.move(dir);
                        if (myLoc.add(dir).distanceSquaredTo(target) < minDistToTarget) {
                            lastObstacle = null;
                        }
                        return;
                    } else {
                        rotateRight = !rotateRight;
                        bugSessionId++;
                        lastObstacle = null;
                        randomWalk(rc);
                        return;
                    }
                } else {
                    MapLocation block = myLoc.add(dir);
                    if (rc.onTheMap(block)) lastObstacle = block;
                }
            }

            noProgressCount++;
            if (noProgressCount > 5) {
                randomWalk(rc);
                lastObstacle = null;
                noProgressCount = 0;
            }
        }
    }

    /* -------------------- RANDOM MOVEMENT -------------------- */

    public static void randomWalk(RobotController rc) throws GameActionException {
        if (!rc.isMovementReady()) return;

        for (int i = 0; i < 8; i++) {
            Direction d = DIRS[Globals.randInt(8)];
            if (rc.canMove(d)) {
                rc.move(d);
                return;
            }
        }
    }

    /* -------------------- LOOP DETECTION -------------------- */

    private static boolean isLooping(RobotController rc, MapLocation loc, Direction dir, boolean rotR) {
        if (!rc.onTheMap(loc)) return false;

        int stateHash = (bugSessionId << 4) | (dir.ordinal() << 1) | (rotR ? 1 : 0);
        int x = loc.x;
        int y = loc.y;

        if (visitedState[x][y] == stateHash) return true;
        visitedState[x][y] = stateHash;
        return false;
    }

    /* -------------------- UTIL -------------------- */

    public static boolean canMove(RobotController rc, Direction d) {
        if (d == Direction.CENTER || !rc.canMove(d)) return false;
        MapLocation next = rc.getLocation().add(d);
        return rc.onTheMap(next);
    }

    public static Direction dirTo8(MapLocation a, MapLocation b) {
        Direction d = a.directionTo(b);
        return (d == Direction.CENTER) ? DIRS[Globals.randInt(8)] : d;
    }

    /* -------------------- EXPLORATION -------------------- */

    public static void explore(RobotController rc) throws GameActionException {
        if (!rc.isMovementReady()) return;

        if (expcnt == 0) {
            expd = DIRS[Globals.randInt(8)];
            if (expd == Direction.CENTER) expd = Direction.NORTH;

            if (rc.isTurningReady() && rc.canTurn()) rc.turn(expd);

            expcnt = 8;
            expt = rc.getLocation();
            for (int i = 0; i < 5; i++) expt = expt.add(expd);
        } else {
            if (expt != null) move(rc, expt);
            expcnt--;
            mx = 0;
        }
    }

    /* -------------------- FLEE -------------------- */

    public static boolean fleeFrom(RobotController rc, MapLocation threat) throws GameActionException {
        if (!rc.isMovementReady() || threat == null) return false;

        MapLocation me = rc.getLocation();
        Direction away = me.directionTo(threat).opposite();

        Direction[] tries = {
                away,
                away.rotateLeft(),
                away.rotateRight(),
                away.rotateLeft().rotateLeft(),
                away.rotateRight().rotateRight()
        };

        for (Direction d : tries) {
            if (d != Direction.CENTER && rc.canMove(d)) {
                rc.move(d);
                return true;
            }
        }
        return false;
    }
}

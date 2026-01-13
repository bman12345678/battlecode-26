package winningbot;

import battlecode.common.*;

public final class BabyLogic {

    private BabyLogic() {}

    private static final Direction[] DIRS_8 = {
            Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
            Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
    };

    private static final int ROLE_MINER = 0;
    private static final int ROLE_MINER2 = 1;
    private static final int ROLE_TRAPPER = 2;
    private static final int ROLE_FIGHTER = 3;
    private static final int ROLE_SCOUT = 4;

    private static MapLocation cheeseMine;
    private static MapLocation chesy;
    private static MapLocation enemyKing;
    private static int tmpp = 1;

    /* ===================== MAIN ===================== */

    public static void babygo(RobotController rc) throws GameActionException {
        if (rc.isBeingCarried() || rc.isBeingThrown()) return;

        int role = rc.getID() % 5;

        /* ---- throw carried rat (SAFE) ---- */
        if (rc.getCarrying() != null && rc.isActionReady() && rc.canThrowRat()) {
            rc.throwRat();
            return;
        }

        RobotInfo[] robots = rc.senseNearbyRobots();
        for (Direction e : DIRS_8) if (rc.canPickUpCheese(rc.getLocation().add(e))) rc.pickUpCheese(rc.getLocation().add(e));
        /* ---- detect threats / enemy king ---- */
        boolean catNear = false;
        MapLocation threatLoc = rc.getLocation();
        for (RobotInfo r : robots) {
            if (r.type == UnitType.CAT) {
                catNear = true;
                threatLoc = r.location;
            }
            if (r.type == UnitType.RAT_KING && r.team != rc.getTeam()) {
                enemyKing = r.location;
            }
        }

        /* ---- combat first ---- */
        Micro.tryAttack(rc, robots);

        /* ---- flee cats (movement-safe) ---- */
        if (catNear && rc.isMovementReady()) {
            Nav.fleeFrom(rc, threatLoc);
            return;
        }

        /* ---- update cheese mine info ---- */
        MapInfo[] mps = rc.senseNearbyMapInfos();
        MapLocation myLoc = rc.getLocation();
        MapLocation closestCheese = null;
        int bestD2 = 999999;

        for (MapInfo e : mps) {
            if (e.getCheeseAmount() > 0) {
                MapLocation l = e.getMapLocation();
                int d2 = myLoc.distanceSquaredTo(l);
                if (d2 < bestD2) {
                    bestD2 = d2;
                    closestCheese = l;
                }
            }
        }

// act on closest cheese
        if (closestCheese != null) {
            // pick up if possible
            if (rc.canPickUpCheese(closestCheese)) {
                rc.pickUpCheese(closestCheese);
            }
            // otherwise move toward it
            else if (rc.isMovementReady()) {
                Nav.move(rc, closestCheese);
            }
        }

        /* ---- deliver cheese if requested ---- */
        if (rc.readSharedArray(10) != 1 && rc.getRawCheese() >= 40) {
            MapLocation king = readKing(rc);
            if (king != null) {
                if (rc.isActionReady() && rc.canTransferCheese(king, 20)) {
                    rc.transferCheese(king, 20);
                    return;
                }
                if (rc.isMovementReady()) {
                    Nav.move(rc, king);
                    return;
                }
            }
        }

        /* ---- exploration mode ---- */
        if (rc.readSharedArray(10) != 1) {
            if (rc.isMovementReady()) Nav.explore(rc);
            return;
        }

        /* ---- attack mode ---- */
        MapLocation target = readAttackTarget(rc);
        if (target == null) return;

        if (tmpp == 1 && rc.isTurningReady() && rc.canTurn()) {
            rc.turn(rc.getLocation().directionTo(target));
            tmpp = 0;
        }

        if (enemyKing != null) {
            if (!rc.canAttack(enemyKing) && rc.isMovementReady()) {
                if (rc.getRoundNum() % 3 == 0 && rc.isTurningReady())
                    rc.turn(rc.getLocation().directionTo(enemyKing));
                Nav.move(rc, enemyKing);
            }
        } else {
            if (rc.isMovementReady()) Nav.move(rc, target);
        }
    }

    /* ===================== CHEESE FARM ===================== */

    private static void chsF(RobotController rc) throws GameActionException {
        if (rc.getRawCheese() >= 20 + Math.max(rc.getRoundNum(), 40)) {
            MapLocation king = readKing(rc);
            if (king == null) return;

            if (rc.isActionReady() && rc.canTransferCheese(king, 20)) {
                rc.transferCheese(king, 20);
                return;
            }
            if (rc.isMovementReady()) Nav.move(rc, king);
            return;
        }

        if (chesy != null) {
            if (rc.isActionReady() && rc.canPickUpCheese(chesy)) {
                rc.pickUpCheese(chesy);
                return;
            }
            if (rc.isMovementReady()) Nav.move(rc, chesy);
            return;
        }

        boolean foundMine = false;
        boolean foundCheese = false;
        for (MapInfo e : rc.senseNearbyMapInfos()) {
            if (cheeseMine != null && e.getMapLocation().equals(cheeseMine))
                foundMine = true;
            if (e.getCheeseAmount() > 0) {
                foundCheese = true;
                chesy = e.getMapLocation();
            }
        }

        if (foundCheese && rc.isMovementReady()) {
            Nav.move(rc, chesy);
        } else if (!foundMine && cheeseMine != null && rc.isMovementReady()) {
            Nav.move(rc, cheeseMine);
        }
    }

    /* ===================== HELPERS ===================== */

    private static MapLocation readKing(RobotController rc) throws GameActionException {
        int x = rc.readSharedArray(0);
        int y = rc.readSharedArray(1);
        if (x < 0 || y < 0) return null;
        return new MapLocation(x, y);
    }

    private static MapLocation readAttackTarget(RobotController rc) throws GameActionException {
        int x = rc.readSharedArray(3);
        int y = rc.readSharedArray(4);
        if (x < 0 || y < 0) return null;
        return mirror(rc, new MapLocation(x, y));
    }

    public static MapLocation mirror(RobotController rc, MapLocation loc) throws GameActionException {
        if (loc == null) return null;
        return new MapLocation(
                rc.getMapWidth() - 1 - loc.x,
                rc.getMapHeight() - 1 - loc.y
        );
    }
}

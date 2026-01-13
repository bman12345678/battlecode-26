package winningbot;

import battlecode.common.*;

public final class Micro {

    private Micro() {
    }

    /** Attempt to bite a high-value target (enemy king > enemy rat > cat). */
    public static boolean tryAttack(RobotController rc, RobotInfo[] sensed) throws GameActionException {
        if (!rc.isActionReady())
            return false;

        boolean coop = rc.isCooperation();
        Team my = rc.getTeam();

        RobotInfo bestKing = null;
        RobotInfo bestEnemy = null;
        RobotInfo bestCat = null;

        int bestKingDist = 99999;
        int bestEnemyDist = 99999;
        int bestCatDist = 99999;
        int cntt = 0;
        MapLocation cur = rc.getLocation();
        for (int i = 0; i < sensed.length; i++) {
            RobotInfo ri = sensed[i];
            if (ri == null)
                continue;

            UnitType t = ri.type;
            MapLocation loc = ri.location;

            int d2 = cur.distanceSquaredTo(loc);

            if (t == UnitType.CAT) {
                if (d2 < bestCatDist) {
                    bestCatDist = d2;
                    bestCat = ri;
                }
                continue;
            }
            if (t==UnitType.BABY_RAT) cntt++;
            if (ri.team == my)
                continue; // ally
            // Enemy rats are only attackable in backstab OR if we intentionally trigger
            // backstab.
          //  if (coop) continue;
            if (t == UnitType.RAT_KING) {
                if (d2 < bestKingDist) {
                    bestKingDist = d2;
                    bestKing = ri;
                }
            } else {
                if (d2 < bestEnemyDist) {
                    bestEnemyDist = d2;
                    bestEnemy = ri;
                }
            }
        }

        // Priority: enemy king, then enemy rat, then cat (even in backstab, cats still
        // matter).
        if (bestKing != null)
            return bite(rc, bestKing, true);
        if (bestEnemy != null)
            return bite(rc, bestEnemy, false);
        if (bestCat != null)
            return (cntt > 2 ? bite(rc, bestCat, true) : kiteCat(rc, bestCat));

        return false;
    }

    private static boolean bite(RobotController rc, RobotInfo target, boolean spendCheeseOk)
            throws GameActionException {
        if (target == null || !rc.isActionReady())
            return false;

        MapLocation atkLoc = pickAttackSquare(rc, target);
        if (atkLoc == null)
            return false;

        int raw = rc.getRawCheese();
        // Spending a small fixed amount is usually efficient for DPS contests.
        int spend = (spendCheeseOk && raw >= 4) ? 3 : 0;
        if (target.type == UnitType.BABY_RAT) {
            if (rc.canCarryRat(atkLoc)) rc.carryRat(atkLoc);
            else {
                if (spend > 0) {
                    if (rc.canAttack(atkLoc, spend)) {
                        rc.attack(atkLoc, spend);
                        return true;
                    }
                }
                if (rc.canAttack(atkLoc)) {
                    rc.attack(atkLoc);
                    return true;
                }
            }
            if (rc.getCarrying()!=null) if (rc.canThrowRat() && rc.isActionReady()) rc.throwRat();
            if (rc.canPlaceRatTrap(rc.getLocation().add(rc.getLocation().directionTo(atkLoc))))
                    if (rc.getGlobalCheese()>1400)rc.placeRatTrap(rc.getLocation().add(rc.getLocation().directionTo(atkLoc)));
            return false;
        }
        else {
            if (spend > 0) {
                if (rc.canAttack(atkLoc, spend)) {
                    rc.attack(atkLoc, spend);
                    return true;
                }
            }
            if (rc.canAttack(atkLoc)) {
                rc.attack(atkLoc);
                return true;
            }
            MapLocation trapLoc = rc.getLocation().add(rc.getLocation().directionTo(atkLoc));
            if (rc.onTheMap(trapLoc) && rc.isActionReady()
                    && rc.getGlobalCheese() > 1400
                    && rc.canPlaceRatTrap(trapLoc)) {
                rc.placeRatTrap(trapLoc);
            }
            return false;
        }
    }

    /** For rat king (3x3), select an occupied square that is attackable. */
    private static MapLocation pickAttackSquare(RobotController rc, RobotInfo target) throws GameActionException {
        if (target.type != UnitType.RAT_KING)
            return target.location;

        MapLocation cur = rc.getLocation();
        MapLocation[] parts = UnitType.RAT_KING.getAllTypeLocations(target.location);

        MapLocation best = null;
        int bestD2 = 999999;
        for (int i = 0; i < parts.length; i++) {
            MapLocation p = parts[i];
            int d2 = cur.distanceSquaredTo(p);
            if (d2 < bestD2 && rc.canAttack(p)) {
                bestD2 = d2;
                best = p;
            }
        }
        if (best == null) {
            for (int i = 0; i < parts.length; i++) {
                MapLocation p = parts[i];
                int d2 = cur.distanceSquaredTo(p);
                if (d2 < bestD2) {
                    bestD2 = d2;
                    best = p;
                }
            }
        }
        return best;
    }

    /**
     * If threatened by a nearby cat, step away.
     *
     * @return
     */
    public static boolean kiteCat(RobotController rc, RobotInfo cat) throws GameActionException {
        if (cat == null || !rc.isMovementReady())
            return false;
        MapLocation cur = rc.getLocation();
        if (rc.getGlobalCheese()>800) {
            if (rc.isActionReady() && rc.canPlaceCatTrap(cur.add(cur.directionTo(cat.location)))) rc.placeCatTrap(cur.add(cur.directionTo(cat.location)));
        }
        int d2 = cur.distanceSquaredTo(cat.location);
        // Too close -> back off.
        if (d2 <= 2) Nav.tryMoveAway(rc, cat.location);
        return false;
    }

    /**
     * Rat king utility: carry adjacent enemy baby rat and throw away (disrupt /
     * isolate).
     */
    public static void kingCarryThrow(RobotController rc, RobotInfo[] sensed) throws GameActionException {
        if (rc.getType() != UnitType.RAT_KING)
            return;
        if (!rc.isActionReady())
            return;

        // If already carrying, throw it away from our base towards enemy.
        RobotInfo carried = rc.getCarrying();
        if (carried != null) {
            Direction dir = (Globals.ENEMY_BASE_GUESS != null) ? rc.getLocation().directionTo(Globals.ENEMY_BASE_GUESS)
                    : Direction.EAST;
            if (dir == Direction.CENTER)
                dir = Direction.EAST;
            if (rc.isTurningReady() && rc.canTurn())
                rc.turn(dir);
            if (rc.canThrowRat()) {
                rc.throwRat();
                return;
            }
            // If throw isn't possible, try to drop in/near the same direction.
            Direction[] drops = new Direction[] {
                    dir,
                    dir.rotateLeft(),
                    dir.rotateRight(),
                    dir.opposite(),
                    dir.rotateLeft().rotateLeft(),
                    dir.rotateRight().rotateRight()
            };
            for (int i = 0; i < drops.length; i++) {
                Direction dd = drops[i];
                if (dd == Direction.CENTER)
                    continue;
                if (rc.canDropRat(dd)) {
                    rc.dropRat(dd);
                    return;
                }
            }
        }

        // Otherwise attempt to pick up an adjacent enemy baby rat.
        Team my = rc.getTeam();
        MapLocation cur = rc.getLocation();
        RobotInfo best = null;
        int bestD2 = 999999;

        for (int i = 0; i < sensed.length; i++) {
            RobotInfo ri = sensed[i];
            if (ri == null)
                continue;
            if (ri.team == my)
                continue;
            if (ri.type != UnitType.BABY_RAT)
                continue;

            int d2 = cur.distanceSquaredTo(ri.location);
            if (d2 <= 2 && d2 < bestD2) { // adjacent-ish
                bestD2 = d2;
                best = ri;
            }
        }

        if (best != null && rc.canCarryRat(best.location)) {
            rc.carryRat(best.location);
        }
    }
}
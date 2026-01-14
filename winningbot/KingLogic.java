package winningbot;

import battlecode.common.*;

import java.awt.*;
import java.util.ArrayDeque;

/**
 * Static King logic adapted to Battlecode 2026 API.
 *
 * Usage:
 *   KingLogic.init(rc);
 *   KingLogic.runOneTurn();   // call each king turn
 *
 * NOTES:
 * - Replace BABY_TYPE with your project's actual baby robot UnitType constant if different.
 * - Replace Comms.* calls if your communications API differs.
 */
public final class KingLogic {

    // spawn counts
    private static final int PHASE1_MIN = 18;
    private static final int PHASE1_MAX = 24;
    private static final int PHASE2_MIN = 5;
    private static final int PHASE2_MAX = 10;
    private static final int FINAL_SPAWN_MIN = 20;
    private static final int FINAL_SPAWN_MAX = 25;

    // relocation distance (chebyshev)
    private static final int RELOCATE_MIN = 8;
    private static final int RELOCATE_MAX = 14;

    // cheese heuristics
    private static final int CHEESE_STABLE_TURNS = 40;

    // danger distances (squared)
    private static final int CAT_ESCAPE_DIST_SQ = 25; // 5 tiles
    private static final int RAT_ESCAPE_DIST_SQ = 9;  // 3 tiles

    // TODO: set this to the baby rat UnitType used in your codebase
    private static final UnitType BABY_TYPE = UnitType.BABY_RAT; // <-- EDIT if needed

    // runtime state (all static per your request)
    private static RobotController rc;
    private static MapLocation spawnLocation;
    private static boolean relocated = false;
    private static MapLocation relocatedLocation;
    private static int phase = 0; // 0 = initial, 1 = relocated mid-spawn, 2 = steady/cheese mode

    private static int cheeseSeenCounter = 0;
    private static int cheeseHarvestedCounter = 0;
    private static int turnsSinceLastCheese = 0;
    private static int spawned = 0;

    // queue of incoming reported enemy locations (local cache)
    private static final ArrayDeque<MapLocation> reportedEnemyKings = new ArrayDeque<>();

    private KingLogic() {} // static-only helper

    /** Initialize KingLogic with RobotController. Call once at king construction. */
    public static void init(RobotController controller) {
        rc = controller;
       if (spawnLocation == null) spawnLocation = rc.getLocation();
        relocated = false;
        relocatedLocation = spawnLocation;
        phase = 0;
        cheeseSeenCounter = 0;
        cheeseHarvestedCounter = 0;
        turnsSinceLastCheese = 0;
        reportedEnemyKings.clear();
    }

    /** Main per-turn function; call from your King robot's loop. */
    public static void kinggo(RobotController controller) throws GameActionException {
        rc = controller;
        if (spawnLocation == null) spawnLocation = rc.getLocation();
        if (rc == null) throw new IllegalStateException("KingLogic.init(rc) not called");
        // Always process reports (non-action, best-effort)
        receiveReports();
        rc.writeSharedArray(0, rc.getLocation().x);
        rc.writeSharedArray(1, rc.getLocation().y);
        if (spawnLocation != null) rc.writeSharedArray(3, spawnLocation.x);
        if (spawnLocation != null) rc.writeSharedArray(4, spawnLocation.y);
        // If we cannot act at all, return

        // Movement priority: escape threats first (movement)

        if (!escapeIfThreatened()) {
            rc.writeSharedArray(5,0); rc.writeSharedArray(6,0);
        }

        // Action: try to box self (placing dirt) if sensible
        boxSelf(); // best-effort; uses canPlaceDirt/placeDirt

        // Spawning decisions: ensure we're active before spawning
        if (rc.isActionReady()) {
            if (phase == 0) {
                rc.writeSharedArray(10, 1);
                int toSpawn = randInRange(PHASE1_MIN, PHASE1_MAX);
                spawnBabies(toSpawn);
                // relocate after initial spawn
                relocateAwayFromSpawn();
                if (spawned >= toSpawn) phase = 1;
                return;
            } else if (phase == 1 && rc.getRoundNum() <= 50) {
                spawned = 0;
                if (rc.getRoundNum() % 5 == 0 && rc.getCurrentRatCost()<120) spawnBabies(3);
                int tmp = 0;
                rc.writeSharedArray(10, 0);
                if (rc.getLocation().x > rc.getMapWidth() / 2) tmp = rc.getMapWidth();
                MapLocation tgt = new MapLocation(tmp, rc.getLocation().y);
                Nav.move(rc, tgt);
               // spawned = 0;
                // remain in phase 1 until we detect steady cheese supply
                return;
            } else {
                spawned = 0;
                rc.writeSharedArray(10, 0);
                if ((rc.getGlobalCheese() > 600 && rc.getCurrentRatCost() < 50) || (rc.getGlobalCheese()>1200&&rc.getCurrentRatCost()<250)) {
                    spawnBabies(3);
                }
//                if (hasSteadyCheeseSupply()) {
//                    int toSpawn = randInRange(FINAL_SPAWN_MIN, FINAL_SPAWN_MAX);
//                    spawnBabies(toSpawn);
//                    // if we know enemy king broadcast rush
//                    MapLocation enemyKing = Comms.readEnemyKing(rc);
//                    if (enemyKing != null) {
//                        Comms.broadcastRush(rc, enemyKing);
//                    }
//                    phase = 2;
//                    return;
//                }
            }
        }

        // If a reported cheese mine exists, try to move toward it and box around it
//        MapLocation cheeseMine = Comms.readCheeseMine(rc);
////        if (cheeseMine != null && rc.isActionReady()) {
////            greedyMoveToward(cheeseMine);
////            if (rc.getLocation().equals(cheeseMine)) {
////                boxSelf();
////            }
////            return;
////        }

        // Default small defensive spawns at relocated base occasionally
//        if (rc.isActionReady() && relocated && rc.getLocation().equals(relocatedLocation) && phase == 1) {
//            if (randInRange(0, 5) == 0) { // 1/6 chance
//                attemptSpawnOne();
//            }
//        }
    }

    // -------------------------
    // Communications & reports
    // -------------------------
    private static void receiveReports() {
        // Best-effort reads from Comms (adjust to your Comms API)
        try {
            MapLocation reported = Comms.readSqueakedEnemyKing(rc);
            if (reported != null) {
                Comms.writeEnemyKing(rc, reported);
                reportedEnemyKings.add(reported);
            }
            MapLocation cheese = Comms.readSqueakedCheeseMine(rc);
            if (cheese != null) {
                Comms.writeCheeseMine(rc, cheese);
            }
        } catch (Exception e) {
            // swallow; best-effort
        }
    }

    // -------------------------
    // Spawning helpers
    // -------------------------
    private static void spawnBabies(int count) throws GameActionException {
        if (!rc.isActionReady()) return;
        Direction[] dlist = new Direction[] {
                Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
                Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
        };

        for (Direction d : dlist) {
            if (spawned >= count) break;
            if (rc.canBuildRat(rc.getLocation().add(d).add(d))) {

                rc.buildRat(rc.getLocation().add(d).add(d));
                spawned++;
                // After spawn the robot may become inactive for the turn (depending on API), so re-check
                if (!rc.isActionReady()) break;
            }
        }
    }

    private static void attemptSpawnOne() throws GameActionException {
        for (Direction d : Direction.values()) {
            if (d == Direction.CENTER) continue;
            if (rc.canBuildRat(rc.getLocation().add(d).add(d))) {
                rc.buildRat(rc.getLocation().add(d).add(d));
                break;
            }
        }
    }

    // -------------------------
    // Relocation
    // -------------------------
    // Picks a random relocation target within RELOCATE_MIN..MAX chebyshev distance and attempts a few greedy moves.
    private static void relocateAwayFromSpawn() throws GameActionException {
        if (relocated) return;
        MapLocation here = rc.getLocation();
        for (int attempt = 0; attempt < 20; attempt++) {
            int dx = randInRange(RELOCATE_MIN, RELOCATE_MAX);
            int dy = randInRange(RELOCATE_MIN, RELOCATE_MAX);
            if (randInRange(0,1) == 0) dx = -dx;
            if (randInRange(0,1) == 0) dy = -dy;
            MapLocation candidate = new MapLocation(here.x + dx, here.y + dy);
            if (!rc.onTheMap(candidate)) continue;
            // do a short greedy approach (one move per turn will be used across multiple turns)
            if (rc.canMove(here.directionTo(candidate))) {
                rc.move(here.directionTo(candidate));
            } else {
                // try to skirt a bit by testing nearby directions this single turn
                for (Direction dd : Direction.values()) {
                    if (dd == Direction.CENTER) continue;
                    if (rc.canMove(dd)) {
                        MapLocation nxt = rc.getLocation().add(dd);
                        if (nxt.distanceSquaredTo(candidate) < here.distanceSquaredTo(candidate)) {
                            rc.move(dd);
                            break;
                        }
                    }
                }
            }
            // set relocated location to wherever we are now
            relocatedLocation = rc.getLocation();
            relocated = true;
            // notify allies (best-effort)
            Comms.writeOurRelocatedBase(rc, relocatedLocation);
            return;
        }
        // fallback: set relocated to current loc
        relocatedLocation = here;
        relocated = true;
        Comms.writeOurRelocatedBase(rc, relocatedLocation);
    }

    // -------------------------
    // Threat handling
    // -------------------------
    private static boolean escapeIfThreatened() throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        RobotInfo[] nearby = rc.senseNearbyRobots(); // best-effort call; adapt if your API needs range
        if (nearby == null || nearby.length == 0) return false;

        MapLocation nearestCat = null;
        int nearestCatDist = Integer.MAX_VALUE;
        MapLocation nearestRat = null;
        int nearestRatDist = Integer.MAX_VALUE;

        for (RobotInfo r : nearby) {
            if (r.team == rc.getTeam()) continue;
            UnitType t = r.type;
            int d = myLoc.distanceSquaredTo(r.location);
            // Adjust these checks to your exact UnitType names if necessary
            if (t == UnitType.CAT && d < nearestCatDist) {
                nearestCat = r.location; nearestCatDist = d;
            } else if ((t == UnitType.BABY_RAT || t == UnitType.RAT_KING) && d < nearestRatDist) {
                // fallback categories for enemy rats; change to correct constants if your scaffold uses different names
                nearestRat = r.location; nearestRatDist = d;
            }
        }

        if (nearestCat != null && nearestCatDist <= CAT_ESCAPE_DIST_SQ) {
            rc.writeSharedArray(5, 1);
            return fleeFrom(nearestCat);
        }
        if (nearestRat != null && nearestRatDist <= RAT_ESCAPE_DIST_SQ) {
            rc.writeSharedArray(6, 1);
            return fleeFrom(nearestRat);
        }
        return false;
    }

    private static boolean fleeFrom(MapLocation threat) throws GameActionException {
        MapLocation me = rc.getLocation();
        Direction away = me.directionTo(threat).opposite();
        Direction[] tries = new Direction[] {
                away, away.rotateLeft(), away.rotateRight(),
                away.rotateLeft().rotateLeft(), away.rotateRight().rotateRight()
        };
        for (Direction d : tries) {
            if (d == Direction.CENTER) continue;
            if (rc.canMove(d)) {
                rc.move(d);
                return true;
            }
        }
        // fallback safe move
        for (Direction d : Direction.values()) {
            if (d == Direction.CENTER) continue;
            if (rc.canMove(d)) {
                rc.move(d);
                return true;
            }
        }
        return false;
    }

    // -------------------------
    // Box (place dirt) logic
    // -------------------------
    private static void boxSelf() throws GameActionException {
        // do nothing if we can't act (best-effort)
        if (!rc.isActionReady()) return;
        MapLocation me = rc.getLocation();
        Direction[] ring = new Direction[] {
                Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
                Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
        };

        for (Direction d : ring) {
            MapLocation adj = me.add(d);
            if (!rc.onTheMap(adj)) continue;
            // if we can sense and it's impassable or there's a robot, skip
            if (rc.canSenseLocation(adj) && (!rc.sensePassability(adj) || rc.senseRobotAtLocation(adj) != null)) continue;
            if (rc.canPlaceDirt(adj)) {
                rc.placeDirt(adj);
                // after placing dirt we might become inactive for remainder of turn
                if (!rc.isActionReady()) return;
            }
        }
    }

    // -------------------------
    // Move toward helpers
    // -------------------------
    private static void greedyMoveToward(MapLocation dest) throws GameActionException {
        if (!rc.isActionReady()) return;
        MapLocation here = rc.getLocation();
        Direction forward = here.directionTo(dest);
        if (forward == Direction.CENTER) return;

        Direction[] options = new Direction[] {
                forward, forward.rotateLeft(), forward.rotateRight(),
                forward.rotateLeft().rotateLeft(), forward.rotateRight().rotateRight()
        };
        for (Direction d : options) {
            if (d == Direction.CENTER) continue;
            if (rc.canMove(d)) {
                rc.move(d);
                return;
            }
        }
    }

    // -------------------------
    // Cheese heuristic
    // -------------------------
    private static boolean hasSteadyCheeseSupply() {
        return cheeseHarvestedCounter >= 5 && cheeseSeenCounter > 20 && turnsSinceLastCheese < CHEESE_STABLE_TURNS;
    }

    // -------------------------
    // Utility helpers
    // -------------------------
    private static int randInRange(int a, int bInclusive) {
        return Globals.randInt(bInclusive - a + 1) + a; // returns a..bInclusive
    }
}

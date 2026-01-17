package goonv5;

import battlecode.common.*;

import java.util.HashSet;
import java.util.Set;
import java.util.Random;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class RatKing {
    private static MapLocation targetCorner = null;
    private static boolean reachedCorner = false;
    private static final int TARGET_RAT_COST = 60;
    private static Set<MapLocation> mines = new HashSet<>();
    private static final double DIAG_WEIGHT = 1.6; // how much more we prefer diagonal directions


    public static void run() throws Exception {
        // Initialize target corner on first run
        if (targetCorner == null) {
            targetCorner = getClosestCorner();
        }

        // Write king location to shared array every round
        writeLocationToSharedArray();

        // Check for nearby cats
        RobotInfo[] nearbyEnemies = G.rc.senseNearbyRobots(-1);
        RobotInfo closestCat = null;

        int cnt = 0;

        for (RobotInfo enemy : nearbyEnemies) {

            if (enemy.getTeam() != G.rc.getTeam()) {
                cnt += 1;
                if (closestCat == null ||
                        G.me.distanceSquaredTo(enemy.location) < G.me.distanceSquaredTo(closestCat.location)) {
                    closestCat = enemy;
                }

                if(enemy.team == G.opponentTeam) {
                    G.rc.writeSharedArray(63, 1);
                }
            }
        }

        if(cnt == 0) {
            G.rc.writeSharedArray(63, 0);
        }

        // If we see a cat, place dirt to block it and run away

        if (closestCat != null) {
            handleCatThreat(closestCat);
        } else {
            // No cat visible, navigate to corner
            if (!reachedCorner) {
//                navigateToCorner();
            }
        }

        boolean seesMine = false;
        for(MapInfo m : G.rc.senseNearbyMapInfos(9)) {
            if(m.hasCheeseMine() || m.isWall()) {
                seesMine = true;
            }
        }

        if(!seesMine) {
            for (RobotInfo r : G.rc.senseNearbyRobots(-1)) {
                if (r.getType().isBabyRatType() && r.getTeam() != G.opponentTeam && r.getRawCheeseAmount() > 0) {
                    Direction d = G.me.directionTo(r.getLocation());
                    if (G.rc.canMove(d)) G.rc.move(d);
                }
            }
        }


        // Spawn rats until we have 20
        if(G.rc.getCurrentRatCost() <= 100) {

            if (G.rc.getGlobalCheese() - G.rc.getCurrentRatCost() >= 1650 && G.rc.getRoundNum()<75) {
                spawnRats();
            }
            else if (G.rc.getGlobalCheese() - G.rc.getCurrentRatCost() >= 1250) {
                spawnRats();
            }
            else if(G.rc.getCurrentRatCost() <= 40 && G.rc.getGlobalCheese() - G.rc.getCurrentRatCost() >= 1100) {
                spawnRats();
            }
            else if(G.rc.getCurrentRatCost() <= 30 && G.rc.getGlobalCheese() - G.rc.getCurrentRatCost() >= 800) {
                spawnRats();
            }
            else if(G.rc.getCurrentRatCost() <= 20 && G.rc.getGlobalCheese() - G.rc.getCurrentRatCost() >= 400) {
                spawnRats();
            }
            else if(G.rc.getCurrentRatCost() <= 10) {
                spawnRats();
            }
        }


    }

    private static void writeLocationToSharedArray() throws Exception {

        int i = G.rc.getID() % 21 ;
        G.rc.writeSharedArray(3 * i, 10 + ( G.rc.getRoundNum() % 1000));

        // Write X and Y to separate indices
        G.rc.writeSharedArray(3*i+1, G.me.x);
        G.rc.writeSharedArray(3*i+2, G.me.y);

    }


    private static MapLocation getClosestCorner() {
        int mapWidth = G.rc.getMapWidth();
        int mapHeight = G.rc.getMapHeight();

        // King is 3x3, so adjust corners to account for size
        // Center of king should be at least 1 away from edges
        MapLocation[] corners = {
                new MapLocation(1, 1),
                new MapLocation(1, mapHeight - 2),
                new MapLocation(mapWidth - 2, 1),
                new MapLocation(mapWidth - 2, mapHeight - 2)
        };

        MapLocation closest = corners[0];
        int minDist = G.me.distanceSquaredTo(corners[0]);

        for (int i = 1; i < corners.length; i++) {
            int dist = G.me.distanceSquaredTo(corners[i]);
            if (dist < minDist) {
                minDist = dist;
                closest = corners[i];
            }
        }

        return closest;
    }

    private static void navigateToCorner() throws Exception {
        // Check if we've reached the corner
        if (G.me.distanceSquaredTo(targetCorner) < 1) {
            reachedCorner = true;
            return;
        }

        // King is 3x3, so dig dirt in all locations it will occupy when moving
        Direction toCorner = G.me.directionTo(targetCorner);

        // Dig dirt in a 3x3 area in the direction we're moving
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                MapLocation digLoc = G.me.translate(dx, dy).add(toCorner);
                if (G.rc.canSenseLocation(digLoc) && G.rc.canRemoveDirt(digLoc)) {
                    G.rc.removeDirt(digLoc);
                    G.indicatorString.append("DIG ");
                }
            }
        }

        // Try to move towards corner using bugnav
        Motion.bugnavTowards(targetCorner);
    }

    private static void handleCatThreat(RobotInfo cat) throws Exception {
        // Cat is 2x2, place dirt to block it - need to block a wider area
        Direction toCat = G.me.directionTo(cat.location);


        // Place dirt in multiple locations to block the 2x2 ca

        for (int i = 0; i < 5; i++) {
            MapLocation blockLoc = G.me.add(toCat).add(toCat);
            if (i > 0) {
                blockLoc = blockLoc.add(G.DIRECTIONS[2*(i-1)]);


            }

            //System.out.println(blockLoc);
            //System.out.println(G.rc.canPlaceDirt(blockLoc));

            if (G.rc.canPlaceDirt(blockLoc)) {

                G.rc.placeDirt(blockLoc);
                G.indicatorString.append("BLOCK ");
            }
        }

        // Navigate away from the cat
        Motion.bugnavAway(cat.location);


        G.indicatorString.append("FLEE ");
    }

    private static void spawnRats() throws Exception {
        // King's location (G.me is the king map location in your code)
        MapLocation king = G.me;
        int mapW = G.rc.getMapWidth();
        int mapH = G.rc.getMapHeight();

        // compute how many steps are available in each direction (distance to border along that ray)
        double[] weights = new double[G.DIRECTIONS.length];
        for (int i = 0; i < G.DIRECTIONS.length; i++) {
            Direction dir = G.DIRECTIONS[i];
            MapLocation probe = king;
            int steps = 0;
            // count how many consecutive on-map tiles are reachable stepping in dir
            while (true) {
                MapLocation nxt = probe.add(dir);
                if (nxt.x < 0 || nxt.x >= mapW || nxt.y < 0 || nxt.y >= mapH) break;
                steps++;
                probe = nxt;
                // safety cap
                if (steps > Math.max(mapW, mapH)) break;
            }
            // boost diagonals so NE/NW/SE/SW are preferred more heavily
            boolean isDiag = (dir == Direction.NORTHEAST || dir == Direction.NORTHWEST
                    || dir == Direction.SOUTHEAST || dir == Direction.SOUTHWEST);
            double w = steps;
            if (isDiag) w *= DIAG_WEIGHT;
            // small floor so even very short dirs have tiny chance
            weights[i] = Math.max(0.1, w);
        }

        // Weighted-random pick (seeded by round & king pos so it's varied but reproducible)
        long seed = ((long)G.rc.getRoundNum() << 32) ^ (((long)king.x << 16) ^ king.y) ^ 0x9E3779B97F4A7C15L;
        Random rnd = new Random(seed);

        double totalWeight = 0.0;
        for (double w : weights) totalWeight += w;

        // fallback to simple loop if something odd happened
        if (totalWeight <= 0.0) {
            for (int i = 0; i < G.DIRECTIONS.length; i++) {
                int realIndex = (i + G.rc.getRoundNum()) % 8;
                Direction dir = G.DIRECTIONS[realIndex];
                MapLocation spawnLoc = G.me.add(dir).add(dir); // 2 steps away
                if (G.rc.canBuildRat(spawnLoc)) {
                    G.rc.buildRat(spawnLoc);
                    G.indicatorString.append("SPAWN ");
                    return;
                }
            }
        } else {
            double r = rnd.nextDouble() * totalWeight;
            double acc = 0.0;
            int chosenIndex = -1;
            for (int i = 0; i < weights.length; i++) {
                acc += weights[i];
                if (r <= acc) { chosenIndex = i; break; }
            }
            if (chosenIndex == -1) chosenIndex = 0;

            // Build an ordered list of direction indices sorted by descending weight.
            ArrayList<Integer> order = new ArrayList<>();
            for (int i = 0; i < weights.length; i++) order.add(i);
            Collections.sort(order, new Comparator<Integer>() {
                public int compare(Integer a, Integer b) {
                    return Double.compare(weights[b], weights[a]);
                }
            });

            // Try the randomly chosen dir first. If blocked, try other directions in descending weight order.
            ArrayList<Integer> tryList = new ArrayList<>();
            tryList.add(chosenIndex);
            for (int idx : order) if (idx != chosenIndex) tryList.add(idx);

            for (int idx : tryList) {
                Direction dir = G.DIRECTIONS[idx];
                // prefer spawning 2 steps away; if not enough room, spawn as far as possible up to 2
                int maxSteps = 2;
                MapLocation probe = king;
                int stepsAvailable = 0;
                for (int s = 0; s < maxSteps; s++) {
                    MapLocation nxt = probe.add(dir);
                    if (nxt.x < 0 || nxt.x >= mapW || nxt.y < 0 || nxt.y >= mapH) break;
                    probe = nxt;
                    stepsAvailable++;
                }
                if (stepsAvailable == 0) continue; // can't spawn in this direction at all

                MapLocation spawnLoc = king;
                for (int s = 0; s < stepsAvailable; s++) spawnLoc = spawnLoc.add(dir); // 1..2 steps
                if (G.rc.canBuildRat(spawnLoc)) {
                    G.rc.buildRat(spawnLoc);
                    G.indicatorString.append("SPAWN ");
                    return;
                }
                // else try next best dir
            }
        }

        // If 2-step (or 1-step) tries all failed, fallback to trying the 3x3 perimeter spots (original fallback)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (Math.abs(dx) == 2 || Math.abs(dy) == 2) {
                    MapLocation spawnLoc = G.me.translate(dx, dy);
                    if (G.rc.canBuildRat(spawnLoc)) {
                        G.rc.buildRat(spawnLoc);
                        G.indicatorString.append("SPAWN ");
                        return;
                    }
                }
            }
        }
    }
}

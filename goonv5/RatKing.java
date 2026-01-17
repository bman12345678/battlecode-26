package goonv5;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RatKing {
    private static MapLocation targetCorner = null;
    private static boolean reachedCorner = false;
    private static final int TARGET_RAT_COST = 60;
    private static Set<MapLocation> mines = new HashSet<>();


    public static void run() throws Exception {
        // Initialize target corner on first run
        if (targetCorner == null) {
            targetCorner = getClosestCorner();
        }

        // Write king location to shared array every round
        writeLocationToSharedArray();


        List<MapLocation> kings = new ArrayList<>();
        for(int i = 0; i < 21; i++) {

            int roundNum = G.rc.readSharedArray(3*i) - 10;
            if(roundNum == 0) continue;

            if(G.rc.getRoundNum() % 1000 == roundNum || (G.rc.getRoundNum() + 999) %1000 == roundNum || (G.rc.getRoundNum() + 1) %1000 == roundNum) {
                kings.add(new MapLocation(G.rc.readSharedArray(3 * i + 1), G.rc.readSharedArray(3 * i + 2)));
            }
        }




        // Spawn rats until we have 20
        if(G.rc.getCurrentRatCost() <= 100) {

            if (G.rc.getGlobalCheese() - G.rc.getCurrentRatCost() >= 1800) {
                spawnRats();
            }
            else if(G.rc.getCurrentRatCost() <= 40 && G.rc.getGlobalCheese() - G.rc.getCurrentRatCost() >= 1600) {
                spawnRats();
            }
            else if(G.rc.getCurrentRatCost() <= 30 && G.rc.getGlobalCheese() - G.rc.getCurrentRatCost() >= 1200) {
                spawnRats();
            }
            else if(G.rc.getCurrentRatCost() <= 20 && G.rc.getGlobalCheese() - G.rc.getCurrentRatCost() >= 800) {
                spawnRats();
            }
            else if(G.rc.getCurrentRatCost() <= 10) {
                spawnRats();
            }
        }



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
                    G.rc.writeSharedArray(63, G.rc.getID() % 21);
                }
            }
        }

        if(cnt == 0) {
            G.rc.writeSharedArray(63, 1023);
        }

        // If we see a cat, place dirt to block it and run away

        if (closestCat != null) {
            handleCatThreat(closestCat);
        } else {
            // No cat visible, navigate to corner
            if (!reachedCorner) {
                //navigateToCorner();
            }
        }


        MapInfo[] infos = G.rc.senseNearbyMapInfos(13);



            for (RobotInfo r : G.rc.senseNearbyRobots(-1)) {
                if (r.getType().isBabyRatType() && r.getTeam() != G.opponentTeam && r.getRawCheeseAmount() > 0) {
                    Direction d = G.me.directionTo(r.getLocation());

                    boolean shouldGo = true;
                    for(MapInfo m : infos) {
                        if((G.me.directionTo(m.getMapLocation()) == d || G.me.directionTo(m.getMapLocation()) == d.rotateLeft() ||  G.me.directionTo(m.getMapLocation()) == d.rotateRight())&& (m.isWall() || m.hasCheeseMine())) {
                            shouldGo = false;
                            break;
                        }
                    }
                    if (G.rc.canMove(d) && shouldGo) G.rc.move(d);
                }
            }

            for(MapLocation k : kings) {
                if(kings.size() > 1 && k != G.rc.getLocation() && k.isWithinDistanceSquared(G.rc.getLocation(), 25)) {
                    Motion.bugnavAway(k);
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


    private static void spawnRats() throws Exception {
        // King is 3x3, so we need to check locations further out
        // Try spawning at distance 2 in all directions to clear the 3x3 body
        //if(G.rc.getRoundNum() % 3 == 0) return;
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

        // If that fails, try adjacent locations around the 3x3 perimeter
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
}

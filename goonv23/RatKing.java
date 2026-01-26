package goonv23;

import battlecode.common.*;

import java.util.*;

public class RatKing {
    private static MapLocation targetCorner = null;
    private static boolean reachedCorner = false;
    private static final int TARGET_RAT_COST = 60;
    private static MapLocation center = new MapLocation(G.rc.getMapWidth()/2, G.rc.getMapHeight()/2);
    private static Set<MapLocation> mines = new HashSet<>();
    private static final double DIAG_WEIGHT = 1.6; // how much more we prefer diagonal directions
    private static MapLocation lastLoc = center;
    private static Map<Integer, Integer> nextTime = new HashMap<>();
    private static boolean spawned = false;
    private static boolean foundMine = false;


    public static void run() throws Exception {
        for (Direction d : G.DIRECTIONS) if (G.rc.canPickUpCheese(G.rc.getLocation().add(d).add(d))) G.rc.pickUpCheese(G.rc.getLocation().add(d).add(d));
        if(mines.isEmpty()) {
            int numOfMines = G.rc.readSharedArray(15);
            if(numOfMines > 0) {
                for(int i = 0; i < numOfMines; i++) {
                    int x = G.rc.readSharedArray(2 * numOfMines + 16);
                    int y = G.rc.readSharedArray(2 * numOfMines + 17);
                    MapLocation loc = new MapLocation(x, y);

                    mines.add(loc);
                }
            }
        }
        List<MapLocation> kings = new ArrayList<>();
        for(int i = 0; i < 5; i++) {

            int roundNum = G.rc.readSharedArray(3*i) - 10;
            if(roundNum == -10) continue;

            if(G.rc.getRoundNum() % 1000 == roundNum || (G.rc.getRoundNum() + 999) %1000 == roundNum || (G.rc.getRoundNum() + 1) %1000 == roundNum) {
                kings.add(new MapLocation(G.rc.readSharedArray(3 * i + 1), G.rc.readSharedArray(3 * i + 2)));
            }
        }

        spawned = false;
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
                System.out.println(enemy);
                if (closestCat == null ||
                        G.rc.getLocation().distanceSquaredTo(enemy.location) < G.rc.getLocation().distanceSquaredTo(closestCat.location)) {
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
        }

        for (RobotInfo enemy : nearbyEnemies) {

            if (enemy.getTeam() != G.rc.getTeam()) {
               handleCatThreat(enemy);
               if(spawned) break;
            }
        }

        if(!foundMine) {
            for (MapInfo m : G.rc.senseNearbyMapInfos(-1)) {
                if(!m.hasCheeseMine()) continue;
                if(!mines.contains(m.getMapLocation())) {
                    mines.add(m.getMapLocation());

                    int currSize = G.rc.readSharedArray(15);
                    if (currSize < 23) {
                        G.rc.writeSharedArray(16 + 2 * currSize, m.getMapLocation().x);
                        G.rc.writeSharedArray(17 + 2 * currSize, m.getMapLocation().y);
                        G.rc.writeSharedArray(15, currSize + 1);
                    }


                    if (m.hasCheeseMine() && center.distanceSquaredTo(m.getMapLocation()) <= G.rc.getMapWidth() * G.rc.getMapHeight() / 4) {
                        boolean valid = true;
                        for(MapLocation k : kings) {
                            if(k.equals(G.rc.getLocation()) || k.isAdjacentTo(G.rc.getLocation())) continue;
                            if(k.distanceSquaredTo(m.getMapLocation()) <= 10) {
                                valid = false;
                                break;
                            }
                        }

                        if(valid) {
                            lastLoc = m.getMapLocation();
                            foundMine = true;
                        }

                    }
                }
            }
        }

        if(!foundMine && !mines.isEmpty()) {
            Iterator<MapLocation> it = mines.iterator();
            MapLocation closest = null;
            while(it.hasNext()) {
                MapLocation p = it.next();
                boolean valid = true;
                for(MapLocation k : kings) {
                    if(k.equals(G.rc.getLocation()) || k.isAdjacentTo(G.rc.getLocation())) continue;
                    if(k.distanceSquaredTo(p) <= 10) {
                        valid = false;
                        break;
                    }
                }
                if(valid) {
                    if(closest == null || G.rc.getLocation().distanceSquaredTo(p) < G.rc.getLocation().distanceSquaredTo(closest)) closest = p;
                }
            }

            if(closest != null) lastLoc = closest;
        }

        if(foundMine && !mines.isEmpty()) {
            Iterator<MapLocation> it = mines.iterator();
            MapLocation closest = null;
            while(it.hasNext()) {
                MapLocation p = it.next();
                boolean valid = true;
                for(MapLocation k : kings) {
                    if(k.equals(G.rc.getLocation()) || k.isAdjacentTo(G.rc.getLocation())) continue;
                    if(k.distanceSquaredTo(p) <= 10) {
                        valid = false;
                        break;
                    }
                }
                if(valid) {
                    if(closest == null || center.distanceSquaredTo(p) < center.distanceSquaredTo(closest)) {
                        if(Motion.canReachYou(p)) {
                            closest = p;
                        }
                    }
                }
            }

            if(closest != null) lastLoc = closest;
        }

        if(lastLoc != null && !G.rc.getLocation().equals(lastLoc)) {
                MotionCodeGen.bugnavTowardsExplore(lastLoc);
        }

        //if(G.rc.getCurrentRatCost() <= 40 && G.rc.getGlobalCheese() >= 1700) {
        //    spawnRats();
        //}
       //else
        if(G.rc.getCurrentRatCost() <= 30 && G.rc.getGlobalCheese() >= 500) {
                spawnRats();
        } else if(G.rc.getCurrentRatCost() <= 20 && G.rc.getGlobalCheese() >= 300) {
            spawnRats();
        } else if(G.rc.getCurrentRatCost() <= 10) {
            spawnRats();
        }
        else if (G.rc.getGlobalCheese() - G.rc.getCurrentRatCost() + Math.min(850, 2 * G.rc.getRoundNum()) >= 2300) {
            spawnRats();
        }


        for(int i = 0; i < 3; i++) {
            MapLocation ahead = G.rc.getLocation().add(G.rc.getDirection());
            if(i == 0) ahead = ahead.add(G.rc.getDirection());
            else if( i==1) ahead = ahead.add(G.rc.getDirection().rotateLeft());
            else if(i==2) ahead = ahead.add(G.rc.getDirection().rotateRight());


            if (G.rc.canSenseLocation(ahead)) {
                if (G.rc.canRemoveDirt(ahead)) {
                    G.rc.removeDirt(ahead);
                }
            }
        }

        boolean pickedUpCheese = false;

        for(Direction d : Direction.allDirections()) {
            MapLocation m = G.rc.getLocation().add(d);
            if(G.rc.canPickUpCheese(m)) {
                G.rc.pickUpCheese(m);
                pickedUpCheese = true;
                break;
            }
        }

        Message[] squeaks = G.rc.readSqueaks(G.rc.getRoundNum()-1);
        for(Message s : squeaks) {
            int msg = s.getBytes();
            if(msg % 1024 == 1) {

                msg -= 1;
                msg /= 1024;
                int x = msg/1024;
                int y = msg % 1024;
                MapLocation m = new MapLocation(x, y);
                if(!mines.contains(m)) {
                    mines.add(m);
                    System.out.println(m);

                    int currSize = G.rc.readSharedArray(15);
                    if(currSize < 23) {
                        G.rc.writeSharedArray(16 + 2 * currSize, x);
                        G.rc.writeSharedArray(17 + 2 * currSize, y);
                        G.rc.writeSharedArray(15, currSize + 1);
                    }
                }
            }
        }

        /*
        if(!pickedUpCheese && G.rc.getLocation().equals(lastLoc)) {
            for(int dx =-2; dx <= 2; dx++) {
                for(int dy = -2; dy <= 2; dy++) {
                    if(Math.abs(dx) != 2 && Math.abs(dy) != 2) continue;
                    MapLocation m = G.rc.getLocation().translate(dx, dy);
                    if(G.rc.canSenseLocation(m) && G.rc.senseMapInfo(m).getCheeseAmount() > 0) {
                        if(Motion.move(G.rc.getLocation().directionTo(m))) {
                            if(G.rc.canPickUpCheese(m)) {
                                G.rc.pickUpCheese(m);
                            }
                        }
                    }
                }
            }
        }
        */










    }

    private static void writeLocationToSharedArray() throws Exception {

        for(int i = 0; i < 5; i++) {
            if(G.rc.readSharedArray(3*i) != 10 + ( G.rc.getRoundNum() % 1000) && G.rc.readSharedArray(3*i) != 10 + ( (G.rc.getRoundNum()-1) % 1000) && G.rc.readSharedArray(3*i) != 10 + ( (G.rc.getRoundNum()+1) % 1000)) {
                G.rc.writeSharedArray(3 * i, 10 + ( G.rc.getRoundNum() % 1000));

                // Write X and Y to separate indices
                G.rc.writeSharedArray(3*i+1, G.rc.getLocation().x);
                G.rc.writeSharedArray(3*i+2, G.rc.getLocation().y);
                break;
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
        int minDist = G.rc.getLocation().distanceSquaredTo(corners[0]);

        for (int i = 1; i < corners.length; i++) {
            int dist = G.rc.getLocation().distanceSquaredTo(corners[i]);
            if (dist < minDist) {
                minDist = dist;
                closest = corners[i];
            }
        }

        return closest;
    }


    private static void handleCatThreat(RobotInfo cat) throws Exception {
        // Cat is 2x2, place dirt to block it - need to block a wider area
        Direction toCat = G.rc.getLocation().directionTo(cat.location);

        //System.out.println(cat.location);
        Motion.move(toCat.opposite());
        Motion.move(toCat.opposite().rotateLeft());
        Motion.move(toCat.opposite().rotateRight());
        Motion.bugnavAwayNoTurning(cat.location);


        MapLocation adj = G.rc.getLocation().add(toCat).add(toCat);
        Direction dirTo = G.rc.getLocation().directionTo(cat.location);


        /*
        int cnt = 0;
        MapLocation curr = G.rc.getLocation();
        int fallback = 0;
        while(!curr.equals(cat.getLocation())) {
            if (G.rc.canSenseRobotAtLocation(curr)) {
                RobotInfo r = G.rc.senseRobotAtLocation(curr);
                if (r.getTeam() == G.rc.getTeam() && !r.getType().isRatKingType() && r.getHealth() > cat.getHealth() && r.getCarryingRobot() == null) {
                    cnt += 1;
                    if (cnt == 2) return;
                }
            }

            curr = curr.add(curr.directionTo(cat.getLocation()));
            fallback += 1;
            if (fallback >= 10) break;
        }

        */

        if(!nextTime.containsKey(cat.getID()) || nextTime.get(cat.getID()) <= G.rc.getRoundNum()) {
            if (dirTo == Direction.NORTH) {
                //Motion.turn(Direction.NORTH);
                spawned = spawned || spawn(G.rc.getLocation().translate(0, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(1, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-1, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, 1), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, 1), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, 0), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, 0), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, -1), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, -1), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(1, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-1, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(0, -2), cat.getLocation());
            } else if (dirTo == Direction.SOUTH) {
                //Motion.turn(Direction.SOUTH);

                spawned = spawned ||spawn(G.rc.getLocation().translate(0, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(1, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-1, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, -1), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, -1), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, 0), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, 0), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, 1), cat.getLocation());
                spawned = spawned || spawn(G.rc.getLocation().translate(-2, 1), cat.getLocation());
                spawned = spawned || spawn(G.rc.getLocation().translate(2, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, 2), cat.getLocation());
                spawned = spawned || spawn(G.rc.getLocation().translate(1, 2), cat.getLocation());
                spawned = spawned || spawn(G.rc.getLocation().translate(-1, 2), cat.getLocation());
                spawned = spawned || spawn(G.rc.getLocation().translate(0, 2), cat.getLocation());
            } else if (dirTo == Direction.EAST) {
                //Motion.turn(Direction.EAST);

                spawned = spawned ||spawn(G.rc.getLocation().translate(2, 0), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, 1), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, -1), cat.getLocation());
                spawned = spawned || spawn(G.rc.getLocation().translate(2, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(1, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(1, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(0, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(0, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-1, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-1, -2), cat.getLocation());
                spawned = spawned || spawn(G.rc.getLocation().translate(-2, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, 1), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, -1), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, 0), cat.getLocation());
            } else if (dirTo == Direction.WEST) {
                //Motion.turn(Direction.WEST);

                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, 0), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, 1), cat.getLocation());
                spawned = spawned || spawn(G.rc.getLocation().translate(-2, -1), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-1, 2), cat.getLocation());
                spawned = spawned || spawn(G.rc.getLocation().translate(-1, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(0, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(0, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(1, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(1, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, 1), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, -1), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, 0), cat.getLocation());
            } else if (dirTo == Direction.NORTHEAST) {
                //Motion.turn(Direction.NORTHEAST);

                spawned = spawned ||spawn(G.rc.getLocation().translate(2, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, 1), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(1, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, 0), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(0, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, -1), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-1, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(1, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, 1), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(0, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, 0), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-1, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, -1), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, -2), cat.getLocation());
            } else if (dirTo == Direction.NORTHWEST) {
                //Motion.turn(Direction.NORTHWEST);

                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, 1), cat.getLocation());
                spawned = spawned || spawn(G.rc.getLocation().translate(-1, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, 0), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(0, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, -1), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(1, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-1, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, 1), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(0, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, 0), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(1, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, -1), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, -2), cat.getLocation());
            } else if (dirTo == Direction.SOUTHWEST) {
                //Motion.turn(Direction.SOUTHWEST);

                spawned = spawned || spawn(G.rc.getLocation().translate(-2, -2), cat.getLocation());
                spawned = spawned || spawn(G.rc.getLocation().translate(-2, -1), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-1, -2), cat.getLocation());
                spawned = spawned || spawn(G.rc.getLocation().translate(-2, -0), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(0, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, 1), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(1, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-1, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, -1), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(0, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, -0), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(1, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, 1), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, 2), cat.getLocation());
            } else if (dirTo == Direction.SOUTHEAST) {
                //Motion.turn(Direction.SOUTHEAST);

                spawned = spawned ||spawn(G.rc.getLocation().translate(2, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, -1), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(1, -2), cat.getLocation());
                spawned = spawned || spawn(G.rc.getLocation().translate(2, -0), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(0, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, 1), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-1, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(2, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, -2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(1, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, -1), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(0, 2), cat.getLocation());
                spawned = spawned || spawn(G.rc.getLocation().translate(-2, -0), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-1, 2), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, 1), cat.getLocation());
                spawned = spawned ||spawn(G.rc.getLocation().translate(-2, 2), cat.getLocation());
            }
        }
        if(spawned) {
            //System.out.println(cat.getID());
            if(cat.getType().isCatType()) nextTime.put(cat.getID(), G.rc.getRoundNum()+40);
            else nextTime.put(cat.getID(), G.rc.getRoundNum() + 1);
        }



/*
        // Place dirt in multiple locations to block the 2x2 ca
        if(cat.getType().isCatType()) {
            for (int i = 0; i < 5; i++) {
                MapLocation blockLoc = G.rc.getLocation().add(toCat).add(toCat);
                if (i > 0) {
                    blockLoc = blockLoc.add(G.DIRECTIONS[2 * (i - 1)]);


                }

                //System.out.println(blockLoc);
                //System.out.println(G.rc.canPlaceDirt(blockLoc));

                if (G.rc.canPlaceDirt(blockLoc)) {

                    G.rc.placeDirt(blockLoc);
                    G.indicatorString.append("BLOCK ");
                }
            }
        }

        // Navigate away from the cat
*/

        G.indicatorString.append("FLEE ");
    }

    private static boolean spawn(MapLocation m, MapLocation enemy) throws Exception {
        if(G.rc.canBuildRat(m)) {
            Motion.turn(m.directionTo(enemy));
            G.rc.buildRat(m);
            return true;
        }
        return false;
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

        // --- sharpen distribution ---
        // POWER controls how concentrated the spawn distribution is.
        // 1.0 -> original linear weighting, 2..4 -> increasingly concentrated.
        final double POWER = 1.5;    // tune this: higher -> more concentrated on long directions
        final double MIN_FLOOR = 0.01; // minimum adjusted weight for any direction
        double[] adj = new double[weights.length];
        double totalAdj = 0.0;
        for (int i = 0; i < weights.length; i++) {
            adj[i] = Math.max(MIN_FLOOR, Math.pow(weights[i], POWER));
            totalAdj += adj[i];
        }

        // Weighted-random pick (seeded by round & king pos so it's varied but reproducible)
        long seed = ((long) G.rc.getRoundNum() << 32) ^ (((long)king.x << 16) ^ king.y) ^ 0x9E3779B97F4A7C15L;
        java.util.Random rnd = new java.util.Random(seed);

        // fallback (should rarely happen) - try basic nearby spawn loop
        if (totalAdj <= 0.0) {
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
            // pick primary direction by weighted-random on adjusted weights
            double r = rnd.nextDouble() * totalAdj;
            double acc = 0.0;
            int chosenIndex = -1;
            for (int i = 0; i < adj.length; i++) {
                acc += adj[i];
                if (r <= acc) { chosenIndex = i; break; }
            }
            if (chosenIndex == -1) chosenIndex = 0;

            // Build an ordered list of direction indices sorted by descending adjusted weight.
            java.util.ArrayList<Integer> order = new java.util.ArrayList<>();
            for (int i = 0; i < adj.length; i++) order.add(i);
            java.util.Collections.sort(order, new java.util.Comparator<Integer>() {
                public int compare(Integer a, Integer b) {
                    return Double.compare(adj[b], adj[a]);
                }
            });

            // Try the randomly chosen dir first. If blocked, try other directions in descending adjusted-weight order.
            java.util.ArrayList<Integer> tryList = new java.util.ArrayList<>();
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
                    MapLocation spawnLoc = G.rc.getLocation().translate(dx, dy);
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
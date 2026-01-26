package goonv23;

import battlecode.common.*;

import java.util.*;

public class BabyRat {
    // Constants

    private static int lastHealth = 100;
    public static MapLocation lastMine = null;
    private static int roundPickedUp = 0;
    private static int catTurnsLeft = 0;
    private static MapLocation catLoc = null;
    private static int ori = -1;
    private static Direction dori = null;
    private static MapLocation tmp = null;
    private static MapLocation king = null;
    private static Set<MapLocation> mines = new HashSet<>();
    public static MapLocation newMine= null;
    public static int dirtcnt = 0;
    public static RobotInfo lastBabyRat = null;
    private static Set<MapLocation> visitedMines = new HashSet<>();
    private static Set<MapLocation> mineset = new HashSet<>();
    private static MapLocation curmine = null;
    private static int mturns = 0;

    public static void run() throws Exception {
        if(mineset.isEmpty()) {
            int numOfMines = G.rc.readSharedArray(15);
            if(numOfMines > 0) {
                for(int i = 0; i < numOfMines; i++) {
                    int x = G.rc.readSharedArray(2 * i + 16);
                    int y = G.rc.readSharedArray(2 * i + 17);
                    MapLocation loc = new MapLocation(x, y);

                    if(!(loc.x==0&&loc.y==0)) mineset.add(loc);
                }
            }
        }
        else {
            int numOfMines = G.rc.readSharedArray(15);
            for(int i = 0; i < numOfMines; i++) {
                int x = G.rc.readSharedArray(2 * i + 16);
                int y = G.rc.readSharedArray(2 * i + 17);
                MapLocation loc = new MapLocation(x, y);

                if (!mineset.contains(loc) && !(loc.x==0&&loc.y==0)) mineset.add(loc);
            }
        }
        if(G.rc.getRawCheese ()<160) for (Direction d : G.DIRECTIONS) if (G.rc.canPickUpCheese(G.me.add(d))) G.rc.pickUpCheese(G.me.add(d));

        List<MapLocation> kings = new ArrayList<>();
        for(int i = 0; i < 5; i++) {

            int roundNum = G.rc.readSharedArray(3*i) - 10;
            if(roundNum == -10) continue;

            if(G.rc.getRoundNum() % 1000 == roundNum || (G.rc.getRoundNum() + 999) %1000 == roundNum || (G.rc.getRoundNum() + 1) %1000 == roundNum) {
                kings.add(new MapLocation(G.rc.readSharedArray(3 * i + 1), G.rc.readSharedArray(3 * i + 2)));
            }
        }

        if(kings.size() > 0) king = kings.get(0);
        for(MapLocation k : kings) {
            if(k.distanceSquaredTo(G.me) < king.distanceSquaredTo(G.me)) {
                king = k;
            }

            if(newMine != null) {

                if(k.distanceSquaredTo(newMine) < 9) newMine = null;
            }
        }

        //System.out.println(kings);
/*
        mines.clear();
        int numOfMines = G.rc.readSharedArray(15);
        if(numOfMines > 0) {
            int cnt = 0;
            for(int i = 0; i < numOfMines; i++) {
                int x = G.rc.readSharedArray(2 * numOfMines + 16);
                int y = G.rc.readSharedArray(2 * numOfMines + 17);
                MapLocation loc = new MapLocation(x, y);

                boolean valid = true;
                for(MapLocation k : kings) {
                    if(k.isAdjacentTo(loc) || k.equals(loc)) {
                        valid = false;
                        break;
                    }
                }
                if(valid && loc.distanceSquaredTo(king) <= G.rc.getMapWidth() * G.rc.getMapHeight()/4) {
                    mines.add(loc);
                    cnt += 1;
                }
            }

            if(cnt > 0) {
                int index = G.rc.getID() % cnt;
                Iterator<MapLocation> it = mines.iterator();
                while(it.hasNext() && index >= 0) {
                    lastMine = it.next();
                    index -= 1;
                }
            }
            else lastMine = null;

        }
        */
        lastMine = newMine;


        //System.out.println(mines);
        //System.out.println(lastMine);







        MapInfo[] mapInfos = G.rc.senseNearbyMapInfos(-1);
        for(MapInfo info : mapInfos) {
            if(info.hasCheeseMine()) {
                if(newMine == null && king.distanceSquaredTo(info.getMapLocation()) > 9) newMine = info.getMapLocation();
            }
        }

        boolean attacked = false;
        int currentHealth = G.rc.getHealth();
        //System.out.println(currentHealth);
        //System.out.println(lastHealth);
        if(currentHealth < lastHealth) attacked = true;

        RobotInfo[] enemyRobots = G.rc.senseNearbyRobots(-1, G.opponentTeam);
        if(enemyRobots.length == 0 && attacked) {
            if(lastBabyRat != null) Motion.turn(G.rc.getLocation().directionTo(lastBabyRat.getLocation()));
            Motion.turn(G.rc.getDirection().rotateLeft().rotateLeft());

            enemyRobots = G.rc.senseNearbyRobots(-1, G.opponentTeam);
            if(enemyRobots.length == 0) {
                Motion.moveNoTurn(G.rc.getDirection().opposite());
                enemyRobots = G.rc.senseNearbyRobots(-1, G.opponentTeam);
            }
        }


        if(G.rc.getCarrying() != null)  {
            if (G.rc.getCarrying().getTeam().equals(G.rc.getTeam())) {
                if (G.rc.getLocation().distanceSquaredTo(king)<=18) {
                    if (G.rc.canDropRat(G.me.directionTo(king))) G.rc.dropRat(G.me.directionTo(king));
                    else {
                        for (Direction d : G.DIRECTIONS) if (G.rc.canDropRat(d)) G.rc.dropRat(d);
                        if (G.rc.getCarrying()!=null) MotionCodeGen.bugnavTowardsExplore(king);
                    }
                }
                else MotionCodeGen.bugnavTowardsExplore(king);
            }
        }

        RobotInfo babyRat = null;
        RobotInfo kingRat = null;
        int bestDist = 100;
        for(RobotInfo x : enemyRobots) {
            if (x.getType().isBabyRatType()) {
                if(!Motion.canReachYou(x.getLocation())) continue;
                int dist = x.getLocation().distanceSquaredTo(G.rc.getLocation());
                if(G.rc.getCarrying() != null && G.rc.getCarrying().getID() == x.getID()) continue;

                if (babyRat == null || dist < bestDist) {
                    bestDist = dist;
                    babyRat = x;
                }
            }
            else if(x.getType().isRatKingType()) {
                kingRat = x;
            }
        }



        boolean fromSqueak = false;
        if(babyRat == null) {
            if(G.rc.getCarrying() != null) {
                if (!tryToThrow()) return;
                else if(G.rc.getCarrying() == null) {
                    if(G.rc.canMoveForward()) G.rc.moveForward();
                }
            }
            //tryToThrow();

            Message[] squeaks = G.rc.readSqueaks(G.rc.getRoundNum()-1);
            //if(squeaks.length == 0) squeaks = G.rc.readSqueaks(G.rc.getRoundNum() - 2);
            //if(squeaks.length == 0) squeaks = G.rc.readSqueaks(G.rc.getRoundNum() - 3);
            //if(squeaks.length == 0) squeaks = G.rc.readSqueaks(G.rc.getRoundNum() - 4);
            //if(squeaks.length == 0) squeaks = G.rc.readSqueaks(G.rc.getRoundNum() - 5);

            int cloesestSqueak = 100;
            MapLocation closest = null;

            for(Message s : squeaks) {

                int msg = s.getBytes();
                if(msg % 2 == 1) {
                    continue;
                }
                msg /= 2;
                int health = msg % 128;

                msg/=128;
                Direction dir = G.DIRECTIONS[msg % 8];

                msg /= 8;
                int x = msg /1024;
                int y = msg % 1024;


                MapLocation l = new MapLocation(x, y);
                if(l.distanceSquaredTo(G.rc.getLocation()) < cloesestSqueak) {
                    cloesestSqueak = l.distanceSquaredTo(G.rc.getLocation());
                    closest = l;
                    fromSqueak = true;
                    babyRat = new RobotInfo(-1, G.opponentTeam, UnitType.BABY_RAT, health, closest, dir, 0, 0, null);
                }
            }

        }

        tryToThrow();

        RobotInfo[] cats = G.rc.senseNearbyRobots(-1, Team.NEUTRAL);
        RobotInfo cat = null;

        for(RobotInfo c : cats) {
            if(cat == null || c.getLocation().distanceSquaredTo(G.rc.getLocation()) < cat.getLocation().distanceSquaredTo(G.rc.getLocation())) {
                //if(!Motion.canReachYou(c.getLocation())) continue;

                cat = c;
            }
        }
        if(cat != null) {



            Direction toCat = G.me.directionTo(cat.getLocation());

            // Try to place traps in a line to block the 2x2 cat
            //if(Motion.canCatReachYou(cat.getLocation())) {


            if(G.rc.isCooperation() && babyRat == null) {
                int dist = G.rc.getLocation().distanceSquaredTo(cat.getLocation());

                if(dist <= 8) {
                    for(Direction d : G.DIRECTIONS) {
                        MapLocation trapLoc = G.rc.adjacentLocation(d);
                        if (trapLoc.isAdjacentTo(cat.getLocation()) && G.rc.canPlaceCatTrap(trapLoc) &&  (cat.getDirection() != d && cat.getDirection() != d.rotateLeft() && cat.getDirection() != d.rotateRight())) {
                            G.rc.placeCatTrap(trapLoc);
                        }
                    }
                    Motion.bugnavAwayNoTurning(cat.getLocation());
                    for(Direction d : G.DIRECTIONS) {
                        MapLocation trapLoc = G.rc.adjacentLocation(d);
                        if (trapLoc.isAdjacentTo(cat.getLocation()) && G.rc.canPlaceCatTrap(trapLoc) &&  (cat.getDirection() != d && cat.getDirection() != d.rotateLeft() && cat.getDirection() != d.rotateRight())) {
                            G.rc.placeCatTrap(trapLoc);
                        }
                    }
                }
                else {
                    if(G.rc.isActionReady()) {
                        Motion.bugnavTowards(cat.getLocation());
                        for (Direction d : G.DIRECTIONS) {
                            MapLocation trapLoc = G.rc.adjacentLocation(d);
                            if (trapLoc.isAdjacentTo(cat.getLocation()) && G.rc.canPlaceCatTrap(trapLoc) &&  (cat.getDirection() != d && cat.getDirection() != d.rotateLeft() && cat.getDirection() != d.rotateRight())) {
                                G.rc.placeCatTrap(trapLoc);
                            }
                        }
                    }
                }




                for (Direction d : Direction.allDirections()) {
                    if (G.rc.canAttack(G.rc.getLocation().add(d))) {
                        G.rc.attack(G.rc.getLocation().add(d));
                    }
                }
            }

            MapLocation trapLoc = G.rc.adjacentLocation(toCat);
            if (G.rc.canPlaceCatTrap(trapLoc) && trapLoc.isAdjacentTo(cat.getLocation())) {
                G.rc.placeCatTrap(trapLoc);
            }

            for (Direction d : Direction.allDirections()) {
                if (G.rc.canAttack(G.rc.getLocation().add(d))) {
                    G.rc.attack(G.rc.getLocation().add(d));
                }
            }

            catLoc = cat.getLocation();
            //catTurnsLeft = 1;
            //catTurnsLeft = 1;
            babyRatMicro();
            if(G.rc.getCurrentRatCost() >= 50 && G.rc.getGlobalCheese() > 1300 && babyRat == null) {
                Direction d = cat.getLocation().directionTo(G.rc.getLocation());
                if (cat.getDirection() != d && cat.getDirection() != d.rotateLeft() && cat.getDirection() != d.rotateRight() && cat.getDirection() != d.rotateLeft().rotateLeft() && cat.getDirection() != d.rotateRight().rotateRight()) {
                    Motion.move(G.rc.getLocation().directionTo(cat.getLocation()));
                    Motion.turn(G.rc.getLocation().directionTo(cat.getLocation()));
                    babyRatMicro();
                }
                else Motion.bugnavAwayNoTurning(cat.getLocation());
            }

            if(king.distanceSquaredTo(G.rc.getLocation()) + 8 >= king.distanceSquaredTo(catLoc) && king.distanceSquaredTo(G.rc.getLocation()) <= 169) {
                G.rc.squeak(3);
            }

        }








        if(babyRat != null) {
            int directionIndex = 0;
            for(int i = 0; i < 8; i++) {
                if(babyRat.getDirection() == G.DIRECTIONS[i]) {
                    directionIndex = i;
                    break;
                }
            }

            int msg = 2 * (128 * (8 * (babyRat.getLocation().x * 1024 + babyRat.getLocation().y) + directionIndex) + babyRat.getHealth());
            if(!fromSqueak) G.rc.squeak(msg);

            int myX = G.rc.getLocation().x;
            int myY = G.rc.getLocation().y;
            Direction myDir = G.rc.getDirection();
            Direction babyRatDir = babyRat.getDirection();
            int dx = myX - babyRat.getLocation().x;
            int dy = myY - babyRat.getLocation().y;
            RobotInfo[] ally = G.rc.senseNearbyRobots(babyRat.getLocation(), 12, G.rc.getTeam());

            if(G.rc.getCarrying() != null) {
                //attackBabyRat(babyRat)
                //Motion.bugnavAwayNoTurning(babyRat.getLocation());
                tryToThrow();

            }

            MapLocation before = G.rc.getLocation();
            //else {
                if(ratnap(babyRat)) {
                    if(before.equals(G.rc.getLocation())) {
                        Direction opp = G.rc.getLocation().directionTo(babyRat.getLocation());
                        if(G.rc.canMove(opp.opposite())) {
                            G.rc.move(opp.opposite());
                        }
                    }

                    //babyRat = nearestBabyRat();

                    if(babyRat == null) {
                        //Motion.bugnavAwayNoTurning(babyRat.getLocation());
                    }
                    else {

                    }
                }

                if(babyRat != null) {
                    babyRatDir = babyRat.getDirection();
                    dx = G.rc.getLocation().x - babyRat.getLocation().x;
                    dy = G.rc.getLocation().y - babyRat.getLocation().y;
                    if (dx == 0 && dy == 1) {
                        attackBabyRat(babyRat);
                        if (babyRatDir == Direction.NORTHEAST || babyRatDir == Direction.EAST || babyRatDir == Direction.SOUTHEAST) {
                            //(2,1)
                            if (Motion.moveNoTurn(Direction.NORTHWEST)) Motion.turn(Direction.SOUTHEAST);
                            //(2,0)
                            if (Motion.moveNoTurn(Direction.NORTH)) Motion.turn(Direction.SOUTH);
                            //(1,1)
                            if (Motion.moveNoTurn(Direction.WEST)) Motion.turn(Direction.SOUTHEAST);
                            // (2,1)
                            if (Motion.moveNoTurn(Direction.NORTHEAST)) Motion.turn(Direction.SOUTHWEST);
                            //(1,1)
                            if (Motion.moveNoTurn(Direction.EAST)) Motion.turn(Direction.SOUTHWEST);
                        } else {
                            //(2,1)
                            if (Motion.moveNoTurn(Direction.NORTHEAST)) Motion.turn(Direction.SOUTHWEST);
                            //(2,0)
                            if (Motion.moveNoTurn(Direction.NORTH)) Motion.turn(Direction.SOUTH);
                            //(1,1)
                            if (Motion.moveNoTurn(Direction.EAST)) Motion.turn(Direction.SOUTHWEST);
                            //(2,1)
                            if (Motion.moveNoTurn(Direction.NORTHWEST)) Motion.turn(Direction.SOUTHEAST);
                            //(1,1)
                            if (Motion.moveNoTurn(Direction.WEST)) Motion.turn(Direction.SOUTHEAST);
                        }
                    } else if (dx == 0 && dy == -1) {
                        attackBabyRat(babyRat);
                        if (babyRatDir == Direction.NORTHEAST || babyRatDir == Direction.EAST || babyRatDir == Direction.SOUTHEAST) {
                            //(2,1)
                            if (Motion.moveNoTurn(Direction.SOUTHWEST)) Motion.turn(Direction.NORTHEAST);
                            //(2,0)
                            if (Motion.moveNoTurn(Direction.SOUTH)) Motion.turn(Direction.NORTH);
                            //(1,1)
                            if (Motion.moveNoTurn(Direction.WEST)) Motion.turn(Direction.NORTHEAST);
                            // (2,1)
                            if (Motion.moveNoTurn(Direction.SOUTHEAST)) Motion.turn(Direction.NORTHWEST);
                            //(1,1)
                            if (Motion.moveNoTurn(Direction.EAST)) Motion.turn(Direction.NORTHWEST);
                        } else {
                            //(2,1)
                            if (Motion.moveNoTurn(Direction.SOUTHEAST)) Motion.turn(Direction.NORTHWEST);
                            //(2,0)
                            if (Motion.moveNoTurn(Direction.SOUTH)) Motion.turn(Direction.NORTH);
                            //(1,1)
                            if (Motion.moveNoTurn(Direction.EAST)) Motion.turn(Direction.NORTHWEST);
                            //(2,1)
                            if (Motion.moveNoTurn(Direction.SOUTHWEST)) Motion.turn(Direction.NORTHEAST);
                            //(1,1)
                            if (Motion.moveNoTurn(Direction.WEST)) Motion.turn(Direction.NORTHEAST);
                        }
                    } else if (dx == 1 && dy == 0) {
                        attackBabyRat(babyRat);
                        if (babyRatDir == Direction.NORTHEAST || babyRatDir == Direction.NORTH || babyRatDir == Direction.NORTHWEST) {
                            //(2,1)
                            if (Motion.moveNoTurn(Direction.SOUTHEAST)) Motion.turn(Direction.NORTHWEST);
                            //(2,0)
                            if (Motion.moveNoTurn(Direction.EAST)) Motion.turn(Direction.WEST);
                            //(1,1)
                            if (Motion.moveNoTurn(Direction.SOUTH)) Motion.turn(Direction.NORTHWEST);
                            // (2,1)
                            if (Motion.moveNoTurn(Direction.NORTHEAST)) Motion.turn(Direction.SOUTHWEST);
                            //(1,1)
                            if (Motion.moveNoTurn(Direction.NORTH)) Motion.turn(Direction.SOUTHWEST);
                        } else {
                            //(2,1)
                            if (Motion.moveNoTurn(Direction.NORTHEAST)) Motion.turn(Direction.SOUTHWEST);
                            //(2,0)
                            if (Motion.moveNoTurn(Direction.EAST)) Motion.turn(Direction.WEST);
                            //(1,1)
                            if (Motion.moveNoTurn(Direction.NORTH)) Motion.turn(Direction.SOUTHWEST);
                            // (2,1)
                            if (Motion.moveNoTurn(Direction.SOUTHEAST)) Motion.turn(Direction.NORTHWEST);
                            //(1,1)
                            if (Motion.moveNoTurn(Direction.SOUTH)) Motion.turn(Direction.NORTHWEST);
                        }

                    } else if (dx == -1 && dy == 0) {
                        attackBabyRat(babyRat);
                        if (babyRatDir == Direction.NORTHWEST || babyRatDir == Direction.NORTH || babyRatDir == Direction.NORTHEAST) {
                            //(2,1)
                            if (Motion.moveNoTurn(Direction.SOUTHWEST)) Motion.turn(Direction.NORTHEAST);
                            //(2,0)
                            if (Motion.moveNoTurn(Direction.WEST)) Motion.turn(Direction.EAST);
                            //(1,1)
                            if (Motion.moveNoTurn(Direction.SOUTH)) Motion.turn(Direction.NORTHEAST);
                            // (2,1)
                            if (Motion.moveNoTurn(Direction.NORTHWEST)) Motion.turn(Direction.SOUTHEAST);
                            //(1,1)
                            if (Motion.moveNoTurn(Direction.NORTH)) Motion.turn(Direction.SOUTHEAST);
                        } else {
                            //(2,1)
                            if (Motion.moveNoTurn(Direction.NORTHWEST)) Motion.turn(Direction.SOUTHEAST);
                            //(2,0)
                            if (Motion.moveNoTurn(Direction.WEST)) Motion.turn(Direction.EAST);
                            //(1,1)
                            if (Motion.moveNoTurn(Direction.NORTH)) Motion.turn(Direction.SOUTHEAST);
                            // (2,1)
                            if (Motion.moveNoTurn(Direction.SOUTHWEST)) Motion.turn(Direction.NORTHEAST);
                            //(1,1)
                            if (Motion.moveNoTurn(Direction.SOUTH)) Motion.turn(Direction.NORTHEAST);
                        }
                    }


                    //(1, 1)
                    else if (dx == 1 && dy == 1) {
                        if(G.rc.getCarrying() != null) {
                            Motion.moveNoTurn(Direction.NORTHEAST);
                        }
                        attackBabyRat(babyRat);
                        if (babyRatDir == Direction.NORTH || babyRatDir == Direction.NORTHWEST || babyRatDir == Direction.WEST) {
                            //(2,1)
                            if (Motion.moveNoTurn(Direction.EAST)) Motion.turn(Direction.SOUTHWEST);
                            //(2,0)
                            if (Motion.moveNoTurn(Direction.SOUTHEAST)) Motion.turn(Direction.WEST);
                            //(2,1)
                            if (Motion.moveNoTurn(Direction.NORTH)) Motion.turn(Direction.SOUTHWEST);
                            //(2,0)
                            if (Motion.moveNoTurn(Direction.NORTHWEST)) Motion.turn(Direction.SOUTH);
                        } else {
                            //(2,1)
                            if (Motion.moveNoTurn(Direction.NORTH)) Motion.turn(Direction.SOUTHWEST);
                            //(2,0)
                            if (Motion.moveNoTurn(Direction.NORTHWEST)) Motion.turn(Direction.SOUTH);
                            //(2,1)
                            if (Motion.moveNoTurn(Direction.EAST)) Motion.turn(Direction.SOUTHWEST);
                            //(2,0)
                            if (Motion.moveNoTurn(Direction.SOUTHEAST)) Motion.turn(Direction.WEST);

                        }
                    } else if (dx == -1 && dy == 1) {
                        if(G.rc.getCarrying() != null) {
                            Motion.moveNoTurn(Direction.NORTHWEST);
                        }
                        attackBabyRat(babyRat);
                        if (babyRatDir == Direction.NORTH || babyRatDir == Direction.NORTHEAST || babyRatDir == Direction.EAST) {
                            //(2,1)
                            if (Motion.moveNoTurn(Direction.WEST)) Motion.turn(Direction.SOUTHEAST);
                            //(2,0)
                            if (Motion.moveNoTurn(Direction.SOUTHWEST)) Motion.turn(Direction.EAST);
                            //(2,1)
                            if (Motion.moveNoTurn(Direction.NORTH)) Motion.turn(Direction.SOUTHEAST);
                            //(2,0)
                            if (Motion.moveNoTurn(Direction.NORTHEAST)) Motion.turn(Direction.SOUTH);
                        } else {
                            //(2,1)
                            if (Motion.moveNoTurn(Direction.NORTH)) Motion.turn(Direction.SOUTHEAST);
                            //(2,0)
                            if (Motion.moveNoTurn(Direction.NORTHEAST)) Motion.turn(Direction.SOUTH);
                            //(2,1)
                            if (Motion.moveNoTurn(Direction.WEST)) Motion.turn(Direction.SOUTHEAST);
                            //(2,0)
                            if (Motion.moveNoTurn(Direction.SOUTHWEST)) Motion.turn(Direction.EAST);

                        }
                    } else if (dx == 1 && dy == -1) {
                        if(G.rc.getCarrying() != null) {
                            Motion.moveNoTurn(Direction.SOUTHEAST);
                        }
                        attackBabyRat(babyRat);
                        if (babyRatDir == Direction.SOUTH || babyRatDir == Direction.SOUTHWEST || babyRatDir == Direction.WEST) {
                            //(2,1)
                            if (Motion.moveNoTurn(Direction.EAST)) Motion.turn(Direction.NORTHWEST);
                            //(2,0)
                            if (Motion.moveNoTurn(Direction.NORTHEAST)) Motion.turn(Direction.WEST);
                            //(2,1)
                            if (Motion.moveNoTurn(Direction.SOUTH)) Motion.turn(Direction.NORTHWEST);
                            //(2,0)
                            if (Motion.moveNoTurn(Direction.SOUTHWEST)) Motion.turn(Direction.NORTH);
                        } else {
                            //(2,1)
                            if (Motion.moveNoTurn(Direction.SOUTH)) Motion.turn(Direction.NORTHWEST);
                            //(2,0)
                            if (Motion.moveNoTurn(Direction.SOUTHWEST)) Motion.turn(Direction.NORTH);
                            //(2,1)
                            if (Motion.moveNoTurn(Direction.EAST)) Motion.turn(Direction.NORTHWEST);
                            //(2,0)
                            if (Motion.moveNoTurn(Direction.NORTHEAST)) Motion.turn(Direction.WEST);

                        }

                    } else if (dx == -1 && dy == -1) {
                        if(G.rc.getCarrying() != null) {
                            Motion.moveNoTurn(Direction.SOUTHWEST);
                        }
                        attackBabyRat(babyRat);
                        if (babyRatDir == Direction.SOUTH || babyRatDir == Direction.SOUTHEAST || babyRatDir == Direction.EAST) {
                            //(2,1)
                            if (Motion.moveNoTurn(Direction.WEST)) Motion.turn(Direction.NORTHEAST);
                            //(2,0)
                            if (Motion.moveNoTurn(Direction.NORTHWEST)) Motion.turn(Direction.EAST);
                            //(2,1)
                            if (Motion.moveNoTurn(Direction.SOUTH)) Motion.turn(Direction.NORTHEAST);
                            //(2,0)
                            if (Motion.moveNoTurn(Direction.SOUTHEAST)) Motion.turn(Direction.NORTH);
                        } else {
                            //(2,1)
                            if (Motion.moveNoTurn(Direction.SOUTH)) Motion.turn(Direction.NORTHEAST);
                            //(2,0)
                            if (Motion.moveNoTurn(Direction.SOUTHEAST)) Motion.turn(Direction.NORTH);
                            //(2,1)
                            if (Motion.moveNoTurn(Direction.WEST)) Motion.turn(Direction.NORTHEAST);
                            //(2,0)
                            if (Motion.moveNoTurn(Direction.NORTHWEST)) Motion.turn(Direction.EAST);


                        }
                    }

                    //(2, 0)
                    else if (dx == 2 && dy == 0) {

                        if (twoDisadvantadge(babyRat)) {
                            rattrap(babyRat);
                            Motion.moveNoTurn(Direction.EAST);
                            if (Motion.moveNoTurn(Direction.NORTHEAST)) Motion.turn(Direction.SOUTHWEST);
                            if (Motion.moveNoTurn(Direction.SOUTHEAST)) Motion.turn(Direction.NORTHWEST);
                        } else if (twoZeroAdvantadge(babyRat)) {
                            if (babyRatDir == Direction.NORTHEAST || babyRatDir == Direction.NORTH || babyRatDir == Direction.NORTHWEST) {
                                if (Motion.moveNoTurn(Direction.SOUTHWEST)) Motion.turn(Direction.NORTHWEST);
                                if (Motion.moveNoTurn(Direction.NORTHWEST)) Motion.turn(Direction.SOUTHWEST);
                            } else {
                                if (Motion.moveNoTurn(Direction.NORTHWEST)) Motion.turn(Direction.SOUTHWEST);
                                if (Motion.moveNoTurn(Direction.SOUTHWEST)) Motion.turn(Direction.NORTHWEST);
                            }

                            attackBabyRat(babyRat);
                        }

                    } else if (dx == -2 && dy == 0) {

                        if (twoDisadvantadge(babyRat)) {
                            rattrap(babyRat);
                            Motion.moveNoTurn(Direction.WEST);
                            if (Motion.moveNoTurn(Direction.NORTHWEST)) Motion.turn(Direction.SOUTHEAST);
                            if (Motion.moveNoTurn(Direction.SOUTHWEST)) Motion.turn(Direction.NORTHEAST);
                        } else if (twoZeroAdvantadge(babyRat)) {

                            if (babyRatDir == Direction.NORTHWEST || babyRatDir == Direction.NORTH || babyRatDir == Direction.NORTHEAST) {
                                if (Motion.moveNoTurn(Direction.SOUTHEAST)) Motion.turn(Direction.NORTHEAST);
                                if (Motion.moveNoTurn(Direction.NORTHEAST)) Motion.turn(Direction.SOUTHEAST);
                            } else {
                                if (Motion.moveNoTurn(Direction.NORTHEAST)) Motion.turn(Direction.SOUTHEAST);
                                if (Motion.moveNoTurn(Direction.SOUTHEAST)) Motion.turn(Direction.NORTHEAST);
                            }

                            attackBabyRat(babyRat);
                        }
                    } else if (dx == 0 && dy == 2) {

                        if (twoDisadvantadge(babyRat)) {
                            rattrap(babyRat);
                            Motion.moveNoTurn(Direction.SOUTH);
                            if (Motion.moveNoTurn(Direction.SOUTHWEST)) Motion.turn(Direction.NORTHEAST);
                            if (Motion.moveNoTurn(Direction.SOUTHEAST)) Motion.turn(Direction.NORTHWEST);
                        } else if (twoZeroAdvantadge(babyRat)) {

                            if (babyRatDir == Direction.NORTHWEST || babyRatDir == Direction.WEST || babyRatDir == Direction.SOUTHWEST) {
                                if (Motion.moveNoTurn(Direction.SOUTHEAST)) Motion.turn(Direction.SOUTHWEST);
                                if (Motion.moveNoTurn(Direction.SOUTHWEST)) Motion.turn(Direction.SOUTHEAST);
                            } else {
                                if (Motion.moveNoTurn(Direction.SOUTHWEST)) Motion.turn(Direction.SOUTHEAST);
                                if (Motion.moveNoTurn(Direction.SOUTHEAST)) Motion.turn(Direction.SOUTHWEST);
                            }

                            attackBabyRat(babyRat);
                        }

                    } else if (dx == 0 && dy == -2) {

                        if (twoDisadvantadge(babyRat)) {
                            rattrap(babyRat);
                            Motion.moveNoTurn(Direction.NORTH);
                            if (Motion.moveNoTurn(Direction.NORTHWEST)) Motion.turn(Direction.SOUTHEAST);
                            if (Motion.moveNoTurn(Direction.NORTHEAST)) Motion.turn(Direction.SOUTHWEST);
                        }
                        if (twoZeroAdvantadge(babyRat)) {

                            if (babyRatDir == Direction.SOUTHWEST || babyRatDir == Direction.WEST || babyRatDir == Direction.NORTHWEST) {
                                if (Motion.moveNoTurn(Direction.NORTHEAST)) Motion.turn(Direction.NORTHWEST);
                                if (Motion.moveNoTurn(Direction.NORTHWEST)) Motion.turn(Direction.NORTHEAST);
                            } else {
                                if (Motion.moveNoTurn(Direction.NORTHWEST)) Motion.turn(Direction.NORTHEAST);
                                if (Motion.moveNoTurn(Direction.NORTHEAST)) Motion.turn(Direction.NORTHWEST);
                            }

                            attackBabyRat(babyRat);
                        }

                    }

                    //(2, 1)
                    else if (dx == 2 && (dy == 1 || dy == -1)) {
                        if (twoDisadvantadge(babyRat)) {
                            rattrap(babyRat);
                            if (dy == 1) if (Motion.moveNoTurn(Direction.SOUTHEAST)) Motion.turn(Direction.WEST);
                            else if (Motion.moveNoTurn(Direction.NORTHEAST)) Motion.turn(Direction.WEST);
                        } else if (twoZeroAdvantadge(babyRat)) {

                            Motion.move(Direction.WEST);
                            attackBabyRat(babyRat);
                        }
                    } else if (dx == -2 && (dy == 1 || dy == -1)) {
                        if (twoDisadvantadge(babyRat)) {
                            rattrap(babyRat);
                            if (dy == 1) if (Motion.moveNoTurn(Direction.SOUTHWEST)) Motion.turn(Direction.EAST);
                            else if (Motion.moveNoTurn(Direction.NORTHWEST)) Motion.turn(Direction.EAST);
                        } else if (twoZeroAdvantadge(babyRat)) {

                            Motion.move(Direction.EAST);
                            attackBabyRat(babyRat);
                        }

                    } else if (dy == 2 && (dx == 1 || dx == -1)) {
                        if (twoDisadvantadge(babyRat)) {
                            rattrap(babyRat);
                            if (dx == 1) if (Motion.moveNoTurn(Direction.NORTHWEST)) Motion.turn(Direction.SOUTH);
                            else if (Motion.moveNoTurn(Direction.NORTHEAST)) Motion.turn(Direction.SOUTH);
                        } else if (twoZeroAdvantadge(babyRat)) {

                            Motion.move(Direction.SOUTH);
                            attackBabyRat(babyRat);
                        }

                    } else if (dy == -2 && (dx == 1 || dx == -1)) {
                        if (twoDisadvantadge(babyRat)) {
                            rattrap(babyRat);
                            if (dx == 1) if (Motion.moveNoTurn(Direction.SOUTHWEST)) Motion.turn(Direction.NORTH);
                            else if (Motion.moveNoTurn(Direction.SOUTHEAST)) Motion.turn(Direction.NORTH);
                        } else if (twoZeroAdvantadge(babyRat)) {

                            Motion.move(Direction.NORTH);
                            attackBabyRat(babyRat);
                        }
                    }


                    //(2, 2)
                    else if (dx == 2 && dy == 2) {
                        if (twoDisadvantadge(babyRat)) {
                            if (Motion.moveNoTurn(Direction.SOUTHEAST)) Motion.turn(Direction.WEST);
                            if (Motion.move(Direction.NORTHWEST)) Motion.turn(Direction.SOUTH);
                        } else if (twoZeroAdvantadge(babyRat)) {

                            Motion.move(Direction.SOUTHWEST);
                            attackBabyRat(babyRat);
                        }

                    } else if (dx == -2 && dy == 2) {
                        if (twoDisadvantadge(babyRat)) {
                            if (Motion.moveNoTurn(Direction.SOUTHWEST)) Motion.turn(Direction.EAST);
                            if (Motion.move(Direction.NORTHEAST)) Motion.turn(Direction.SOUTH);
                        } else if (twoZeroAdvantadge(babyRat)) {

                            Motion.move(Direction.SOUTHEAST);
                            attackBabyRat(babyRat);
                        }
                    } else if (dx == 2 && dy == -2) {
                        if (twoDisadvantadge(babyRat)) {
                            if (Motion.moveNoTurn(Direction.NORTHEAST)) Motion.turn(Direction.WEST);
                            if (Motion.move(Direction.SOUTHEAST)) Motion.turn(Direction.NORTH);
                        } else if (twoZeroAdvantadge(babyRat)) {

                            Motion.move(Direction.NORTHWEST);
                            attackBabyRat(babyRat);
                        }
                    } else if (dx == -2 && dy == -2) {
                        if (twoDisadvantadge(babyRat)) {
                            if (Motion.moveNoTurn(Direction.NORTHWEST)) Motion.turn(Direction.EAST);
                            if (Motion.move(Direction.SOUTHEAST)) Motion.turn(Direction.NORTH);
                        } else if (twoZeroAdvantadge(babyRat)) {

                            Motion.move(Direction.NORTHEAST);
                            attackBabyRat(babyRat);
                        }
                    } else {

                        if (dx == 3 && dy == 0) {
                            if (hasAdvantadge(babyRat)) {
                                if (Motion.move(Direction.WEST)) Motion.turn(Direction.WEST);

                            }
                        } else if (dx == -3 && dy == 0) {
                            if (hasAdvantadge(babyRat)) {
                                if (Motion.move(Direction.EAST)) Motion.turn(Direction.EAST);
                            }
                        } else if (dx == 0 && dy == 3) {
                            if (hasAdvantadge(babyRat)) {
                                if (Motion.move(Direction.SOUTH)) Motion.turn(Direction.SOUTH);
                            }
                        } else if (dx == 0 && dy == -3) {
                            if (hasAdvantadge(babyRat)) {
                                if (Motion.move(Direction.NORTH)) Motion.turn(Direction.NORTH);
                            }
                        } else if (dx == 3 && dy == 1) {
                            if (hasAdvantadge(babyRat)) {
                                if (Motion.move(Direction.SOUTHWEST)) Motion.turn(Direction.WEST);
                            }
                        } else if (dx == 3 && dy == -1) {
                            if (hasAdvantadge(babyRat)) {
                                if (Motion.move(Direction.NORTHWEST)) Motion.turn(Direction.WEST);
                            }
                        } else if (dx == -3 && dy == 1) {
                            if (hasAdvantadge(babyRat)) {
                                if (Motion.move(Direction.SOUTHEAST)) Motion.turn(Direction.EAST);
                            }
                        } else if (dx == -3 && dy == -1) {
                            if (hasAdvantadge(babyRat)) {
                                if (Motion.move(Direction.NORTHEAST)) Motion.turn(Direction.EAST);
                            }
                        } else if (dx == 1 && dy == 3) {
                            if (hasAdvantadge(babyRat)) {
                                if (Motion.move(Direction.SOUTHWEST)) Motion.turn(Direction.SOUTH);
                            }
                        } else if (dx == -1 && dy == 3) {
                            if (hasAdvantadge(babyRat)) {
                                if (Motion.move(Direction.SOUTHEAST)) Motion.turn(Direction.SOUTH);
                            }
                        } else if (dx == 1 && dy == -3) {
                            if (hasAdvantadge(babyRat)) {
                                if (Motion.move(Direction.NORTHEAST)) Motion.turn(Direction.NORTH);
                            }
                        } else if (dx == -1 && dy == -3) {
                            if (hasAdvantadge(babyRat)) {
                                if (Motion.move(Direction.NORTHWEST)) Motion.turn(Direction.NORTH);
                            }
                        } else if (dx == 3 && dy == 2) {
                            if (hasAdvantadge(babyRat)) {
                                if (Motion.move(Direction.SOUTHWEST)) Motion.turn(Direction.WEST);
                            }
                        } else if (dx == 3 && dy == -2) {
                            if (hasAdvantadge(babyRat)) {
                                if (Motion.move(Direction.NORTHWEST)) Motion.turn(Direction.WEST);
                            }
                        } else if (dx == -3 && dy == 2) {
                            if (hasAdvantadge(babyRat)) {
                                if (Motion.move(Direction.SOUTHEAST)) Motion.turn(Direction.EAST);
                            }
                        } else if (dx == -3 && dy == -2) {
                            if (hasAdvantadge(babyRat)) {
                                if (Motion.move(Direction.NORTHEAST)) Motion.turn(Direction.EAST);
                            }
                        } else if (dx == 2 && dy == 3) {
                            if (hasAdvantadge(babyRat)) {
                                if (Motion.move(Direction.SOUTHWEST)) Motion.turn(Direction.SOUTH);
                            }
                        } else if (dx == -2 && dy == 3) {
                            if (hasAdvantadge(babyRat)) {
                                if (Motion.move(Direction.SOUTHEAST)) Motion.turn(Direction.SOUTH);
                            }
                        } else if (dx == 2 && dy == -3) {
                            if (hasAdvantadge(babyRat)) {
                                if (Motion.move(Direction.NORTHEAST)) Motion.turn(Direction.NORTH);
                            }
                        } else if (dx == -2 && dy == -3) {
                            if (hasAdvantadge(babyRat)) {
                                if (Motion.move(Direction.NORTHWEST)) Motion.turn(Direction.NORTH);
                            }
                        } else {

                            //if(fromSqueak || hasAdvantadge(babyRat) || G.rc.getHealth() + 50 >= babyRat.getHealth()) {
                            //   Motion.turn(G.rc.getLocation().directionTo(babyRat.getLocation()));
                            //Motion.turn(G.rc.getLocation().directionTo(babyRat.getLocation()));

                            Motion.bugnavTowards(babyRat.getLocation());
                            //}
                            //else Motion.bugnavAwayNoTurning(babyRat.getLocation());
                        }
                        Motion.turn(G.rc.getLocation().directionTo(babyRat.getLocation()));

                    }


                    //}

                    tryToThrow();
                    rattrap(babyRat);
                    attackBabyRat(babyRat);
                    //tryAttackEverywhere();
                    //ratnap(babyRat);
                    babyRatMicro();

                    if (G.rc.getCarrying() == null && !G.rc.isMovementReady())
                        Motion.turn(G.rc.getLocation().directionTo(babyRat.getLocation()));
                }
        }
        else {


            if (kingRat != null) {

                Motion.bugnavTowards(kingRat.getLocation());
                babyRatMicro();
            }


            if(G.rc.getCarrying() != null) {
                RobotInfo r = G.rc.getCarrying();
                if(r.getRawCheeseAmount() == 0 && r.getTeam() == G.rc.getTeam()) {
                    for(Direction d: G.DIRECTIONS) {
                        if(G.rc.canDropRat(d)) G.rc.dropRat(d);
                    }
                }
            }



            if (G.rc.getRoundNum() >= 1940 && G.rc.canBecomeRatKing()) {
                G.rc.becomeRatKing();
            }


            if ((mineset.size() > kings.size() && G.rc.getCurrentRatCost() > (50 + 5 * kings.size()) && G.rc.getGlobalCheese() > 1200 && ((G.rc.getRoundNum() < 1200 && kings.size() >= 2) || kings.size() == 1))  || (G.rc.getRoundNum() > 1940 && kings.size() == 1)) {
                MapInfo[] nearby = G.rc.senseNearbyMapInfos(-1);
                MapLocation thatMine = null;
                for(MapInfo x : nearby) {
                    if(x.hasCheeseMine()) {
                        thatMine = x.getMapLocation();
                    }
                }
                if(thatMine == null) thatMine = lastMine;

                if (enemyRobots.length == 0 && thatMine != null && thatMine.equals(G.rc.getLocation()))
                    G.rc.squeak(2 * (16384 * (1024 * G.rc.getLocation().x + G.rc.getLocation().y) + G.rc.getID()) + 1);

                Message[] squeaks = G.rc.readSqueaks(G.rc.getRoundNum() - 1);
                int lowestId = 99999;
                MapLocation toGoTo = null;

                for (Message m : squeaks) {
                    int msg = m.getBytes();

                    if (msg % 2 == 1) {
                        msg -= 1;

                        msg /= 2;
                        int id = msg % 16384;
                        msg /= 16384;
                        int x = msg / 1024;
                        int y = msg % 1024;

                        MapLocation loc = new MapLocation(x, y);
                        if (id < lowestId) {
                            lowestId = id;
                            toGoTo = loc;
                        }
                    }
                }


                if(toGoTo != null) MotionCodeGen.bugnavTowardsExplore(toGoTo);

                else if(thatMine != null){
                    if(G.rc.getLocation().equals(thatMine)) return;
                    if(G.rc.canSenseLocation(thatMine)) {
                        if(G.rc.senseRobotAtLocation(thatMine) != null) {
                            if(G.rc.getLocation().distanceSquaredTo(thatMine) <= 2) return;
                        }
                    }
                    MotionCodeGen.bugnavTowardsExplore(thatMine);
                }



                if (G.rc.canBecomeRatKing() && thatMine != null && G.rc.getLocation().distanceSquaredTo(thatMine) <= 17) {
                    G.rc.becomeRatKing();
                }
            }

            RobotInfo[] team = G.rc.senseNearbyRobots();
            for (RobotInfo e : team) {
                if (!e.getTeam().equals(G.rc.getTeam())) continue;
                if (e.getRawCheeseAmount()>=100&&G.rc.getRawCheese()<=20) {
                    if (G.rc.canCarryRat(e.getLocation())) {
                        G.rc.carryRat(e.getLocation());
                        roundPickedUp = G.rc.getRoundNum();
                    }
                    else MotionCodeGen.bugnavTowardsExplore(e.getLocation());
                }
            }
            MapInfo[] cheeses = G.rc.senseNearbyMapInfos(-1);
            MapLocation closestCheese = null;
            int bestCheese = 100;
            for (MapInfo c : cheeses) {
                if (c.getCheeseAmount() > 0) {
                    if (G.rc.getLocation().distanceSquaredTo(c.getMapLocation()) < bestCheese) {
                        closestCheese = c.getMapLocation();
                        bestCheese = G.rc.getLocation().distanceSquaredTo(c.getMapLocation());
                        if (G.rc.canPickUpCheese(closestCheese) && G.rc.getRawCheese() < 300 && G.me.distanceSquaredTo(king)<125 ||
                                G.rc.canPickUpCheese(closestCheese) && G.rc.getRawCheese() < 100) {
                            G.rc.pickUpCheese(closestCheese);
                        }
                    }
                }
            }

            if (closestCheese != null && G.rc.getRawCheese() <= 240) {
                if (G.rc.getRawCheese() < 60 && G.me.distanceSquaredTo(king)<450 ||
                        G.rc.getRawCheese()<40) Motion.bugnavTowards(closestCheese);
                if (G.rc.canPickUpCheese(closestCheese)) {
                    G.rc.pickUpCheese(closestCheese);
                }
            } else if (G.rc.getRawCheese() > 0) {

                MotionCodeGen.bugnavTowardsExplore(king);

                for (Direction d : Direction.allDirections()) {
                    if (G.rc.canTransferCheese(king.add(d), G.rc.getRawCheese())) {
                        if(newMine != null && !mines.contains(newMine)) {
                            G.rc.squeak(1024 * (1024 * newMine.x + newMine.y) + 1);
                        }

                        G.rc.transferCheese(king.add(d), G.rc.getRawCheese());


                    }
                }


            }


            MapLocation ahead = G.rc.getLocation().add(G.rc.getDirection());
//            if (G.rc.canSenseLocation(ahead)) {
//                if (G.rc.canRemoveDirt(ahead) && enemyRobots.length == 0 && dirtcnt < G.rc.getRoundNum() / 25) {
//                    G.rc.removeDirt(ahead);
//                }
//            }

            babyRatMicro();
            int rand = G.rc.getID() % 1000+50;
            if (G.rc.getRawCheese() > 0) MotionCodeGen.bugnavTowardsExplore(king);
            else if (G.rc.isMovementReady() && (rand>=mineset.size()*30+Math.min(500,G.rc.getRoundNum()) || (!mineLogic()))) {
                G.indicatorString.append("  spreading  ");
                spreadingBehavior();
            }
            babyRatMicro();
        }

        tryToThrow();




        lastHealth = G.rc.getHealth();
        lastBabyRat = babyRat;
        if(G.rc.isBeingCarried()) {
            lastBabyRat = G.rc.senseRobotAtLocation(G.rc.getLocation());
        }
    }


    /***
     mine logic: as our collection of mines grows, increase amonut of mine collectors
     scoring func: balance distance from current location and distance from king (minimize sum of dists) maybe add randomness
     to prevent all rats going to same mine
     if you go to mine and dont collect any cheese then repeat scoring func to choose next mine not including all that have been visited
     maybe choose a suffix of ones visited rather than not included all that haven't been visited
     consider switching to explore if you do not achieve sufficient cheese or some other parameter
     */
    private static boolean mineLogic() throws GameActionException {
        if (visitedMines.size()==Math.min(mineset.size(),Math.max(3,G.rc.getID()%10))) visitedMines = new HashSet<>();
        if(mineset.isEmpty()) return false;
        if (curmine == null || (G.me.distanceSquaredTo(curmine)<=1) || (G.me.distanceSquaredTo(curmine)<=9&&mturns==0)) {
            curmine = mineScore();
            if (curmine == null) return false;
            visitedMines.add(curmine);
            // mturns = 5;
        }
        if (G.me.distanceSquaredTo(curmine)<9&&mturns==0) mturns = 4;
        else if (G.me.distanceSquaredTo(curmine)<9) mturns--;
        //if (curmine==null) return false;
        G.indicatorString.append("Curmine" + curmine.x + " "+ curmine.y + " ");
        G.indicatorString.append("  collecting  ");
        MotionCodeGen.bugnavTowardsExplore(curmine);
        return true;

    }
    private static MapLocation mineScore() throws GameActionException {
        int bestScore = Integer.MAX_VALUE; MapLocation bestMine = null, best2 = null, best3 = null;
        for (MapLocation e : mineset) {
            if (visitedMines.contains(e)) continue;
            int mapW = G.rc.getMapWidth();
            int mapH = G.rc.getMapHeight();
            MapLocation mapCenter = new MapLocation(mapW / 2, mapH / 2);
            int centerDist = mapCenter.distanceSquaredTo(e);
            int score = G.me.distanceSquaredTo(e) + king.distanceSquaredTo(e)+(centerDist/5);
            if (score<bestScore) {
                bestScore = score; best3 = best2; best2 = bestMine; bestMine = e;
            }
        }
        // if (bestMine!=null&&G.me.distanceSquaredTo(bestMine)>G.me.distanceSquaredTo(king)) return null;
     //   if (best3!=null && G.rc.getID()%1000 > 400&&G.rc.getID() %1000< 600) return best3;
        if (best2!=null && G.rc.getID()%1000>300 & G.rc.getID() %1000< 500) return best2;
        return bestMine;
    }

    public static void babyRatMicro() throws Exception {
        for(Direction d : Direction.allDirections()) {
            if(G.rc.canSenseLocation(G.rc.getLocation().add(d))) {
                RobotInfo r = G.rc.senseRobotAtLocation(G.rc.getLocation().add(d));
                if(r != null && r.getTeam() == G.opponentTeam) {
                    if(G.rc.canCarryRat(G.rc.getLocation().add(d)) ) {
                        G.rc.carryRat(G.rc.getLocation().add(d));
                        roundPickedUp = G.rc.getRoundNum();
                    }
                }
            }

        }

        for(Direction d : Direction.allDirections()) {
            if(G.rc.canAttack(G.rc.getLocation().add(d))) {
                int cheeseToUse = Math.min(G.rc.getGlobalCheese(), 0);

                if(G.rc.getRawCheese()/2 >= 5) {
                    cheeseToUse = 5;
                }
                else {
                    cheeseToUse = (Math.max(0, G.rc.getRawCheese()));
                }
                G.rc.attack(G.rc.getLocation().add(d), cheeseToUse);
            }
        }

        for(Direction d : Direction.allDirections()) {
            if(G.rc.canSenseLocation(G.rc.getLocation().add(d))) {
                RobotInfo r = G.rc.senseRobotAtLocation(G.rc.getLocation().add(d));
                if(r != null && r.getTeam() == G.opponentTeam) {
                    if(G.rc.canCarryRat(G.rc.getLocation().add(d)) ) {
                        G.rc.carryRat(G.rc.getLocation().add(d));
                        roundPickedUp = G.rc.getRoundNum();
                    }
                }
            }

        }
    }

    private static void spreadingBehavior() throws Exception {
        MapLocation kingLocation = new MapLocation(G.rc.readSharedArray(0), G.rc.readSharedArray(1));

        if (G.rc.senseNearbyRobots().length >= 10) {
            Motion.spreadRandomly();
        } else {
            // canonical current location
            MapLocation me = G.rc.getLocation();

            // initialize orientation if needed
            if (ori == -1) {
                dori = me.directionTo(king).opposite();
                // if dori is a diagonal-like direction set ori=0 else ori=2 (preserve your intent but explicit)
                if (dori == Direction.NORTHEAST || dori == Direction.NORTHWEST
                        || dori == Direction.SOUTHEAST || dori == Direction.SOUTHWEST) {
                    ori = 0;
                } else {
                    ori = 2;
                }
            }

            int mapW = G.rc.getMapWidth();
            int mapH = G.rc.getMapHeight();

            // if the next step in dori is too close to the border, pick an adjusted direction
            MapLocation oneStep = me.add(dori);
            boolean nearEdge = (mapW - oneStep.x < 4) || (oneStep.x < 4) || (mapH - oneStep.y < 4) || (oneStep.y < 4);
            if (nearEdge) {
                if (ori == 0) {
                    // diagonal-oriented adjustments (kept your original heuristics but cleaned)
                    if (me.x < 5 && me.x <= me.y && me.x <= (mapH - me.y)) {
                        dori = (dori == Direction.NORTHWEST) ? Direction.NORTHEAST : Direction.SOUTHEAST;
                    } else if (me.y < 5 && me.y <= me.x && me.y <= (mapW - me.x)) {
                        dori = (dori == Direction.SOUTHWEST) ? Direction.NORTHWEST : Direction.NORTHEAST;
                    } else if (mapW - me.x < 5 && (mapW - me.x) <= me.y && (mapW - me.x) <= (mapH - me.y)) {
                        dori = (dori == Direction.NORTHEAST) ? Direction.NORTHWEST : Direction.SOUTHWEST;
                    } else {
                        dori = (dori == Direction.NORTHWEST) ? Direction.SOUTHWEST : Direction.SOUTHEAST;
                    }
                } else if (ori == 1) {
                    // straight-oriented adjustments
                    if (me.x < 5 && me.x <= me.y && me.x <= (mapH - me.y)) {
                        dori = Direction.EAST;
                    } else if (me.y < 5 && me.y <= me.x && me.y <= (mapW - me.x)) {
                        dori = Direction.NORTH;
                    } else if (mapW - me.x < 5 && (mapW - me.x) <= me.y && (mapW - me.x) <= (mapH - me.y)) {
                        dori = Direction.WEST;
                    } else {
                        dori = Direction.SOUTH;
                    }
                } else {
                    // mixed heuristics
                    if (me.x < 5 && me.x <= me.y && me.x <= (mapH - me.y)) {
                        dori = (me.y < mapH/2) ? Direction.NORTHEAST : Direction.SOUTHEAST;
                    } else if (me.y < 5 && me.y <= me.x && me.y <= (mapW - me.x)) {
                        dori = (me.y < mapW/2) ? Direction.NORTHEAST : Direction.NORTHWEST;
                    } else if (mapW - me.x < 5 && (mapW - me.x) <= me.y && (mapW - me.x) <= (mapH - me.y)) {
                        dori = (me.y < mapH/2) ? Direction.NORTHWEST : Direction.SOUTHWEST;
                    } else {
                        dori = (me.y < mapW/2) ? Direction.SOUTHEAST : Direction.SOUTHWEST;
                    }
                }
                ori = (ori + 1) % 3;
            }

            // compute farthest reachable point in dori while keeping a safe margin (margin = 2)

            if (dori == Direction.CENTER) {
                // no forward direction; pick a fallback (north) so the rat moves
                dori = Direction.NORTH;
            }

            final int MARGIN = 2; // keep this many tiles from edge
            // move tmp forward while next remains inside safe bounds
            if (nearEdge || tmp == null) {
                MapLocation tmpp = me; MapLocation next = tmpp.add(dori);
                while (next.x >= MARGIN && next.x <= mapW - 1 - MARGIN && next.y >= MARGIN && next.y <= mapH - 1 - MARGIN) {
                    tmpp = next;
                    next = tmpp.add(dori);
                }
                tmp = tmpp;
            }

            // If tmp equals me (no room forward), try rotating dori clockwise to find a direction with room
            if (tmp.equals(me)) {
                Direction[] allDirs = new Direction[] {
                        Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
                        Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
                };
                // find index of current dori
                int idx = 0;
                for (int i = 0; i < allDirs.length; i++) if (allDirs[i] == dori) { idx = i; break; }
                boolean found = false;
                for (int k = 1; k < 8 && !found; k++) {
                    Direction cand = allDirs[(idx + k) % 8];
                    // test if cand has at least one safe step
                    MapLocation testNext = me.add(cand);
                    if (testNext.x >= MARGIN && testNext.x <= mapW - 1 - MARGIN && testNext.y >= MARGIN && testNext.y <= mapH - 1 - MARGIN) {
                        // compute farthest in cand
                        MapLocation ttmp = me;
                        MapLocation tnext = ttmp.add(cand);
                        while (tnext.x >= MARGIN && tnext.x <= mapW - 1 - MARGIN && tnext.y >= MARGIN && tnext.y <= mapH - 1 - MARGIN) {
                            ttmp = tnext;
                            tnext = ttmp.add(cand);
                        }
                        tmp = ttmp;
                        dori = cand;
                        found = true;
                    }
                }
                // if still not found, tmp==me and we'll try to move one step (fallback) below
            }

            // if tmp still equals current location, make a one-step move in some safe direction (fallback)
            if (tmp.equals(me)) {
                // try the 8 neighbors for any legal on-map step (prioritize cardinal then diagonal)
                Direction[] tryOrder = new Direction[] {
                        Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST,
                        Direction.NORTHEAST, Direction.SOUTHEAST, Direction.SOUTHWEST, Direction.NORTHWEST
                };
                for (Direction d : tryOrder) {
                    MapLocation cand = me.add(d);
                    if (cand.x >= 0 && cand.x < mapW && cand.y >= 0 && cand.y < mapH) {
                        tmp = cand;
                        dori = d;
                        break;
                    }
                }
            }

            // Finally, navigate toward tmp (tmp should be a different position normally)
            if (!tmp.equals(me)) {
                {
                    MotionCodeGen.bugnavTowardsExplore(tmp);
                }
            } else {
                // no move possible; optionally rotate in place to vary future choices
                // e.g., pick a new dori next tick:
                ori = -1;
            }

//               int centerX = G.rc.getMapWidth() / 2;
//               int centerY = G.rc.getMapHeight() / 2;
//               MapLocation mapCenter = new MapLocation(centerX, centerY);
//
//               double ratio = 0.1 + ((G.rc.getID() + ((int) Math.floor(G.rc.getRoundNum() / 40) * 20)) % 80) / 100.0;
//               int targetX = (int) Math.floor(ratio * G.rc.getMapWidth());
//               int targetY = (int) Math.floor((1 - ratio) * G.rc.getMapHeight());
//
//
//               int dx = G.rc.getID() % (7);
//               if (kingLocation.x > mapCenter.x) {
//                   targetX += 2 * (dx - 3);
//               } else {
//                   targetX -= 2 * (dx - 3);
//               }
//
//               int dy = (G.rc.getID() * 5) % (7);
//               if (kingLocation.x > mapCenter.y) {
//                   targetY += 2 * (dy - 3);
//               } else {
//                   targetY -= 2 * (dy - 3);
//               }
//
//
//               MapLocation gatherPoint = new MapLocation(targetX, targetY);
//
//
//               Motion.bugnavTowards(gatherPoint);
        }

    }


    private static boolean ratnap(RobotInfo babyRat) throws  Exception {
       // if(G.rc.canSenseRobotAtLocation(babyRat.getLocation())) babyRat = G.rc.senseRobotAtLocation(babyRat.getLocation());
        if(!G.rc.isActionReady()) return false;
        if(G.rc.getTeam() == babyRat.getTeam()) return false;
        if(G.rc.getCarrying() != null) return false;


        if(G.rc.canCarryRat(babyRat.getLocation())) {
            G.rc.carryRat(babyRat.getLocation());
            roundPickedUp = G.rc.getRoundNum();
            return true;
        }

        for(Direction d : Direction.allDirections()) {
            if(!G.rc.canMove(d)) continue;

            MapLocation future = G.rc.getLocation().add(d);
            if(future.equals(babyRat.getLocation())) continue;


            if(future.isAdjacentTo(babyRat.getLocation())) {
                Direction ratToFuture = babyRat.getLocation().directionTo(future);
                Direction actualDirection = babyRat.getDirection();

                Direction dirRequired = future.directionTo(babyRat.getLocation());
                if(dirRequired == G.rc.getDirection() || dirRequired.rotateRight() == G.rc.getDirection() || dirRequired.rotateLeft() == G.rc.getDirection()) {
                    //you good
                }
                else {
                    if(!G.rc.canTurn()) continue;
                }

                /*
                if(babyRat.getHealth() <= 10) {
                    G.rc.move(d);
                    if(G.rc.canTurn()) G.rc.turn(future.directionTo(babyRat.getLocation()));
                    if(G.rc.canAttack(babyRat.getLocation())) G.rc.attack(babyRat.getLocation());
                }

                else*/ if(babyRat.getHealth() < G.rc.getHealth()) {
                    if(dirRequired == d) Motion.turn(dirRequired);
                    G.rc.move(d);
                    if(G.rc.canTurn()) G.rc.turn(future.directionTo(babyRat.getLocation()));


                    if(babyRat.getID() == -1) {
                        if(G.rc.canSenseRobotAtLocation(babyRat.getLocation())) {
                            babyRat = G.rc.senseRobotAtLocation(babyRat.getLocation());
                            if(G.rc.getTeam().equals(babyRat.getTeam())) return false;
                        }
                        else return false;
                    }

                    if(G.rc.canCarryRat(babyRat.getLocation())) {
                        //System.out.println(babyRat);
                        G.rc.carryRat(babyRat.getLocation());
                        roundPickedUp = G.rc.getRoundNum();
                        return true;
                    }

                }
                else if(actualDirection != ratToFuture && actualDirection.rotateLeft() != ratToFuture && actualDirection.rotateRight() != ratToFuture) {
                    if(dirRequired == d) Motion.turn(dirRequired);
                    G.rc.move(d);
                    if(G.rc.canTurn()) G.rc.turn(future.directionTo(babyRat.getLocation()));


                    if(babyRat.getID() == -1) {
                        if(G.rc.canSenseRobotAtLocation(babyRat.getLocation())) {
                            babyRat = G.rc.senseRobotAtLocation(babyRat.getLocation());
                            if(G.rc.getTeam().equals(babyRat.getTeam())) return false;
                        }
                        else return false;
                    }

                    if(G.rc.canCarryRat(babyRat.getLocation())) {
                        //System.out.println(babyRat);
                        G.rc.carryRat(babyRat.getLocation());
                        roundPickedUp = G.rc.getRoundNum();
                        return true;
                    }
                }




            }
        }
        return false;
    }

    public static void attackBabyRat(RobotInfo babyRat) throws Exception {
        if(G.rc.canAttack(babyRat.getLocation())) {
            int cheeseToUse = Math.min(G.rc.getGlobalCheese(), 0);
            if(babyRat.getHealth() == G.rc.getHealth() || babyRat.getHealth() == 100) cheeseToUse = Math.min(G.rc.getGlobalCheese(), 1);

            if(G.rc.getRawCheese()/2 >= 10) {
                cheeseToUse = Math.max(cheeseToUse, G.rc.getRawCheese() - 20);
            }
            else if(G.rc.getRawCheese()/2 >= 5) {
                cheeseToUse = Math.max(cheeseToUse, 5);
            }
            else {
                cheeseToUse = Math.max(cheeseToUse, Math.max(0, G.rc.getRawCheese()));
            }
            G.rc.attack(babyRat.getLocation(), cheeseToUse);
        }
    }

    public static void tryAttackEverywhere() throws Exception {
        for(Direction d : G.DIRECTIONS) {
            if(G.rc.canAttack(G.rc.getLocation().add(d))) {
                G.rc.attack(G.rc.getLocation().add(d));
                return;
            }
        }
    }

    public static void rattrap(RobotInfo babyRat) throws Exception {
        MapLocation loc = babyRat.getLocation();

        if(G.rc.getGlobalCheese() < 150) return;
        if(!G.rc.canSenseRobotAtLocation(loc)) return;
        RobotInfo r = G.rc.senseRobotAtLocation(loc);
        if(r.getTeam() == G.rc.getTeam()) return;
        if(r.getCarryingRobot() != null) return;
        if(r.getHealth() <= 10) return;
        //if(G.rc.getRawCheese() == 0) return;
        //System.out.println(r);

        Direction[] order = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTHEAST, Direction.NORTHWEST, Direction.SOUTHEAST, Direction.SOUTHWEST};

        for(Direction d : order) {
            MapLocation l = loc.add(d);
            if(d == Direction.EAST || d == Direction.WEST || d == Direction.NORTH || d == Direction.SOUTH) {
                if (G.rc.canSenseLocation(l)) {
                    MapInfo i = G.rc.senseMapInfo(l);
                    if (i.getTrap().equals(TrapType.RAT_TRAP)) return;
                }
            }

        }
        for(Direction d : order) {
            MapLocation l = loc.add(d);
            if(d == babyRat.getDirection() || d.rotateLeft() == babyRat.getDirection() || d.rotateRight() == babyRat.getDirection()) {

                if (G.rc.canPlaceRatTrap(l)) {
                    G.rc.placeRatTrap(l);
                }
            }
        }

    }

    public static boolean hasAdvantadge(RobotInfo babyRat) throws Exception {
        if(G.rc.canSenseRobotAtLocation(babyRat.getLocation())) {
            if(G.rc.senseRobotAtLocation(babyRat.getLocation()).getCarryingRobot() != null) return true;
        }
        return G.rc.getHealth() >= babyRat.getHealth(); //|| G.rc.getRawCheese() < babyRat.getRawCheeseAmount();
    }

    public static boolean twoZeroAdvantadge(RobotInfo babyRat) throws Exception {
        if(G.rc.getCarrying() != null) return false;
        if(G.rc.canSenseRobotAtLocation(babyRat.getLocation())) {
            if(G.rc.senseRobotAtLocation(babyRat.getLocation()).getCarryingRobot() != null) return true;
        }
        return G.rc.getHealth() >= babyRat.getHealth() && G.rc.isActionReady() && G.rc.getCarrying() == null;
    }

    public static boolean twoDisadvantadge(RobotInfo babyRat) throws Exception {
        if(G.rc.canSenseRobotAtLocation(babyRat.getLocation())) {
            if(G.rc.senseRobotAtLocation(babyRat.getLocation()).getCarryingRobot() != null) return false;
        }

        int newDist;
        if(babyRat.getLocation().distanceSquaredTo(G.rc.getLocation()) == 4) {
            newDist = 10;

        }
        else if(babyRat.getLocation().distanceSquaredTo(G.rc.getLocation()) == 5) newDist = 13;
        else newDist = 16;
        RobotInfo[] enemies = G.rc.senseNearbyRobots( newDist, G.opponentTeam);
        RobotInfo[] allies = G.rc.senseNearbyRobots(16, G.rc.getTeam());
        int totalHealth = 0;
        int cnt = 0;
        for(RobotInfo x : enemies) {
            if(x.getType().isBabyRatType()) {
                totalHealth += x.getHealth();
                cnt += 1;
            }
        }
        cnt -= allies.length;

        return babyRat.getHealth() > G.rc.getHealth() || cnt > 2 || G.rc.getCarrying() != null;


        //return babyRat.getHealth() > G.rc.getHealth();
    }

    public static boolean tryToThrow() throws  Exception{
        RobotInfo temp = G.rc.getCarrying();

        if(temp == null || temp.getTeam() == G.rc.getTeam()) return true;


        for (int i = 0; i < 3; i++) {
            Direction dir = G.rc.getDirection();
            if (i == 1) {
                dir = dir.rotateLeft();
            } else if (i == 2) {
                dir = dir.rotateRight();
            }

            MapLocation curr = G.rc.getLocation().add(dir);
            if (G.rc.canSenseLocation(curr)) {
                MapInfo m_ = G.rc.senseMapInfo(curr);

                if (!m_.isPassable()) continue;

                while (G.rc.canSenseLocation(curr)) {
                    MapInfo m = G.rc.senseMapInfo(curr);
                    RobotInfo r = G.rc.senseRobotAtLocation(curr);
                    if(r != null && r.getTeam() == G.rc.getTeam()) break;
                    if (m.isDirt() || m.isWall() || (r != null && r.getTeam() != G.rc.getTeam())) {
                        if(!G.rc.isActionReady() && r != null) {
                            return false;
                        }

                        Motion.turn(dir);


                        if (G.rc.canThrowRat()) {
                            G.rc.throwRat();
                            //if(r == null || !r.getLocation().isWithinDistanceSquared(G.rc.getLocation(), 15)) Motion.move(dir);
                            return true;
                        }
                    }
                    curr = curr.add(dir);
                }
            }
        }

        RobotInfo nearestRat = nearestBabyRat();

        for(Direction d : G.DIRECTIONS) {
            if(G.rc.canMove(d)) {
                if(nearestRat != null && nearestRat.getLocation().isWithinDistanceSquared(G.rc.getLocation().add(d), 4)) continue;

                for (int i = 0; i < 3; i++) {
                    Direction dir = G.rc.getDirection();
                    if (i == 1) {
                        dir = dir.rotateLeft();
                    } else if (i == 2) {
                        dir = dir.rotateRight();
                    }

                    MapLocation curr = G.rc.getLocation().add(d).add(dir);
                    if (G.rc.canSenseLocation(curr)) {
                        MapInfo m_ = G.rc.senseMapInfo(curr);

                        if (!m_.isPassable() && !m_.getMapLocation().equals(G.rc.getLocation())) continue;

                        while (G.rc.canSenseLocation(curr)) {
                            MapInfo m = G.rc.senseMapInfo(curr);
                            RobotInfo r = G.rc.senseRobotAtLocation(curr);
                            if(r != null && r.getTeam() == G.rc.getTeam()) break;
                            if (m.isDirt() || m.isWall() || (r != null && r.getTeam() != G.rc.getTeam())) {
                                if(!G.rc.isActionReady() && r != null) {
                                    return false;
                                }

                                Motion.moveNoTurn(d);
                                Motion.turn(dir);


                                if (G.rc.canThrowRat()) {
                                    G.rc.throwRat();
                                    //if(r == null || !r.getLocation().isWithinDistanceSquared(G.rc.getLocation(), 15)) Motion.move(dir);
                                    return true;
                                }
                            }
                            curr = curr.add(dir);
                        }
                    }
                }
            }
        }

        if (G.rc.getRoundNum() - roundPickedUp >= 9) {
            Motion.turn(G.rc.getDirection().opposite());
        }

        return true;
    }

    public static RobotInfo nearestBabyRat() throws Exception {
        RobotInfo[] enemyRobots = G.rc.senseNearbyRobots(-1, G.opponentTeam);


        RobotInfo babyRat = null;
        RobotInfo kingRat = null;
        int bestDist = 100;
        for(RobotInfo x : enemyRobots) {
            if (x.getType().isBabyRatType()) {
                if(!Motion.canReachYou(x.getLocation())) continue;
                int dist = x.getLocation().distanceSquaredTo(G.rc.getLocation());
                if(dist == 0) continue;

                if (babyRat == null || dist < bestDist) {
                    bestDist = dist;
                    babyRat = x;
                }
            }
            else if(x.getType().isRatKingType()) {
                kingRat = x;
            }
        }



        boolean fromSqueak = false;
        if(babyRat == null) {
            //tryToThrow();

            Message[] squeaks = G.rc.readSqueaks(G.rc.getRoundNum()-1);
            int cloesestSqueak = 100;
            MapLocation closest = null;

            for(Message s : squeaks) {

                int msg = s.getBytes();
                if(msg % 2 == 1) {
                    continue;
                }
                msg /= 2;
                int health = msg % 128;

                msg/=128;
                Direction dir = G.DIRECTIONS[msg % 8];

                msg /= 8;
                int x = msg /1024;
                int y = msg % 1024;


                MapLocation l = new MapLocation(x, y);
                if(l.distanceSquaredTo(G.rc.getLocation()) < cloesestSqueak) {
                    cloesestSqueak = l.distanceSquaredTo(G.rc.getLocation());
                    closest = l;
                    fromSqueak = true;
                    babyRat = new RobotInfo(-1, G.opponentTeam, UnitType.BABY_RAT, health, closest, dir, 0, 0, null);
                }
            }

        }


        return babyRat;
    }



}
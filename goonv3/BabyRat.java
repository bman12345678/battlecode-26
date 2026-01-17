package goonv3;

import battlecode.common.*;

import java.util.Map;

public class BabyRat {
    // Constants

    private static int lastHealth = 100;
    public static MapLocation lastMine = null;
    private static int roundsCarrying = 0;
    private static int catTurnsLeft = 0;
    private static MapLocation catLoc = null;

   public static void run() throws Exception {



       MapInfo[] mapInfos = G.rc.senseNearbyMapInfos(-1);
       for(MapInfo info : mapInfos) {
           if(info.hasCheeseMine()) {
               if(lastMine == null) lastMine = info.getMapLocation();
           }
       }

       if(catTurnsLeft > 0 && catLoc != null) {
            catTurnsLeft -= 1;
            Motion.bugnavAway(catLoc);
       }

        boolean attacked = false;
        int currentHealth = G.rc.getHealth();
        if(currentHealth < lastHealth) attacked = true;

       if(G.rc.getRoundNum() >= 1940 || ((G.rc.getID() % 7) > 5 && G.rc.readSharedArray(2) == 1) || (G.rc.getID() % 20 == 0 && G.rc.getCurrentRatCost() >= 80)) {
           Motion.bugnavTowards(new MapLocation(G.rc.readSharedArray(0), G.rc.readSharedArray(1)));
       }

        RobotInfo[] enemyRobots = G.rc.senseNearbyRobots(-1, G.opponentTeam);
        if(enemyRobots.length == 0 && attacked) {
            Motion.turn(G.rc.getDirection().opposite());

            enemyRobots = G.rc.senseNearbyRobots(-1, G.opponentTeam);
            if(enemyRobots.length == 0) {
                Motion.moveNoTurn(G.rc.getDirection().opposite());
                enemyRobots = G.rc.senseNearbyRobots(-1, G.opponentTeam);
            }
       }




        if(G.rc.getCarrying() != null)  {
            if(roundsCarrying >= 8) {
                Motion.turn(G.rc.getDirection().opposite());
            }

            boolean thrown = false;
            for(int i = 0; i < 3; i++ ) {
                Direction dir = G.rc.getDirection();
                if(i == 1) {
                    dir = dir.rotateLeft();
                }
                else if( i== 2) {
                    dir = dir.rotateRight();
                }

                MapLocation curr = G.rc.getLocation().add(dir);
                if(G.rc.canSenseLocation(curr)) {
                    MapInfo m_ = G.rc.senseMapInfo(curr);

                    if(!m_.isPassable()) continue;

                    while (G.rc.canSenseLocation(curr)) {
                        MapInfo m = G.rc.senseMapInfo(curr);
                        RobotInfo r = G.rc.senseRobotAtLocation(curr);
                        if (roundsCarrying >= 6 || m.isDirt() || m.isWall() || !m.isPassable() || (r != null && r.getTeam() != G.rc.getTeam())) {
                            Motion.turn(dir);
                            if (G.rc.canThrowRat()) {
                                G.rc.throwRat();
                                thrown = true;
                                break;
                            }
                        }
                        curr = curr.add(dir);
                    }
                }

                if(thrown) break;
            }
        }
        else {
            roundsCarrying = 0;
        }


       RobotInfo[] cats = G.rc.senseNearbyRobots(-1, Team.NEUTRAL);
       if(cats.length > 0) {
           RobotInfo cat = cats[0];


            Direction toCat = G.me.directionTo(cat.getLocation());

        // Try to place traps in a line to block the 2x2 cat

            MapLocation trapLoc = G.rc.adjacentLocation(toCat);

            if (G.rc.canPlaceCatTrap(trapLoc)) {
                G.rc.placeCatTrap(trapLoc);
            }

           for(Direction d : Direction.allDirections()) {
               if(G.rc.canAttack(G.rc.getLocation().add(d))) {
                   G.rc.attack(G.rc.getLocation().add(d));
               }
           }

           catLoc = cat.getLocation();
           catTurnsLeft = 3;
           Motion.bugnavAway(cat.getLocation());
       }




        RobotInfo babyRat = null;
       RobotInfo kingRat = null;
        int bestDist = 100;
        for(RobotInfo x : enemyRobots) {
            if (x.getType().isBabyRatType()) {
                if (babyRat == null || x.getLocation().distanceSquaredTo(G.rc.getLocation()) < bestDist) {
                    bestDist = x.getLocation().distanceSquaredTo(G.rc.getLocation());
                    babyRat = x;
                }
            }
            else if(x.getType().isRatKingType()) {
                kingRat = x;
            }
        }

        boolean fromSqueak = false;
        if(babyRat == null) {
            Message[] squeaks = G.rc.readSqueaks(G.rc.getRoundNum()-1);
            int cloesestSqueak = 100;
            MapLocation closest = null;

            for(Message s : squeaks) {
                int msg = s.getBytes();
                Direction dir = G.DIRECTIONS[msg % 8];

                msg /= 8;
                int x = msg /1024;
                int y = msg % 1024;


                MapLocation l = new MapLocation(x, y);
                if(l.distanceSquaredTo(G.rc.getLocation()) < cloesestSqueak) {
                    cloesestSqueak = l.distanceSquaredTo(G.rc.getLocation());
                    closest = l;
                    fromSqueak = true;
                    babyRat = new RobotInfo(-1, G.opponentTeam, UnitType.BABY_RAT, 100, closest, dir, 0, 0, null);
                }
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
            int msg = 8 * (babyRat.getLocation().x * 1024 + babyRat.getLocation().y) + directionIndex;
            if(!fromSqueak) G.rc.squeak(msg);

            int myX = G.rc.getLocation().x;
            int myY = G.rc.getLocation().y;
            Direction myDir = G.rc.getDirection();
            Direction babyRatDir = babyRat.getDirection();
            int dx = myX - babyRat.getLocation().x;
            int dy = myY - babyRat.getLocation().y;

            if(G.rc.getCarrying() != null) {
                if(dx * dx + dy * dy <= 10) {
                    babyRatMicro();
                    Motion.bugnavAwayNoTurning(babyRat.getLocation());
                }
            }

            //(1, 0)
            if(dx == 0 && dy == 1) {
                babyRatMicro();

               if(babyRatDir == Direction.NORTHEAST) {
                   Motion.moveNoTurn(Direction.NORTHWEST);
                   Motion.turn(Direction.SOUTHEAST);
               }
               else {
                   Motion.moveNoTurn(Direction.NORTHEAST);
                   Motion.turn(Direction.SOUTHWEST);
               }

               Motion.bugnavAwayNoTurning(babyRat.getLocation());
            }

            if(dx == 0 && dy == -1) {
                babyRatMicro();

                if(babyRatDir == Direction.SOUTHEAST) {
                    Motion.moveNoTurn(Direction.SOUTHWEST);
                    Motion.turn(Direction.NORTHEAST);
                }
                else {
                    Motion.moveNoTurn(Direction.SOUTHEAST);
                    Motion.turn(Direction.NORTHWEST);
                }

                Motion.bugnavAwayNoTurning(babyRat.getLocation());
            }

            if(dx == 1 && dy == 0) {
                babyRatMicro();

                if(babyRatDir == Direction.NORTHEAST) {
                    Motion.moveNoTurn(Direction.SOUTHEAST);
                    Motion.turn(Direction.NORTHWEST);
                }
                else {
                    Motion.moveNoTurn(Direction.NORTHEAST);
                    Motion.turn(Direction.SOUTHWEST);
                }

                Motion.bugnavAwayNoTurning(babyRat.getLocation());
            }

            if(dx == -1 && dy == 0) {
                babyRatMicro();

                if(babyRatDir == Direction.NORTHWEST) {
                    Motion.moveNoTurn(Direction.SOUTHWEST);
                    Motion.turn(Direction.NORTHEAST);
                }
                else {
                    Motion.moveNoTurn(Direction.NORTHWEST);
                    Motion.turn(Direction.SOUTHEAST);
                }

                Motion.bugnavAwayNoTurning(babyRat.getLocation());
            }


            //(1, 1)
            if(dx == 1 && dy == 1) {
                babyRatMicro();

                Motion.moveNoTurn(Direction.NORTHEAST);
                Motion.turn(Direction.SOUTHWEST);

                Motion.bugnavAwayNoTurning(babyRat.getLocation());
            }

            if(dx == -1 && dy == 1) {
                babyRatMicro();

                Motion.moveNoTurn(Direction.NORTHWEST);
                Motion.turn(Direction.SOUTHEAST);

                Motion.bugnavAwayNoTurning(babyRat.getLocation());
            }

            if(dx == 1 && dy == -1) {
                babyRatMicro();

                Motion.moveNoTurn(Direction.SOUTHEAST);
                Motion.turn(Direction.NORTHWEST);

                Motion.bugnavAwayNoTurning(babyRat.getLocation());
            }

            if(dx == -1 && dy == -1) {
                babyRatMicro();

                Motion.moveNoTurn(Direction.SOUTHWEST);
                Motion.turn(Direction.NORTHEAST);

                Motion.bugnavAwayNoTurning(babyRat.getLocation());
            }

            //(2, 0)
            if(dx == 2 && dy == 0) {


                if(babyRatDir == Direction.NORTHEAST) {
                    Motion.moveNoTurn(Direction.SOUTHWEST);
                    Motion.turn(Direction.NORTHWEST);
                }
                else {
                    Motion.moveNoTurn(Direction.NORTHWEST);
                    Motion.turn(Direction.SOUTHWEST);
                }

                Motion.bugnavTowards(babyRat.getLocation());
                babyRatMicro();
            }

            if(dx == -2 && dy == 0) {


                if(babyRatDir == Direction.NORTHWEST) {
                    Motion.moveNoTurn(Direction.SOUTHEAST);
                    Motion.turn(Direction.NORTHEAST);
                }
                else {
                    Motion.moveNoTurn(Direction.NORTHEAST);
                    Motion.turn(Direction.SOUTHEAST);
                }

                Motion.bugnavTowards(babyRat.getLocation());
                babyRatMicro();
            }

            if(dx == 0 && dy == 2) {


                if(babyRatDir == Direction.NORTHWEST) {
                    Motion.moveNoTurn(Direction.SOUTHEAST);
                    Motion.turn(Direction.SOUTHWEST);
                }
                else {
                    Motion.moveNoTurn(Direction.SOUTHWEST);
                    Motion.turn(Direction.SOUTHEAST);
                }

                Motion.bugnavTowards(babyRat.getLocation());
                babyRatMicro();
            }

            if(dx == 0 && dy == -2) {


                if(babyRatDir == Direction.SOUTHWEST) {
                    Motion.moveNoTurn(Direction.NORTHEAST);
                    Motion.turn(Direction.NORTHWEST);
                }
                else {
                    Motion.moveNoTurn(Direction.NORTHWEST);
                    Motion.turn(Direction.NORTHEAST);
                }

                Motion.bugnavTowards(babyRat.getLocation());
                babyRatMicro();
            }

            //(2, 1)
            if(dx == 2) {
                if(dy == 1 || dy == -1) {
                    Motion.bugnavTowardsNoTurning(new MapLocation(myX - 1, myY));
                    Motion.turn(G.me.directionTo(babyRat.getLocation()));
                    babyRatMicro();
                }
            }
            if(dx == -2) {
                if(dy == 1 || dy == -1) {
                    Motion.bugnavTowardsNoTurning(new MapLocation(myX + 1, myY));
                    Motion.turn(G.me.directionTo(babyRat.getLocation()));

                    babyRatMicro();
                }
            }

            if(dy == 2) {
                if(dx == 1 || dx == -1) {
                    Motion.bugnavTowardsNoTurning(new MapLocation(myX, myY-1));
                    Motion.turn(G.me.directionTo(babyRat.getLocation()));

                    babyRatMicro();
                }
            }

            if(dy == -2) {
                if(dx == 1 || dx == -1) {
                    Motion.bugnavTowardsNoTurning(new MapLocation(myX, myY+1));
                    Motion.turn(G.me.directionTo(babyRat.getLocation()));

                    babyRatMicro();
                }
            }


            //(2, 2)
            if(dx == 2 && dy == 2) {
                Motion.moveNoTurn(Direction.SOUTH);
                Motion.moveNoTurn(Direction.WEST);
                Motion.turn(G.me.directionTo(babyRat.getLocation()));
                Motion.bugnavTowards(babyRat.getLocation());

                babyRatMicro();
            }

            if(dx == -2 && dy == 2) {
                Motion.moveNoTurn(Direction.SOUTH);
                Motion.moveNoTurn(Direction.EAST);
                Motion.turn(G.me.directionTo(babyRat.getLocation()));
                Motion.bugnavTowards(babyRat.getLocation());

                babyRatMicro();
            }

            if(dx == 2 && dy == -2) {
                Motion.moveNoTurn(Direction.NORTH);
                Motion.moveNoTurn(Direction.WEST);
                Motion.turn(G.me.directionTo(babyRat.getLocation()));

                Motion.bugnavTowards(babyRat.getLocation());

                babyRatMicro();
            }

            if(dx == -2 && dy == -2) {
                Motion.moveNoTurn(Direction.NORTH);
                Motion.moveNoTurn(Direction.EAST);
                Motion.turn(G.me.directionTo(babyRat.getLocation()));

                Motion.bugnavTowards(babyRat.getLocation());

                babyRatMicro();
            }

            Motion.bugnavTowards(babyRat.getLocation());
            babyRatMicro();

            dx = G.rc.getLocation().x - babyRat.getLocation().x;
            dy = G.rc.getLocation().y - babyRat.getLocation().y;
            if(dx * dx + dy * dy > 2 && dx *dx +  dy * dy < 9) {
                if (G.rc.getHealth() <= babyRat.getHealth() && G.rc.canPlaceRatTrap(G.rc.adjacentLocation(G.rc.getLocation().directionTo(babyRat.getLocation())))) {
                    MapLocation loc = G.rc.adjacentLocation(G.rc.getLocation().directionTo(babyRat.getLocation()));
                    if(loc.directionTo(babyRat.getLocation()) != babyRat.getDirection()) {
                        G.rc.placeRatTrap(loc);
                    }
                }
            }


        }

        if(kingRat != null) {

            Motion.bugnavTowards(kingRat.getLocation());
            babyRatMicro();
        }

        if(G.rc.getRoundNum() >= 1940 && G.rc.canBecomeRatKing()) {
            G.rc.becomeRatKing();
        }









            MapInfo[] cheeses = G.rc.senseNearbyMapInfos(-1);
            MapLocation closestCheese = null;
            int bestCheese = 100;
            for (MapInfo c : cheeses) {
                if (c.getCheeseAmount() > 0) {
                    if (G.rc.getLocation().distanceSquaredTo(c.getMapLocation()) < bestCheese) {
                        closestCheese = c.getMapLocation();
                        bestCheese = G.rc.getLocation().distanceSquaredTo(c.getMapLocation());
                    }
                }
            }

            if (closestCheese != null && G.rc.getRawCheese() <= 100) {
                Motion.bugnavTowards(closestCheese);
                if(G.rc.canPickUpCheese(closestCheese)) {
                    G.rc.pickUpCheese(closestCheese);
                }
            }
        else if(G.rc.getRawCheese() > 0) {

            MapLocation king = new MapLocation(G.rc.readSharedArray(0), G.rc.readSharedArray(1));
            Motion.bugnavTowards(king);

            for(Direction d : Direction.allDirections()) {
                if(G.rc.canTransferCheese(king.add(d), G.rc.getRawCheese())) {
                    G.rc.transferCheese(king.add(d), G.rc.getRawCheese());
                }
            }


        }


        MapLocation ahead = G.rc.getLocation().add(G.rc.getDirection());
        if (G.rc.canSenseLocation(ahead)) {
            if (G.rc.canRemoveDirt(ahead) && enemyRobots.length == 0) {
                G.rc.removeDirt(ahead);
            }
        }

        babyRatMicro();
        spreadingBehavior();




        lastHealth = G.rc.getHealth();
   }

   public static void babyRatMicro() throws Exception {
       for(Direction d : Direction.allDirections()) {
           if(G.rc.canSenseLocation(G.rc.getLocation().add(d))) {
              RobotInfo r = G.rc.senseRobotAtLocation(G.rc.getLocation().add(d));
                if(r != null && r.getTeam() == G.opponentTeam) {
                    if(G.rc.canCarryRat(G.rc.getLocation().add(d)) ) {
                        G.rc.carryRat(G.rc.getLocation().add(d));
                    }
                }
           }

       }

       for(Direction d : Direction.allDirections()) {
           if(G.rc.canAttack(G.rc.getLocation().add(d))) {
               int cheeseToUse = Math.max(Math.min(G.rc.getGlobalCheese(), 1), Math.min(4, G.rc.getRawCheese()));
               G.rc.attack(G.rc.getLocation().add(d), cheeseToUse);
           }
       }
   }

   private static void spreadingBehavior() throws Exception {
       MapLocation kingLocation = new MapLocation(G.rc.readSharedArray(0), G.rc.readSharedArray(1));
       if(lastMine == null || (G.rc.getCurrentRatCost() <= 70 && G.rc.getID() % 6 != 0) || (G.rc.getCurrentRatCost() > 70 && G.rc.getID() % 12 != 0)) {
           if (G.rc.senseNearbyRobots().length >= 10) {
               Motion.spreadRandomly();
           } else {
               int centerX = G.rc.getMapWidth() / 2;
               int centerY = G.rc.getMapHeight() / 2;
               MapLocation mapCenter = new MapLocation(centerX, centerY);

               double ratio = 0.1 + ((G.rc.getID() + ((int) Math.floor(G.rc.getRoundNum() / 40) * 20)) % 80) / 100.0;
               int targetX = (int) Math.floor(ratio * G.rc.getMapWidth());
               int targetY = (int) Math.floor((1 - ratio) * G.rc.getMapHeight());


               int dx = G.rc.getID() % (9);
               if (kingLocation.x > mapCenter.x) {
                   targetX += 2 * (dx - 4);
               } else {
                   targetX -= 2 * (dx - 4);
               }

               int dy = (G.rc.getID() * 5) % (7);
               if (kingLocation.x > mapCenter.y) {
                   targetY += 2 * (dy - 3);
               } else {
                   targetY -= 2 * (dy - 3);
               }


               MapLocation gatherPoint = new MapLocation(targetX, targetY);


               Motion.bugnavTowards(gatherPoint);
           }
       }
        else {

            Motion.bugnavTowards(lastMine);
            if(G.rc.senseNearbyRobots(-1).length >= 9) {
                lastMine = null;
            }
        }
    }
}

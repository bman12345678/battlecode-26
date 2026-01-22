package goonv10;

import battlecode.common.*;

import java.util.*;

public class BabyRat {
    // Constants

    private static int lastHealth = 100;
    public static MapLocation lastMine = null;
    private static int roundsCarrying = 0;
    private static int catTurnsLeft = 0;
    private static MapLocation catLoc = null;
    private static int ori = -1;
    private static Direction dori = null;
    private static MapLocation tmp = null;
    private static MapLocation king = null;
    private static Set<MapLocation> mines = new HashSet<>();
    public static MapLocation newMine= null;
    public static int dirtcnt = 0;


    public static void run() throws Exception {
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

            if(lastMine != null) {

                if(k.distanceSquaredTo(lastMine) < 9) lastMine = null;
            }
        }



        int numOfMines = G.rc.readSharedArray(15);
        if(lastMine == null && numOfMines > 0) {
            mines.clear();
            for(int i = 0; i < numOfMines; i++) {
                int x = G.rc.readSharedArray(2 * i + 16);
                int y = G.rc.readSharedArray(2 * i + 17)    ;
                MapLocation loc = new MapLocation(x, y);
                mines.add(loc);
            }

                int index = G.rc.getID() % numOfMines;
                Iterator<MapLocation> it = mines.iterator();
                while(it.hasNext() && index >= 0) {
                    lastMine = it.next();
                    index -= 1;
                }
        }

        //lastMine = newMine;


        //System.out.println(mines);
        //System.out.println(lastMine);







        MapInfo[] mapInfos = G.rc.senseNearbyMapInfos(-1);
        for(MapInfo info : mapInfos) {
            if(info.hasCheeseMine()) {
                if(newMine == null && king.distanceSquaredTo(info.getMapLocation()) > 9 && !mines.contains(info.getMapLocation())) newMine = info.getMapLocation();
                if(lastMine == null && king.distanceSquaredTo(info.getMapLocation()) > 9) lastMine = info.getMapLocation();
            }
        }

        if(catTurnsLeft > 0 && catLoc != null) {
            catTurnsLeft -= 1;
            babyRatMicro();
            if(G.rc.getCurrentRatCost() >= 70) Motion.bugnavAway(catLoc);
            else Motion.bugnavAway(catLoc);

            if(king.distanceSquaredTo(G.rc.getLocation()) + 8 >= king.distanceSquaredTo(catLoc) && king.distanceSquaredTo(G.rc.getLocation()) <= 169) {
                G.rc.squeak(3);
            }

        }

        boolean attacked = false;
        int currentHealth = G.rc.getHealth();
        if(currentHealth < lastHealth) attacked = true;

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
            if (G.rc.getCarrying().getTeam().equals(G.rc.getTeam())) {
                if (G.rc.getLocation().distanceSquaredTo(king)<=18) {
                    if (G.rc.canDropRat(G.me.directionTo(king))) G.rc.dropRat(G.me.directionTo(king));
                    else {
                        for (Direction d : G.DIRECTIONS) if (G.rc.canDropRat(d)) G.rc.dropRat(d);
                        if (G.rc.getCarrying()!=null) Motion.bugnavTowardsExplore(king);
                    }
                }
                else Motion.bugnavTowardsExplore(king);
            }
            roundsCarrying += 1;
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
        RobotInfo cat = null;

        for(RobotInfo c : cats) {
            if(cat == null || c.getLocation().distanceSquaredTo(G.rc.getLocation()) < cat.getLocation().distanceSquaredTo(G.rc.getLocation())) {
                if(!Motion.canReachYou(c.getLocation())) continue;

                cat = c;
            }
        }
        if(cat != null) {



                Direction toCat = G.me.directionTo(cat.getLocation());

                // Try to place traps in a line to block the 2x2 cat
            //if(Motion.canCatReachYou(cat.getLocation())) {

                MapLocation trapLoc = G.rc.adjacentLocation(toCat);

                if (G.rc.canPlaceCatTrap(trapLoc)) {
                    G.rc.placeCatTrap(trapLoc);
                }

                for (Direction d : Direction.allDirections()) {
                    if (G.rc.canAttack(G.rc.getLocation().add(d))) {
                        G.rc.attack(G.rc.getLocation().add(d));
                    }
                }

                catLoc = cat.getLocation();
                catTurnsLeft = 1;
                babyRatMicro();
                if(G.rc.getCurrentRatCost() >= 70) Motion.bugnavAway(cat.getLocation());
                else Motion.bugnavAway(cat.getLocation());

                if(king.distanceSquaredTo(G.rc.getLocation()) + 8 >= king.distanceSquaredTo(catLoc) && king.distanceSquaredTo(G.rc.getLocation()) <= 169) {
                    G.rc.squeak(3);
                }

        }




        RobotInfo babyRat = null;
        RobotInfo kingRat = null;
        int bestDist = 100;
        for(RobotInfo x : enemyRobots) {
            if (x.getType().isBabyRatType()) {
                if(!Motion.canReachYou(x.getLocation())) continue;

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
                if(dx * dx + dy * dy <= 10) {
                    babyRatMicro();
                    Motion.bugnavAway(babyRat.getLocation());
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

                Motion.bugnavAway(babyRat.getLocation());
            }

            else if(dx == 0 && dy == -1) {
                babyRatMicro();

                if(babyRatDir == Direction.SOUTHEAST) {
                    Motion.moveNoTurn(Direction.SOUTHWEST);
                    Motion.turn(Direction.NORTHEAST);
                }
                else {
                    Motion.moveNoTurn(Direction.SOUTHEAST);
                    Motion.turn(Direction.NORTHWEST);
                }

                Motion.bugnavAway(babyRat.getLocation());
            }

            else if(dx == 1 && dy == 0) {
                babyRatMicro();

                if(babyRatDir == Direction.NORTHEAST) {
                    Motion.moveNoTurn(Direction.SOUTHEAST);
                    Motion.turn(Direction.NORTHWEST);
                }
                else {
                    Motion.moveNoTurn(Direction.NORTHEAST);
                    Motion.turn(Direction.SOUTHWEST);
                }

                Motion.bugnavAway(babyRat.getLocation());
            }

            else if(dx == -1 && dy == 0) {
                babyRatMicro();

                if(babyRatDir == Direction.NORTHWEST) {
                    Motion.moveNoTurn(Direction.SOUTHWEST);
                    Motion.turn(Direction.NORTHEAST);
                }
                else {
                    Motion.moveNoTurn(Direction.NORTHWEST);
                    Motion.turn(Direction.SOUTHEAST);
                }

                Motion.bugnavAway(babyRat.getLocation());
            }


            //(1, 1)
            else if(dx == 1 && dy == 1) {
                babyRatMicro();

                if(Motion.moveNoTurn(Direction.NORTHEAST) || Motion.moveNoTurn(Direction.NORTH) || Motion.moveNoTurn(Direction.EAST)) {
                    Motion.turn(Direction.SOUTHWEST);
                }
                else if(Motion.moveNoTurn(Direction.SOUTHEAST) || Motion.moveNoTurn(Direction.NORTHWEST)) {
                    Motion.turn(G.rc.getLocation().directionTo(babyRat.getLocation()));
                }
            }

            else if(dx == -1 && dy == 1) {
                babyRatMicro();

                if(Motion.moveNoTurn(Direction.NORTHWEST) || Motion.moveNoTurn(Direction.NORTH) || Motion.moveNoTurn(Direction.WEST)) {
                    Motion.turn(Direction.SOUTHEAST);
                }
                else if(Motion.moveNoTurn(Direction.SOUTHWEST) || Motion.moveNoTurn(Direction.NORTHEAST)) {
                    Motion.turn(G.rc.getLocation().directionTo(babyRat.getLocation()));
                }
            }

            else if(dx == 1 && dy == -1) {
                babyRatMicro();

                if(Motion.moveNoTurn(Direction.SOUTHEAST) || Motion.moveNoTurn(Direction.SOUTH) || Motion.moveNoTurn(Direction.EAST)) {
                    Motion.turn(Direction.NORTHWEST);
                }
                else if(Motion.moveNoTurn(Direction.NORTHEAST) || Motion.moveNoTurn(Direction.SOUTHWEST)) {
                    Motion.turn(G.rc.getLocation().directionTo(babyRat.getLocation()));
                }
            }

            else if(dx == -1 && dy == -1) {
                babyRatMicro();

                if(Motion.moveNoTurn(Direction.SOUTHWEST) || Motion.moveNoTurn(Direction.SOUTH) || Motion.moveNoTurn(Direction.WEST)) {
                    Motion.turn(Direction.NORTHEAST);
                }
                else if(Motion.moveNoTurn(Direction.NORTHWEST) || Motion.moveNoTurn(Direction.SOUTHEAST)) {
                    Motion.turn(G.rc.getLocation().directionTo(babyRat.getLocation()));
                }
            }

            //(2, 0)
            else if(dx == 2 && dy == 0) {


                if(babyRatDir == Direction.NORTHEAST || babyRatDir == Direction.NORTH) {
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

            else if(dx == -2 && dy == 0) {


                if(babyRatDir == Direction.NORTHWEST || babyRatDir == Direction.NORTH) {
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

            else if(dx == 0 && dy == 2) {


                if(babyRatDir == Direction.NORTHWEST || babyRatDir == Direction.WEST) {
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

            else if(dx == 0 && dy == -2) {


                if(babyRatDir == Direction.SOUTHWEST || babyRatDir == Direction.WEST) {
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
            else if(dx == 2 && (dy == 1 || dy == -1)) {

                    Motion.bugnavTowards(new MapLocation(myX - 1, myY));
                    Motion.turn(G.me.directionTo(babyRat.getLocation()));
                    babyRatMicro();

            }
            else if(dx == -2 && (dy == 1 || dy == -1)) {

                    Motion.bugnavTowards(new MapLocation(myX + 1, myY));
                    Motion.turn(G.me.directionTo(babyRat.getLocation()));

                    babyRatMicro();

            }

            else if(dy == 2 && (dx == 1 || dx == -1)) {

                    Motion.bugnavTowards(new MapLocation(myX, myY-1));
                    Motion.turn(G.me.directionTo(babyRat.getLocation()));

                    babyRatMicro();

            }

            else if(dy == -2 && (dx == 1 || dx == -1)) {

                    Motion.bugnavTowards(new MapLocation(myX, myY+1));
                    Motion.turn(G.me.directionTo(babyRat.getLocation()));

                    babyRatMicro();

            }


            //(2, 2)
            else if(dx == 2 && dy == 2) {
                Motion.moveNoTurn(Direction.SOUTHWEST);
                Motion.moveNoTurn(Direction.SOUTH);
                Motion.moveNoTurn(Direction.WEST);
                Motion.bugnavTowards(babyRat.getLocation());
                Motion.turn(G.me.directionTo(babyRat.getLocation()));


                babyRatMicro();
            }

            else if(dx == -2 && dy == 2) {
                Motion.moveNoTurn(Direction.SOUTHEAST);

                Motion.moveNoTurn(Direction.SOUTH);
                Motion.moveNoTurn(Direction.EAST);
                Motion.bugnavTowards(babyRat.getLocation());
                Motion.turn(G.me.directionTo(babyRat.getLocation()));

                babyRatMicro();
            }

            else if(dx == 2 && dy == -2) {
                Motion.moveNoTurn(Direction.NORTHWEST);
                Motion.moveNoTurn(Direction.NORTH);
                Motion.moveNoTurn(Direction.WEST);
                Motion.bugnavTowards(babyRat.getLocation());
                Motion.turn(G.me.directionTo(babyRat.getLocation()));

                babyRatMicro();
            }

            else if(dx == -2 && dy == -2) {
                Motion.moveNoTurn(Direction.NORTHEAST);
                Motion.moveNoTurn(Direction.NORTH);
                Motion.moveNoTurn(Direction.EAST);
                Motion.bugnavTowards(babyRat.getLocation());
                Motion.turn(G.me.directionTo(babyRat.getLocation()));

                babyRatMicro();
            }
            else {
                Motion.bugnavTowards(babyRat.getLocation());
                Motion.turn(G.rc.getLocation().directionTo(babyRat.getLocation()));
                babyRatMicro();
            }


            /*
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
            */

        }
        else {


            if (kingRat != null) {

                Motion.bugnavTowards(kingRat.getLocation());
                babyRatMicro();
            }

            if (G.rc.getRoundNum() >= 1940 && G.rc.canBecomeRatKing()) {
                G.rc.becomeRatKing();
            }


            if ((G.rc.getCurrentRatCost() > (50 + 5 * kings.size()) && G.rc.getGlobalCheese() > 1200 && G.rc.getRoundNum() < 1200 && kings.size() < 5) || (G.rc.getRoundNum() > 1940 && kings.size() == 1)) {
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


                        if(toGoTo != null) Motion.bugnavTowardsExplore(toGoTo);

                    else if(thatMine != null){
                        if(G.rc.getLocation().equals(thatMine)) return;
                        if(G.rc.canSenseLocation(thatMine)) {
                            if(G.rc.senseRobotAtLocation(thatMine) != null) {
                                if(G.rc.getLocation().distanceSquaredTo(thatMine) <= 2) return;
                            }
                        }
                        Motion.bugnavTowardsExplore(thatMine);
                    }



                if (G.rc.canBecomeRatKing() && thatMine != null && G.rc.getLocation().distanceSquaredTo(thatMine) <= 17) {
                    G.rc.becomeRatKing();
                }
            }

            RobotInfo[] team = G.rc.senseNearbyRobots();
            for (RobotInfo e : team) {
                if (!e.getTeam().equals(G.rc.getTeam())) continue;
                if (e.getRawCheeseAmount()>=100&&G.rc.getRawCheese()<=20) {
                    if (G.rc.canCarryRat(e.getLocation())) G.rc.carryRat(e.getLocation());
                    else Motion.bugnavTowardsExplore(e.getLocation());
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
                        if (G.rc.canPickUpCheese(closestCheese) && G.rc.getRawCheese() < 240) {
                            G.rc.pickUpCheese(closestCheese);
                        }
                    }
                }
            }

            if (closestCheese != null && G.rc.getRawCheese() <= 240) {
                if (G.rc.getRawCheese() < 60) Motion.bugnavTowards(closestCheese);
                if (G.rc.canPickUpCheese(closestCheese)) {
                    G.rc.pickUpCheese(closestCheese);
                }
            } else if (G.rc.getRawCheese() > 0) {

                Motion.bugnavTowardsExplore(king);

                for (Direction d : Direction.allDirections()) {
                    if (G.rc.canTransferCheese(king.add(d), G.rc.getRawCheese())) {
                        if(newMine != null && !mines.contains(newMine)) {
                            G.rc.squeak(1024 * (1024 * newMine.x + newMine.y) + 1);
                            newMine = null;
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
            spreadingBehavior();
            babyRatMicro();
        }




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
                    }
                }
            }

        }
    }

    private static void spreadingBehavior() throws Exception {
        MapLocation kingLocation = new MapLocation(G.rc.readSharedArray(0), G.rc.readSharedArray(1));
        if(lastMine == null || G.rc.getID() % 8 != 0) {
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
                MapLocation tmp = me;
                if (dori == Direction.CENTER) {
                    // no forward direction; pick a fallback (north) so the rat moves
                    dori = Direction.NORTH;
                }
                MapLocation next = tmp.add(dori);
                final int MARGIN = 2; // keep this many tiles from edge
                // move tmp forward while next remains inside safe bounds
                while (next.x >= MARGIN && next.x <= mapW - 1 - MARGIN && next.y >= MARGIN && next.y <= mapH - 1 - MARGIN) {
                    tmp = next;
                    next = tmp.add(dori);
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
                    Motion.bugnavTowardsExplore(tmp);
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
        else {

            Motion.bugnavTowardsExplore(lastMine);
        }
    }
}
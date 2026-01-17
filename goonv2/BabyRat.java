package goonv2;

import battlecode.common.*;
import goonv2.G;
import goonv2.Motion;
import goonv2.RobotPlayer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class BabyRat {
    // Constants
    private static final int KING_X_INDEX = 0;
    private static final int KING_Y_INDEX = 1;
    private static final int CHEESE_THRESHOLD = 20;
    private static final int CAT_EVASION_MOVES = 5;
    private static final double TURN_CHANCE = 0.22;

    // State variables
    private static MapLocation kingLocation = null;
    private static RobotInfo lockedTarget = null;
    private static int catEvasionCounter = 0;
    private static MapLocation lastCatLocation = null;
    private static boolean spawnedTrap = false;
    public static MapLocation lastMine = null;

    // Cycle phase: 0 = gathering, 1 = returning, 2 = spreading
    private static int cyclePhase = 2;

    public static void run() throws Exception {
        // Read king location from shared array
        readKingLocation();
/*
        if(G.rc.getCurrentRatCost() <= 20 && G.rc.getRoundNum() <= 20 && ! spawnedTrap) {

            for(MapLocation t : G.rc.getAllLocationsWithinRadiusSquared(G.me, 2)) {
                if(G.rc.canPlaceCatTrap(t)) {
                    spawnedTrap = true;
                    G.rc.placeCatTrap(t);
                    break;
                }
            }
        }
        */


        MapInfo[] mapInfos = G.rc.senseNearbyMapInfos(-1);
        for(MapInfo info : mapInfos) {
            if(info.hasCheeseMine()) {
                if(lastMine == null) lastMine = info.getMapLocation();
            }
        }

            // Handle cat evasion if active
        if (catEvasionCounter > 0) {
            if(G.rc.getID() % 7 == 0) {
                //
                // G.rc.squeak(1);
            }
            handleCatEvasion();
            catEvasionCounter--;
            return;
        }

        // Check for threats and targets in vision cone
        scanAndPrioritize();
        if(G.rc.canThrowRat()) {
            G.rc.throwRat();
        }

        //boolean dontMove = false;
        if(G.rc.senseMapInfo(G.me).hasCheeseMine()) {
            for(Direction d : G.DIRECTIONS) {
                if(G.rc.canPlaceRatTrap(G.me.add(d))) {
                    G.rc.placeRatTrap(G.me.add(d));
                    //dontMove = true;
                }
            }
        }

        // Handle locked target (combat)

        if (lockedTarget != null ) {

            handleCombat();
            return;
        }




        if(G.rc.getRoundNum() >= 1900 && G.rc.canBecomeRatKing()) {
            G.rc.becomeRatKing();
        }



        // Check if we should return to king
        if (G.rc.getRawCheese() >= CHEESE_THRESHOLD || (G.rc.getID() % 7 > 2 && G.rc.readSharedArray(2) == 1)) {
            cyclePhase = 1; // Returning phase
        }

        // Pick up cheese if available
        collectCheese();



        MapInfo[] infos = G.rc.senseNearbyMapInfos(-1);
        for(MapInfo m : infos) {
            if(m.getCheeseAmount() > 0) {
                Motion.bugnavTowards(m.getMapLocation());
                break;
            }
        }

        if(G.rc.getRoundNum() >= 1900) {
            cyclePhase = 1;
        }

        // Execute behavior based on phase
        switch (cyclePhase) {
            case 0: // Gathering phase
                gatheringBehavior();
                break;
            case 1: // Returning phase
                returningBehavior();
                break;
            case 2: // Spreading phase
                spreadingBehavior();
                break;
        }



        // Dig dirt if blocking path
        digDirtIfNeeded();
    }

    private static void readKingLocation() throws Exception {
        int x = G.rc.readSharedArray(KING_X_INDEX);
        int y = G.rc.readSharedArray(KING_Y_INDEX);
        if (x != 0 || y != 0) {
            kingLocation = new MapLocation(x, y);
        }
    }

    private static void scanAndPrioritize() throws Exception {
        // Priority: 1) Enemy King, 2) Enemy Baby Rat, 3) Cat
        RobotInfo enemyKing = null;
        RobotInfo enemyBabyRat = null;
        RobotInfo cat = null;

        // Get all robots we can sense (vision cone limited)
        RobotInfo[] nearbyRobots = G.rc.senseNearbyRobots();

        for (RobotInfo robot : nearbyRobots) {
            if (robot.team == G.opponentTeam) {
                // Check if robot is in our vision cone
                if (!isInVisionCone(robot.location)) {
                    continue;
                }

                if (robot.type == UnitType.RAT_KING) {
                    enemyKing = robot;
                } else if (robot.type == UnitType.BABY_RAT) {
                    if (enemyBabyRat == null ||
                        G.me.distanceSquaredTo(robot.location) < G.me.distanceSquaredTo(enemyBabyRat.location)) {
                        enemyBabyRat = robot;
                    }
                }
            }
            else if (robot.type == UnitType.CAT) {
                if (cat == null ||
                        G.me.distanceSquaredTo(robot.location) < G.me.distanceSquaredTo(cat.location)) {
                    cat = robot;
                }
            }
        }

        // Handle cat threat immediately
        if (cat != null) {
            handleCatThreat(cat);
            return;
        }

        // Lock onto targets by priority
        if (enemyKing != null) {
            lockedTarget = enemyKing;
            G.indicatorString.append("LOCK-KING ");
        } else if (enemyBabyRat != null) {
            lockedTarget = enemyBabyRat;
            G.indicatorString.append("LOCK-RAT ");
        } else {
            lockedTarget = null;
        }
    }

    private static boolean isInVisionCone(MapLocation target) throws Exception {
        // Baby rats have a 90-degree vision cone
        Direction myDirection = G.rc.getDirection();
        Direction toTarget = G.me.directionTo(target);

        // Check if target direction is within 45 degrees (90 degree cone) of facing direction
        // This means the target direction can be: myDirection, rotateLeft(), or rotateRight()
        return toTarget == myDirection ||
               toTarget == myDirection.rotateLeft() ||
               toTarget == myDirection.rotateRight();
    }

    private static void handleCombat() throws Exception {
        // Check if target is still valid

        RobotInfo[] info = G.rc.senseNearbyRobots(-1);
        if (info.length == 0) {
            lockedTarget = null;
            return;
        }
        else {
            boolean stillThere = false;
            for(RobotInfo x : info) {
                if(x.getID() == lockedTarget.ID) {
                    stillThere = true;
                    lockedTarget = x;
                    break;
                }
            }

            if(!stillThere) {
                scanAndPrioritize();
            }
        }

        if(lockedTarget == null) {
            return;
        }

        // Check if target is in vision cone, if not, rotate to face it
        //if (!isInVisionCone(lockedTarget.location)) {
        //    rotateToFace(lockedTarget.location);
        //}

        if(lockedTarget.type == UnitType.BABY_RAT) {
            //rat nap

            for(Direction d : G.DIRECTIONS) {
                MapLocation adj = G.rc.adjacentLocation(d);
                if(G.rc.canCarryRat(adj) && G.rc.canSenseRobotAtLocation(adj) && G.rc.senseRobotAtLocation(adj).getTeam() == G.opponentTeam) {
                    G.rc.carryRat(adj);
                }
            }

        }


        Direction toCat = G.me.directionTo(lockedTarget.location);

        // Try to place traps in a line to block the 2x2 cat

        MapLocation trapLoc = G.me.add(toCat);
        if (G.rc.readSharedArray(2) == 1 && G.rc.canPlaceRatTrap(trapLoc) && Motion.getChebyshevDistance(lockedTarget.location, G.rc.getLocation()) <= 2 && G.rng.nextInt(0, 3) % 3 == 0) {
            G.rc.placeRatTrap(trapLoc);
            G.indicatorString.append("TRAP ");
        }

        // Try to attack if in range

        boolean attacked = false;
        MapLocation adj = G.rc.adjacentLocation(toCat);
            if (G.rc.canAttack(adj)) {
                G.rc.attack(adj, Math.max(G.rc.getRawCheese(), 2));
                attacked = true;
                G.indicatorString.append("ATK ");
            }

        MapLocation x = G.rc.adjacentLocation(toCat.rotateLeft());
        if (G.rc.canAttack(x)) {
            G.rc.attack(x, Math.max(G.rc.getRawCheese(), 2));
            attacked = true;

            G.indicatorString.append("ATK ");
        }

        MapLocation y = G.rc.adjacentLocation(toCat.rotateRight());
        if (G.rc.canAttack(y)) {
            G.rc.attack(y, Math.max(G.rc.getRawCheese(), 2));
            attacked = true;

            G.indicatorString.append("ATK ");
        }

        tryToAttack();




        //
        // System.out.println(attacked);
        // Move towards target while keeping it in vision
        if(!attacked) approachTargetWithVision(lockedTarget.location);
    }

    private static void rotateToFace(MapLocation target) throws Exception {
        Direction toTarget = G.me.directionTo(target);
        Direction currentDirection = G.rc.senseRobotAtLocation(G.me).direction;

        // Determine shortest rotation direction
        int rotationsClockwise = 0;
        Direction testDir = currentDirection;
        while (testDir != toTarget && rotationsClockwise < 8) {
            testDir = testDir.rotateRight();
            rotationsClockwise++;
        }

        int rotationsCounterClockwise = 0;
        testDir = currentDirection;
        while (testDir != toTarget && rotationsCounterClockwise < 8) {
            testDir = testDir.rotateLeft();
            rotationsCounterClockwise++;
        }

        // Rotate in the shorter direction
        if (rotationsClockwise <= rotationsCounterClockwise) {
            // Try to move in a direction that rotates us clockwise
            Direction moveDir = currentDirection.rotateRight();
            if (Motion.canMove(moveDir)) {
                Motion.move(moveDir);
            }
        } else {
            // Try to move in a direction that rotates us counterclockwise
            Direction moveDir = currentDirection.rotateLeft();
            if (Motion.canMove(moveDir)) {
                Motion.move(moveDir);
            }
        }

        G.indicatorString.append("ROTATE ");
    }

    private static void approachTargetWithVision(MapLocation target) throws Exception {
        /*
        // Move towards target while trying to keep facing it
        Direction toTarget = G.me.directionTo(target);
        Direction currentDirection = G.rc.senseRobotAtLocation(G.me).direction;

        // Try to move in the direction of the target
        Motion.bugnavTowards(target);
        if (Motion.canMove(toTarget)) {
            Motion.move(toTarget);
        } else {
            // Try adjacent directions
            Direction[] tryDirs = {
                toTarget.rotateLeft(),
                toTarget.rotateRight(),
                toTarget.rotateLeft().rotateLeft(),
                toTarget.rotateRight().rotateRight()
            };

            for (Direction dir : tryDirs) {
                if (Motion.canMove(dir)) {
                    Motion.move(dir);
                    break;
                }
            }
        }
        */
        //System.out.println(target);
        Motion.bugnavTowardsNoTurning(target);
    }

    private static void tryToAttack() throws Exception {
        for(Direction d : Direction.allDirections()) {
            if (G.rc.canAttack(G.me.add(d))

            )
                G.rc.attack(G.me.add(d), Math.max(G.rc.getRawCheese(), 2));


        }
    }

    private static void handleCatThreat(RobotInfo cat) throws Exception {
        // Cat is 2x2, place multiple traps to block it effectively
        if(G.rc.getID() % 7 == 0) {
            G.rc.squeak(1);
        }
        Direction toCat = G.me.directionTo(cat.location);

        // Try to place traps in a line to block the 2x2 cat

            MapLocation trapLoc = G.rc.adjacentLocation(toCat);

            if (G.rc.canPlaceCatTrap(trapLoc)) {
                G.rc.placeCatTrap(trapLoc);
                G.indicatorString.append("TRAP ");
                System.out.println("cat trap");
            }


        // Set evasion counter and remember cat location
        catEvasionCounter = CAT_EVASION_MOVES;
        lastCatLocation = cat.location;

        // Start fleeing immediately
        handleCatEvasion();
    }

    private static void handleCatEvasion() throws Exception {
        tryToAttack();
        if (lastCatLocation != null) {
            if(G.rc.getID() % 7 == 0) {
                Motion.bugnavAwayNoTurning(lastCatLocation);
            }
            else {
                Motion.bugnavAway(lastCatLocation);

            }
            G.indicatorString.append("EVADE ");
        }

        if (catEvasionCounter == 0) {
            lastCatLocation = null;
            cyclePhase = 1;
        }
    }

    private static void collectCheese() throws Exception {
        // Check all nearby locations for cheese
        if(G.rc.getRawCheese() >= 100) return;
        for (Direction dir : G.DIRECTIONS) {
            MapLocation loc = G.me.add(dir);
            if (G.rc.canPickUpCheese(loc)) {
                G.rc.pickUpCheese(loc);
                G.indicatorString.append("CHEESE ");
            }
        }
    }

    private static void gatheringBehavior() throws Exception {
        cyclePhase = 1;
        return;
    }

    private static void returningBehavior() throws Exception {
        if (kingLocation == null) {
            cyclePhase = 0;
            return;
        }

        // King is 3x3, need to be within range accounting for size
        // Get close enough to transfer (king center + 1.5 radius = within ~4 distance)
        if (G.me.distanceSquaredTo(kingLocation) <= 5) {
            // Try to find any location of the 3x3 king we can transfer to


            //send cheese mines
            if(lastMine != null) {
                //G.rc.squeak(lastMine.x * 1024 + lastMine.y);
            }

            int cheese = G.rc.getRawCheese();
            if (cheese > 0) {
                // Try king center and all locations around it
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        MapLocation kingPart = kingLocation.translate(dx, dy);
                        if (G.rc.canTransferCheese(kingPart, cheese)) {
                            G.rc.transferCheese(kingPart, cheese);
                            G.indicatorString.append("TRANSFER ");
                            if(G.rng.nextBoolean()) {
                                //lastMine = null;
                            }
                            cyclePhase = 2; // Move to spreading phase
                            return;
                        }
                    }
                }
            }
            else {
                cyclePhase = 2;
                return;
            }
        }

        Motion.bugnavTowards(kingLocation);
        G.indicatorString.append("RETURN ");
    }

    private static void spreadingBehavior() throws Exception {
        if (kingLocation == null) {
            System.out.println("king loc null");
            cyclePhase = 0;
            return;
        }

        // Spread around the gather point
        if(lastMine == null || G.rc.getID() % 6 != 0) {
            if(G.rc.senseNearbyRobots().length >= 10) {
                Motion.spreadRandomly();
            }
            else {
                int centerX = G.rc.getMapWidth() / 2;
                int centerY = G.rc.getMapHeight() / 2;
                MapLocation mapCenter = new MapLocation(centerX, centerY);

                double ratio = 0.1 + ((G.rc.getID() + ((int) Math.floor(G.rc.getRoundNum() / 40) * 20)) % 80) / 100.0;
                int targetX = (int) Math.floor(ratio * G.rc.getMapWidth());
                int targetY = (int) Math.floor((1 - ratio) * G.rc.getMapHeight());


                    int dx = G.rc.getID() % (7);
                    if (kingLocation.x > mapCenter.x) {
                        targetX += 2 * (dx - 3);
                    } else {
                        targetX -= 2 * (dx - 3);
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
        /*

        if (Math.abs(kingLocation.x - centerX) <= G.rc.getMapWidth() /4 && Math.abs(kingLocation.y - centerY) <= G.rc.getMapHeight()/4) {
            Motion.bugnavTowards(mapCenter);
        }
        else {
            if(G.rc.getID() % 2 == 0) Motion.bugnavAround(kingLocation, G.rc.getMapWidth() * G.rc.getMapHeight() /2,G.rc.getMapWidth() * G.rc.getMapHeight());
            else {
                Motion.bugnavAround(kingLocation, G.rc.getMapWidth() * G.rc.getMapHeight() /6,G.rc.getMapWidth() * G.rc.getMapHeight());
            }

            G.indicatorString.append("SPREAD ");

        }
*/
        // After spreading, return to gathering phase
        if (G.rc.getRawCheese() >= CHEESE_THRESHOLD) {
            cyclePhase = 0;
        }
    }

    private static void randomTurn() throws Exception {
        // Randomly turn left or right
        Direction currentDirection = G.rc.senseRobotAtLocation(G.me).direction;
        Direction newDirection = G.rng.nextBoolean() ?
            currentDirection.rotateRight() : currentDirection.rotateLeft();

        // Try to move in the new direction
        if (Motion.canMove(newDirection)) {
            Motion.move(newDirection);
            G.indicatorString.append("TURN ");
        }
    }

    private static void digDirtIfNeeded() throws Exception {
        // Check if there's dirt in our current movement direction
        Direction currentDirection = G.rc.getDirection();
        MapLocation ahead = G.me.add(currentDirection);

        if (G.rc.canSenseLocation(ahead)) {
            if (G.rc.canRemoveDirt(ahead)) {
                G.rc.removeDirt(ahead);
                G.indicatorString.append("DIG ");
            }
        }
    }
}

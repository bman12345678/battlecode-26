package goon;

import battlecode.common.*;
import battlecode.schema.RatSqueak;

public class BabyRat {
    static MapLocation enemyKingTarget = null;
    static boolean hasSeenEnemyKing = false;
    static boolean isProtector = false;
    static boolean checkedProtectorStatus = false;
    static MapLocation myKingLocation = null;
    static int mySpawnRound = -1;

    public static void run() throws Exception {

        // Find our rat king's location
        if (myKingLocation == null) {
            for (RobotInfo ally : G.allyRobots) {
                if (ally.type == UnitType.RAT_KING) {
                    myKingLocation = ally.location;
                    break;
                }
            }
        }

        // Check shared array for enemy king location (index 0 = x, index 1 = y)
        int sharedX = G.rc.readSharedArray(0);
        int sharedY = G.rc.readSharedArray(1);

        if (sharedX != 0 || sharedY != 0) {
            // Someone found the king! Use that location
            enemyKingTarget = new MapLocation(sharedX, sharedY);
            hasSeenEnemyKing = true;
            System.out.println("Read enemy king location from shared array: " + enemyKingTarget);
        } else if (enemyKingTarget == null) {
            // Find enemy rat king location (estimate by reflection from map center)
            // Vary the estimate based on robot ID so rats spread out (squeak radius is sqrt(16) = 4)
            int mapWidth = G.rc.getMapWidth();
            int mapHeight = G.rc.getMapHeight();
            MapLocation myKingEstimate = G.rc.getLocation();

            // Enemy king is likely reflected across map center
            int dx = G.mapCenter.x - myKingEstimate.x;
            int dy = G.mapCenter.y - myKingEstimate.y;
            MapLocation baseEstimate = new MapLocation(G.mapCenter.x + dx, G.mapCenter.y + dy);

            // Add variation based on robot ID (spread by ~3 tiles)
            int robotId = G.rc.getID();
            int offsetX = (((robotId * 7) % 7) - 3) * 3; // Range: -3 to 3
            int offsetY = (((robotId * 11) % 7) - 3) * 3; // Range: -3 to 3

            enemyKingTarget = new MapLocation(
                Math.max(0, Math.min(mapWidth - 1, baseEstimate.x + offsetX)),
                Math.max(0, Math.min(mapHeight - 1, baseEstimate.y + offsetY))
            );

            System.out.println("Calculated varied enemy king target: " + enemyKingTarget + " (offset: " + offsetX + ", " + offsetY + ")");
        }

        // Priority 0: Check for cats (highest priority - survival!)
        // Cats are neutral NPCs, so we need to sense all nearby robots (not just opponents)
        RobotInfo nearestCat = null;
        int nearestCatDist = Integer.MAX_VALUE;

        // Sense all robots in vision range (including neutral cats)
        RobotInfo[] allNearbyRobots = G.rc.senseNearbyRobots(-1);
        for (RobotInfo robot : allNearbyRobots) {
            if (robot.type == UnitType.CAT) {
                int dist = G.me.distanceSquaredTo(robot.location);
                if (dist < nearestCatDist) {
                    nearestCat = robot;
                    nearestCatDist = dist;
                }
                System.out.println("  CAT spotted at " + robot.location + " (dist=" + dist + ")");
            }
        }

        // If we see a cat, RUN! Don't squeak, don't fight, just flee
        if (nearestCat != null) {
            System.out.println("!!! CAT DETECTED at " + nearestCat.location + " - FLEEING SILENTLY !!!");

            // Try to place a cat trap if we have enough cheese
            if (G.rc.isActionReady() && G.rc.getGlobalCheese() > 500) {
                try {
                    // Try to place trap between us and the cat
                    MapLocation trapLoc = G.rc.adjacentLocation(G.me.directionTo(nearestCat.location));
                    if (G.rc.canPlaceCatTrap(trapLoc)) {
                        G.rc.placeCatTrap(trapLoc);
                        System.out.println("Placed cat trap at " + trapLoc + " while fleeing!");
                    }
                } catch (Exception e) {
                    System.out.println("Failed to place cat trap: " + e.getMessage());
                }
            }

            if (G.rc.isMovementReady()) {
                Motion.bugnavAway(nearestCat.location);
            }
            return; // Skip everything else - survival first!
        }


        // Priority 1: Attack nearby enemy baby rats
        RobotInfo targetBabyRat = null;
        int closestDist = Integer.MAX_VALUE;

        System.out.println("Opponent robots in vision: " + G.opponentRobots.length);

        for (RobotInfo enemy : G.opponentRobots) {
            System.out.println("  Enemy: " + enemy.type + " at " + enemy.location);
            if (enemy.type == UnitType.BABY_RAT) {
                int dist = G.me.distanceSquaredTo(enemy.location);
                if (dist < closestDist) {
                    closestDist = dist;
                    targetBabyRat = enemy;
                }
            }
        }

        // Priority 2: Attack enemy rat kings
        RobotInfo targetKing = null;
        for (RobotInfo enemy : G.opponentRobots) {
            if (enemy.type == UnitType.RAT_KING) {
                targetKing = enemy;
                // Update our target to the actual king location once we see it
                enemyKingTarget = targetKing.location;
                hasSeenEnemyKing = true;
                System.out.println("*** CAN SEE ENEMY KING at " + targetKing.location);
                break;
            }
        }

        // Squeak logic:
        // - If we can see the king: squeak its location (ignore other squeaks)
        // - If we can't see the king but heard a squeak: relay that squeak
        if (targetKing != null) {
            // We can see the king, squeak its actual location
            int message = (targetKing.location.x) | (targetKing.location.y << 6);
            try {
                G.rc.squeak(message);
                System.out.println("Squeaked ACTUAL king location: " + targetKing.location);
            } catch (Exception e) {
                System.out.println("Failed to squeak: " + e.getMessage());
            }
        } else {
            // We can't see the king, listen for squeaks and relay them
            Message[] squeaks = G.rc.readSqueaks(-1);
            if (squeaks.length > 0) {
                // Get the most recent squeak
                Message latestSqueak = squeaks[squeaks.length - 1];
                int messageContent = latestSqueak.getBytes();
                int x = messageContent & 0b111111;
                int y = (messageContent >> 6) & 0b111111;

                if (x > 0 || y > 0) {
                    MapLocation squeakedLocation = new MapLocation(x, y);
                    System.out.println("Heard squeak about enemy king at " + squeakedLocation);
                    enemyKingTarget = squeakedLocation;
                    hasSeenEnemyKing = true;

                    // Relay the squeak
                    try {
                        G.rc.squeak(messageContent);
                        System.out.println("Relayed squeak about king at " + squeakedLocation);
                    } catch (Exception e) {
                        System.out.println("Failed to relay squeak: " + e.getMessage());
                    }
                }
            }
        }

        // Try to place rat trap if we see an enemy rat and have enough cheese
        if ((targetBabyRat != null || targetKing != null) && G.rc.getGlobalCheese() > 500) {
            RobotInfo trapTarget = (targetBabyRat != null) ? targetBabyRat : targetKing;
            try {
                // Try to place trap near the enemy
                MapLocation trapLoc = G.me.add(G.me.directionTo(trapTarget.location));
                if (G.rc.canPlaceRatTrap(trapLoc) && Motion.getChebyshevDistance(trapLoc, G.me) <= 2) {
                    G.rc.placeRatTrap(trapLoc);
                    System.out.println("Placed rat trap at " + trapLoc + " near enemy!");
                }
            } catch (Exception e) {
                // Trap placement failed, continue with attack
            }
        }

        // Attack logic
        //System.out.println("Action ready: " + G.rc.isActionReady());
        //System.out.println("Action cooldown: " + G.rc.getActionCooldownTurns());

        if (G.rc.isActionReady()) {
            RobotInfo target = (targetBabyRat != null) ? targetBabyRat : targetKing;

            if (target != null) {
                MapLocation attackLoc = target.location;

                // If attacking a rat king (3x3), find an attackable location within its body
                if (target.type == UnitType.RAT_KING) {
                    System.out.println("Targeting rat king at center " + target.location);
                    // Rat king is 3x3, try all 9 locations it occupies
                    MapLocation[] kingLocations = new MapLocation[] {
                        target.location,
                        target.location.translate(-1, -1), target.location.translate(0, -1), target.location.translate(1, -1),
                        target.location.translate(-1, 0), target.location.translate(1, 0),
                        target.location.translate(-1, 1), target.location.translate(0, 1), target.location.translate(1, 1)
                    };

                    boolean foundAttackable = false;
                    for (MapLocation loc : kingLocations) {
                        if (G.rc.canAttack(loc)) {
                            attackLoc = loc;
                            foundAttackable = true;
                            System.out.println("  Can attack king tile: " + loc);
                            break;
                        }
                    }
                    if (!foundAttackable) {
                        System.out.println("  Cannot attack any king tile!");
                    }
                } else {
                    System.out.println("Targeting baby rat at " + target.location);
                }

                System.out.println("Attack location: " + attackLoc);
                System.out.println("Can attack: " + G.rc.canAttack(attackLoc));

                if (G.rc.canAttack(attackLoc)) {
                    // Calculate max cheese we can spend for attack
                    int availableCheese = G.rc.getRawCheese();
                    int cheeseToSpend = Math.min(availableCheese, 10); // Cap spending

                    System.out.println("Available cheese: " + availableCheese);
                    System.out.println("Spending cheese: " + cheeseToSpend);

                    G.rc.attack(attackLoc, cheeseToSpend);
                    System.out.println("*** ATTACKED!");
                }
            } else {
                System.out.println("No target to attack");
            }
        }



        if (G.rc.isMovementReady()) {
            // Move towards nearby enemy baby rat if exists
            if (targetBabyRat != null) {
                System.out.println("Moving towards enemy baby rat at " + targetBabyRat.location);
                Motion.bugnavTowards(targetBabyRat.location);
            } else if (targetKing != null) {
                System.out.println("Moving towards enemy king at " + targetKing.location);
                Motion.bugnavTowards(targetKing.location);
            } else {
                // No visible enemies
                // If we've reached the estimated location but haven't seen the king, spread to search
                int distToTarget = G.me.distanceSquaredTo(enemyKingTarget);
                if (hasSeenEnemyKing || distToTarget > 16) {
                    // Still far from target, or we've seen king before - keep moving to target
                    System.out.println("Moving towards target location " + enemyKingTarget + " (dist=" + distToTarget + ")");
                    Motion.bugnavTowards(enemyKingTarget);
                } else {
                    // We're close to estimated location but don't see king - spread to search
                    System.out.println("At estimated location but no king found - spreading to search");
                    Motion.spreadRandomly();
                }
            }
        }

        G.indicatorString.append("Target=" + enemyKingTarget + " ");
        digDirtIfNeeded();
    }

    private static void digDirtIfNeeded() throws Exception {
        // Check if there's dirt in our current movement direction
        Direction currentDirection = G.rc.senseRobotAtLocation(G.me).direction;
        MapLocation ahead = G.me.add(currentDirection);

        if (G.rc.canSenseLocation(ahead)) {
            if (G.rc.canRemoveDirt(ahead)) {
                G.rc.removeDirt(ahead);
                G.indicatorString.append("DIG ");
            }
        }
    }
}

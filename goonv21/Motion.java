package goonv21;

import battlecode.common.*;

import java.util.*;

public class Motion {
    public static final int TOWARDS = 0;
    public static final int AWAY = 1;
    public static final int AROUND = 2;
    public static final int NONE = 0;
    public static final int CLOCKWISE = 1;
    public static final int COUNTER_CLOCKWISE = -1;

    public static Direction lastDir = Direction.CENTER;
    public static Direction optimalDir = Direction.CENTER;
    public static int rotation = NONE;
    public static int circleDirection = CLOCKWISE;

    public static Direction lastRandomDir = Direction.CENTER;
    public static MapLocation lastRandomSpread;

    public static boolean nextSideTurnRight = true;          // toggles side each successful move (true=>right, false=>left)

    // cycle/wall-follow helpers
    public static HashSet<Long> visitedStates = new HashSet<>();
    private static int flipAttempts = 0;         // tries to break cycles by flipping obstacle side a couple times
    private static int followWallAttempts = 0;   // used to break pathological wall-follow loops

    // last mined tile: prioritize moving into it next tick
    public static MapLocation lastMined = null;
    public static int lastMinedTick = -1;

    // common distance stuff
    public static int getManhattanDistance(MapLocation a, MapLocation b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }

    public static int getChebyshevDistance(MapLocation a, MapLocation b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y));
    }

    public static MapLocation getClosest(MapLocation[] a) throws GameActionException {
        return getClosest(a, G.rc.getLocation());
    }

    public static MapLocation getClosest(MapLocation[] a, MapLocation me) throws GameActionException {
        /* Get closest MapLocation to me (Euclidean) */
        MapLocation closest = a[0];
        int distance = me.distanceSquaredTo(a[0]);
        for (int i = a.length; --i >= 0;) {
            MapLocation loc = a[i];
            if (me.distanceSquaredTo(loc) < distance) {
                closest = loc;
                distance = me.distanceSquaredTo(loc);
            }
        }
        return closest;
    }

    public static MapLocation getClosestPair(MapLocation[] a, MapLocation[] b) throws GameActionException {
        /* Get closest pair (Euclidean) */
        MapLocation closest = a[0];
        int distance = b[0].distanceSquaredTo(a[0]);
        for (int i = a.length; --i >= 0;) {
            MapLocation loc = a[i];
            for (int j = b.length; --j >= 0;) {
                MapLocation loc2 = b[i];
                if (loc2.distanceSquaredTo(loc) < distance) {
                    closest = loc;
                    distance = loc2.distanceSquaredTo(loc);
                }
            }
        }
        return closest;
    }

    public static MapLocation getFarthest(MapLocation[] a) throws GameActionException {
        /* Get farthest MapLocation to this robot (Euclidean) */
        return getFarthest(a, G.rc.getLocation());
    }

    public static MapLocation getFarthest(MapLocation[] a, MapLocation me) throws GameActionException {
        /* Get farthest MapLocation to me (Euclidean) */
        MapLocation closest = a[0];
        int distance = me.distanceSquaredTo(a[0]);
        for (int i = a.length; --i >= 0;) {
            MapLocation loc = a[i];
            if (me.distanceSquaredTo(loc) > distance) {
                closest = loc;
                distance = me.distanceSquaredTo(loc);
            }
        }
        return closest;
    }

    // basic random movement
    public static void moveRandomly() throws GameActionException {
        if (G.rc.isMovementReady()) {
            boolean stuck = true;
            for (int i = G.DIRECTIONS.length; --i >= 0;) {
                if (G.rc.canMove(G.DIRECTIONS[i])) {
                    stuck = false;
                }
            }
            if (stuck) {
                return;
            }
            // move in a random direction but minimize making useless moves back to where
            // you came from
            Direction direction = G.DIRECTIONS[G.rng.nextInt(G.DIRECTIONS.length)];
            if (direction == lastRandomDir.opposite() && G.rc.canMove(direction.opposite())) {
                direction = direction.opposite();
            }
            if (move(direction)) {
                lastRandomDir = direction;
            }
        }
    }

    public static void spreadRandomly() throws GameActionException {
        boolean stuck = true;
        for (int i = G.DIRECTIONS.length; --i >= 0;) {
            if (canMove(G.DIRECTIONS[i])) {
                stuck = false;
            }
        }
        if (stuck) {
            return;
        }
        if (G.rc.isMovementReady()) {
            MapLocation me = G.rc.getLocation();
            MapLocation target = me;
            for (int i = G.allyRobots.length; --i >= 0;) {
                if (!G.allyRobots[i].type.isRobotType())
                    // ignore towers
                    target = target.subtract(me.directionTo(G.allyRobots[i].getLocation()));
            }
            for (int i = 7; --i >= 0;) {
                if (!G.rc.canMove(G.DIRECTIONS[i])) {
                    target = target.subtract(G.DIRECTIONS[i]);
                }
            }
            if (target.equals(me)) {
                // just keep moving in the same direction as before if there's no robots nearby
                if (G.rc.getRoundNum() % 3 == 0 || lastRandomSpread == null) {
                    moveRandomly(); // occasionally move randomly to avoid getting stuck
                } else if (G.rng.nextInt(20) == 1) {
                    // don't get stuck in corners
                    lastRandomSpread = me.add(G.DIRECTIONS[G.rng.nextInt(G.DIRECTIONS.length)]);
                    moveRandomly();
                } else {
                    Direction direction = me.directionTo(target);
                    if (move(direction)) {
                        lastRandomSpread = lastRandomSpread.add(direction);
                        lastRandomDir = direction;
                    } else {
                        moveRandomly();
                    }
                }
                lastDir = Direction.CENTER;
                optimalDir = Direction.CENTER;
            } else {
                if (lastDir == me.directionTo(target)) {
                    lastDir = Direction.CENTER;
                }
                Direction direction = bug2Helper(me, target, TOWARDS, 0, 0);
                if (move(direction)) {
                    lastRandomSpread = target;
                    lastRandomDir = direction;
                }
            }
        }
    }

    // bugnav helpers
    public static MapLocation bugnavTarget;
    public static int bugnavMode = -1;

    public static int minDistanceToTarget;
    public static int maxDistanceFromTarget;
    public static int minCircleDistance;
    public static int maxCircleDistance;
    public static boolean obstacleOnRight;
    public static MapLocation currentObstacle;
    public static StringBuilder visitedList = new StringBuilder();

    public static Direction bug2Helper(MapLocation me, MapLocation target, int mode, int minCircleDistance1,
                                       int maxCircleDistance1) throws GameActionException {
        boolean stuck = true;
        for (int i = 8; --i >= 0;) {
            if (G.rc.canMove(G.DIRECTIONS[i])) {
                stuck = false;
                break;
            }
        }

        if (stuck) {
            // If totally immobile but we can remove adjacent dirt, do so aggressively:
            for (Direction d : Direction.allDirections()) {
                MapLocation adj = G.rc.getLocation().add(d);
                if (G.rc.canSenseLocation(adj) && G.rc.onTheMap(adj)) {
                    MapInfo mi = G.rc.senseMapInfo(adj);
                    if (mi.isDirt() && G.rc.canRemoveDirt(adj)) {
                        G.indicatorString.append("AGGRESSIVE-MINE-IMMOBILE ");
                        G.rc.removeDirt(adj);
                        lastMined = adj;
                        lastMinedTick = G.rc.getRoundNum();
                        // attempt to move into it same tick if possible
                        if (G.rc.canMove(d)) {
                            move(d);
                            return d;
                        }
                    }
                }
            }
            return Direction.CENTER;
        }

        if (bugnavTarget == null || !bugnavTarget.equals(target) || bugnavMode != mode) {
            reset();
            visitedStates.clear();
            flipAttempts = 0;
            followWallAttempts = 0;
        }
        bugnavTarget = target;
        bugnavMode = mode;
        minCircleDistance = minCircleDistance1;
        maxCircleDistance = maxCircleDistance1;

        int distanceToTarget = getChebyshevDistance(G.rc.getLocation(), target);
        switch (bugnavMode) {
            case TOWARDS:
                if (distanceToTarget < minDistanceToTarget) {
                    reset();
                    minDistanceToTarget = distanceToTarget;
                }
                break;
            case AWAY:
                if (distanceToTarget > maxDistanceFromTarget) {
                    reset();
                    maxDistanceFromTarget = distanceToTarget;
                }
                break;
            case AROUND:
                // kind of approximation
                // probably won't circle around something with very large radius?
                int dist = G.me.distanceSquaredTo(bugnavTarget);
                if (dist < minCircleDistance) {
                    if (distanceToTarget > maxDistanceFromTarget) {
                        reset();
                        maxDistanceFromTarget = distanceToTarget;
                    }
                } else if (dist > maxCircleDistance) {
                    if (distanceToTarget < minDistanceToTarget) {
                        reset();
                        minDistanceToTarget = distanceToTarget;
                    }
                }
                break;
        }

        if (currentObstacle != null && G.rc.canSenseLocation(currentObstacle)
                && G.rc.sensePassability(currentObstacle) && !G.rc.canSenseRobotAtLocation(currentObstacle)) {
            reset();
        }

        // visitedList legacy: keep but not relied on heavily
        if (visitedList.indexOf("" + getState()) != -1) {
            reset();
        }
        visitedList.append("" + getState());

        Direction targetDirection = getTargetDirection();

        // if we just mined something last tick, try to move into it first (prioritize)
        if (lastMined != null && lastMinedTick == G.rc.getRoundNum() - 1) {
            Direction tryDir = me.directionTo(lastMined);
            if (tryDir != Direction.CENTER && G.rc.canMove(tryDir)) {
                lastMined = null;
                lastMinedTick = -1;
                return tryDir;
            } else {
                // if we cannot move into it now, keep it for next tick (maybe occupied)
            }
        }

        if (currentObstacle == null) {
            if (canMove(targetDirection)) {
                return targetDirection;
            }
            setInitialDirection(targetDirection);
        }

        return followWall(true);
    }

    public static void reset() {
        minDistanceToTarget = Integer.MAX_VALUE;
        maxDistanceFromTarget = 0;
        obstacleOnRight = true;
        currentObstacle = null;
        visitedList = new StringBuilder();
        rotation = NONE;
        optimalDir = Direction.CENTER;
    }

    public static Direction getTargetDirection() throws GameActionException {
        if (bugnavTarget == null) {
            return Direction.CENTER;
        }
        if (G.me.equals(bugnavTarget)) {
            if (bugnavMode == AROUND) {
                return Direction.EAST;
            } else {
                return Direction.CENTER;
            }
        }
        Direction direction = G.me.directionTo(bugnavTarget);
        switch (bugnavMode) {
            case AWAY:
                direction = direction.opposite();
                break;
            case AROUND:
                int dist = G.me.distanceSquaredTo(bugnavTarget);
                if (dist < minCircleDistance) {
                    direction = direction.opposite();
                } else if (dist <= maxCircleDistance) {
                    direction = direction.rotateLeft().rotateLeft();
                    if (circleDirection == COUNTER_CLOCKWISE) {
                        direction = direction.opposite();
                    }

                    if (!canMove(direction)) {
                        direction = direction.opposite();
                        circleDirection *= -1;
                    }
                }
                break;
        }
        return direction;
    }

    public static void setInitialDirection(Direction forward) throws GameActionException {
        if (forward == null) forward = Direction.CENTER;
        Direction left = forward.rotateLeft();
        for (int i = 8; --i >= 0;) {
            MapLocation location = G.rc.adjacentLocation(left);
            if (G.rc.canSenseLocation(location)) {
                if (G.rc.onTheMap(location) && G.rc.sensePassability(location) && !G.rc.canSenseRobotAtLocation(location)) {
                    break;
                }
            }
            left = left.rotateLeft();
        }

        Direction right = forward.rotateRight();
        for (int i = 8; --i >= 0;) {
            MapLocation location = G.rc.adjacentLocation(right);
            if (G.rc.canSenseLocation(location)) {
                if (G.rc.onTheMap(location) && G.rc.sensePassability(location) && !G.rc.canSenseRobotAtLocation(location)) {
                    break;
                }
            }
            right = right.rotateRight();
        }

        MapLocation leftLocation = G.rc.adjacentLocation(left);
        MapLocation rightLocation = G.rc.adjacentLocation(right);

        int leftDistance = Integer.MAX_VALUE;
        int rightDistance = Integer.MAX_VALUE;
        if (bugnavTarget != null) {
            leftDistance = getChebyshevDistance(leftLocation, bugnavTarget);
            rightDistance = getChebyshevDistance(rightLocation, bugnavTarget);
        }

        if (leftDistance == rightDistance) {
            obstacleOnRight = (G.rng.nextBoolean());
        } else if (leftDistance < rightDistance) {
            obstacleOnRight = true;
        } else if (rightDistance < leftDistance) {
            obstacleOnRight = false;
        } else {
            obstacleOnRight = G.me.distanceSquaredTo(leftLocation) < G.me.distanceSquaredTo(rightLocation);
        }

        if (obstacleOnRight) {
            currentObstacle = G.rc.adjacentLocation(left.rotateRight());
        } else {
            currentObstacle = G.rc.adjacentLocation(right.rotateLeft());
        }
    }

    // helper: encode small state (x,y,direction,side) into long for visited detection
    private static long encodeState(MapLocation me, MapLocation obstacle, boolean side) {
        int dirOrd = 0;
        if (obstacle != null) {
            dirOrd = me.directionTo(obstacle).ordinal();
        } else if (bugnavTarget != null) {
            dirOrd = me.directionTo(bugnavTarget).ordinal();
        } else {
            dirOrd = Direction.CENTER.ordinal();
        }
        long key = (((long) me.x & 0xFFFFL) << 48) | (((long) me.y & 0xFFFFL) << 32)
                | (((long) dirOrd & 0xFFL) << 16) | (side ? 1L : 0L);
        return key;
    }

    // returns count of passable neighbors (after mining loc) - used to choose which dirt to mine
    private static int passableNeighborsIfMined(MapLocation loc) throws GameActionException {
        int count = 0;
        for (Direction d : Direction.allDirections()) {
            MapLocation n = loc.add(d);
            if (!G.rc.onTheMap(n)) continue;
            if (!G.rc.canSenseLocation(n)) continue;
            MapInfo mi = G.rc.senseMapInfo(n);
            if (mi.isPassable()) count++;
        }
        return count;
    }

    // small Dijkstra/BFS over currently sensed tiles: cost = number of dirt tiles that must be removed.
    // Returns a MapLocation (tile adjacent to us) that is a good dirt to remove to escape, or null if none.
    private static MapLocation findEscapeDirt(MapLocation me, MapLocation target) throws GameActionException {
        PriorityQueue<long[]> pq = new PriorityQueue<>(Comparator.comparingLong(a -> a[0]));
        HashMap<Long, Integer> bestCost = new HashMap<>();

        int sx = me.x, sy = me.y;
        long startKey = (((long) sx) << 32) | (sy & 0xffffffffL);
        pq.add(new long[]{0, sx, sy});
        bestCost.put(startKey, 0);

        MapLocation bestDirt = null;
        int bestDirtCost = Integer.MAX_VALUE;

        int iterations = 0;
        final int MAX_ITER = 2500; // cap for CPU safety
        while (!pq.isEmpty() && iterations++ < MAX_ITER) {
            long[] cur = pq.poll();
            int cost = (int) cur[0];
            int x = (int) cur[1];
            int y = (int) cur[2];
            long key = (((long) x) << 32) | (y & 0xffffffffL);
            if (bestCost.getOrDefault(key, Integer.MAX_VALUE) < cost) continue;

            MapLocation loc = new MapLocation(x, y);
            if (!(x == sx && y == sy) && G.rc.canSenseLocation(loc) && G.rc.onTheMap(loc)) {
                MapInfo mi = G.rc.senseMapInfo(loc);
                if (mi.isPassable()) {
                    // if any neighbor of this location is passable and not enclosed,
                    for (Direction d : Direction.allDirections()) {
                        MapLocation adj = loc.add(d);
                        if (!G.rc.canSenseLocation(adj) || !G.rc.onTheMap(adj)) continue;
                        MapInfo ami = G.rc.senseMapInfo(adj);
                        if (ami.isPassable()) {
                            if (cost == 0) return null; // no dirt needed to escape
                            if (cost < bestDirtCost) {
                                bestDirtCost = cost;
                                // choose adjacent dirt near start if possible
                                for (Direction dd : Direction.allDirections()) {
                                    MapLocation cand = me.add(dd);
                                    if (!G.rc.canSenseLocation(cand) || !G.rc.onTheMap(cand)) continue;
                                    MapInfo cmi = G.rc.senseMapInfo(cand);
                                    if (cmi.isDirt() && G.rc.canRemoveDirt(cand)) {
                                        bestDirt = cand;
                                        break;
                                    }
                                }
                                if (bestDirt != null) return bestDirt;
                            }
                        }
                    }
                }
            }

            // expand neighbors
            for (Direction d : Direction.allDirections()) {
                int nx = x + d.dx;
                int ny = y + d.dy;
                MapLocation nloc = new MapLocation(nx, ny);
                if (!G.rc.canSenseLocation(nloc) || !G.rc.onTheMap(nloc)) continue;
                MapInfo nmi = G.rc.senseMapInfo(nloc);
                int ncost = cost + (nmi.isDirt() ? 1 : 0);
                long nkey = (((long) nx) << 32) | (ny & 0xffffffffL);
                if (!bestCost.containsKey(nkey) || bestCost.get(nkey) > ncost) {
                    bestCost.put(nkey, ncost);
                    pq.add(new long[]{ncost, nx, ny});
                }
            }
        }

        return bestDirt;
    }

    // improved followWall that prefers a consistent side and tries to mine-then-move if necessary
    public static Direction followWall(boolean canRotate) throws GameActionException {
        MapLocation me = G.rc.getLocation();

        // safety: if bugnavTarget is null, we can't sensibly follow a wall toward nothing
        if (bugnavTarget == null) {
            return Direction.CENTER;
        }

        long stateKey = encodeState(me, currentObstacle, obstacleOnRight);
        if (visitedStates.contains(stateKey)) {
            if (flipAttempts < 2) {
                obstacleOnRight = !obstacleOnRight;
                rotation = NONE;
                currentObstacle = null;
                flipAttempts++;
                G.indicatorString.append("FLIP-SIDE ");
            } else {
                visitedStates.clear();
                flipAttempts = 0;
                reset();
                G.indicatorString.append("HARD-RESET-FOLLOW ");
            }
        } else {
            visitedStates.add(stateKey);
        }

        // guard: ensure currentObstacle is not null. If null, attempt re-init or return a target-directed move.
        if (currentObstacle == null) {
            Direction td = getTargetDirection();
            if (td != Direction.CENTER && canMove(td)) {
                return td;
            }
            // attempt to set initial direction; if that leaves currentObstacle null still, set a fallback
            setInitialDirection(td);
            if (currentObstacle == null) {
                // fallback: try to mine an adjacent forward dirt or just return CENTER
                for (Direction d : Direction.allDirections()) {
                    MapLocation adj = me.add(d);
                    if (G.rc.canSenseLocation(adj) && G.rc.onTheMap(adj)) {
                        MapInfo mi = G.rc.senseMapInfo(adj);
                        if (mi.isDirt() && G.rc.canRemoveDirt(adj)) {
                            G.rc.removeDirt(adj);
                            lastMined = adj;
                            lastMinedTick = G.rc.getRoundNum();
                            if (G.rc.canMove(d)) {
                                move(d);
                                return d;
                            }
                            return Direction.CENTER;
                        }
                    }
                }
                return Direction.CENTER;
            }
        }

        // compute the direction from me to currentObstacle (should be valid now)
        Direction direction = null;
        try {
            direction = me.directionTo(currentObstacle);
        } catch (Exception e) {
            direction = null;
        }
        if (direction == null) {
            // fallback
            Direction td = getTargetDirection();
            if (td != Direction.CENTER && canMove(td)) {
                return td;
            }
            return Direction.CENTER;
        }

        // 1) Try the standard rotated scan for a legal move
        for (int i = 8; --i >= 0;) {
            direction = obstacleOnRight ? direction.rotateLeft() : direction.rotateRight();
            if (direction == null || direction == Direction.CENTER) continue;
            if (canMove(direction) && lastDir != direction.opposite()) {
                followWallAttempts = 0;
                return direction;
            }

            MapLocation location = G.rc.adjacentLocation(direction);
            if (!G.rc.canSenseLocation(location)) continue;
            if (canRotate && !G.rc.onTheMap(location)) {
                obstacleOnRight = !obstacleOnRight;
                G.indicatorString.append("MAP-BORDER-FLIP ");
                return followWall(false);
            }

            if (G.rc.onTheMap(location) && (!G.rc.senseMapInfo(location).isPassable() || G.rc.canSenseRobotAtLocation(location))) {
                currentObstacle = location;
            }
        }

        // 2) If no immediate move found, attempt to mine a good adjacent dirt (prefer ones that open space)
        Direction forward = me.directionTo(bugnavTarget);
        Direction[] candidates = new Direction[]{
                forward, forward.rotateLeft(), forward.rotateRight(),
                forward.rotateLeft().rotateLeft(), forward.rotateRight().rotateRight(),
                forward.rotateLeft().rotateLeft().rotateLeft(), forward.rotateRight().rotateRight().rotateRight()
        };

        MapLocation bestPick = null;
        int bestScore = -1;
        for (Direction cand : candidates) {
            if (cand == null) continue;
            MapLocation candLoc = me.add(cand);
            if (!G.rc.onTheMap(candLoc)) continue;
            if (!G.rc.canSenseLocation(candLoc)) continue;
            MapInfo mi = G.rc.senseMapInfo(candLoc);
            if (!mi.isDirt()) continue;
            if (!G.rc.canRemoveDirt(candLoc)) continue;
            int score = passableNeighborsIfMined(candLoc);
            // prefer more openings; tie-break by closer to target
            int distScore = -getChebyshevDistance(candLoc, bugnavTarget);
            int combined = (score << 16) + (distScore & 0xFFFF);
            if (combined > bestScore) {
                bestScore = combined;
                bestPick = candLoc;
            }
        }

        if (bestPick != null) {
            Direction dirToPick = me.directionTo(bestPick);
            G.indicatorString.append("MINING-ADJ " + bestPick + " score=" + bestScore + " ");
            G.rc.removeDirt(bestPick);
            lastMined = bestPick;
            lastMinedTick = G.rc.getRoundNum();
            if (dirToPick != Direction.CENTER && G.rc.canMove(dirToPick)) {
                move(dirToPick);
                return dirToPick;
            } else {
                // cannot move same tick: return CENTER and try next tick
                followWallAttempts = 0;
                return Direction.CENTER;
            }
        }

        // 3) If no adjacent candidate, run a small sensed-area search to find a minimal-dirt tile to remove
        MapLocation escapeDirt = findEscapeDirt(me, bugnavTarget);
        if (escapeDirt != null && G.rc.canRemoveDirt(escapeDirt)) {
            Direction dirTo = me.directionTo(escapeDirt);
            G.indicatorString.append("MINING-ESCAPE " + escapeDirt + " ");
            G.rc.removeDirt(escapeDirt);
            lastMined = escapeDirt;
            lastMinedTick = G.rc.getRoundNum();
            if (dirTo != Direction.CENTER && G.rc.canMove(dirTo)) {
                move(dirTo);
                return dirTo;
            } else {
                return Direction.CENTER;
            }
        }

        // 4) Rotation/choice attempt (if rotation not chosen, simulate short lookahead to set rotation)
        if (rotation == NONE) {
            int[] simulated = simulateMovement(me, bugnavTarget);
            int clockwiseDist = simulated[0];
            int counterClockwiseDist = simulated[2];
            boolean clockwiseStuck = simulated[1] == 1;
            boolean counterClockwiseStuck = simulated[3] == 1;

            int tempMode = bugnavMode;
            if (bugnavMode == AROUND) {
                if (clockwiseDist < minCircleDistance) {
                    if (counterClockwiseDist < minCircleDistance) {
                        tempMode = AWAY;
                    } else {
                        tempMode = AWAY;
                    }
                } else {
                    if (counterClockwiseDist < minCircleDistance) {
                        tempMode = AWAY;
                    } else {
                        tempMode = TOWARDS;
                    }
                }
            }
            if (clockwiseStuck) {
                rotation = COUNTER_CLOCKWISE;
            } else if (counterClockwiseStuck) {
                rotation = CLOCKWISE;
            } else if (tempMode == TOWARDS) {
                rotation = (clockwiseDist < counterClockwiseDist) ? CLOCKWISE : COUNTER_CLOCKWISE;
            } else if (tempMode == AWAY) {
                rotation = (clockwiseDist < counterClockwiseDist) ? COUNTER_CLOCKWISE : CLOCKWISE;
            }
            G.indicatorString.append("ROT-SET " + rotation + " ");
        }

        // 5) Attempt rotation-ordered motion once more (respecting lastDir)
        boolean flip = false;
        Direction rotated = direction;
        for (int i = 8; --i >= 0;) {
            rotated = (rotation == CLOCKWISE) ? rotated.rotateRight() : rotated.rotateLeft();
            if (rotated == null || rotated == Direction.CENTER) continue;
            if (!G.rc.onTheMap(me.add(rotated))) {
                flip = true;
            }
            if (canMove(rotated) && lastDir != rotated.opposite()) {
                if (flip) rotation *= -1;
                followWallAttempts = 0;
                return rotated;
            }
        }
        if (flip) rotation *= -1;

        // follow-wall attempt cap: reset if we've tried too many times following walls
        followWallAttempts++;
        if (followWallAttempts > 18) {
            followWallAttempts = 0;
            reset();
            visitedStates.clear();
            flipAttempts = 0;
            G.indicatorString.append("FOLLOWCAP-RESET ");
            return Direction.CENTER;
        }

        // fallback: try to back out if possible
        if (lastDir != Direction.CENTER && canMove(lastDir.opposite())) {
            followWallAttempts = 0;
            return lastDir.opposite();
        }

        return Direction.CENTER;
    }

    public static char getState() {
        Direction direction = Direction.CENTER;
        if (bugnavTarget != null || currentObstacle != null) {
            MapLocation ref = currentObstacle != null ? currentObstacle : bugnavTarget;
            if (ref != null) direction = G.me.directionTo(ref);
        }
        int rotation = obstacleOnRight ? 1 : 0;

        return (char) ((((G.me.x << 6) | G.me.y) << 4) | (direction.ordinal() << 1) |
                rotation);
    }

    public static int[] simulateMovement(MapLocation me, MapLocation dest) throws GameActionException {
        MapLocation clockwiseLoc = me;
        Direction clockwiseLastDir = lastDir;
        int clockwiseStuck = 0;
        MapLocation counterClockwiseLoc = me;
        Direction counterClockwiseLastDir = lastDir;
        int counterClockwiseStuck = 0;
        search: for (int t = 0; t < 10; t++) {
            if (clockwiseLoc.equals(dest)) {
                break;
            }
            if (counterClockwiseLoc.equals(dest)) {
                break;
            }
            Direction clockwiseDir = clockwiseLoc.directionTo(dest);
            {
                for (int i = 9; --i >= 0;) {
                    MapLocation loc = clockwiseLoc.add(clockwiseDir);
                    if (G.rc.onTheMap(loc)) {
                        if (!G.rc.canSenseLocation(loc)) {
                            break search;
                        }
                        if (clockwiseDir != clockwiseLastDir.opposite() && G.rc.senseMapInfo(loc).isPassable()
                                && G.rc.senseRobotAtLocation(loc) == null) {
                            clockwiseLastDir = clockwiseDir;
                            break;
                        }
                    }
                    clockwiseDir = clockwiseDir.rotateRight();
                    if (i == 7) {
                        clockwiseStuck = 1;
                        break search;
                    }
                }
            }
            Direction counterClockwiseDir = counterClockwiseLoc.directionTo(dest);
            {
                for (int i = 9; --i >= 0;) {
                    MapLocation loc = counterClockwiseLoc.add(counterClockwiseDir);
                    if (G.rc.onTheMap(loc)) {
                        if (!G.rc.canSenseLocation(loc)) {
                            break search;
                        }
                        if (counterClockwiseDir != counterClockwiseLastDir.opposite()
                                && G.rc.senseMapInfo(loc).isPassable() && G.rc.senseRobotAtLocation(loc) == null) {
                            counterClockwiseLastDir = counterClockwiseDir;
                            break;
                        }
                    }
                    counterClockwiseDir = counterClockwiseDir.rotateLeft();
                    if (i == 7) {
                        counterClockwiseStuck = 1;
                        break search;
                    }
                }
            }
            clockwiseLoc = clockwiseLoc.add(clockwiseDir);
            counterClockwiseLoc = counterClockwiseLoc.add(counterClockwiseDir);
        }

        int clockwiseDist = clockwiseLoc.distanceSquaredTo(dest);
        int counterClockwiseDist = counterClockwiseLoc.distanceSquaredTo(dest);

        return new int[]{clockwiseDist, clockwiseStuck, counterClockwiseDist, counterClockwiseStuck};
    }

    // bugnav public wrappers

    public static void bugnavTowards(MapLocation dest) throws GameActionException {
        bugnavTowards(dest, defaultMicro);
    }

    public static void bugnavTowardsNoTurning(MapLocation dest) throws GameActionException {
        bugnavTowards(dest, defaultMicroNoTurn);
    }

    public static void bugnavTowardsExplore(MapLocation dest) throws GameActionException {
        Micro m = defaultMicro;

        // If we mined a tile last tick, try to move into it now (priority)
        if (lastMined != null && lastMinedTick == G.rc.getRoundNum() - 1) {
            Direction dirToLast = G.rc.getLocation().directionTo(lastMined);
            if (dirToLast != Direction.CENTER && G.rc.canMove(dirToLast)) {
                move(dirToLast);
                lastMined = null;
                lastMinedTick = -1;
                return;
            } else if (!G.rc.canSenseLocation(lastMined) || !G.rc.onTheMap(lastMined)) {
                lastMined = null; // invalidated
                lastMinedTick = -1;
            }
        }

        // compute desired movement direction
        Direction desired = bug2Helper(G.rc.getLocation(), dest, TOWARDS, 0, 0);
        if (desired == Direction.CENTER) desired = G.rc.getLocation().directionTo(dest);

        // If desired is blocked and the forward tile is dirt we can remove, try to remove it
        MapLocation forwardLoc = G.rc.getLocation().add(desired);
        if (!G.rc.canMove(desired) && G.rc.canSenseLocation(forwardLoc) && G.rc.onTheMap(forwardLoc)) {
            MapInfo fmi = G.rc.senseMapInfo(forwardLoc);
            if (fmi.isDirt() && G.rc.canRemoveDirt(forwardLoc)) {
                // only mine if truly necessary: ensure no other neighbor reduces distance
                boolean otherBetter = false;
                for (Direction cand : G.DIRECTIONS) {
                    if (cand == desired) continue;
                    MapLocation candLoc = G.rc.getLocation().add(cand);
                    if (G.rc.onTheMap(candLoc) && G.rc.canMove(cand)) {
                        if (getChebyshevDistance(candLoc, dest) < getChebyshevDistance(G.rc.getLocation(), dest)) {
                            otherBetter = true;
                            break;
                        }
                    }
                }
                if (!otherBetter) {
                    // remove dirt, then attempt to move the same tick if allowed
                    G.indicatorString.append("MINING-FORWARD " + forwardLoc + " ");
                    G.rc.removeDirt(forwardLoc);
                    lastMined = forwardLoc;
                    lastMinedTick = G.rc.getRoundNum();
                    if (G.rc.canMove(desired)) {
                        move(desired);
                        return;
                    }
                    // otherwise, wait next tick
                    return;
                }
            }
        }

        // authoritative robot facing
        Direction facing = G.rc.getDirection();

        // Decide the 90° side-turn (use two rotates for 90 degrees)
        Direction side90Right = desired.rotateRight().rotateRight();
        Direction side90Left = desired.rotateLeft().rotateLeft();

        // CASE 1: Not facing desired -> prefer turn then move (both in same tick) if possible
        if (facing != desired) {
            MapLocation before = G.rc.getLocation();
            m.micro(desired, dest);
            MapLocation after = G.rc.getLocation();
            if (!before.equals(after)) {
                Direction movementDir = lastDir != Direction.CENTER ? lastDir : desired;
                Direction side = nextSideTurnRight ? movementDir.rotateRight().rotateRight() : movementDir.rotateLeft().rotateLeft();
                if (Motion.turn(side)) {
                    nextSideTurnRight = !nextSideTurnRight;
                }
            }
            return;
        }

        // CASE 2: Facing desired -> move forward then do a 90° side-turn (if possible) in the same tick.
        if (G.rc.canMove(desired)) {
            MapLocation before = G.rc.getLocation();
            defaultMicroNoTurn.micro(desired, dest);
            MapLocation after = G.rc.getLocation();
            boolean moved = !before.equals(after);

            if (moved) {
                Direction side = nextSideTurnRight ? side90Right : side90Left;
                if (Motion.turn(side)) {
                    nextSideTurnRight = !nextSideTurnRight;
                }
            }
            return;
        }

        // If we reach here, we were facing desired but forward is blocked.
        MapLocation before = G.rc.getLocation();
        m.micro(desired, dest);
        MapLocation after = G.rc.getLocation();
        boolean moved = !before.equals(after);
        if (moved) {
            Direction movementDir = lastDir != Direction.CENTER ? lastDir : desired;
            Direction side = nextSideTurnRight ? movementDir.rotateRight().rotateRight() : movementDir.rotateLeft().rotateLeft();
            if (Motion.turn(side)) {
                nextSideTurnRight = !nextSideTurnRight;
            }
        }
    }

    public static void bugnavTowards(MapLocation dest, Micro m) throws GameActionException {
        if (G.rc.isMovementReady()) {
            Direction d = bug2Helper(G.rc.getLocation(), dest, TOWARDS, 0, 0);
            if (d == Direction.CENTER) {
                d = G.rc.getLocation().directionTo(dest);
            }
            m.micro(d, dest);
        }
    }

    public static void bugnavAway(MapLocation dest) throws GameActionException {
        bugnavAway(dest, defaultMicro);
    }

    public static void bugnavAwayNoTurning(MapLocation dest) throws GameActionException {
        bugnavAway(dest, defaultMicroNoTurn);
    }

    public static void bugnavAway(MapLocation dest, Micro m) throws GameActionException {
        if (G.rc.isMovementReady()) {
            Direction d = bug2Helper(G.rc.getLocation(), dest, AWAY, 0, 0);
            if (d == Direction.CENTER) {
                d = G.rc.getLocation().directionTo(dest);
            }
            System.out.println(d);
            m.micro(d, dest);
        }
    }

    public static void bugnavAround(MapLocation dest, int minRadiusSquared, int maxRadiusSquared) throws GameActionException {
        bugnavAround(dest, minRadiusSquared, maxRadiusSquared, defaultMicro);
    }

    public static void bugnavAround(MapLocation dest, int minRadiusSquared, int maxRadiusSquared, Micro m) throws GameActionException {
        if (G.rc.isMovementReady()) {
            Direction d = bug2Helper(G.rc.getLocation(), dest, AROUND, minRadiusSquared, maxRadiusSquared);
            if (d == Direction.CENTER) {
                d = G.rc.getLocation().directionTo(dest);
            }
            m.micro(d, dest);
        }
    }

    // public static StringBuilder bfsQueue = new StringBuilder();
    public static final int MAX_PATH_LENGTH = 20;

    public static int step = 1;
    public static int stepOffset;
    public static int width;
    public static int height;
    public static long long1 = 1;
    public static int recalculationNeeded = MAX_PATH_LENGTH;
    public static int roundsToForget = 3;

    public static Map<Integer, Integer> lastCats = new HashMap<>();
    public static Map<Integer, MapLocation> lastCatLocations = new HashMap<>();

    public static Micro defaultMicro = new Micro() {
        public void micro(Direction d, MapLocation dest) throws GameActionException {
            RobotInfo[] nearbyCats = G.rc.senseNearbyRobots(-1, Team.NEUTRAL);
            ArrayList<MapLocation> cats = new ArrayList<>();
            for(RobotInfo c : nearbyCats) {

                lastCats.put(c.getID(), G.rc.getRoundNum());
                lastCatLocations.put(c.getID(), c.getLocation());
            }




            for(Integer id : lastCats.keySet()) {
                if(G.rc.canSenseRobot(id)) {
                    RobotInfo c = G.rc.senseRobot(id);
                    if(c.getLocation().distanceSquaredTo(G.rc.getLocation()) <= 20) {
                        cats.add(c.getLocation());
                    }
                }
                else {
                    if(lastCats.get(id) < G.rc.getRoundNum() - roundsToForget) continue;
                    MapLocation loc = lastCatLocations.get(id);
                   if(loc.distanceSquaredTo(G.rc.getLocation() )<= 20) {
                       cats.add(loc);
                   }
                }
            }


            Direction best = d;
            int bestScore = Integer.MIN_VALUE;
            for (int i = 7; --i >= 0; ) {
                if (!G.rc.canMove(G.DIRECTIONS[i])) continue;
                int score = 0;
                MapLocation nxt = G.me.add(G.DIRECTIONS[i]);

                // Only sense if we can actually see that location
                if (G.rc.canSenseLocation(nxt)) {
                    MapInfo info = G.rc.senseMapInfo(nxt);
                }

                if (G.DIRECTIONS[i] == d) {
                    score += 20;
                } else if (G.DIRECTIONS[i].rotateLeft() == d || G.DIRECTIONS[i].rotateRight() == d) {
                    score += 10;
                }

                if (!G.rc.isCooperation()) {
                    for (MapLocation cat : cats) {
                        if (cat.distanceSquaredTo(nxt) <= 10) score -= 1000 * (11 - nxt.distanceSquaredTo(cat));
                    }
                }


                if (score > bestScore) {
                    best = G.DIRECTIONS[i];
                    bestScore = score;
                }
            }
            Motion.move(best);
        }
    };

    public static Micro defaultMicroNoTurn = new Micro() {
        public void micro(Direction d, MapLocation dest) throws GameActionException  {
            RobotInfo[] nearbyCats = G.rc.senseNearbyRobots(-1, Team.NEUTRAL);
            ArrayList<MapLocation> cats = new ArrayList<>();
            for(RobotInfo c : nearbyCats) {

                lastCats.put(c.getID(), G.rc.getRoundNum());
                lastCatLocations.put(c.getID(), c.getLocation());
            }




            for(Integer id : lastCats.keySet()) {
                if(G.rc.canSenseRobot(id)) {
                    RobotInfo c = G.rc.senseRobot(id);
                    if(c.getLocation().distanceSquaredTo(G.rc.getLocation()) <= 20) {
                        cats.add(c.getLocation());
                    }
                }
                else {
                    if(lastCats.get(id) < G.rc.getRoundNum() - roundsToForget) continue;
                    MapLocation loc = lastCatLocations.get(id);
                    if(loc.distanceSquaredTo(G.rc.getLocation() )<= 20) {
                        cats.add(loc);
                    }
                }
            }

            Direction best = d;
            int bestScore = Integer.MIN_VALUE;
            for (int i = 7; --i >= 0; ) {
                if (!G.rc.canMove(G.DIRECTIONS[i])) continue;
                int score = 0;
                MapLocation nxt = G.me.add(G.DIRECTIONS[i]);

                if (G.DIRECTIONS[i] == d) {
                    score += 20;
                } else if (G.DIRECTIONS[i].rotateLeft() == d || G.DIRECTIONS[i].rotateRight() == d) {
                    score += 10;
                }

                if (!G.rc.isCooperation()) {
                    for (MapLocation cat : cats) {
                        if (cat.distanceSquaredTo(nxt) <= 10) score -= 1000 * (11 - nxt.distanceSquaredTo(cat));
                    }
                }

                if (score > bestScore) {
                    best = G.DIRECTIONS[i];
                    bestScore = score;
                }
            }
            Motion.moveNoTurn(best);
        }
    };

    // central move helper: ALWAYS check canMove BEFORE calling rc.move;
    // do the preferred turn+move pattern when possible
    public static boolean move(Direction dir) throws GameActionException {
        if (dir == null || dir == Direction.CENTER) return false;
        if (!G.rc.canMove(dir)) return false; // guard: don't try to move into impassable/occupied tiles

        // If we need to turn first and we can turn, do a turn then move if possible.
        if (dir != G.rc.getDirection() && Motion.turn(dir)) {
            // try moving after turning
            if (G.rc.canMove(dir)) {
                G.rc.move(dir);
                lastDir = dir;
                RobotPlayer.updateInfo();
                return true;
            } else {
                return false;
            }
        }
        // either already facing dir or we can't turn; attempt to move (canMove already true)
        if (G.rc.canMove(dir)) {
            G.rc.move(dir);
            lastDir = dir;
            RobotPlayer.updateInfo();
            return true;
        }
        return false;
    }

    // move without turning (used where we want to preserve facing)
    public static boolean moveNoTurn(Direction dir) throws GameActionException {
        if (dir == null || dir == Direction.CENTER) return false;
        if (G.rc.canMove(dir)) {
            G.rc.move(dir);
            lastDir = dir;
            RobotPlayer.updateInfo();
            return true;
        }
        return false;
    }

    // safe turn helper
    public static boolean turn(Direction dir) throws GameActionException {
        if (canTurn() && dir != null && dir != Direction.CENTER) {
            G.rc.turn(dir);

            if(G.rc.getCarrying() != null) {
                RobotInfo r_ = G.rc.getCarrying();
                if(r_.getTeam() != G.rc.getTeam()) {
                    MapLocation curr = G.rc.getLocation().add(dir);

                        if (G.rc.canSenseLocation(curr)) {

                        MapInfo m_ = G.rc.senseMapInfo(curr);

                        if (!m_.isPassable()) return true;

                        while (G.rc.canSenseLocation(curr)) {
                            MapInfo m = G.rc.senseMapInfo(curr);
                            RobotInfo r = G.rc.senseRobotAtLocation(curr);
                            if (r != null && r.getTeam() == G.rc.getTeam()) break;
                            if (m.isDirt() || m.isWall() || !m.isPassable() || (r != null && r.getTeam() != G.rc.getTeam())) {
                                if (G.rc.canThrowRat()) {
                                    G.rc.throwRat();
                                    break;
                                    //if(r == null || !r.getLocation().isWithinDistanceSquared(G.rc.getLocation(), 15)) Motion.move(dir);
                                }
                            }
                            curr = curr.add(dir);
                        }

                    }
                }
            }
            return true;
        }
        return false;
    }

    public static boolean canMove(Direction dir) throws GameActionException {
        if (dir == null || dir == Direction.CENTER) return false;
        return G.rc.canMove(dir);
    }

    public static boolean canTurn() throws GameActionException {
        return G.rc.canTurn();
    }

    public static boolean canReachYou(MapLocation c) throws Exception {
        boolean ans = true;
        MapLocation me = G.rc.getLocation();

        int fallback = 0;
        while(!me.equals(c)) {
            fallback  += 1;
            Direction dir = me.directionTo(c);
            me = me.add(dir);

            if(isDirtOrWall(me)) {
                int cnt = 0;
                if(isDirtOrWall(me.add(Direction.EAST))) cnt += 1;
                if(isDirtOrWall(me.add(Direction.WEST))) cnt += 1;

                if(isDirtOrWall(me.add(Direction.SOUTH))) cnt += 1;
                if(isDirtOrWall(me.add(Direction.NORTH))) cnt += 1;

                if(cnt >= 2) {
                    ans = false;
                    break;
                }
            }

            if(fallback >= 10) break;
        }


        return ans;


    }

    public static boolean isDirtOrWall(MapLocation c) throws GameActionException {
        if (G.rc.canSenseLocation(c)) {
            MapInfo x = G.rc.senseMapInfo(c);
            return x.isWall() || x.isDirt();
        } else return true;
    }

    public static void check() throws GameActionException {
        // keep original check behavior; no change here
    }

    // try to remove dirt at me.add(dir). Returns true if we initiated a remove action.
    private static boolean attemptRemoveDirt(MapLocation me, Direction dir) throws GameActionException {
        if (dir == null || dir == Direction.CENTER) return false;
        MapLocation loc = me.add(dir);
        if (!G.rc.canSenseLocation(loc)) return false;
        MapInfo info = G.rc.senseMapInfo(loc);
        if (info.isDirt() && G.rc.canRemoveDirt(loc)) {
            // perform the removal; then if engine allows same-tick move, attempt it
            G.rc.removeDirt(loc);
            lastMined = loc;
            lastMinedTick = G.rc.getRoundNum();
            return true;
        }
        return false;
    }

    public static void babyRatMicro() throws GameActionException {
        for (Direction d : Direction.allDirections()) {
            if (G.rc.canSenseLocation(G.rc.getLocation().add(d))) {
                RobotInfo r = G.rc.senseRobotAtLocation(G.rc.getLocation().add(d));
                if (r != null && r.getTeam() == G.opponentTeam) {
                    if (G.rc.canCarryRat(G.rc.getLocation().add(d))) {
                        G.rc.carryRat(G.rc.getLocation().add(d));
                    }
                }
            }

        }

        for (Direction d : Direction.allDirections()) {
            if (G.rc.canAttack(G.rc.getLocation().add(d))) {

                RobotInfo there = G.rc.senseRobotAtLocation(G.rc.getLocation().add(d));
                int cheeseToUse = 0;
                if (there.getHealth() == G.rc.getHealth() || there.getHealth() + 1 == G.rc.getHealth())
                    cheeseToUse = Math.min(G.rc.getGlobalCheese(), 1);
                if (G.rc.getRawCheese() / 2 >= 10) {
                    cheeseToUse = 10;
                } else if (G.rc.getRawCheese() / 2 >= 5) {
                    cheeseToUse = 5;
                } else {
                    cheeseToUse = Math.min(cheeseToUse, Math.min(2, G.rc.getRawCheese()));
                }
                G.rc.attack(G.rc.getLocation().add(d), cheeseToUse);
            }
        }

        for (Direction d : Direction.allDirections()) {
            if (G.rc.canSenseLocation(G.rc.getLocation().add(d))) {
                RobotInfo r = G.rc.senseRobotAtLocation(G.rc.getLocation().add(d));
                if (r != null && r.getTeam() == G.opponentTeam) {
                    if (G.rc.canCarryRat(G.rc.getLocation().add(d))) {
                        G.rc.carryRat(G.rc.getLocation().add(d));
                    }
                }
            }

        }
    }
}

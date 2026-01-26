package goonv23;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;

import java.util.ArrayList;
import java.util.HashSet;
import battlecode.common.RobotInfo;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

public class MotionCodeGen {

        private static MapLocation currentTarget;

        private static int minDistanceToTarget;
        private static boolean obstacleOnRight;
        private static MapLocation currentObstacle;
        private static HashSet visitedStates;
        private static int perprot = 0;

        public static void bugnavTowardsExplore(MapLocation target) throws GameActionException {
            if (!G.rc.isMovementReady()) return;
            if (currentTarget == null || (currentTarget.distanceSquaredTo(target)>25)) {
                G.indicatorString.append((target.x+" "+target.y+"  LOL  " + (currentTarget==null?"null":(currentTarget.x+" "+currentTarget.y+" "))));
                reset(); //handle king moving
            }

            boolean hasOptions = false;
            for (int i = G.DIRECTIONS.length; --i >= 0; ) {
                if (canMoveWithFill(G.DIRECTIONS[i])) {
                    hasOptions = true;
                    break;
                }
            }

            if (!hasOptions) {
                return;
            }

            MapLocation myLocation = G.rc.getLocation();

            int distanceToTarget = distance1d(myLocation, target);
            if (distanceToTarget < minDistanceToTarget && canMoveWithFill(G.me.directionTo(target))) {
                G.indicatorString.append(" issue4 ");
                reset();
                minDistanceToTarget = distanceToTarget;
            }

            if (currentObstacle != null && G.rc.canMove(G.me.directionTo(currentObstacle))) {
                G.indicatorString.append(" issue5 ");
                reset();
            }

            if (!visitedStates.add(getState(target))) {
                G.indicatorString.append(" issue6 ");
                reset();
            }

            currentTarget = target;
            // if (currentObstacle!=null&&G.rc.canSenseRobotAtLocation(currentObstacle)) Motion.moveRandomly();
            if (currentObstacle == null) {
                Direction forward = myLocation.directionTo(target);
                if (G.rc.canRemoveDirt(G.me.add(forward))) {
                    G.rc.removeDirt(G.me.add(forward));
                }
                if (canMoveWithFill(forward)) {
                    moveWithFill(forward);
                    return;
                }
                G.indicatorString.append(" setindir ");
                setInitialDirection();
            }

            followWall(true);
        }

        public static void reset() {
            currentTarget = null;
            minDistanceToTarget = Integer.MAX_VALUE;
            obstacleOnRight = true;
            currentObstacle = null;
            visitedStates = new HashSet();
        }

        private static void setInitialDirection() throws GameActionException {
            MapLocation myLocation = G.rc.getLocation();
            Direction forward = myLocation.directionTo(currentTarget);

            Direction left = forward.rotateLeft();
            for (int i = 8; --i >= 0; ) {
                MapLocation location = G.rc.adjacentLocation(left);
                if (G.rc.onTheMap(location) && G.rc.canMove(G.me.directionTo(location))) {
                    break;
                }

                left = left.rotateLeft();
            }

            Direction right = forward.rotateRight();
            for (int i = 8; --i >= 0; ) {
                MapLocation location = G.rc.adjacentLocation(right);
                if (G.rc.onTheMap(location) && G.rc.canMove(G.me.directionTo(location))) {
                    break;
                }

                right = right.rotateRight();
            }

            MapLocation leftLocation = G.rc.adjacentLocation(left);
            MapLocation rightLocation = G.rc.adjacentLocation(right);

            int leftDistance = distance1d(leftLocation, currentTarget);
            int rightDistance = distance1d(rightLocation, currentTarget);

            if (leftDistance < rightDistance) {
                obstacleOnRight = true;
                G.indicatorString.append(" issue2 ");
            } else if (rightDistance < leftDistance) {
                obstacleOnRight = false;G.indicatorString.append(" issue3 ");
            } else {
                obstacleOnRight = myLocation.distanceSquaredTo(leftLocation) < myLocation.distanceSquaredTo(rightLocation);
            }

            if (obstacleOnRight) {
                currentObstacle = G.rc.adjacentLocation(left.rotateRight());
                G.indicatorString.append(" 3null" + currentObstacle==null + "  ");
            } else {
                currentObstacle = G.rc.adjacentLocation(right.rotateLeft());
                G.indicatorString.append(" 2null" + currentObstacle==null + "  ");
            }
           // if (currentObstacle!=null&&G.rc.canSenseRobotAtLocation(currentObstacle)) Motion.moveRandomly();
        }

        private static void followWall(boolean canRotate) throws GameActionException {
            Direction direction = G.rc.getLocation().directionTo(currentObstacle);
            G.indicatorString.append("cur obs" + currentObstacle.x + " " + currentObstacle.y + " " + obstacleOnRight + " ");
            for (int i = 8; --i >= 0; ) {
                direction = obstacleOnRight ? direction.rotateLeft() : direction.rotateRight();
                if (G.rc.canRemoveDirt(currentObstacle)) {
                    G.rc.removeDirt(currentObstacle);
                }
                if (canMoveWithFill(direction)) {
                    moveWithFill(direction);
                    return;
                }

                MapLocation location = G.rc.adjacentLocation(direction);
                if (canRotate && !G.rc.onTheMap(location)) {
                    G.indicatorString.append(" issue ");
                    obstacleOnRight = !obstacleOnRight;
                    followWall(false);
                    return;
                }

                if (G.rc.onTheMap(location) && !G.rc.canMove(direction)) {
                    currentObstacle = location;
                    G.indicatorString.append(" 1null" + currentObstacle==null + "  ");
                }
            }
        }

        private static char getState(MapLocation target) {
            MapLocation myLocation = G.rc.getLocation();
            Direction direction = myLocation.directionTo(currentObstacle != null ? currentObstacle : target);
            int rotation = obstacleOnRight ? 1 : 0;

            return (char) ((((myLocation.x << 6) | myLocation.y) << 4) | (direction.ordinal() << 1) | rotation);
        }

        private static int distance1d(MapLocation a, MapLocation b) {
            return Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y));
        }

        private static boolean canMoveWithFill(Direction direction) {
            return G.rc.canMove(direction);
        }

        private static void moveWithFill(Direction direction) throws GameActionException {
            MapLocation fillLocation = G.rc.adjacentLocation(direction);
            if (G.rc.canRemoveDirt(fillLocation)) {
                G.rc.removeDirt(fillLocation);
            }
            if (!G.rc.getDirection().equals(direction)&&G.rc.canTurn()) G.rc.turn(direction);
            if (G.rc.canMove(direction)) {
                G.rc.move(direction);
            }
            if (G.rc.getDirection().equals(direction)&&G.rc.canTurn()) {
                G.rc.turn(perprot == 1 ? G.rc.getDirection().rotateLeft().rotateLeft() : G.rc.getDirection().rotateRight().rotateRight());
                perprot^=1;
            }

            //Logger.log("bug " + direction);
        }


}
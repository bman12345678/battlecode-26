package goonv8;

import battlecode.common.Team;

public class POI {
    public static Team opponentTeam = G.rc.getTeam().opponent();


    /*
    //50 towers
    //filled in backwards cuz for loop bytecode optimization
    public static int[] towers = new int[] {
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
    };

    // symmetry detection
    public static long[] wall = new long[60];
    public static long[] ruin = new long[60];
    public static boolean[] symmetry = new boolean[] { true, true, true };
    public static boolean criticalSymmetry = false;
    // 0: horz
    // 1: vert
    // 2: rot

    // stores all tower and ruin data

    // upto 50 robots
    // 51 is symmetry
    public static StringBuilder[] robotsThatKnowInformation = new StringBuilder[] {
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder()
    };
    public static boolean[] critical = new boolean[50];

    public static void addTowers(int[] data) {
        // IMPORTANT: make sure to call addTower right after tower is built
        if (data[0] == -1) return;
        for (int i = 49, ind = 0; --i >= 0; ) {
            if (((towers[i] ^ data[ind]) & 0b111111111111) == 0 || towers[i] == -1) {
                if (towers[i] != data[ind]) {
                    towers[i] = data[ind++];
                    robotsThatKnowInformation[i] = new StringBuilder("--1-");
                    critical[i] = true;
                }
            }
        }
    }

    public static void addTower(int source, int data) {
        // IMPORTANT: make sure to call addTower right after tower is built
        for (int i = 49; --i >= 0; ) {
            if (((towers[i] ^ data) & 0b111111111111) == 0 || towers[i] == -1) {
                if (towers[i] != data) {
                    towers[i] = data;
                    robotsThatKnowInformation[i] = new StringBuilder("-" + source + "-");
                    if (source == -1) {
                        critical[i] = true;
                    } else {
                        critical[i] = false;
                    }
                } else if (source != -1) {
                    robotsThatKnowInformation[i].append("-" + source + "-");
                }
                break;
            }
        }
    };

    public static void removeValidSymmetry(int source, int index) {
        if (symmetry[index]) {
            symmetry[index] = false;
            robotsThatKnowInformation[50] = new StringBuilder("-" + source + "-");
            if (source == -1) {
                criticalSymmetry = true;
            } else {
                criticalSymmetry = false;
            }
        } else if (source != -1) {
            robotsThatKnowInformation[50].append("-" + source + "-");
        }
    };

    // bytecode optimize this later
    // bytecode optimize this later
    // bytecode optimize this later
    // bytecode optimize this later
    // bytecode optimize this later
    // uses a ton of bytecode wtf?
    public static void updateInfo() throws Exception {
        MapLocation[] nearbyRuins = G.rc.senseNearbyRuins(-1);

        int[] towersToAdd = new int[]{-1,-1,-1,-1,-1}; //no way you can see that many
        int ind = 0;
        for (int i = nearbyRuins.length; --i >= 0;) {
            if (G.rc.canSenseRobotAtLocation(nearbyRuins[i])) {
                RobotInfo info = G.rc.senseRobotAtLocation(nearbyRuins[i]);
                if (info.team == G.opponentTeam) {
                    towersToAdd[ind++] = intifyTower(G.opponentTeam, info.getType()) | intifyLocation(nearbyRuins[i]);
                } else {
                    towersToAdd[ind++] = intifyTower(G.rc.getTeam(), info.getType()) | intifyLocation(nearbyRuins[i]);
                }
            } else {
                towersToAdd[ind++] = intifyTower(Team.NEUTRAL, UnitType.LEVEL_ONE_DEFENSE_TOWER) | intifyLocation(nearbyRuins[i]);
            }
        }
        addTowers(towersToAdd);
        for (int i = 49; --i >= 0; ) {
            if (towers[i] == -1) {
                break;
            }
            // // System.out.println(parseLocation(towers[i]));
            try {
                if (parseTowerTeam(towers[i]) == G.rc.getTeam()) {
                    // // G.rc.setIndicatorLine(G.me, parseLocation(towers[i]), 0, 100, 0);
                } else {
                    // // G.rc.setIndicatorLine(G.me, parseLocation(towers[i]), 100, 0, 0);
                }
            } catch (Exception e) {
            }
        }

        for (int i = nearbyRuins.length; --i >= 0;) {
            MapLocation xy = G.infos[i].getMapLocation();
            ruin[xy.y] |= 1L << xy.x;
        }
        for (int i = G.infos.length; --i >= 0;) {
            if (G.infos[i].isWall()) {
                MapLocation xy = G.infos[i].getMapLocation();
                wall[xy.y] |= 1L << xy.x;
            }
        }
        if (symmetry[0] && !symmetryValid(0)) {
            removeValidSymmetry(-1, 0);
        }
        if (symmetry[1] && !symmetryValid(1)) {
            removeValidSymmetry(-1, 1);
        }
        if (symmetry[2] && !symmetryValid(2)) {
            removeValidSymmetry(-1, 2);
        }
        sendMessages();
    };

    public static boolean symmetryValid(int sym) throws Exception {
        // completely untested...
        int w = G.rc.getMapWidth();
        int h = G.rc.getMapHeight();
        switch (sym) {
            case 0: // horz
                for (int i = h / 2; --i >= 0; ) {
                    if ((wall[i] ^ wall[h - i]) != 0)
                        return false;
                    if ((ruin[i] ^ ruin[h - i]) != 0)
                        return false;
                }
                return true;
            case 1: // vert
                for (int i = h; --i >= 0; ) {
                    if ((Long.reverse(wall[i]) << (64 - w)) != wall[i])
                        return false;
                    if ((Long.reverse(ruin[i]) << (64 - w)) != ruin[i])
                        return false;
                }
                return true;
            case 2: // rot
                for (int i = h / 2; --i >= 0; ) {
                    if (((Long.reverse(wall[i]) << (64 - w)) ^ wall[h - i]) != 0)
                        return false;
                    if (((Long.reverse(ruin[i]) << (64 - w)) ^ ruin[h - i]) != 0)
                        return false;
                }
                return true;
        }
        // System.out.println("invalid symmetry argument");
        return false;
    }

    public static void sendMessages() throws Exception {
        if (G.rc.getType().isTowerType()) {
            // we just send all info that the robots dont have
            for (int j = G.allyRobots.length; --j >= 0;) {
                RobotInfo r = G.allyRobots[j];
                while (G.rc.canSendMessage(r.getLocation())) {
                    int message = -1;
                    int messages = 0;
                    if (!robotsThatKnowInformation[50].toString().contains("-" + r.getID() + "-")) {
                        message = intifySymmetry();
                        messages += 1;
                        robotsThatKnowInformation[50].append("-" + r.getID() + "-");
                    }
                    for (int i = 49; --i >= 0; ) {
                        if (towers[i] == -1) {
                            break;
                        }
                        if (!robotsThatKnowInformation[i].toString().contains("-" + r.getID() + "-")) {
                            message = appendToMessage(message, towers[i]);
                            messages++;
                            robotsThatKnowInformation[i].append("-" + r.getID() + "-");
                            if (messages == 2) {
                                break;
                            }
                        }
                    }
                    if (messages == 0) {
                        break;
                    }
                    G.rc.sendMessage(r.getLocation(), message);
                    if (Clock.getBytecodesLeft() < 3000) break;
                }
                if (Clock.getBytecodesLeft() < 3000) break;
            }
        } else {
            int message = -1;
            int messages = 0;
            if (criticalSymmetry) {
                message = intifySymmetry();
                messages += 1;
            }
            for (int j = G.allyRobots.length; --j >= 0;) {
                if (G.allyRobots[j].getType().isTowerType()) {
                    RobotInfo r = G.allyRobots[j];
                    if (G.rc.canSendMessage(r.getLocation())) {
                        if (messages < 2) {
                            for (int i = 49; --i >= 0; ) {
                                if (towers[i] == -1) {
                                    break;
                                }
                                if (critical[i]
                                        && ((intifyLocation(r.getLocation()) ^ towers[i]) & 0b111111111111) != 0) {
                                    message = appendToMessage(message, towers[i]);
                                    messages++;
                                    critical[i] = false;
                                    if (messages == 2) {
                                        break;
                                    }
                                }
                            }
                            if (!robotsThatKnowInformation[50].toString().contains("-" + r.getID() + "-")) {
                                message = appendToMessage(message, intifySymmetry());
                                messages += 1;
                                robotsThatKnowInformation[50].append("-" + r.getID() + "-");
                            }
                            if (messages < 2) {
                                for (int i = 49; --i >= 0; ) {
                                    if (towers[i] == -1) {
                                        break;
                                    }
                                    if (!robotsThatKnowInformation[i].toString().contains("-" + r.getID() + "-")) {
                                        message = appendToMessage(message, towers[i]);
                                        messages++;
                                        robotsThatKnowInformation[i].append("-" + r.getID() + "-");
                                        if (messages == 2) {
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        if (messages != 0) {
                            criticalSymmetry = false;
                            G.rc.sendMessage(r.getLocation(), message);
                            break;
                        }
                    }
                }
                if (Clock.getBytecodesLeft() < 3000) break;
            }
        }
    };

    public static void readMessages() throws Exception {
        // what hapepns if message is sent in same round?? oof oof oof
        Message[] messages = G.rc.readMessages(G.rc.getRoundNum() - 1);
        for (Message m : messages) {
            int n1 = m.getBytes() & 0b1111111111111111;
            if ((n1 << 12) >= 7) {
                int n2 = (n1 << 12) - 7;
                if (n2 % 2 == 0) {
                    removeValidSymmetry(m.getSenderID(), 0);
                }
                if ((n2 >> 1) % 2 == 0) {
                    removeValidSymmetry(m.getSenderID(), 1);
                }
                if ((n2 >> 2) % 2 == 0) {
                    removeValidSymmetry(m.getSenderID(), 2);
                }
            } else {
                addTower(m.getSenderID(), m.getBytes() & 0b1111111111111111);
            }
            if ((m.getBytes() >> 16) != 0) {
                addTower(m.getSenderID(), (m.getBytes() >> 16) & 0b1111111111111111);
            }
        }
    };

    public static MapLocation parseLocation(int n) {
        // n -= 1;
        return new MapLocation((n & 0b111111), (n >> 6) & 0b111111);
    }

    public static int intifyLocation(MapLocation loc) {
        // return ((loc.y << 6) | loc.x) + 1;
        return ((loc.y << 6) | loc.x);
    }

    // team 0 for ally
    // team 1 for opp
    // team 2 for neutral
    public static Team parseTowerTeam(int n) {
        int t = n >> 12;
        if (t == 0) {
            return Team.NEUTRAL;
        }
        if (t <= 3) {
            return Team.A;
        }
        return Team.B;
    }

    // 0: neutral
    // 1: paint
    // 2: chip
    // 3: defense
    public static UnitType parseTowerType(int n) {
        int t = n >> 12;
        if (t == 0) {
            return UnitType.LEVEL_TWO_PAINT_TOWER;
        }
        if (t % 3 == 1) {
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        }
        if (t % 3 == 2) {
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        }
        return UnitType.LEVEL_ONE_DEFENSE_TOWER;
    }

    public static int intifyTower(Team team, UnitType type) {
        if (team == Team.NEUTRAL) {
            return 0;
        }
        if (type == UnitType.LEVEL_ONE_PAINT_TOWER || type == UnitType.LEVEL_TWO_PAINT_TOWER
                || type == UnitType.LEVEL_THREE_PAINT_TOWER) {
            return (1 + team.ordinal() * 3) << 12;
        }
        if (type == UnitType.LEVEL_ONE_MONEY_TOWER || type == UnitType.LEVEL_TWO_MONEY_TOWER
                || type == UnitType.LEVEL_THREE_MONEY_TOWER) {
            return (2 + team.ordinal() * 3) << 12;
        }
        if (type == UnitType.LEVEL_ONE_DEFENSE_TOWER || type == UnitType.LEVEL_TWO_DEFENSE_TOWER
                || type == UnitType.LEVEL_THREE_DEFENSE_TOWER) {
            return (3 + team.ordinal() * 3) << 12;
        }
        return 0;
    }

    public static int intifySymmetry() {
        return ((symmetry[0] ? 1 : 0) + (symmetry[1] ? 1 : 0) * 2 + (symmetry[2] ? 1 : 0) * 4 + 7) << 12;
    }

    public static int appendToMessage(int message, int a) {
        if (message == -1) {
            return a;
        }
        return (message << 16) | a;
    }

    // need to keep track of what info each tower has
    // as well as what info is most important
    // then send messages to tower

    // for tower, recieve message = update stuff
    // also send most important stuff to nearby robots
    */

}
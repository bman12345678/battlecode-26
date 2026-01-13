package winningbot;

import battlecode.common.*;

public final class Comms {
    // Squeak message types (top 4 bits).
    private static final int T_ENEMY_KING = 1;
    private static final int T_CAT = 2;
    private static final int T_MINE = 3;
    private static final int T_RALLY = 4;
    // Example channels - adjust to your project's constants
    private static final int CHANNEL_ENEMY_KING_X = 0;
    private static final int CHANNEL_ENEMY_KING_Y = 1;
    private static final int CHANNEL_CHEESE_X = 2;
    private static final int CHANNEL_CHEESE_Y = 3;
    private static final int CHANNEL_RELOCATED_BASE_X = 4;
    private static final int CHANNEL_RELOCATED_BASE_Y = 5;
    private static final int NO_VAL = -99999;

    public static void writeEnemyKing(RobotController rc, MapLocation loc) {
        try {
            if (loc == null) return;
            rc.writeSharedArray(CHANNEL_ENEMY_KING_X, loc.x);
            rc.writeSharedArray(CHANNEL_ENEMY_KING_Y, loc.y);
        } catch (GameActionException e) {
            // swallow - best-effort
        }
    }

    public static MapLocation readEnemyKing(RobotController rc) {
        try {
            int x = rc.readSharedArray(CHANNEL_ENEMY_KING_X);
            int y = rc.readSharedArray(CHANNEL_ENEMY_KING_Y);
            if (x == 0 && y == 0) return null; // default empty, adapt if your array initialization differs
            return new MapLocation(x, y);
        } catch (Exception e) { return null; }
    }

    public static void writeCheeseMine(RobotController rc, MapLocation loc) {
        try {
            if (loc == null) return;
            rc.writeSharedArray(CHANNEL_CHEESE_X, loc.x);
            rc.writeSharedArray(CHANNEL_CHEESE_Y, loc.y);
        } catch (GameActionException e) { }
    }

    public static MapLocation readCheeseMine(RobotController rc) {
        try {
            int x = rc.readSharedArray(CHANNEL_CHEESE_X);
            int y = rc.readSharedArray(CHANNEL_CHEESE_Y);
            if (x == 0 && y == 0) return null;
            return new MapLocation(x, y);
        } catch (Exception e) { return null; }
    }

    public static void writeOurRelocatedBase(RobotController rc, MapLocation loc) {
        try {
            if (loc == null) return;
            rc.writeSharedArray(CHANNEL_RELOCATED_BASE_X, loc.x);
            rc.writeSharedArray(CHANNEL_RELOCATED_BASE_Y, loc.y);
        } catch (GameActionException e) { }
    }

    // Methods for squawk-style immediate reports (babies that "squeak" to the king to carry a report)
    // If your code uses actual squeaks, replace the following helpers with calls to read squeak queue.
    public static void reportSqueakEnemyKing(RobotController rc, MapLocation loc) {
        // In your baby code: Comms.reportSqueakEnemyKing(rc, loc);
        // Here we just immediately write canonical location.
        writeEnemyKing(rc, loc);
    }

    public static void reportSqueakCheese(RobotController rc, MapLocation loc) {
        writeCheeseMine(rc, loc);
    }

    // Broadcast target for baby rush
    public static void broadcastRush(RobotController rc, MapLocation enemyKing) {
        // Example: set the enemy king shared location (already done) then set a 'rush flag'
        try {
            rc.writeSharedArray(100, 1); // arbitrary channel; check your project
            rc.writeSharedArray(101, enemyKing.x);
            rc.writeSharedArray(102, enemyKing.y);
        } catch (GameActionException e) { }
    }

    // Read simple squeak fallbacks (no-op if you use real squeaks)
    public static MapLocation readSqueakedEnemyKing(RobotController rc) { return readEnemyKing(rc); }
    public static MapLocation readSqueakedCheeseMine(RobotController rc) { return readCheeseMine(rc); }

    private Comms() {}

    public static void squeakEnemyKing(RobotController rc, MapLocation loc) throws GameActionException {
        if (loc == null) return;
        rc.squeak(pack(T_ENEMY_KING, loc.x, loc.y, 0));
    }

    public static void squeakCat(RobotController rc, MapLocation loc) throws GameActionException {
        if (loc == null) return;
        rc.squeak(pack(T_CAT, loc.x, loc.y, 0));
    }

    public static void squeakMine(RobotController rc, MapLocation loc) throws GameActionException {
        if (loc == null) return;
        rc.squeak(pack(T_MINE, loc.x, loc.y, 0));
    }

    public static void squeakRally(RobotController rc, MapLocation loc, int mode) throws GameActionException {
        if (loc == null) return;
        rc.squeak(pack(T_RALLY, loc.x, loc.y, mode & 0xFF));
    }

    private static int pack(int type, int x, int y, int extra) {
        // [type:4][x:6][y:6][extra:16]
        return (type << 28) | ((x & 63) << 22) | ((y & 63) << 16) | (extra & 0xFFFF);
    }

    public static int type(int msg) { return (msg >>> 28) & 0xF; }
    public static int x(int msg) { return (msg >>> 22) & 63; }
    public static int y(int msg) { return (msg >>> 16) & 63; }
    public static int extra(int msg) { return msg & 0xFFFF; }

    public static void kingConsumeSqueaks(RobotController rc) throws GameActionException {
        if (rc.getType() != UnitType.RAT_KING) return;

        Message[] ms = rc.readSqueaks(-1);
        for (int i = 0; i < ms.length; i++) {
            int v = ms[i].getBytes();
            int t = type(v);
            int x = x(v), y = y(v);
            if (t == T_ENEMY_KING) {
                writeLoc(rc, Globals.IDX_ENEMY_KING_X, Globals.IDX_ENEMY_KING_Y, x, y);
            } else if (t == T_RALLY) {
                writeLoc(rc, Globals.IDX_RALLY_X, Globals.IDX_RALLY_Y, x, y);
                // Keep flags in extra if desired; currently ignored.
            }
        }
    }

    public static void kingBroadcastBase(RobotController rc, MapLocation base) throws GameActionException {
        if (rc.getType() != UnitType.RAT_KING || base == null) return;
        writeLoc(rc, Globals.IDX_BASE_X, Globals.IDX_BASE_Y, base.x, base.y);
        MapLocation enemyGuess = Globals.mirror(base);
        writeLoc(rc, Globals.IDX_ENEMY_BASE_X, Globals.IDX_ENEMY_BASE_Y, enemyGuess.x, enemyGuess.y);
    }

    public static void kingSetFlag(RobotController rc, int flag, boolean enabled) throws GameActionException {
        if (rc.getType() != UnitType.RAT_KING) return;
        int cur = rc.readSharedArray(Globals.IDX_FLAGS);
        int nxt = enabled ? (cur | flag) : (cur & ~flag);
        nxt = clampShared(nxt);
        if (nxt != cur) rc.writeSharedArray(Globals.IDX_FLAGS, nxt);
    }

    private static void writeLoc(RobotController rc, int idxX, int idxY, int x, int y) throws GameActionException {
        if (rc.getType() != UnitType.RAT_KING) return;
        if (x < 0 || y < 0) return;
        x = Math.min(1023, x);
        y = Math.min(1023, y);
        if (rc.readSharedArray(idxX) != x) rc.writeSharedArray(idxX, x);
        if (rc.readSharedArray(idxY) != y) rc.writeSharedArray(idxY, y);
    }

    private static int clampShared(int v) {
        if (v < 0) return 0;
        if (v > 1023) return 1023;
        return v;
    }
}

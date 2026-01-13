package winningbot;

import battlecode.common.*;

/** Shared static memory across all allied units. */
public final class Globals {

    // Shared array layout (rat kings only can write).
    public static final int IDX_BASE_X = 0;
    public static final int IDX_BASE_Y = 1;
    public static final int IDX_ENEMY_BASE_X = 2;
    public static final int IDX_ENEMY_BASE_Y = 3;
    public static final int IDX_FLAGS = 4;          // bitfield
    public static final int IDX_ENEMY_KING_X = 5;
    public static final int IDX_ENEMY_KING_Y = 6;
    public static final int IDX_RALLY_X = 7;
    public static final int IDX_RALLY_Y = 8;

    // Flags bitfield (in shared array IDX_FLAGS).
    public static final int FLAG_BACKSTAB_NOW = 1 << 0;   // babies may trigger by biting enemy
    public static final int FLAG_RALLY_CENTER = 1 << 1;   // gather near cats/center
    public static final int FLAG_DEFEND_BASE  = 1 << 2;   // return to base perimeter

    // Map / team
    public static int W, H;
    public static Team MY_TEAM, ENEMY_TEAM;
    public static MapLocation CENTER;

    // Base + enemy base guess
    public static MapLocation BASE = null;
    public static MapLocation ENEMY_BASE_GUESS = null;

    // Last known enemy king center (shared)
    public static MapLocation ENEMY_KING = null;

    // Shared map cache (0 unknown, 1 passable, 2 wall, 3 dirt)
    public static byte[][] terrain;
    public static byte[][] trap;         // 0 none, 1 rat trap, 2 cat trap
    public static short[][] cheese;      // sensed cheese on ground
    public static boolean[][] mine;      // cheese mine

    public static boolean inited = false;

    // Simple shared RNG (xorshift); deterministic, cheap.
    private static int rngState = 0x9E3779B9;

    // Round tracking
    public static int round = 0;

    private Globals() {}

    public static void init(RobotController rc) throws GameActionException {
        if (inited) return;

        W = rc.getMapWidth();
        H = rc.getMapHeight();
        MY_TEAM = rc.getTeam();
        ENEMY_TEAM = (MY_TEAM == Team.A ? Team.B : Team.A);
        CENTER = new MapLocation(W / 2, H / 2);

        terrain = new byte[W][H];
        trap = new byte[W][H];
        cheese = new short[W][H];
        mine = new boolean[W][H];

        // Seed RNG with map + id to reduce mirror symmetry deadlocks.
        rngState ^= (rc.getID() * 1103515245);
        rngState ^= (W << 16) ^ H;

        // Read base/enemy base if already broadcast by an earlier king.
        readShared(rc);

        inited = true;
    }

    public static void update(RobotController rc) throws GameActionException {
        round = rc.getRoundNum();
        // Refresh small shared state occasionally (babies read cheaply).
        if ((round & 7) == 0) {
            readShared(rc);
        }

        // Update local map cache for all units with a small radius (cheap).
        MapInfo[] infos = rc.senseNearbyMapInfos(16);
        for (int i = 0; i < infos.length; i++) {
            MapInfo mi = infos[i];
            MapLocation l = mi.getMapLocation();
            int x = l.x, y = l.y;

            terrain[x][y] = mi.isWall() ? (byte)2 : (mi.isDirt() ? (byte)3 : (mi.isPassable() ? (byte)1 : (byte)0));
            mine[x][y] = mi.hasCheeseMine();

            TrapType tt = mi.getTrap();
            if (tt == TrapType.NONE) trap[x][y] = 0;
            else if (tt == TrapType.RAT_TRAP) trap[x][y] = 1;
            else trap[x][y] = 2;

            int c = mi.getCheeseAmount();
            if (c > 0) cheese[x][y] = (short)Math.min(32767, c);
        }
    }

    private static void readShared(RobotController rc) throws GameActionException {
        int bx = rc.readSharedArray(IDX_BASE_X);
        int by = rc.readSharedArray(IDX_BASE_Y);
        if (bx > 0 || by > 0) BASE = new MapLocation(bx, by);

        int ex = rc.readSharedArray(IDX_ENEMY_BASE_X);
        int ey = rc.readSharedArray(IDX_ENEMY_BASE_Y);
        if (ex > 0 || ey > 0) ENEMY_BASE_GUESS = new MapLocation(ex, ey);

        int kx = rc.readSharedArray(IDX_ENEMY_KING_X);
        int ky = rc.readSharedArray(IDX_ENEMY_KING_Y);
        if (kx > 0 || ky > 0) ENEMY_KING = new MapLocation(kx, ky);
    }

    public static int flags(RobotController rc) throws GameActionException {
        return rc.readSharedArray(IDX_FLAGS);
    }

    public static int nextRand() {
        int x = rngState;
        x ^= (x << 13);
        x ^= (x >>> 17);
        x ^= (x << 5);
        rngState = x;
        return x;
    }

    public static int randInt(int bound) {
        int r = nextRand();
        if (r < 0) r = -r;
        return r % bound;
    }

    public static boolean onMap(int x, int y) {
        return (x >= 0 && x < W && y >= 0 && y < H);
    }

    public static int packXY(int x, int y) {
        return x | (y << 6); // supports up to 64x64
    }

    public static int unpackX(int p) { return p & 63; }
    public static int unpackY(int p) { return (p >>> 6) & 63; }

    public static MapLocation mirror(MapLocation loc) {
        return new MapLocation(W - 1 - loc.x, H - 1 - loc.y);
    }
}

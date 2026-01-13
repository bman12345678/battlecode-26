package winningbot;

import battlecode.common.*;

/**
 * Battlecode 2026 bot: "WinningBot"
 *
 * Notes:
 * - This bot is designed around:
 * (1) Aggressive cat DPS + cat-trap farming in cooperation.
 * (2) Strong cheese economy to keep rat kings alive.
 * (3) Controlled, opportunistic backstab timing and king assassination.
 *
 * Keep comments short: tournament bytecode matters.
 */
public final class RobotPlayer {

    public static void run(RobotController rc) {
        try {
            Globals.init(rc);
        } catch (Exception e) {
            // If init fails, keep yielding to avoid crashing the JVM.
        }

        while (true) {
            try {
                Globals.update(rc);

                UnitType t = rc.getType();
                if (t == UnitType.RAT_KING) {
                    KingLogic.kinggo(rc);
                } else {
                    BabyLogic.babygo(rc);
                }

            } catch (GameActionException e) {
                // Swallow; we prefer robustness over strictness.
            } catch (Exception e) {
                // Guard against any unexpected runtime exceptions.
            } finally {
                Clock.yield();
            }
        }
    }
}

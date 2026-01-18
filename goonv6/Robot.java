package goonv6;

public class Robot {
    public static void init() throws Exception {

    }

    public static void run() throws Exception {
        switch (G.rc.getType()) {
            case BABY_RAT -> BabyRat.run();
            case RAT_KING -> RatKing.run();
            default -> throw new Exception("robot run is fried");
        }
        //G.indicatorString.append("SYM="
        //      + (POI.symmetry[0] ? "0" : "1") + (POI.symmetry[1] ? "0" : "1") + (POI.symmetry[2] ? "0 " : "1 "));
    }


}
package util;

public class SameDirection {

    private static final double ANGLEPRECISION = 0.1;

    public static boolean compute(double dir1, double dir2) {
        return Math.abs(normalize(dir1)-normalize(dir2)) < ANGLEPRECISION;
    }

    private static double normalize(double dir){
        double res=dir;
        while (res<0) res+=2*Math.PI;
        while (res>=2*Math.PI) res-=2*Math.PI;
        return res;
    }
}

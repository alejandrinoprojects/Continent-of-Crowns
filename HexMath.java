public class HexMath {

    public static int hexDistance(int q1, int r1, int q2, int r2) {
        int y1 = -q1 - r1;
        int y2 = -q2 - r2;
        return (Math.abs(q1 - q2) + Math.abs(y1 - y2) + Math.abs(r1 - r2)) / 2;
    }
}
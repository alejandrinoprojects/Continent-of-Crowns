import java.awt.Point;

public class CoordinateConverter {
    public static final int HEX_SIZE = 30;
    public static int MAP_COLS = 600;
    public static int MAP_ROWS = 600; // PERFECT 600x600 square

    private static int originX = 0;
    private static int originY = 0;

    public static void hexToPixel(int q, int r, Point out) {
        out.x = getPixelX(q, r);
        out.y = getPixelY(q, r);
    }

    public static void computeOrigin(int width, int height) {
        originX = HEX_SIZE * 2;
        originY = HEX_SIZE * 2;
    }

    public static int getMapWidth() {
        return (int)(MAP_COLS * HEX_SIZE * 1.5) + HEX_SIZE * 4;
    }

    public static int getMapHeight() {
        return (int)(MAP_ROWS * HEX_SIZE * Math.sqrt(3)) + HEX_SIZE * 4;
    }

    public static Point hexToPixel(int q, int r) {
        int x = (int)(HEX_SIZE * 1.5 * q) + originX;
        int y = (int)(HEX_SIZE * Math.sqrt(3) * (r + q * 0.5)) + originY;
        return new Point(x, y);
    }

    // --- ADDED: Zero-allocation coordinate getters ---

    public static int getPixelX(int q, int r) {
        return (int)(HEX_SIZE * 1.5 * q) + originX;
    }

    public static int getPixelY(int q, int r) {
        return (int)(HEX_SIZE * Math.sqrt(3) * (r + q * 0.5)) + originY;
    }

    // -------------------------------------------------

    public static Point pixelToHex(int x, int y) {
        int adjustedX = x - originX;
        int adjustedY = y - originY;
        double q = (2.0 / 3.0 * adjustedX) / HEX_SIZE;
        double r = (-1.0 / 3.0 * adjustedX + Math.sqrt(3) / 3.0 * adjustedY) / HEX_SIZE;
        return new Point((int) Math.round(q), (int) Math.round(r));
    }

    public static int getOriginX() { return originX; }
    public static int getOriginY() { return originY; }
}
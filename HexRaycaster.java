import java.awt.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HexRaycaster {

    private static class Cube {
        double x, y, z;
        Cube(double x, double y, double z) {
            this.x = x; this.y = y; this.z = z;
        }
    }

    /**
     * Calculates the dynamic "Vision Shadows". Returns all hexes a unit can currently see.
     */
    public static Set<Point> calculateVisibleHexes(Point startHex, int radius) {
        Set<Point> visible = new HashSet<>();
        visible.add(startHex);

        List<Point> perimeter = getRing(startHex, radius);

        for (Point target : perimeter) {
            List<Point> ray = getLine(startHex, target);
            for (Point p : ray) {
                visible.add(p);

                // SHADOWCASTER LOGIC: Check if the ray hits an obstacle
                HexTile tile = GridManager.hexes.get(GridManager.packKey(p.x, p.y));
                if (tile != null && tile.getTileType() == TileType.ROCK && tile.getZ() > 0) {
                    break; // The ray is blocked! Everything behind this is in shadow.
                }
            }
        }
        return visible;
    }

    // --- Line Casting Math ---

    public static List<Point> getLine(Point startHex, Point endHex) {
        int N = getDistance(startHex, endHex);
        List<Point> results = new ArrayList<>();

        if (N == 0) {
            results.add(startHex);
            return results;
        }

        Cube a = axialToCube(startHex.x, startHex.y);
        Cube b = axialToCube(endHex.x, endHex.y);

        // Nudge to prevent unpredictable zigzag rounding errors on corners
        Cube aNudged = new Cube(a.x + 1e-6, a.y + 1e-6, a.z - 2e-6);

        for (int i = 0; i <= N; i++) {
            double t = 1.0 / Math.max(N, 1) * i;
            Cube lerped = lerp(aNudged, b, t);
            results.add(cubeToAxial(cubeRound(lerped)));
        }
        return results;
    }

    private static List<Point> getRing(Point center, int radius) {
        List<Point> results = new ArrayList<>();
        int[][] dirs = {{1, 0}, {1, -1}, {0, -1}, {-1, 0}, {-1, 1}, {0, 1}};
        Point hex = new Point(center.x - radius, center.y + radius);
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < radius; j++) {
                results.add(new Point(hex.x, hex.y));
                hex = new Point(hex.x + dirs[i][0], hex.y + dirs[i][1]);
            }
        }
        return results;
    }

    private static int getDistance(Point a, Point b) {
        Cube ca = axialToCube(a.x, a.y);
        Cube cb = axialToCube(b.x, b.y);
        return (int) Math.max(Math.abs(ca.x - cb.x), Math.max(Math.abs(ca.y - cb.y), Math.abs(ca.z - cb.z)));
    }

    private static Cube axialToCube(int q, int r) { return new Cube(q, -q - r, r); }
    private static Point cubeToAxial(Cube c) { return new Point((int) c.x, (int) c.z); }

    private static Cube lerp(Cube a, Cube b, double t) {
        return new Cube(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }

    private static Cube cubeRound(Cube c) {
        double rx = Math.round(c.x), ry = Math.round(c.y), rz = Math.round(c.z);
        double xDiff = Math.abs(rx - c.x), yDiff = Math.abs(ry - c.y), zDiff = Math.abs(rz - c.z);
        if (xDiff > yDiff && xDiff > zDiff) rx = -ry - rz;
        else if (yDiff > zDiff) ry = -rx - rz;
        else rz = -rx - ry;
        return new Cube(rx, ry, rz);
    }
}
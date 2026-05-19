import java.util.List;

public class BuildTesterWorld {

    public static void setup(List<Unit> units, Object unitsLock) {
        GridManager.hexes.clear();
        GridManager.clearGridOccupancy();
        synchronized (unitsLock) { units.clear(); }

        int cols = CoordinateConverter.MAP_COLS;
        int rows = CoordinateConverter.MAP_ROWS;

        for (int q = 0; q < cols; q++) {
            int rOffset = q >> 1;
            for (int r = -rOffset; r < rows - rOffset; r++) {
                HexTile tile = new HexTile(q, r);
                tile.setTileType(TileType.PLAIN);
                tile.setBiomeType(BiomeType.GRASSLAND);
                tile.customZ = 0;

                // FIXED: Used GridManager.packKey instead of HexMath.key
                GridManager.hexes.put(GridManager.packKey(q, r), tile);
            }
        }

        int centerQ = cols / 2;
        int centerR = (rows / 2) - (centerQ / 2);

        for (int i = 0; i < 5; i++) {
            int spawnQ = centerQ + (i - 2);
            int spawnR = centerR;

            // Updated constructor to match Unit.java signature
            Unit builder = new Unit(spawnQ, spawnR, UnitType.VILLAGER, true);
            synchronized (unitsLock) { units.add(builder); }
            GridManager.markHex(spawnQ, spawnR, builder);
        }

        GridManager.landCenterPX = CoordinateConverter.getMapWidth() / 2;
        GridManager.landCenterPY = CoordinateConverter.getMapHeight() / 2;
    }
}
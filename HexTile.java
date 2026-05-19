import java.awt.geom.Area;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

public class HexTile {
    public final int q, r;

    private byte tileTypeOrdinal;
    private byte biomeTypeOrdinal;
    public int customZ = -1;
    public boolean isSparseForest = false;

    private static final int SHADOW_STRIDE = 4;
    private static final int INITIAL_SHADOW_CAPACITY = 8;
    private int[] shadowData = null;
    private int shadowCount = 0;

    public volatile Area compiledShadow = null;

    public HexTile(int q, int r) {
        this.q = q;
        this.r = r;
        this.tileTypeOrdinal = (byte) TileType.PLAIN.ordinal();
        this.biomeTypeOrdinal = (byte) BiomeType.GRASSLAND.ordinal();
    }

    public TileType getTileType() {
        return TileType.values()[tileTypeOrdinal];
    }

    public void setTileType(TileType t) {
        this.tileTypeOrdinal = (byte) t.ordinal();
    }

    public BiomeType getBiomeType() {
        return BiomeType.values()[biomeTypeOrdinal];
    }

    public void setBiomeType(BiomeType b) {
        this.biomeTypeOrdinal = (byte) b.ordinal();
    }

    public int getZ() {
        return customZ >= 0 ? customZ : getTileType().zHeight;
    }

    public void clearShadows() {
        shadowCount = 0;
    }

    public void addShadow(int cx, int cy, int deltaZ, int casterZ) {
        if (shadowData == null) {
            shadowData = new int[INITIAL_SHADOW_CAPACITY * SHADOW_STRIDE];
        }
        int needed = (shadowCount + 1) * SHADOW_STRIDE;
        if (needed > shadowData.length) {
            int[] grown = new int[shadowData.length * 2];
            System.arraycopy(shadowData, 0, grown, 0, shadowData.length);
            shadowData = grown;
        }
        int base = shadowCount * SHADOW_STRIDE;
        shadowData[base]     = cx;
        shadowData[base + 1] = cy;
        shadowData[base + 2] = deltaZ;
        shadowData[base + 3] = casterZ;
        shadowCount++;
    }

    public int getShadowCount() {
        return shadowCount;
    }

    public int getShadowField(int i, int field) {
        return shadowData[i * SHADOW_STRIDE + field];
    }

    public boolean hasShadows() {
        return shadowCount > 0;
    }

    @Deprecated
    public static class ShadowRecord {
        public final int cx, cy, z, casterZ;
        public ShadowRecord(int cx, int cy, int z, int casterZ) {
            this.cx = cx; this.cy = cy; this.z = z; this.casterZ = casterZ;
        }
    }

    @Deprecated
    public final List<ShadowRecord> incomingShadows = new java.util.concurrent.CopyOnWriteArrayList<>();
}

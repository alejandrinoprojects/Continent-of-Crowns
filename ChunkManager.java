import java.awt.Point;
import java.awt.Graphics2D;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkManager {
    public static final int CHUNK_SIZE = 256;
    public static final int NUM_TOD_STEPS = 20;

    public static class ChunkMesh {
        public float[] vertices;
        public int vertexCount;
        public int vaoId = -1;
        public int vboId = -1;
        
        public void dispose() {
            // LWJGL glDelete calls should be handled by the OpenGL renderer context
        }
    }

    private final ConcurrentHashMap<Long, ChunkMesh> chunkMeshes = new ConcurrentHashMap<>();
    private HexTile[][][] flatChunkTiles;
    private int cols;
    private int rows;
    private ByteBuffer elevationData;

    public void buildChunks(GameRenderer renderer) {
        chunkMeshes.clear();
        int mapWidth = CoordinateConverter.getMapWidth();
        int mapHeight = CoordinateConverter.getMapHeight();
        cols = (int) Math.ceil((double) mapWidth / CHUNK_SIZE);
        rows = (int) Math.ceil((double) mapHeight / CHUNK_SIZE);
        flatChunkTiles = new HexTile[cols][rows][];

        elevationData = ByteBuffer.allocateDirect(CoordinateConverter.MAP_COLS * CoordinateConverter.MAP_ROWS);
        for (int q = 0; q < CoordinateConverter.MAP_COLS; q++) {
            for (int r = 0; r < CoordinateConverter.MAP_ROWS; r++) {
                HexTile tile = GridManager.hexes.get(GridManager.packKey(q, r));
                byte z = (tile != null) ? (byte) Math.max(0, Math.min(255, tile.getZ())) : 0;
                elevationData.put(q + r * CoordinateConverter.MAP_COLS, z);
            }
        }
        elevationData.flip();

        @SuppressWarnings("unchecked")
        List<HexTile>[][] tempBuckets = new ArrayList[cols][rows];
        List<HexTile> allTiles = new ArrayList<>(GridManager.hexes.values());

        for (HexTile tile : allTiles) {
            int pX = CoordinateConverter.getPixelX(tile.q, tile.r);
            int pY = CoordinateConverter.getPixelY(tile.q, tile.r);
            
            int maxZ = Math.max(0, tile.getZ());
            int halfSpan = Math.max(120, maxZ / 2 + 60);
            int tileMinX = pX - halfSpan;
            int tileMinY = pY - maxZ - halfSpan;
            int tileMaxX = pX + halfSpan;
            int tileMaxY = pY + halfSpan;

            int startCx = Math.max(0, tileMinX / CHUNK_SIZE);
            int endCx = Math.min(cols - 1, tileMaxX / CHUNK_SIZE);
            int startCy = Math.max(0, tileMinY / CHUNK_SIZE);
            int endCy = Math.min(rows - 1, tileMaxY / CHUNK_SIZE);

            for (int cx = startCx; cx <= endCx; cx++) {
                for (int cy = startCy; cy <= endCy; cy++) {
                    int cMinX = cx * CHUNK_SIZE, cMinY = cy * CHUNK_SIZE;
                    int cMaxX = cMinX + CHUNK_SIZE, cMaxY = cMinY + CHUNK_SIZE;
                    if (tileMaxX >= cMinX && tileMinX <= cMaxX && tileMaxY >= cMinY && tileMinY <= cMaxY) {
                        if (tempBuckets[cx][cy] == null) tempBuckets[cx][cy] = new ArrayList<>();
                        tempBuckets[cx][cy].add(tile);
                    }
                }
            }
        }

        for (int cx = 0; cx < cols; cx++) {
            for (int cy = 0; cy < rows; cy++) {
                if (tempBuckets[cx][cy] != null) {
                    tempBuckets[cx][cy].sort((t1, t2) -> {
                        int py1 = CoordinateConverter.getPixelY(t1.q, t1.r);
                        int py2 = CoordinateConverter.getPixelY(t2.q, t2.r);
                        if (py1 != py2) return Integer.compare(py1, py2);
                        int px1 = CoordinateConverter.getPixelX(t1.q, t1.r);
                        int px2 = CoordinateConverter.getPixelX(t2.q, t2.r);
                        if (px1 != px2) return Integer.compare(px1, px2);
                        return Integer.compare(t1.getZ(), t2.getZ());
                    });
                    flatChunkTiles[cx][cy] = tempBuckets[cx][cy].toArray(new HexTile[0]);
                }
            }
        }
    }

    public ChunkMesh getChunkMesh(int cx, int cy) {
        long key = ((long) cx << 32) | (cy & 0xFFFFFFFFL);
        if (chunkMeshes.containsKey(key)) return chunkMeshes.get(key);

        if (flatChunkTiles == null || cx < 0 || cx >= cols || cy < 0 || cy >= rows || flatChunkTiles[cx][cy] == null) {
            return null;
        }

        ChunkMesh mesh = generateMesh(flatChunkTiles[cx][cy]);
        chunkMeshes.put(key, mesh);
        return mesh;
    }

    private ChunkMesh generateMesh(HexTile[] tiles) {
        int floatsPerVertex = 8; 
        int totalVertices = 0;
        for (HexTile t : tiles) {
            totalVertices += 18; 
            if (t.getZ() > 0) {
                totalVertices += 36; 
            }
        }

        float[] vertices = new float[totalVertices * floatsPerVertex];
        int idx = 0;

        float topSize = CoordinateConverter.HEX_SIZE - 1.0f;
        float baseSize = CoordinateConverter.HEX_SIZE;

        for (HexTile tile : tiles) {
            int cx = CoordinateConverter.getPixelX(tile.q, tile.r);
            int cy = CoordinateConverter.getPixelY(tile.q, tile.r);
            int z = tile.getZ();

            float type = tile.getTileType().ordinal();
            float biome = tile.getBiomeType().ordinal();
            float elevation = z;
            float variation = Math.abs(tile.q * 31 + tile.r * 17) % 25;
            float frame = 0.0f;

            float[] tx = new float[6];
            float[] ty = new float[6];
            float[] bx = new float[6];
            float[] by = new float[6];

            for (int i = 0; i < 6; i++) {
                double angle = Math.PI / 3 * i + Math.PI / 6;
                tx[i] = cx + (float)(topSize * Math.cos(angle));
                ty[i] = cy + (float)(topSize * Math.sin(angle)) - z;
                bx[i] = cx + (float)(baseSize * Math.cos(angle));
                by[i] = cy + (float)(baseSize * Math.sin(angle));
            }

            float centerTy = cy - z;

            // Top Face (6 triangles)
            for (int i = 0; i < 6; i++) {
                int next = (i + 1) % 6;
                
                // Vertex 1: Center
                vertices[idx++] = cx; vertices[idx++] = centerTy; vertices[idx++] = 0.0f;
                vertices[idx++] = type; vertices[idx++] = biome; vertices[idx++] = elevation; vertices[idx++] = variation; vertices[idx++] = frame;
                
                // Vertex 2: i
                vertices[idx++] = tx[i]; vertices[idx++] = ty[i]; vertices[idx++] = 0.0f;
                vertices[idx++] = type; vertices[idx++] = biome; vertices[idx++] = elevation; vertices[idx++] = variation; vertices[idx++] = frame;

                // Vertex 3: next
                vertices[idx++] = tx[next]; vertices[idx++] = ty[next]; vertices[idx++] = 0.0f;
                vertices[idx++] = type; vertices[idx++] = biome; vertices[idx++] = elevation; vertices[idx++] = variation; vertices[idx++] = frame;
            }

            // Side Faces
            if (z > 0) {
                for (int i = 0; i < 6; i++) {
                    int next = (i + 1) % 6;
                    
                    // Triangle 1
                    vertices[idx++] = tx[i]; vertices[idx++] = ty[i]; vertices[idx++] = 0.0f;
                    vertices[idx++] = type; vertices[idx++] = biome; vertices[idx++] = elevation; vertices[idx++] = variation; vertices[idx++] = frame;
                    
                    vertices[idx++] = bx[i]; vertices[idx++] = by[i]; vertices[idx++] = 0.0f;
                    vertices[idx++] = type; vertices[idx++] = biome; vertices[idx++] = 0.0f; vertices[idx++] = variation; vertices[idx++] = frame;
                    
                    vertices[idx++] = bx[next]; vertices[idx++] = by[next]; vertices[idx++] = 0.0f;
                    vertices[idx++] = type; vertices[idx++] = biome; vertices[idx++] = 0.0f; vertices[idx++] = variation; vertices[idx++] = frame;

                    // Triangle 2
                    vertices[idx++] = tx[i]; vertices[idx++] = ty[i]; vertices[idx++] = 0.0f;
                    vertices[idx++] = type; vertices[idx++] = biome; vertices[idx++] = elevation; vertices[idx++] = variation; vertices[idx++] = frame;
                    
                    vertices[idx++] = bx[next]; vertices[idx++] = by[next]; vertices[idx++] = 0.0f;
                    vertices[idx++] = type; vertices[idx++] = biome; vertices[idx++] = 0.0f; vertices[idx++] = variation; vertices[idx++] = frame;
                    
                    vertices[idx++] = tx[next]; vertices[idx++] = ty[next]; vertices[idx++] = 0.0f;
                    vertices[idx++] = type; vertices[idx++] = biome; vertices[idx++] = elevation; vertices[idx++] = variation; vertices[idx++] = frame;
                }
            }
        }

        ChunkMesh mesh = new ChunkMesh();
        mesh.vertices = vertices;
        mesh.vertexCount = totalVertices;
        return mesh;
    }

    public int getCols() { return cols; }
    public int getRows() { return rows; }
    public ByteBuffer getElevationData() { return elevationData; }

    // Stubs for legacy GamePanel compatibility
    public void markChunkDirty(int cx, int cy) {}
    public void preBakeInitialMap(int initialTod, Point spawnPixel) {}
    public void setShadowsReadyForTod(int initialTod) {}
    public void renderChunks(Graphics2D g2d, int offsetX, int offsetY, int screenW, int screenH, double zoom) {}
    public void drawCollectiveShadowOverlay(Graphics2D g2d, int targetLod) {}
}

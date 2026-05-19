import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class GridManager {
    // FUNDAMENTAL OPTIMIZATION: Replaced String keys with primitive Long keys.
    // Completely eliminates millions of String allocations and GC lag during map generation/pathing.
    public static final Map<Long, HexTile> hexes = new ConcurrentHashMap<>();
    private static final Map<Long, Unit> gridOccupied = new ConcurrentHashMap<>();

    public static final Set<Long> transitOccupied = ConcurrentHashMap.newKeySet();
    public static final Set<Long> townhallFootprints = ConcurrentHashMap.newKeySet();

    // GENERATION OPTIMIZATION: Dense 2D array for world gen phase.
    // Eliminates HashMap overhead (Long boxing, node allocations, hashing) during the
    // most allocation-heavy phase. hexGrid[q][r + rOffset] gives direct O(1) access
    // with excellent cache locality. Populated into hexes map after generation completes.
    private static HexTile[][] hexGrid;
    private static int gridROffset = 0;

    public static int landCenterPX = 0;
    public static int landCenterPY = 0;

    public static float timeOfDay = 0.0f;
    public static int currentDay = 1;

    public static final Map<HexTile, Integer> rockDistToGrass = new ConcurrentHashMap<>();

    private static final int[][] DIRS = { {1,0},{1,-1},{0,-1},{-1,0},{-1,1},{0,1} };

    // Ultra-fast bitwise coordinate packing
    public static long packKey(int q, int r) {
        return (((long) q) << 32) | (r & 0xFFFFFFFFL);
    }

    // Array-based access for generation phase (zero-allocation, direct indexing)
    private static HexTile getFromArray(int q, int r) {
        int idx = r + gridROffset;
        if (q < 0 || q >= hexGrid.length || idx < 0 || idx >= hexGrid[q].length) return null;
        return hexGrid[q][idx];
    }

    private static void setInArray(int q, int r, HexTile tile) {
        int idx = r + gridROffset;
        hexGrid[q][idx] = tile;
    }

    private static boolean isWater(TileType t) {
        return t == TileType.WATER || t == TileType.ICE;
    }

    private static boolean isWalkable(TileType t) {
        return t == TileType.PLAIN || t == TileType.SAND || t == TileType.SNOW;
    }

    private static boolean isTree(TileType t) {
        return t == TileType.TREES;
    }

    private static boolean isRock(TileType t) {
        return t == TileType.ROCK;
    }

    private static boolean isColdBiome(BiomeType b) {
        return b == BiomeType.TUNDRA || b == BiomeType.TAIGA;
    }

    private static boolean isWarmBiome(BiomeType b) {
        return b == BiomeType.DESERT || b == BiomeType.SAVANNAH;
    }

    public static void setTimeOfDay(float time) {
        timeOfDay = Math.max(0.0f, Math.min(2.0f, time));
    }

    public static void generateMap(long seed) {
        hexes.clear();
        rockDistToGrass.clear();
        int cols = CoordinateConverter.MAP_COLS;
        int rows = CoordinateConverter.MAP_ROWS;

        // Allocate dense 2D array for generation phase
        gridROffset = rows; // enough room for negative r values
        hexGrid = new HexTile[cols][rows + gridROffset + 10];

        double scaleF = 1.0;
        Random rand = new Random(seed);
        Perlin2D elevationNoise = new Perlin2D(rand.nextLong());
        Perlin2D featureNoise = new Perlin2D(rand.nextLong());
        Perlin2D temperatureNoise = new Perlin2D(rand.nextLong());

        double centerX = CoordinateConverter.getMapWidth() / 2.0;
        double centerY = CoordinateConverter.getMapHeight() / 2.0;
        double sizeRatio = (double) cols / 450.0;
        double dynamicCoeff = Math.max(0.30, Math.min(0.55, 0.34 + (Math.log10(sizeRatio) * 0.12)));
        double maxRadius = Math.min(CoordinateConverter.getMapWidth(), CoordinateConverter.getMapHeight()) * dynamicCoeff;
        double falloffPower = Math.max(0.5, 1.1 - (sizeRatio * 0.1));

        // Phase 1: Create all tiles in the dense array (parallel)
        IntStream.range(0, cols).parallel().forEach(q -> {
            int rOffset = q >> 1;
            for (int r = -rOffset; r < rows - rOffset; r++) {
                HexTile tile = new HexTile(q, r);
                int pX = CoordinateConverter.getPixelX(q, r);
                int pY = CoordinateConverter.getPixelY(q, r);

                double dx = pX - centerX;
                double dy = pY - centerY;
                double distance = Math.sqrt(dx * dx + dy * dy);

                double mask = Math.max(0.0, 1.0 - (distance / maxRadius));
                mask = Math.pow(mask, falloffPower);

                double n1 = elevationNoise.noise(q * 0.014 * scaleF, r * 0.014 * scaleF);
                double n2 = elevationNoise.noise(q * 0.05 * scaleF,  r * 0.05 * scaleF)  * 0.35;
                double n3 = elevationNoise.noise(q * 0.15 * scaleF,  r * 0.15 * scaleF)  * 0.12;
                double rawNoise = (n1 + n2 + n3) / 1.47;

                double finalHeight = (rawNoise * 0.28) + (mask * 0.72);

                if (finalHeight < 0.20) {
                    tile.setTileType(TileType.WATER);
                    tile.setBiomeType(BiomeType.NONE);
                } else {
                    tile.setTileType(TileType.PLAIN);
                    tile.setBiomeType(BiomeType.GRASSLAND);
                }
                tile.customZ = -1;
                setInArray(q, r, tile);
            }
        });

        // Phase 2: Smoothing passes using array access (parallel)
        for (int smoothPass = 0; smoothPass < 3; smoothPass++) {
            List<HexTile> toOcean = collectFromArrayParallel(t -> {
                if (t.getTileType() != TileType.PLAIN) return false;
                int waterNeighbors = 0;
                for (int[] d : DIRS) {
                    HexTile n = getFromArray(t.q + d[0], t.r + d[1]);
                    if (n == null || isWater(n.getTileType())) waterNeighbors++;
                }
                return waterNeighbors >= 5;
            });

            List<HexTile> toLand = collectFromArrayParallel(t -> {
                if (t.getTileType() != TileType.WATER) return false;
                int waterNeighbors = 0;
                for (int[] d : DIRS) {
                    HexTile n = getFromArray(t.q + d[0], t.r + d[1]);
                    if (n == null || isWater(n.getTileType())) waterNeighbors++;
                }
                return waterNeighbors <= 1;
            });

            toOcean.parallelStream().forEach(t -> { t.setTileType(TileType.WATER); t.setBiomeType(BiomeType.NONE); });
            toLand.parallelStream().forEach(t -> { t.setTileType(TileType.PLAIN); t.setBiomeType(BiomeType.GRASSLAND); });
        }

        int centerQ = cols / 2;
        int centerR = (rows / 2) - (centerQ >> 1);
        HexTile anchorSeed = getFromArray(centerQ, centerR);

        if (anchorSeed == null || isWater(anchorSeed.getTileType())) {
            for (int radius = 1; radius < 100; radius++) {
                boolean found = false;
                for (int[] d : DIRS) {
                    HexTile check = getFromArray(centerQ + d[0]*radius, centerR + d[1]*radius);
                    if (check != null && isWalkable(check.getTileType())) {
                        anchorSeed = check; found = true; break;
                    }
                }
                if (found) break;
            }
        }

        Set<HexTile> connectedContinent = new HashSet<>();
        if (anchorSeed != null) {
            Queue<HexTile> cQueue = new LinkedList<>();
            cQueue.add(anchorSeed);
            connectedContinent.add(anchorSeed);

            while (!cQueue.isEmpty()) {
                HexTile curr = cQueue.poll();
                for (int[] d : DIRS) {
                    HexTile n = getFromArray(curr.q + d[0], curr.r + d[1]);
                    if (n != null && isWalkable(n.getTileType()) && !connectedContinent.contains(n)) {
                        connectedContinent.add(n); cQueue.add(n);
                    }
                }
            }
        }

        iterateArrayParallel(tile -> {
            if (isWalkable(tile.getTileType()) && !connectedContinent.contains(tile)) {
                tile.setTileType(TileType.WATER);
                tile.setBiomeType(BiomeType.NONE);
            }
        });

        applyBiomeSystemArray(temperatureNoise, scaleF, rand);

        iterateArrayParallel(tile -> {
            TileType tt = tile.getTileType();
            if (!isWater(tt) && tt != TileType.ICE) {
                int pX = CoordinateConverter.getPixelX(tile.q, tile.r);
                int pY = CoordinateConverter.getPixelY(tile.q, tile.r);

                double f1 = featureNoise.noise(pX * 0.0005 * scaleF, pY * 0.0005 * scaleF);
                double f2 = featureNoise.noise(pX * 0.002 * scaleF,  pY * 0.002 * scaleF) * 0.3;
                double finalFeature = (f1 + f2) / 1.3;

                boolean deepInland = true;
                for (int[] d : DIRS) {
                    HexTile n = getFromArray(tile.q + d[0], tile.r + d[1]);
                    if (n == null || isWater(n.getTileType()) || n.getTileType() == TileType.ICE) { deepInland = false; break; }
                }

                if (deepInland && finalFeature > 0.55) {
                    tile.setTileType(TileType.ROCK);
                    tile.setBiomeType(BiomeType.NONE);
                } else if (deepInland) {
                    applyBiomeFeature(tile, finalFeature, rand);
                }
            }
        });

        int relativeMountainThreshold = Math.max(10, (cols * rows) / 12000);
        pruneClustersArrayTile(TileType.ROCK, relativeMountainThreshold, TileType.PLAIN);

        for (int smoothPass = 0; smoothPass < 2; smoothPass++) {
            List<HexTile> toGrass = collectFromArrayParallel(t -> {
                if (t.getTileType() != TileType.ROCK) return false;
                int rockNeighbors = 0;
                for (int[] d : DIRS) {
                    HexTile n = getFromArray(t.q + d[0], t.r + d[1]);
                    if (n != null && n.getTileType() == TileType.ROCK) rockNeighbors++;
                }
                return rockNeighbors <= 2;
            });

            List<HexTile> toRock = collectFromArrayParallel(t -> {
                TileType tt = t.getTileType();
                if (tt != TileType.PLAIN && !isTree(tt)) return false;
                int rockNeighbors = 0;
                for (int[] d : DIRS) {
                    HexTile n = getFromArray(t.q + d[0], t.r + d[1]);
                    if (n != null && n.getTileType() == TileType.ROCK) rockNeighbors++;
                }
                return rockNeighbors >= 5;
            });

            toGrass.parallelStream().forEach(t -> { t.setTileType(TileType.PLAIN); t.setBiomeType(BiomeType.GRASSLAND); });
            toRock.parallelStream().forEach(t -> { t.setTileType(TileType.ROCK); t.setBiomeType(BiomeType.NONE); });
        }

        int forestPathCount = (cols * rows) / 6000;
        List<HexTile> forestNodes = collectFromArrayParallel(t -> isTree(t.getTileType()));

        for (int i = 0; i < forestPathCount; i++) {
            if (forestNodes.isEmpty()) break;
            HexTile startNode = forestNodes.remove(rand.nextInt(forestNodes.size()));
            double baseAngle = rand.nextDouble() * Math.PI * 2;
            traceForestPathArray(CoordinateConverter.getPixelX(startNode.q, startNode.r), CoordinateConverter.getPixelY(startNode.q, startNode.r), baseAngle, rand, scaleF);
        }

        Map<HexTile, Integer> rockLevels = new ConcurrentHashMap<>();
        iterateArrayParallel(tile -> {
            if (tile.getTileType() == TileType.ROCK) rockLevels.put(tile, 1);
        });

        boolean changed = true;
        int currentLevel = 1;
        while (changed) {
            changed = false;
            final int lvl = currentLevel;

            List<HexTile> toPromote = rockLevels.entrySet().parallelStream().filter(entry -> {
                if (entry.getValue() != lvl) return false;
                HexTile tile = entry.getKey();
                int requiredNeighbors = 6;
                int variance = Math.abs(tile.q * 17 + tile.r * 31) % 100;
                if (variance < 20) requiredNeighbors = 4;
                else if (variance < 60) requiredNeighbors = 5;

                int neighborCount = 0;
                for (int[] d : DIRS) {
                    HexTile n = getFromArray(tile.q + d[0], tile.r + d[1]);
                    if (n != null && n.getTileType() == TileType.ROCK && rockLevels.getOrDefault(n, 0) >= lvl) {
                        neighborCount++;
                    }
                }
                return neighborCount >= requiredNeighbors;
            }).map(Map.Entry::getKey).collect(Collectors.toList());

            if (!toPromote.isEmpty()) {
                changed = true;
                currentLevel++;
                final int nextLvl = currentLevel;
                toPromote.parallelStream().forEach(t -> rockLevels.put(t, nextLvl));
            }
        }

        rockLevels.entrySet().parallelStream().forEach(entry -> {
            entry.getKey().customZ = 20 + ((entry.getValue() - 1) * 10);
        });

        Set<HexTile> oceanTiles = ConcurrentHashMap.newKeySet();
        Queue<HexTile> oQueue = new LinkedList<>();
        HexTile oceanOrigin = getFromArray(0, 0);

        if (oceanOrigin != null && isWater(oceanOrigin.getTileType())) {
            oQueue.add(oceanOrigin); oceanTiles.add(oceanOrigin);
            while (!oQueue.isEmpty()) {
                HexTile curr = oQueue.poll();
                for (int[] d : DIRS) {
                    HexTile n = getFromArray(curr.q + d[0], curr.r + d[1]);
                    if (n != null && isWater(n.getTileType()) && !oceanTiles.contains(n)) {
                        oceanTiles.add(n); oQueue.add(n);
                    }
                }
            }
        }

        List<HexTile> shoreLaunchingNodes = collectFromArrayParallel(t -> {
            if (!isWalkable(t.getTileType()) && !isTree(t.getTileType())) return false;
            for (int[] d : DIRS) {
                HexTile n = getFromArray(t.q + d[0], t.r + d[1]);
                if (n != null && isWater(n.getTileType()) && oceanTiles.contains(n)) return true;
            }
            return false;
        });

        int transContinentalRiverCount = 2;
        Set<HexTile> masterRiverTiles = ConcurrentHashMap.newKeySet();
        Set<HexTile> masterBridgesSet = ConcurrentHashMap.newKeySet();
        Set<HexTile> globalBlockedSet = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < transContinentalRiverCount; i++) {
            if (shoreLaunchingNodes.isEmpty()) break;
            HexTile startNode = shoreLaunchingNodes.remove(rand.nextInt(shoreLaunchingNodes.size()));
            int pixelPosX = CoordinateConverter.getPixelX(startNode.q, startNode.r);
            int pixelPosY = CoordinateConverter.getPixelY(startNode.q, startNode.r);

            double baseAngle = Math.atan2(centerY - pixelPosY, centerX - pixelPosX);
            baseAngle += (rand.nextDouble() - 0.5) * 0.3;

            int[] forkCounter = new int[]{0};
            Set<HexTile> currentSystemTiles = new HashSet<>();
            Map<HexTile, Integer> centerLineSteps = new HashMap<>();

            traceContinuousRiverArray(pixelPosX, pixelPosY, baseAngle, oceanTiles, currentSystemTiles, globalBlockedSet, masterBridgesSet, rand, 0, forkCounter, scaleF, centerLineSteps);

            masterRiverTiles.addAll(currentSystemTiles);
            globalBlockedSet.addAll(currentSystemTiles);
        }

        masterRiverTiles.removeAll(masterBridgesSet);

        masterBridgesSet.parallelStream().forEach(bt -> {
            if (bt.getTileType() == TileType.ROCK) {
                bt.setTileType(TileType.PLAIN);
                bt.setBiomeType(BiomeType.GRASSLAND);
                bt.customZ = -1;
            }
        });

        masterRiverTiles.parallelStream().forEach(rt -> {
            rt.setTileType(TileType.WATER);
            rt.setBiomeType(BiomeType.NONE);
            rt.customZ = -1;
        });

        List<HexTile> initialShores = collectFromArrayParallel(t -> {
            if (!isWalkable(t.getTileType()) && !isTree(t.getTileType())) return false;
            for (int[] d : DIRS) {
                HexTile n = getFromArray(t.q + d[0], t.r + d[1]);
                if (n != null && isWater(n.getTileType())) return true;
            }
            return false;
        });

        Map<HexTile, Integer> remainingSandDepth = new ConcurrentHashMap<>();
        Queue<HexTile> bQueue = new LinkedList<>();

        for (HexTile shore : initialShores) {
            boolean touchesOcean = false;
            for (int[] d : DIRS) {
                HexTile n = getFromArray(shore.q + d[0], shore.r + d[1]);
                if (n != null && isWater(n.getTileType()) && oceanTiles.contains(n)) {
                    touchesOcean = true; break;
                }
            }

            int targetThickness = masterBridgesSet.contains(shore) ? (1 + rand.nextInt(3)) : (touchesOcean ? (1 + rand.nextInt(5)) : rand.nextInt(4));

            if (targetThickness > 0 && targetThickness > remainingSandDepth.getOrDefault(shore, -1)) {
                remainingSandDepth.put(shore, targetThickness);
                bQueue.add(shore);
            }
        }

        while (!bQueue.isEmpty()) {
            HexTile curr = bQueue.poll();
            int currentAllowance = remainingSandDepth.get(curr);

            if (currentAllowance <= 1) continue;

            for (int[] d : DIRS) {
                HexTile n = getFromArray(curr.q + d[0], curr.r + d[1]);
                if (n != null && isWalkable(n.getTileType())) {
                    int nextAllowance = currentAllowance - 1;
                    if (nextAllowance == 1 && rand.nextDouble() < 0.45) nextAllowance = 0;

                    if (nextAllowance > 0 && nextAllowance > remainingSandDepth.getOrDefault(n, -1)) {
                        remainingSandDepth.put(n, nextAllowance);
                        bQueue.add(n);
                    }
                }
            }
        }

        remainingSandDepth.keySet().parallelStream().forEach(t -> {
            if (t.getBiomeType() == BiomeType.DESERT) {
                t.setTileType(TileType.SAND);
                t.setBiomeType(BiomeType.NONE);
            } else {
                t.setTileType(TileType.SAND);
                t.setBiomeType(BiomeType.NONE);
            }
            t.customZ = -1;
        });

        pruneClustersArrayTile(TileType.SAND, 3, TileType.PLAIN);

        applySlopePostProcess(seed);

        // Phase 3: Prune empty borders and compact into new coordinate space
        int[] bounds = { Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE };

        iterateArray(tile -> {
            TileType tt = tile.getTileType();
            if (!isWater(tt) && tt != TileType.ICE) {
                int h = tile.r + (tile.q >> 1);
                if (tile.q < bounds[0]) bounds[0] = tile.q;
                if (tile.q > bounds[1]) bounds[1] = tile.q;
                if (h < bounds[2]) bounds[2] = h;
                if (h > bounds[3]) bounds[3] = h;
            }
        });

        int minQ = bounds[0], maxQ = bounds[1], minH = bounds[2], maxH = bounds[3];
        int startQ = (minQ - 10) % 2 != 0 ? minQ - 11 : minQ - 10;
        int endQ = maxQ + 10, startH = minH - 10, endH = maxH + 14;

        Map<Long, HexTile> prunedHexes = new ConcurrentHashMap<>();
        int newCols = (endQ - startQ) + 1;
        int newRows = (endH - startH) + 1;

        for (int nq = 0; nq < newCols; nq++) {
            for (int nh = 0; nh < newRows; nh++) {
                int oldQ = startQ + nq;
                int oldH = startH + nh;
                int oldR = oldH - (oldQ >> 1);

                int newQ = nq;
                int newR = nh - (nq >> 1);

                HexTile oldTile = getFromArray(oldQ, oldR);
                HexTile newTile = new HexTile(newQ, newR);

                if (oldTile != null) {
                    newTile.setTileType(oldTile.getTileType());
                    newTile.setBiomeType(oldTile.getBiomeType());
                    newTile.customZ = oldTile.customZ;
                    newTile.isSparseForest = oldTile.isSparseForest;
                } else {
                    newTile.setTileType(TileType.ICE);
                    newTile.setBiomeType(BiomeType.NONE);
                    newTile.customZ = -1;
                }
                prunedHexes.put(packKey(newQ, newR), newTile);
            }
        }

        hexes.clear();
        hexes.putAll(prunedHexes);

        CoordinateConverter.MAP_COLS = newCols;
        CoordinateConverter.MAP_ROWS = newRows;

        // Clear array reference to free memory
        hexGrid = null;
        gridROffset = 0;

        Queue<HexTile> distQueue = new LinkedList<>();
        for (HexTile t : hexes.values()) {
            if (t.getTileType() != TileType.ROCK) {
                rockDistToGrass.put(t, 0);
                distQueue.add(t);
            }
        }

        while (!distQueue.isEmpty()) {
            HexTile curr = distQueue.poll();
            int currentDist = rockDistToGrass.get(curr);

            for (int[] d : DIRS) {
                HexTile n = hexes.get(packKey(curr.q + d[0], curr.r + d[1]));
                if (n != null && n.getTileType() == TileType.ROCK && !rockDistToGrass.containsKey(n)) {
                    rockDistToGrass.put(n, currentDist + 1);
                    distQueue.add(n);
                }
            }
        }

        calculateShadows();

        long totalX = 0, totalY = 0;
        int landCount = 0;
        for (HexTile t : hexes.values()) {
            TileType tt = t.getTileType();
            if (!isWater(tt) && tt != TileType.ICE) {
                totalX += CoordinateConverter.getPixelX(t.q, t.r);
                totalY += CoordinateConverter.getPixelY(t.q, t.r);
                landCount++;
            }
        }
        if (landCount > 0) {
            landCenterPX = (int)(totalX / landCount);
            landCenterPY = (int)(totalY / landCount);
        } else {
            landCenterPX = CoordinateConverter.getMapWidth() / 2;
            landCenterPY = CoordinateConverter.getMapHeight() / 2;
        }
    }

    // Array iteration helpers (zero-allocation during generation)
    private static void iterateArray(java.util.function.Consumer<HexTile> action) {
        for (int q = 0; q < hexGrid.length; q++) {
            HexTile[] row = hexGrid[q];
            for (int i = 0; i < row.length; i++) {
                HexTile t = row[i];
                if (t != null) action.accept(t);
            }
        }
    }

    private static void iterateArrayParallel(java.util.function.Consumer<HexTile> action) {
        List<HexTile> allTiles = new ArrayList<>(hexGrid.length * hexGrid[0].length / 2);
        for (int q = 0; q < hexGrid.length; q++) {
            HexTile[] row = hexGrid[q];
            for (int i = 0; i < row.length; i++) {
                if (row[i] != null) allTiles.add(row[i]);
            }
        }
        allTiles.parallelStream().forEach(action);
    }

    private static List<HexTile> collectFromArrayParallel(java.util.function.Predicate<HexTile> filter) {
        List<HexTile> result = Collections.synchronizedList(new ArrayList<>());
        for (int q = 0; q < hexGrid.length; q++) {
            HexTile[] row = hexGrid[q];
            for (int i = 0; i < row.length; i++) {
                HexTile t = row[i];
                if (t != null && filter.test(t)) {
                    result.add(t);
                }
            }
        }
        return result;
    }

    // Array-based generation methods (use hexGrid instead of hexes map)
    private static void applyBiomeSystemArray(Perlin2D tempNoise, double scaleF, Random rand) {
        Perlin2D moistureNoise = new Perlin2D(rand.nextLong());
        Perlin2D macroTemp1 = new Perlin2D(rand.nextLong());
        Perlin2D macroTemp2 = new Perlin2D(rand.nextLong());

        iterateArrayParallel(tile -> {
            if (!isWalkable(tile.getTileType())) return;

            int pX = CoordinateConverter.getPixelX(tile.q, tile.r);
            int pY = CoordinateConverter.getPixelY(tile.q, tile.r);

            double t1 = macroTemp1.noise(pX * 0.0003 * scaleF, pY * 0.0003 * scaleF);
            double t2 = macroTemp2.noise(pX * 0.0008 * scaleF, pY * 0.0008 * scaleF) * 0.5;
            double t3 = tempNoise.noise(pX * 0.0005 * scaleF, pY * 0.0005 * scaleF) * 0.7;
            double temperature = (t1 + t2 + t3) / 2.2;

            double m1 = moistureNoise.noise(pX * 0.0004 * scaleF, pY * 0.0004 * scaleF);
            double m2 = moistureNoise.noise(pX * 0.001 * scaleF, pY * 0.001 * scaleF) * 0.4;
            double moisture = (m1 + m2) / 1.4;

            if (temperature > 0.30) {
                tile.setTileType(TileType.SAND);
                tile.setBiomeType(BiomeType.DESERT);
            } else if (temperature > 0.15) {
                tile.setTileType(TileType.SAND);
                tile.setBiomeType(BiomeType.SAVANNAH);
            } else if (temperature < -0.30) {
                tile.setTileType(TileType.SNOW);
                tile.setBiomeType(BiomeType.TUNDRA);
            } else if (temperature < -0.12) {
                tile.setTileType(TileType.SNOW);
                tile.setBiomeType(BiomeType.TAIGA);
            } else {
                tile.setTileType(TileType.PLAIN);
                tile.setBiomeType(BiomeType.GRASSLAND);
            }
        });

        for (int pass = 0; pass < 3; pass++) {
            List<HexTile> coldToBuffer = collectFromArrayParallel(t -> {
                BiomeType b = t.getBiomeType();
                return b == BiomeType.TUNDRA || b == BiomeType.TAIGA;
            });
            coldToBuffer = coldToBuffer.stream().filter(t -> {
                for (int[] d : DIRS) {
                    HexTile n = getFromArray(t.q + d[0], t.r + d[1]);
                    if (n != null) {
                        BiomeType nb = n.getBiomeType();
                        if (nb == BiomeType.DESERT || nb == BiomeType.SAVANNAH) return true;
                    }
                }
                return false;
            }).collect(Collectors.toList());

            List<HexTile> warmToBuffer = collectFromArrayParallel(t -> {
                BiomeType b = t.getBiomeType();
                return b == BiomeType.DESERT || b == BiomeType.SAVANNAH;
            });
            warmToBuffer = warmToBuffer.stream().filter(t -> {
                for (int[] d : DIRS) {
                    HexTile n = getFromArray(t.q + d[0], t.r + d[1]);
                    if (n != null) {
                        BiomeType nb = n.getBiomeType();
                        if (nb == BiomeType.TUNDRA || nb == BiomeType.TAIGA) return true;
                    }
                }
                return false;
            }).collect(Collectors.toList());

            coldToBuffer.parallelStream().forEach(t -> { t.setTileType(TileType.PLAIN); t.setBiomeType(BiomeType.GRASSLAND); });
            warmToBuffer.parallelStream().forEach(t -> { t.setTileType(TileType.PLAIN); t.setBiomeType(BiomeType.GRASSLAND); });
        }

        iterateArrayParallel(tile -> {
            if (tile.getBiomeType() == BiomeType.TAIGA) {
                boolean nearWater = false;
                for (int[] d : DIRS) {
                    HexTile n = getFromArray(tile.q + d[0], tile.r + d[1]);
                    if (n != null && isWater(n.getTileType())) {
                        nearWater = true; break;
                    }
                }
                if (nearWater) { tile.setTileType(TileType.SNOW); tile.setBiomeType(BiomeType.TUNDRA); }
            }
        });

        List<HexTile> tundraToTaiga = collectFromArrayParallel(t -> {
            if (t.getBiomeType() != BiomeType.TUNDRA) return false;
            int waterNeighbors = 0;
            for (int[] d : DIRS) {
                HexTile n = getFromArray(t.q + d[0], t.r + d[1]);
                if (n != null && isWater(n.getTileType())) waterNeighbors++;
            }
            return waterNeighbors < 1;
        });
        tundraToTaiga.parallelStream().forEach(t -> { t.setTileType(TileType.SNOW); t.setBiomeType(BiomeType.TAIGA); });

        pruneClustersArrayBiome(BiomeType.TUNDRA, 3, BiomeType.TAIGA);
        pruneClustersArrayBiome(BiomeType.SAVANNAH, 5, BiomeType.GRASSLAND);
        pruneClustersArrayBiome(BiomeType.DESERT, 5, BiomeType.SAVANNAH);
    }

    private static void pruneClustersArrayBiome(BiomeType targetBiome, int minSize, BiomeType replacementBiome) {
        Set<HexTile> visited = new HashSet<>();
        List<HexTile> toReplace = Collections.synchronizedList(new ArrayList<>());

        iterateArray(tile -> {
            if (tile.getBiomeType() == targetBiome && !visited.contains(tile)) {
                List<HexTile> cluster = new ArrayList<>();
                Queue<HexTile> queue = new LinkedList<>();

                synchronized (visited) {
                    if (visited.contains(tile)) return;
                    visited.add(tile);
                }
                queue.add(tile);

                while (!queue.isEmpty()) {
                    HexTile curr = queue.poll();
                    cluster.add(curr);
                    for (int[] d : DIRS) {
                        HexTile n = getFromArray(curr.q + d[0], curr.r + d[1]);
                        if (n != null && n.getBiomeType() == targetBiome) {
                            synchronized (visited) {
                                if (!visited.contains(n)) { visited.add(n); queue.add(n); }
                            }
                        }
                    }
                }
                if (cluster.size() < minSize) toReplace.addAll(cluster);
            }
        });

        toReplace.parallelStream().forEach(t -> {
            t.setBiomeType(replacementBiome);
            if (replacementBiome == BiomeType.GRASSLAND) t.setTileType(TileType.PLAIN);
            else if (replacementBiome == BiomeType.TAIGA) t.setTileType(TileType.SNOW);
            else if (replacementBiome == BiomeType.SAVANNAH) t.setTileType(TileType.SAND);
            t.customZ = -1;
        });
    }

    private static void pruneClustersArrayTile(TileType targetType, int minSize, TileType replacementType) {
        Set<HexTile> visited = new HashSet<>();
        List<HexTile> toReplace = Collections.synchronizedList(new ArrayList<>());

        iterateArray(tile -> {
            if (tile.getTileType() == targetType && !visited.contains(tile)) {
                List<HexTile> cluster = new ArrayList<>();
                Queue<HexTile> queue = new LinkedList<>();

                synchronized (visited) {
                    if (visited.contains(tile)) return;
                    visited.add(tile);
                }
                queue.add(tile);

                while (!queue.isEmpty()) {
                    HexTile curr = queue.poll();
                    cluster.add(curr);
                    for (int[] d : DIRS) {
                        HexTile n = getFromArray(curr.q + d[0], curr.r + d[1]);
                        if (n != null && n.getTileType() == targetType) {
                            synchronized (visited) {
                                if (!visited.contains(n)) { visited.add(n); queue.add(n); }
                            }
                        }
                    }
                }
                if (cluster.size() < minSize) toReplace.addAll(cluster);
            }
        });

        toReplace.parallelStream().forEach(t -> {
            t.setTileType(replacementType);
            t.customZ = -1;
        });
    }

    private static void traceForestPathArray(double startX, double startY, double baseAngle, Random rand, double scaleF) {
        double curX = startX, curY = startY, angle = baseAngle;
        int step = 0, maxSteps = 30 + rand.nextInt(50);
        Perlin2D pathWobble = new Perlin2D(rand.nextLong());
        Perlin2D thicknessNoise = new Perlin2D(rand.nextLong());

        while (step < maxSteps) {
            double wobble = pathWobble.noise(curX * 0.015 * scaleF, curY * 0.015 * scaleF) * 2.0;
            angle = baseAngle + wobble;
            curX += Math.cos(angle) * 35.0;
            curY += Math.sin(angle) * 35.0;

            Point hCoord = CoordinateConverter.pixelToHex((int)curX, (int)curY);
            HexTile tile = getFromArray(hCoord.x, hCoord.y);
            if (tile == null) break;

            double tNoise = thicknessNoise.noise(curX * 0.02 * scaleF, curY * 0.02 * scaleF);
            int currentWidth = Math.max(1, Math.min(3, 1 + (int) Math.round((tNoise + 1.0) * 1.5)));

            paintForestPathBrush(tile, currentWidth, rand);
            step++;
        }
    }

    private static void traceContinuousRiverArray(double startX, double startY, double baseAngle, Set<HexTile> oceanTiles, Set<HexTile> currentSystemTiles, Set<HexTile> blockedTiles, Set<HexTile> masterBridgesSet, Random rand, int branchDepth, int[] forkCounter, double scaleF, Map<HexTile, Integer> centerLineSteps) {
        double curX = startX, curY = startY, angle = baseAngle;
        int step = 0;
        int maxSteps = (int)(Math.min(CoordinateConverter.MAP_COLS, CoordinateConverter.MAP_ROWS) * 0.75);
        Perlin2D riverWobble = new Perlin2D(rand.nextLong());

        int stepsSinceLastBridge = 0, activeBridgeSteps = 0, currentBridgeThickness = 0;
        boolean justFinishedBridge = false;
        int postBridgeCount = 0;

        while (step < maxSteps) {
            double wobble = 0.8 * Math.sin(step * 0.04) + riverWobble.noise(curX * 0.004 * scaleF, curY * 0.004 * scaleF) * 1.2;
            angle = baseAngle + wobble;

            curX += Math.cos(angle) * 38.0;
            curY += Math.sin(angle) * 38.0;

            Point hCoord = CoordinateConverter.pixelToHex((int)curX, (int)curY);
            HexTile tile = getFromArray(hCoord.x, hCoord.y);

            if (tile == null || blockedTiles.contains(tile)) break;
            if (centerLineSteps.containsKey(tile) && Math.abs(centerLineSteps.get(tile) - step) > 20) break;

            centerLineSteps.put(tile, step);

            if (activeBridgeSteps > 0) {
                paintBridgeBrush(tile, currentBridgeThickness, masterBridgesSet);
                activeBridgeSteps--;
                if (activeBridgeSteps == 0) {
                    stepsSinceLastBridge = 0;
                    justFinishedBridge = true;
                    postBridgeCount = 0;
                }
            } else {
                stepsSinceLastBridge++;
                int activeWidth = 5 - (int)(((double)step / maxSteps) * 4.0);
                int bridgeInterval = maxSteps / 5;
                boolean bridgeComingSoon = (step > 30 && step < maxSteps - 35 && stepsSinceLastBridge > bridgeInterval - 8);

                if (justFinishedBridge) {
                    postBridgeCount++;
                    if (postBridgeCount > 8) justFinishedBridge = false;
                }

                if (bridgeComingSoon || justFinishedBridge) activeWidth = 1;

                if (step > 30 && step < maxSteps - 35 && stepsSinceLastBridge > bridgeInterval) {
                    currentBridgeThickness = 5 + rand.nextInt(5);
                    activeBridgeSteps = currentBridgeThickness;
                    paintBridgeBrush(tile, currentBridgeThickness, masterBridgesSet);
                    activeBridgeSteps--;
                } else {
                    activeWidth = Math.max(1, Math.min(5, activeWidth));
                    paintRiverBrush(tile, activeWidth, currentSystemTiles);
                }
            }

            if (branchDepth < 2 && step > 80 && step < maxSteps - 60 && forkCounter[0] < 2 && rand.nextDouble() < 0.004) {
                forkCounter[0]++;
                double forkAngle = baseAngle + (rand.nextBoolean() ? 0.42 : -0.42);
                Map<HexTile, Integer> forkCenterLines = new HashMap<>(centerLineSteps);
                traceContinuousRiverArray(curX, curY, forkAngle, oceanTiles, currentSystemTiles, blockedTiles, masterBridgesSet, rand, branchDepth + 1, forkCounter, scaleF, forkCenterLines);
            }

            if (step > 60 && tile.getTileType() == TileType.WATER && oceanTiles.contains(tile)) break;
            step++;
        }
    }

    private static void paintBridgeBrush(HexTile center, int thickness, Set<HexTile> masterBridgesSet) {
        if (center == null) return;
        int radius = (thickness - 1) / 2;

        Queue<HexTile> queue = new LinkedList<>();
        Set<HexTile> visited = new HashSet<>();
        queue.add(center); visited.add(center); masterBridgesSet.add(center);

        int currentRadius = 1;
        while (!queue.isEmpty() && currentRadius <= radius) {
            int layerSize = queue.size();
            for (int i = 0; i < layerSize; i++) {
                HexTile cell = queue.poll();
                for (int[] d : DIRS) {
                    HexTile n = getFromArray(cell.q + d[0], cell.r + d[1]);
                    if (n != null && !visited.contains(n)) {
                        visited.add(n); masterBridgesSet.add(n); queue.add(n);
                    }
                }
            }
            currentRadius++;
        }
    }

    private static void paintRiverBrush(HexTile center, int width, Set<HexTile> currentSystemTiles) {
        if (center == null) return;
        currentSystemTiles.add(center);

        if (width > 1) {
            for (int[] d : DIRS) {
                HexTile n = getFromArray(center.q + d[0], center.r + d[1]);
                if (n != null) currentSystemTiles.add(n);
            }
        }
        if (width > 3) {
            for (int[] d : DIRS) {
                HexTile firstRing = getFromArray(center.q + d[0], center.r + d[1]);
                if (firstRing != null) {
                    for (int[] d2 : DIRS) {
                        HexTile n2 = getFromArray(firstRing.q + d2[0], firstRing.r + d2[1]);
                        if (n2 != null) currentSystemTiles.add(n2);
                    }
                }
            }
        }
    }

    private static void paintForestPathBrush(HexTile center, int width, Random rand) {
        if (center == null) return;
        if (center.getTileType() == TileType.TREES) {
            center.setTileType(TileType.PLAIN);
            center.setBiomeType(BiomeType.GRASSLAND);
            center.isSparseForest = false;
        }
        if (width >= 2) {
            for (int[] d : DIRS) {
                HexTile n = getFromArray(center.q + d[0], center.r + d[1]);
                if (n != null && n.getTileType() == TileType.TREES) {
                    if (rand.nextDouble() < 0.60) {
                        n.setTileType(TileType.PLAIN);
                        n.setBiomeType(BiomeType.GRASSLAND);
                    }
                    else n.isSparseForest = true;
                }
            }
        }
        if (width >= 3) {
            for (int[] d : DIRS) {
                HexTile firstRing = getFromArray(center.q + d[0], center.r + d[1]);
                if (firstRing != null) {
                    for (int[] d2 : DIRS) {
                        HexTile n2 = getFromArray(firstRing.q + d2[0], firstRing.r + d2[1]);
                        if (n2 != null && n2.getTileType() == TileType.TREES) {
                            if (rand.nextDouble() < 0.20) {
                                n2.setTileType(TileType.PLAIN);
                                n2.setBiomeType(BiomeType.GRASSLAND);
                            }
                            else if (rand.nextDouble() < 0.70) n2.isSparseForest = true;
                        }
                    }
                }
            }
        }
    }

    private static void applySlopePostProcess(long seed) {
        Random slopeRand = new Random(seed + 9999);
        Perlin2D slopeNoise = new Perlin2D(slopeRand.nextLong());
        Perlin2D macroNoise = new Perlin2D(slopeRand.nextLong());
        Perlin2D jaggedNoise = new Perlin2D(slopeRand.nextLong());

        Map<HexTile, Integer> biomeDist = new ConcurrentHashMap<>();
        Queue<HexTile> distQueue = new LinkedList<>();

        for (HexTile t : hexes.values()) {
            TileType tt = t.getTileType();
            if (isWater(tt) || tt == TileType.ICE) {
                t.customZ = -1;
                continue;
            }

            int baseZ = t.getTileType().zHeight;
            boolean onBiomeEdge = false;
            BiomeType myBiome = t.getBiomeType();
            for (int[] d : DIRS) {
                HexTile n = getFromArray(t.q + d[0], t.r + d[1]);
                if (n != null && n.getBiomeType() != myBiome && !isWater(n.getTileType()) && n.getTileType() != TileType.ICE) {
                    onBiomeEdge = true;
                    break;
                }
            }

            if (onBiomeEdge) {
                biomeDist.put(t, 0);
                distQueue.add(t);
            }
        }

        while (!distQueue.isEmpty()) {
            HexTile curr = distQueue.poll();
            int currentDist = biomeDist.get(curr);

            for (int[] d : DIRS) {
                HexTile n = getFromArray(curr.q + d[0], curr.r + d[1]);
                if (n != null && !isWater(n.getTileType()) && n.getTileType() != TileType.ICE && !biomeDist.containsKey(n)) {
                    biomeDist.put(n, currentDist + 1);
                    distQueue.add(n);
                }
            }
        }

        int maxDist = biomeDist.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        final float finalMaxDist = maxDist;

        iterateArray(tile -> {
            TileType tt = tile.getTileType();
            if (isWater(tt) || tt == TileType.ICE) return;

            int baseZ = tile.getTileType().zHeight;
            int dist = biomeDist.getOrDefault(tile, 0);
            float distRatio = finalMaxDist > 0 ? (float) dist / finalMaxDist : 0f;

            if (tt == TileType.ROCK) {
                double n1 = slopeNoise.noise(tile.q * 0.06, tile.r * 0.06);
                double n2 = slopeNoise.noise(tile.q * 0.15, tile.r * 0.15) * 0.5;
                double n3 = slopeNoise.noise(tile.q * 0.3, tile.r * 0.3) * 0.25;
                double n4 = jaggedNoise.noise(tile.q * 0.5, tile.r * 0.5) * 0.12;
                double noiseVal = (n1 + n2 + n3 + n4) / 1.87;
                int baseVariation = (int) Math.round(noiseVal * 18);
                int distBoost = (int) (distRatio * 30);
                tile.customZ = Math.max(15, Math.min(70, baseZ + baseVariation + distBoost));
            } else if (tt == TileType.TREES || tile.getBiomeType() == BiomeType.TAIGA) {
                double macro = macroNoise.noise(tile.q * 0.02, tile.r * 0.02) * 3;
                double micro = slopeNoise.noise(tile.q * 0.15, tile.r * 0.15) * 2;
                tile.customZ = baseZ + (int) Math.round(macro + micro);
            } else {
                double macro = macroNoise.noise(tile.q * 0.015, tile.r * 0.015) * 4;
                double micro = slopeNoise.noise(tile.q * 0.1, tile.r * 0.1) * 1.5;
                tile.customZ = baseZ + (int) Math.round(macro + micro);
            }
        });

        for (int pass = 0; pass < 3; pass++) {
            Map<HexTile, Integer> newZ = new ConcurrentHashMap<>();
            iterateArray(tile -> {
                TileType tt = tile.getTileType();
                if (isWater(tt) || tt == TileType.ICE) return;
                if (tt == TileType.ROCK) return;

                int selfZ = tile.customZ;
                int maxNeighborZ = Integer.MIN_VALUE;
                int minNeighborZ = Integer.MAX_VALUE;
                int neighborCount = 0;

                for (int[] d : DIRS) {
                    HexTile n = getFromArray(tile.q + d[0], tile.r + d[1]);
                    if (n != null) {
                        TileType ntt = n.getTileType();
                        if (!isWater(ntt) && ntt != TileType.ICE && ntt != TileType.ROCK) {
                            int nZ = n.customZ;
                            if (nZ > maxNeighborZ) maxNeighborZ = nZ;
                            if (nZ < minNeighborZ) minNeighborZ = nZ;
                            neighborCount++;
                        }
                    }
                }

                if (neighborCount == 0) return;

                int targetZ = selfZ;
                if (selfZ > maxNeighborZ + 2) {
                    targetZ = maxNeighborZ + 2;
                } else if (selfZ < minNeighborZ - 2) {
                    targetZ = minNeighborZ - 2;
                }

                if (targetZ != selfZ) {
                    newZ.put(tile, targetZ);
                }
            });

            for (Map.Entry<HexTile, Integer> e : newZ.entrySet()) {
                e.getKey().customZ = e.getValue();
            }

            if (newZ.isEmpty()) break;
        }
    }

    public static void calculateShadows() {
        hexes.values().parallelStream().forEach(t -> t.clearShadows());

        final double sunVecX = 0.8;
        final double sunVecY = 0.6;

        hexes.values().parallelStream().forEach(caster -> {
            int cz = caster.getZ();
            TileType ct = caster.getTileType();
            if (cz > 0 && (ct == TileType.ROCK || ct == TileType.TREES || caster.getBiomeType() == BiomeType.TAIGA)) {
                int dist = rockDistToGrass.getOrDefault(caster, 1);
                double multiplier = dist * 0.75;

                int searchRadius = Math.min(20, Math.max(1, (int)Math.ceil(((double)cz / CoordinateConverter.HEX_SIZE) * multiplier) + 2));
                int cpX = CoordinateConverter.getPixelX(caster.q, caster.r);
                int cpY = CoordinateConverter.getPixelY(caster.q, caster.r);

                for (int dq = -searchRadius; dq <= searchRadius; dq++) {
                    for (int dr = -searchRadius; dr <= searchRadius; dr++) {
                        HexTile receiver = hexes.get(packKey(caster.q + dq, caster.r + dr));

                        if (receiver != null && receiver.getZ() < cz && !isSolidTile(receiver.getTileType())) {
                            int rpX = CoordinateConverter.getPixelX(receiver.q, receiver.r);
                            int rpY = CoordinateConverter.getPixelY(receiver.q, receiver.r);

                            if (rpY < cpY - 15 || rpX < cpX - 15) continue;

                            int deltaZ = cz - receiver.getZ();
                            int scaledDeltaZ = (int)(deltaZ * multiplier);

                            Polygon shadowPoly = generateShadowFootprintPolygon(cpX, cpY, cz, scaledDeltaZ, sunVecX, sunVecY);
                            Rectangle shadowBounds = shadowPoly.getBounds();
                            Rectangle recApprox = new Rectangle(rpX - 30, rpY - receiver.getZ() - 30, 60, receiver.getZ() + 60);

                            if (shadowBounds.intersects(recApprox)) {
                                synchronized (receiver) {
                                    receiver.addShadow(cpX, cpY, scaledDeltaZ, cz);
                                }
                            }
                        }
                    }
                }
            }
        });

        castTownHallShadowsStatic();

        rebuildShadowsStatic();
    }

    private static boolean isSolidTile(TileType t) {
        return t == TileType.ROCK;
    }

    private static void castTownHallShadowsStatic() {
        int thHeight = TownHall.TOTAL_HEIGHT;
        int hexSize = CoordinateConverter.HEX_SIZE;
        int stepPx = hexSize;
        int maxSteps = thHeight / hexSize * 7;
        final double sunDirX = 0.8;
        final double sunDirY = 0.6;

        for (long key : townhallFootprints) {
            HexTile caster = hexes.get(key);
            if (caster == null) continue;

            int cpX = CoordinateConverter.getPixelX(caster.q, caster.r);
            int cpY = CoordinateConverter.getPixelY(caster.q, caster.r);

            for (int step = 1; step <= maxSteps; step++) {
                double rayX = cpX + sunDirX * stepPx * step;
                double rayY = cpY + sunDirY * stepPx * step;

                Point hex = CoordinateConverter.pixelToHex((int)rayX, (int)rayY);
                HexTile receiver = hexes.get(packKey(hex.x, hex.y));
                if (receiver == null) continue;

                double rayHeight = thHeight - (step * hexSize * 0.6);
                if (rayHeight <= receiver.getZ()) break;

                int deltaZ = (int)(rayHeight * 0.5);
                synchronized (receiver) {
                    receiver.addShadow(cpX, cpY, deltaZ, thHeight);
                }
            }
        }
    }

    private static void rebuildShadowsStatic() {
        Area collectiveShadow = new Area();
        final double sunVecX = 0.8;
        final double sunVecY = 0.6;

        hexes.values().parallelStream().forEach(t -> {
            if (t.hasShadows()) {
                Area union = new Area();
                int count = t.getShadowCount();
                for (int i = 0; i < count; i++) {
                    int cx = t.getShadowField(i, 0);
                    int cy = t.getShadowField(i, 1);
                    int deltaZ = t.getShadowField(i, 2);
                    int casterZ = t.getShadowField(i, 3);
                    union.add(new Area(generateShadowFootprintPolygon(cx, cy, casterZ, deltaZ, sunVecX, sunVecY)));
                }
                int pX = CoordinateConverter.getPixelX(t.q, t.r);
                int pY = CoordinateConverter.getPixelY(t.q, t.r);
                Area tileArea = new Area(get3DHexPolygon(pX, pY, t.getZ()));
                union.intersect(tileArea);
                t.compiledShadow = union;

                synchronized (collectiveShadow) {
                    collectiveShadow.add(union);
                }
            } else {
                t.compiledShadow = null;
            }
        });

        collectiveShadowCache = collectiveShadow;
    }

    private static void applyBiomeFeature(HexTile tile, double finalFeature, Random rand) {
        BiomeType biome = tile.getBiomeType();
        switch (biome) {
            case TUNDRA:
                if (finalFeature > 0.20) {
                    tile.setTileType(TileType.ROCK);
                    tile.setBiomeType(BiomeType.NONE);
                }
                break;

            case TAIGA:
                if (finalFeature < -0.15) {
                    tile.setTileType(TileType.TREES);
                    tile.setBiomeType(BiomeType.NONE);
                } else if (finalFeature >= -0.15 && finalFeature < 0.05) {
                    double densityFactor = (0.05 - finalFeature) / 0.20;
                    double scatterChance = Math.pow(densityFactor, 1.5) * 0.65;
                    if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() < scatterChance) {
                        tile.setTileType(TileType.TREES);
                        tile.setBiomeType(BiomeType.NONE);
                    }
                }
                break;

            case DESERT:
                if (finalFeature > 0.30) {
                    tile.setTileType(TileType.ROCK);
                    tile.setBiomeType(BiomeType.NONE);
                }
                break;

            case SAVANNAH:
                if (finalFeature < -0.20) {
                    tile.setTileType(TileType.TREES);
                    tile.setBiomeType(BiomeType.NONE);
                } else if (finalFeature >= -0.20 && finalFeature < 0.10) {
                    double densityFactor = (0.10 - finalFeature) / 0.30;
                    double scatterChance = Math.pow(densityFactor, 1.5) * 0.30;
                    if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() < scatterChance) {
                        tile.setTileType(TileType.TREES);
                        tile.setBiomeType(BiomeType.NONE);
                    }
                }
                break;

            default:
                if (finalFeature < -0.35) {
                    tile.setTileType(TileType.ROCK);
                    tile.setBiomeType(BiomeType.NONE);
                } else if (finalFeature < -0.10) {
                    tile.setTileType(TileType.TREES);
                    tile.setBiomeType(BiomeType.NONE);
                } else if (finalFeature >= -0.10 && finalFeature < 0.20) {
                    double densityFactor = (0.20 - finalFeature) / 0.30;
                    double scatterChance = Math.pow(densityFactor, 1.8) * 0.50;
                    if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() < scatterChance) {
                        tile.setTileType(TileType.TREES);
                        tile.setBiomeType(BiomeType.NONE);
                    }
                }
                break;
        }
    }

    public static Area getCollectiveShadow() {
        return collectiveShadowCache;
    }

    private static volatile Area collectiveShadowCache = new Area();

    private static Polygon get3DHexPolygon(int cx, int cy, int z) {
        int baseSize = CoordinateConverter.HEX_SIZE;
        int topSize = baseSize;

        if (z > 25) {
            int tiers = (z - 25) / 15;
            topSize = Math.max(12, topSize - (tiers * 5));
        }

        int[] tx = new int[6], ty = new int[6], bx = new int[6], by = new int[6];
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 3 * i;
            tx[i] = cx + (int)(topSize * Math.cos(a));
            ty[i] = cy + (int)(topSize * Math.sin(a)) - z;
            bx[i] = cx + (int)(baseSize * Math.cos(a));
            by[i] = cy + (int)(baseSize * Math.sin(a));
        }

        Polygon p = new Polygon();
        if (z > 0) {
            int[] sqx = { tx[0], tx[5], tx[4], tx[3], bx[3], bx[2], bx[1], bx[0] };
            int[] sqy = { ty[0], ty[5], ty[4], ty[3], by[3], by[2], by[1], by[0] };
            for (int i = 0; i < 8; i++) p.addPoint(sqx[i], sqy[i]);
        } else {
            for (int i = 0; i < 6; i++) p.addPoint(tx[i], ty[i]);
        }
        return p;
    }

    // OPTIMIZATION: Zero-allocation footprint generation removes thousands of Point objects
    private static Polygon generateShadowFootprintPolygon(int cx, int cy, int casterZ, int deltaZ, double sunVecX, double sunVecY) {
        int shadowOffsetX = (int)(deltaZ * sunVecX);
        int shadowOffsetY = (int)(deltaZ * sunVecY);
        int baseSize = CoordinateConverter.HEX_SIZE;

        int[] px = new int[12];
        int[] py = new int[12];

        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 3 * i;
            px[i] = cx + (int)(baseSize * Math.cos(a));
            py[i] = cy + (int)(baseSize * Math.sin(a));
            px[i+6] = cx + (int)(baseSize * Math.cos(a)) + shadowOffsetX;
            py[i+6] = cy + (int)(baseSize * Math.sin(a)) + shadowOffsetY;
        }

        return buildConvexHull(px, py, 12);
    }

    // OPTIMIZATION: Jarvis march algorithm executing entirely on primitive arrays to evade GC overhead
    private static Polygon buildConvexHull(int[] px, int[] py, int n) {
        int l = 0;
        for (int i = 1; i < n; i++) {
            if (px[i] < px[l]) l = i;
        }

        List<Integer> hullIndices = new ArrayList<>();
        int p = l, q;
        do {
            hullIndices.add(p);
            q = (p + 1) % n;
            for (int i = 0; i < n; i++) {
                int val = (py[i] - py[p]) * (px[q] - px[i]) - (px[i] - px[p]) * (py[q] - py[i]);
                if (val < 0) q = i;
            }
            p = q;
        } while (p != l);

        Polygon poly = new Polygon();
        for (int idx : hullIndices) poly.addPoint(px[idx], py[idx]);
        return poly;
    }

    // OPTIMIZATION: Primitive shuffling evades boxing/unboxing overhead
    private static class Perlin2D {
        private final int[] p = new int[512];
        public Perlin2D(long seed) {
            Random r = new Random(seed);
            int[] arr = new int[256];
            for (int i = 0; i < 256; i++) arr[i] = i;
            for (int i = 255; i > 0; i--) {
                int index = r.nextInt(i + 1);
                int temp = arr[index]; arr[index] = arr[i]; arr[i] = temp;
            }
            for (int i = 0; i < 256; i++) {
                p[i] = arr[i];
                p[256 + i] = arr[i];
            }
        }
        public double noise(double x, double y) {
            int X = (int) Math.floor(x) & 255, Y = (int) Math.floor(y) & 255;
            x -= Math.floor(x); y -= Math.floor(y);
            double u = fade(x), v = fade(y);
            int A = p[X] + Y, B = p[X + 1] + Y;
            return lerp(v, lerp(u, grad(p[A], x, y), grad(p[B], x - 1, y)),
                    lerp(u, grad(p[A + 1], x, y - 1), grad(p[B + 1], x - 1, y - 1)));
        }
        private double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
        private double lerp(double t, double a, double b) { return a + t * (b - a); }
        private double grad(int hash, double x, double y) {
            int h = hash & 7;
            double u = h < 4 ? x : y, v = h < 4 ? y : x;
            return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? 2.0 * v : -2.0 * v);
        }
    }

    public static void clearGridOccupancy() { gridOccupied.clear(); }
    public static void markHex(int q, int r, Unit u) {
        long key = packKey(q, r);
        if (u == null) gridOccupied.remove(key);
        else gridOccupied.put(key, u);
    }
    public static Unit getUnitAt(int q, int r) { return gridOccupied.get(packKey(q, r)); }
    public static boolean isHexOccupied(int q, int r) { return getUnitAt(q, r) != null; }
    public static boolean isHexOccupiedByOther(int q, int r, Unit self) {
        Unit u = getUnitAt(q, r); return u != null && u != self;
    }
    public static boolean isOutOfBounds(int q, int r) {
        int cols = CoordinateConverter.MAP_COLS;
        int rows = CoordinateConverter.MAP_ROWS;
        int rOffset = q >> 1;
        return q < 0 || q >= cols || r < -rOffset || r >= rows - rOffset;
    }
    public static boolean isBlockedForPath(int q, int r, int sQ, int sR, int tQ, int tR) {
        if (isOutOfBounds(q, r)) return true;
        long key = packKey(q, r);
        if (townhallFootprints.contains(key)) return true;
        HexTile tile = hexes.get(key);
        if (tile != null) { TileType tt = tile.getTileType(); if (isWater(tt) || tt == TileType.ROCK || tt == TileType.TREES) return true; }
        if ((q == sQ && r == sR) || (q == tQ && r == tR)) return false;
        Unit u = getUnitAt(q, r); return u != null && u.currentOrder == null;
    }
}
import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Deque;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;

public class PathFinder {
    private static final int[][] DIRS = { {1,0},{1,-1},{0,-1},{-1,0},{-1,1},{0,1} };
    private static final int MAX_PATH_CONCURRENT = 4;
    private static final int MAX_ITERATIONS = 800;
    private static final int MAX_BFS_STEPS = 600;

    private static final Semaphore pathSemaphore = new Semaphore(MAX_PATH_CONCURRENT);

    private static final ExecutorService pathExecutor = Executors.newFixedThreadPool(
            2,
            new ThreadFactory() {
                private int count;
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "PathFinder-" + (count++));
                    t.setDaemon(true);
                    t.setPriority(Thread.NORM_PRIORITY - 1);
                    return t;
                }
            }
    );

    private static final class LongSet {
        private long[] keys;
        private boolean[] used;
        private int size;
        LongSet(int cap) {
            int n = 1; while (n < cap) n <<= 1;
            keys = new long[n]; used = new boolean[n]; size = 0;
        }
        private int probe(long k) {
            int mask = keys.length - 1;
            int i = (int)(k ^ (k >>> 32)) & mask;
            while (used[i] && keys[i] != k) i = (i + 1) & mask;
            return i;
        }
        boolean contains(long k) { int i = probe(k); return used[i] && keys[i] == k; }
        boolean add(long k) { int i = probe(k); if (used[i]) return false; keys[i] = k; used[i] = true; size++; return true; }
        int size() { return size; }
    }

    private static final class LongIntMap {
        private long[] keys;
        private int[] vals;
        private boolean[] used;
        private final int NULL = Integer.MIN_VALUE;
        LongIntMap(int cap) {
            int n = 1; while (n < cap) n <<= 1;
            keys = new long[n]; vals = new int[n]; used = new boolean[n];
            for (int i = 0; i < n; i++) vals[i] = NULL;
        }
        private int probe(long k) {
            int mask = keys.length - 1;
            int i = (int)(k ^ (k >>> 32)) & mask;
            while (used[i] && keys[i] != k) i = (i + 1) & mask;
            return i;
        }
        int get(long k) { int i = probe(k); return used[i] && keys[i] == k ? vals[i] : NULL; }
        void put(long k, int v) {
            int i = probe(k);
            if (!used[i]) { keys[i] = k; used[i] = true; }
            vals[i] = v;
        }
        boolean containsKey(long k) { int i = probe(k); return used[i] && keys[i] == k; }
    }

    private static final class NodePool {
        private final int[] q, r, g, h, f, parentIdx;
        private final double[] lineDeviation;
        private int top = 0;
        NodePool(int cap) {
            q = new int[cap]; r = new int[cap]; g = new int[cap];
            h = new int[cap]; f = new int[cap]; parentIdx = new int[cap];
            lineDeviation = new double[cap];
        }
        void reset() { top = 0; }
        int alloc() {
            if (top >= q.length) return -1;
            int idx = top++;
            parentIdx[idx] = -1;
            return idx;
        }
    }

    private static final class IntMinHeap {
        private final int[] heap;
        private int size = 0;
        IntMinHeap(int cap) { heap = new int[cap]; }
        void reset() { size = 0; }
        void push(int idx, int priority) {
            if (size >= heap.length) return;
            heap[size] = idx;
            int i = size++;
            while (i > 0) {
                int parent = (i - 1) >>> 1;
                if (heap[parent] <= idx) break;
                heap[i] = heap[parent];
                i = parent;
            }
            heap[i] = idx;
        }
        int pop() {
            if (size == 0) return -1;
            int result = heap[0];
            int last = heap[--size];
            int i = 0;
            while ((i << 1) + 1 < size) {
                int left = (i << 1) + 1;
                int right = left + 1;
                int smaller = (right < size && heap[right] < heap[left]) ? right : left;
                if (heap[smaller] >= last) break;
                heap[i] = heap[smaller];
                i = smaller;
            }
            heap[i] = last;
            return result;
        }
        boolean isEmpty() { return size == 0; }
    }

    public static CompletableFuture<Deque<Point>> requestAsyncPath(int sQ, int sR, int tQ, int tR, Unit unit, Point avoidedHex) {
        return CompletableFuture.supplyAsync(() -> {
            if (!pathSemaphore.tryAcquire()) return null;
            try {
                Deque<Point> direct = computePathBetween(sQ, sR, tQ, tR, avoidedHex, unit);
                if (direct != null && !direct.isEmpty()) return direct;

                Point best = findBestReachableHex(sQ, sR, tQ, tR, unit);
                if (best == null || (best.x == sQ && best.y == sR)) return null;
                return computePathBetween(sQ, sR, best.x, best.y, avoidedHex, unit);
            } finally {
                pathSemaphore.release();
            }
        }, pathExecutor);
    }

    public static Deque<Point> computePathBetween(int sQ, int sR, int tQ, int tR, Point avoidedHex, Unit unit) {
        if (sQ == tQ && sR == tR) return new ArrayDeque<>();

        int startPxX = CoordinateConverter.getPixelX(sQ, sR);
        int startPxY = CoordinateConverter.getPixelY(sQ, sR);
        int targetPxX = CoordinateConverter.getPixelX(tQ, tR);
        int targetPxY = CoordinateConverter.getPixelY(tQ, tR);
        double dxGoal = targetPxX - startPxX;
        double dyGoal = targetPxY - startPxY;

        int poolCap = MAX_ITERATIONS + 256;
        NodePool pool = new NodePool(poolCap);
        IntMinHeap openHeap = new IntMinHeap(poolCap);
        LongIntMap openF = new LongIntMap(poolCap);
        LongIntMap closedG = new LongIntMap(poolCap);
        LongIntMap parentMap = new LongIntMap(poolCap);

        int startH = HexMath.hexDistance(sQ, sR, tQ, tR);
        int startIdx = pool.alloc();
        if (startIdx < 0) return null;
        pool.q[startIdx] = sQ; pool.r[startIdx] = sR;
        pool.g[startIdx] = 0; pool.h[startIdx] = startH;
        pool.f[startIdx] = startH; pool.lineDeviation[startIdx] = 0.0;

        long startKey = GridManager.packKey(sQ, sR);
        openF.put(startKey, startH);
        parentMap.put(startKey, -1);
        openHeap.push(startIdx, startH);

        int goalIdx = -1;
        int iterations = 0;

        while (!openHeap.isEmpty() && iterations < MAX_ITERATIONS) {
            iterations++;
            int curIdx = openHeap.pop();
            if (curIdx < 0) break;
            int curQ = pool.q[curIdx], curR = pool.r[curIdx];
            int curG = pool.g[curIdx];
            long packedCurKey = GridManager.packKey(curQ, curR);

            if (closedG.containsKey(packedCurKey)) continue;
            closedG.put(packedCurKey, curG);

            if (curQ == tQ && curR == tR) {
                goalIdx = curIdx;
                break;
            }

            for (int[] d : DIRS) {
                int nq = curQ + d[0];
                int nr = curR + d[1];

                if (avoidedHex != null && nq == avoidedHex.x && nr == avoidedHex.y) continue;

                int searchQ = nq, searchR = nr;
                int jumpCost = 1;

                if (GridManager.isBlockedForPath(nq, nr, sQ, sR, tQ, tR)) {
                    if (unit != null && unit.type == UnitType.CAVALRY) {
                        int landingQ = nq + d[0];
                        int landingR = nr + d[1];
                        if (avoidedHex != null && landingQ == avoidedHex.x && landingR == avoidedHex.y) continue;
                        if (!GridManager.isBlockedForPath(landingQ, landingR, sQ, sR, tQ, tR)) {
                            searchQ = landingQ; searchR = landingR; jumpCost = 2;
                        } else {
                            continue;
                        }
                    } else {
                        continue;
                    }
                }

                long packedNeighborKey = GridManager.packKey(searchQ, searchR);
                if (closedG.containsKey(packedNeighborKey)) continue;

                int g = curG + jumpCost;
                int existingF = openF.get(packedNeighborKey);
                if (existingF != Integer.MIN_VALUE) continue;

                int h = HexMath.hexDistance(searchQ, searchR, tQ, tR);
                int f = g + h;
                int nodePxX = CoordinateConverter.getPixelX(searchQ, searchR);
                int nodePxY = CoordinateConverter.getPixelY(searchQ, searchR);
                double dxNode = nodePxX - startPxX, dyNode = nodePxY - startPxY;
                double lineDeviation = Math.abs(dxNode * dyGoal - dxGoal * dyNode);

                int neighborIdx = pool.alloc();
                if (neighborIdx < 0) continue;
                pool.q[neighborIdx] = searchQ; pool.r[neighborIdx] = searchR;
                pool.g[neighborIdx] = g; pool.h[neighborIdx] = h;
                pool.f[neighborIdx] = f; pool.lineDeviation[neighborIdx] = lineDeviation;
                pool.parentIdx[neighborIdx] = curIdx;

                openF.put(packedNeighborKey, f);
                parentMap.put(packedNeighborKey, curIdx);
                openHeap.push(neighborIdx, f);
            }
        }

        if (goalIdx < 0) return null;

        int[] pathQ = new int[MAX_ITERATIONS];
        int[] pathR = new int[MAX_ITERATIONS];
        int pathLen = 0;
        int cur = goalIdx;
        while (cur >= 0 && pathLen < MAX_ITERATIONS) {
            pathQ[pathLen] = pool.q[cur];
            pathR[pathLen] = pool.r[cur];
            pathLen++;
            cur = pool.parentIdx[cur];
        }

        Deque<Point> result = new ArrayDeque<>(pathLen);
        for (int i = pathLen - 1; i >= 0; i--) {
            result.add(new Point(pathQ[i], pathR[i]));
        }
        return result;
    }

    public static Point findBestReachableHex(int sQ, int sR, int tQ, int tR, Unit unit) {
        int[] qQueue = new int[MAX_BFS_STEPS];
        int[] rQueue = new int[MAX_BFS_STEPS];
        int head = 0, tail = 0;

        LongSet vis = new LongSet(MAX_BFS_STEPS);
        qQueue[tail] = sQ; rQueue[tail] = sR; tail++;
        vis.add(GridManager.packKey(sQ, sR));

        int bestQ = sQ, bestR = sR;
        int bestD = HexMath.hexDistance(sQ, sR, tQ, tR);

        int bfsLimit = 0;
        while (head < tail && bfsLimit < MAX_BFS_STEPS) {
            bfsLimit++;
            int cq = qQueue[head], cr = rQueue[head]; head++;
            int d = HexMath.hexDistance(cq, cr, tQ, tR);
            if (d < bestD) { bestD = d; bestQ = cq; bestR = cr; }

            for (int[] dir : DIRS) {
                int nq = cq + dir[0], nr = cr + dir[1];
                if (GridManager.isOutOfBounds(nq, nr)) continue;

                long packedNeighborKey = GridManager.packKey(nq, nr);
                if (vis.contains(packedNeighborKey)) continue;

                if (GridManager.isHexOccupiedByOther(nq, nr, unit) || GridManager.transitOccupied.contains(packedNeighborKey)) {
                    if (unit != null && unit.type == UnitType.CAVALRY) {
                        int lq = nq + dir[0], lr = nr + dir[1];
                        if (!GridManager.isOutOfBounds(lq, lr)) {
                            long packedLandingKey = GridManager.packKey(lq, lr);
                            if (!vis.contains(packedLandingKey) && !GridManager.isHexOccupiedByOther(lq, lr, unit) && !GridManager.transitOccupied.contains(packedLandingKey)) {
                                vis.add(packedLandingKey);
                                if (tail < qQueue.length) { qQueue[tail] = lq; rQueue[tail] = lr; tail++; }
                            }
                        }
                    }
                    continue;
                }
                vis.add(packedNeighborKey);
                if (tail < qQueue.length) { qQueue[tail] = nq; rQueue[tail] = nr; tail++; }
            }
        }
        return new Point(bestQ, bestR);
    }

    public static Point findNearestFreeHex(int sQ, int sR, Unit exclude) {
        int[] qQueue = new int[MAX_BFS_STEPS];
        int[] rQueue = new int[MAX_BFS_STEPS];
        int head = 0, tail = 0;

        LongSet vis = new LongSet(MAX_BFS_STEPS);
        qQueue[tail] = sQ; rQueue[tail] = sR; tail++;
        vis.add(GridManager.packKey(sQ, sR));

        while (head < tail) {
            int cq = qQueue[head], cr = rQueue[head]; head++;
            long pKey = GridManager.packKey(cq, cr);
            if (!GridManager.isHexOccupiedByOther(cq, cr, exclude) && !GridManager.transitOccupied.contains(pKey)) {
                return new Point(cq, cr);
            }
            for (int[] d : DIRS) {
                int nq = cq + d[0], nr = cr + d[1];
                if (GridManager.isOutOfBounds(nq, nr)) continue;
                long packedNeighborKey = GridManager.packKey(nq, nr);
                if (!vis.contains(packedNeighborKey)) {
                    vis.add(packedNeighborKey);
                    if (tail < qQueue.length) { qQueue[tail] = nq; rQueue[tail] = nr; tail++; }
                }
            }
        }
        return null;
    }
}
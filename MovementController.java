import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class MovementController {

    private static final int[][] AXIS_STEPS  = { {1,0},{0,1},{1,-1} };
    private static final double[] AXIS_ANGLES = {
            Math.PI / 6.0, Math.PI / 2.0, -Math.PI / 6.0
    };

    // Reusable snapshot array — avoids new ArrayList<>(units) every tick
    private Unit[] unitSnapshot = new Unit[64];
    private int    snapshotSize = 0;

    // Reusable sort buffer (same contents as snapshot, sorted in place)
    private Unit[] sortBuffer = new Unit[64];

    // Reusable frameReserved — cleared each tick, never reallocated
    private final Set<Long> frameReserved = new HashSet<>(128);

    public void updateSimulation(double dt, List<Unit> units, Object unitsLock, Queue<Command> commandQueue) {
        if (!commandQueue.isEmpty()) {
            Command cmd = commandQueue.poll();
            synchronized (unitsLock) { cmd.execute(); }
        }

        // Snapshot under lock — avoids holding lock during expensive simulation
        synchronized (unitsLock) {
            int count = 0;
            for (Unit u : units) {
                if (count >= unitSnapshot.length) {
                    int newSize = Math.max(count + 32, unitSnapshot.length * 2);
                    unitSnapshot = new Unit[newSize];
                    sortBuffer   = new Unit[newSize];
                }
                unitSnapshot[count] = u;
                sortBuffer[count]   = u;
                count++;
            }
            snapshotSize = count;
        }

        // Single pass: occupancy + transit (was three separate loops)
        GridManager.clearGridOccupancy();
        GridManager.transitOccupied.clear();
        for (int i = 0; i < snapshotSize; i++) {
            Unit u = unitSnapshot[i];
            GridManager.markHex(u.q, u.r, u);
            if (u.movingSegment) {
                GridManager.transitOccupied.add(GridManager.packKey(u.toQ, u.toR));
            }
        }

        // Sort ALL units in ONE pass — closest-first for units with orders,
        // idle units last. Must be unified: splitting into two passes caused
        // frameReserved to be partially filled when idle units ran, producing
        // false transit conflicts that froze movement.
        insertionSort(sortBuffer, snapshotSize);

        frameReserved.clear();
        for (int i = 0; i < snapshotSize; i++) {
            sortBuffer[i].updateMovement(dt, frameReserved);
        }
    }

    private static void insertionSort(Unit[] arr, int count) {
        for (int i = 1; i < count; i++) {
            Unit key     = arr[i];
            int  keyDist = getSortKey(key);
            int  j       = i - 1;
            while (j >= 0 && getSortKey(arr[j]) > keyDist) {
                arr[j + 1] = arr[j];
                j--;
            }
            arr[j + 1] = key;
        }
    }

    private static int getSortKey(Unit u) {
        if (u.currentOrder == null) return Integer.MAX_VALUE;
        int tq = (u.formationTargetQ != null) ? u.formationTargetQ : u.currentOrder.targetQ;
        int tr = (u.formationTargetR != null) ? u.formationTargetR : u.currentOrder.targetR;
        return HexMath.hexDistance(u.q, u.r, tq, tr);
    }

    public void issueGroupMove(List<Unit> selectedUnits, int destQ, int destR, CommandType cmd) {
        if (selectedUnits == null || selectedUnits.isEmpty()) return;

        if (selectedUnits.size() == 1) {
            Unit u = selectedUnits.get(0);
            u.formationTargetQ = null;
            u.formationTargetR = null;
            u.applyNewOrder(new Order(cmd, destQ, destR));
            return;
        }

        double cx = 0, cy = 0;
        for (Unit u : selectedUnits) { cx += u.x; cy += u.y; }
        cx /= selectedUnits.size();
        cy /= selectedUnits.size();

        int destPxX = CoordinateConverter.getPixelX(destQ, destR);
        int destPxY = CoordinateConverter.getPixelY(destQ, destR);
        double dx   = destPxX - cx;
        double dy   = destPxY - cy;

        double approachAngle  = Math.atan2(dy, dx);
        double idealPerpAngle = approachAngle + (Math.PI / 2.0);

        int    bestAxisIndex = 0;
        double minAngleDiff  = Double.MAX_VALUE;
        for (int i = 0; i < AXIS_ANGLES.length; i++) {
            double diff = Math.min(
                    getAngleDiff(idealPerpAngle, AXIS_ANGLES[i]),
                    getAngleDiff(idealPerpAngle, AXIS_ANGLES[i] + Math.PI));
            if (diff < minAngleDiff) { minAngleDiff = diff; bestAxisIndex = i; }
        }

        int rankStepQ = AXIS_STEPS[bestAxisIndex][0];
        int rankStepR = AXIS_STEPS[bestAxisIndex][1];

        int[][] allDirs        = { {1,0},{1,-1},{0,-1},{-1,0},{-1,1},{0,1} };
        int     bestFileDirIdx = 0;
        double  maxDot         = -Double.MAX_VALUE;
        for (int i = 0; i < allDirs.length; i++) {
            int npX = CoordinateConverter.getPixelX(destQ + allDirs[i][0], destR + allDirs[i][1]);
            int npY = CoordinateConverter.getPixelY(destQ + allDirs[i][0], destR + allDirs[i][1]);
            double ndx = npX - destPxX, ndy = npY - destPxY;
            double len = Math.sqrt(ndx*ndx + ndy*ndy);
            double dot = ((ndx/len)*(-dx)) + ((ndy/len)*(-dy));
            if (dot > maxDot) { maxDot = dot; bestFileDirIdx = i; }
        }

        int fileStepQ = allDirs[bestFileDirIdx][0];
        int fileStepR = allDirs[bestFileDirIdx][1];

        List<Point> targetSlots  = new ArrayList<>();
        Set<Long>   assignedKeys = new HashSet<>();
        int totalUnits      = selectedUnits.size();
        int maxPerRank      = Math.min(7, Math.max(3, (int)Math.ceil(Math.sqrt(totalUnits)*1.3)));
        int fileRank        = 0;

        while (targetSlots.size() < totalUnits && fileRank < 15) {
            int anchorQ  = destQ + (fileRank * fileStepQ);
            int anchorR  = destR + (fileRank * fileStepR);
            int needed   = Math.min(maxPerRank, totalUnits - targetSlots.size());
            for (int i = 0; i < needed; i++) {
                int off   = (i%2==0) ? (i/2) : -((i+1)/2);
                int slotQ = anchorQ + (off * rankStepQ);
                int slotR = anchorR + (off * rankStepR);
                if (!GridManager.isOutOfBounds(slotQ, slotR)) {
                    long key = GridManager.packKey(slotQ, slotR);
                    if (assignedKeys.add(key)) {
                        targetSlots.add(new Point(slotQ, slotR));
                        if (targetSlots.size() == totalUnits) break;
                    }
                }
            }
            fileRank++;
        }

        if (targetSlots.size() < totalUnits) {
            Queue<Point> bfsQueue    = new ArrayDeque<>();
            Set<Long>    enqueued    = new HashSet<>(assignedKeys);
            bfsQueue.add(new Point(destQ, destR));
            enqueued.add(GridManager.packKey(destQ, destR));
            while (!bfsQueue.isEmpty() && targetSlots.size() < totalUnits) {
                Point curr = bfsQueue.poll();
                long  key  = GridManager.packKey(curr.x, curr.y);
                if (!assignedKeys.contains(key) && !GridManager.isOutOfBounds(curr.x, curr.y)) {
                    assignedKeys.add(key);
                    targetSlots.add(curr);
                }
                for (int[] d : allDirs) {
                    int nq = curr.x+d[0], nr = curr.y+d[1];
                    if (!GridManager.isOutOfBounds(nq, nr)) {
                        long nk = GridManager.packKey(nq, nr);
                        if (enqueued.add(nk)) bfsQueue.add(new Point(nq, nr));
                    }
                }
            }
        }

        int rsPxX      = CoordinateConverter.getPixelX(destQ+rankStepQ, destR+rankStepR);
        int rsPxY      = CoordinateConverter.getPixelY(destQ+rankStepQ, destR+rankStepR);
        double rvx       = rsPxX - destPxX, rvy = rsPxY - destPxY;
        double rLen      = Math.sqrt(rvx*rvx + rvy*rvy);
        double normRankX = rvx/rLen, normRankY = rvy/rLen;

        List<Unit> toAssign = new ArrayList<>(selectedUnits);
        toAssign.sort(Comparator.comparingDouble(u -> (u.x*normRankX + u.y*normRankY)));
        targetSlots.sort(Comparator.comparingDouble(s -> {
            int sx = CoordinateConverter.getPixelX(s.x, s.y);
            int sy = CoordinateConverter.getPixelY(s.x, s.y);
            return sx*normRankX + sy*normRankY;
        }));

        for (int i = 0; i < toAssign.size(); i++) {
            Unit  u = toAssign.get(i);
            Point s = targetSlots.get(i);
            u.formationTargetQ = s.x;
            u.formationTargetR = s.y;
            u.applyNewOrder(new Order(cmd, destQ, destR));
        }
    }

    private double getAngleDiff(double a1, double a2) {
        double d = Math.abs(a1-a2) % (Math.PI*2);
        return d > Math.PI ? (Math.PI*2)-d : d;
    }
}
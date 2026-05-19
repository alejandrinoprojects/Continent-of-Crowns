import java.awt.Point;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class Unit {
    public int q, r;
    public double x, y;
    double segmentStartX, segmentStartY;
    public boolean selected = false;
    public UnitType type;
    public boolean isPlayerOwned = true;
    public Order currentOrder = null;
    public boolean isHoldingPosition = false;
    public boolean atBestReachable = false;
    public boolean isWaitingForAsyncPath = false;
    private CompletableFuture<Deque<Point>> pendingPathFuture = null;
    public Integer formationTargetQ = null;
    public Integer formationTargetR = null;
    public boolean useCurrentPosAsSegmentStart = false;
    Deque<Point> path = new ArrayDeque<>();
    int toQ, toR;
    public boolean movingSegment = false;
    double toPixelX, toPixelY;
    double blockedTime = 0.0;
    public boolean isLeaping = false;
    public double leapProgress = 0.0;
    public double leapZ = 0.0;
    public double leapCooldown = 0.0;

    public Unit(int q, int r, UnitType type, boolean isPlayerOwned) {
        this.type = type;
        this.isPlayerOwned = isPlayerOwned;
        setHex(q, r);
    }

    public void setOwnership(boolean isPlayer) { this.isPlayerOwned = isPlayer; }

    public void prepareForQueuedOrder(Order order) {
        cancelPendingPath();
        this.currentOrder = order;
        this.isHoldingPosition = false;
        this.atBestReachable = false;
        path.clear();
        blockedTime = 0.0;
        if (!movingSegment) stopSegment();
    }

    private void cancelPendingPath() {
        if (pendingPathFuture != null) {
            pendingPathFuture.cancel(true);
            pendingPathFuture = null;
        }
        isWaitingForAsyncPath = false;
    }

    public void applyNewOrder(Order order) {
        cancelPendingPath();
        this.currentOrder = order;
        this.isHoldingPosition = false;
        this.atBestReachable = false;

        if (order.commandType == CommandType.HOLD_POSITION) {
            applyHoldPosition();
            return;
        }

        int targetQ = (formationTargetQ != null) ? formationTargetQ : order.targetQ;
        int targetR = (formationTargetR != null) ? formationTargetR : order.targetR;

        Unit blocker = GridManager.getUnitAt(targetQ, targetR);
        if (blocker != null && blocker != this && blocker.currentOrder == null) {
            Point free = PathFinder.findNearestFreeHex(targetQ, targetR, this);
            if (free != null) {
                targetQ = free.x; targetR = free.y;
                if (formationTargetQ != null) { formationTargetQ = free.x; formationTargetR = free.y; }
            }
        }

        path.clear();
        blockedTime = 0.0;
        int startQ = movingSegment ? toQ : this.q;
        int startR = movingSegment ? toR : this.r;

        if (!movingSegment) {
            Point nearest = CoordinateConverter.pixelToHex((int) Math.round(x), (int) Math.round(y));
            startQ = nearest.x; startR = nearest.y;
            stopSegment();
            useCurrentPosAsSegmentStart = true;
        }

        isWaitingForAsyncPath = true;
        pendingPathFuture = PathFinder.requestAsyncPath(startQ, startR, targetQ, targetR, this, null);
    }

    public void applyHoldPosition() {
        cancelPendingPath();
        this.currentOrder = null;
        this.isHoldingPosition = true;
        this.atBestReachable = false;
        this.formationTargetQ = null;
        this.formationTargetR = null;
        path.clear();
        stopSegment();
        blockedTime = 0.0;
        snapToCenter();
    }

    void setHex(int q, int r) {
        this.q = q; this.r = r;
        this.x = CoordinateConverter.getPixelX(q, r);
        this.y = CoordinateConverter.getPixelY(q, r);
        GridManager.markHex(q, r, this);
    }

    void snapToCenter() {
        this.x = CoordinateConverter.getPixelX(q, r);
        this.y = CoordinateConverter.getPixelY(q, r);
        this.leapZ = 0.0;
    }

    void stopSegment() {
        movingSegment = false;
        isLeaping = false;
        leapProgress = 0.0;
        leapZ = 0.0;
    }

    void startSegment(int nextQ, int nextR, boolean leaping) {
        toQ = nextQ; toR = nextR;
        toPixelX = CoordinateConverter.getPixelX(nextQ, nextR);
        toPixelY = CoordinateConverter.getPixelY(nextQ, nextR);
        movingSegment = true;
        isLeaping = leaping;
        leapProgress = 0.0;
        leapZ = 0.0;

        if (useCurrentPosAsSegmentStart) {
            segmentStartX = x; segmentStartY = y;
            useCurrentPosAsSegmentStart = false;
        } else {
            segmentStartX = CoordinateConverter.getPixelX(q, r);
            segmentStartY = CoordinateConverter.getPixelY(q, r);
            x = segmentStartX; y = segmentStartY;
        }
    }

    public void updateMovement(double dt, Set<Long> frameReserved) {
        if (leapCooldown > 0) leapCooldown -= dt;

        if (isWaitingForAsyncPath && pendingPathFuture != null) {
            if (pendingPathFuture.isDone()) {
                try {
                    Deque<Point> newPath = pendingPathFuture.get();
                    if (newPath != null && !newPath.isEmpty()) {
                        path.clear();
                        for (Point p : newPath) if (!(p.x == this.q && p.y == this.r)) path.addLast(p);
                    } else atBestReachable = true;
                } catch (Exception e) { e.printStackTrace(); }
                pendingPathFuture = null; isWaitingForAsyncPath = false;
            } else {
                if (movingSegment) {
                    processPixelInterpolation(dt);
                    long destKey = GridManager.packKey(toQ, toR);
                    frameReserved.add(destKey);
                    GridManager.transitOccupied.add(destKey);
                }
                return;
            }
        }

        if (isHoldingPosition) { stopSegment(); return; }

        if (atBestReachable) {
            if (currentOrder == null) { stopSegment(); snapToCenter(); return; }
            recomputePath(null);
            if (path.isEmpty()) { stopSegment(); snapToCenter(); return; }
            atBestReachable = false;
        }

        if (currentOrder == null && path.isEmpty()) { stopSegment(); snapToCenter(); return; }

        if (!movingSegment && !path.isEmpty()) {
            Point next = path.peekFirst();
            int nextQ = next.x, nextR = next.y;
            long destKey = GridManager.packKey(nextQ, nextR);

            boolean isPlannedVault = (this.type == UnitType.CAVALRY && HexMath.hexDistance(this.q, this.r, nextQ, nextR) == 2);
            boolean occupiedByOther = GridManager.isHexOccupiedByOther(nextQ, nextR, this);
            boolean alreadyReserved = frameReserved.contains(destKey);
            boolean transitConflict = GridManager.transitOccupied.contains(destKey);

            if (isPlannedVault) {
                if (occupiedByOther || alreadyReserved || transitConflict || leapCooldown > 0) {
                    blockedTime += dt;
                    if (currentOrder != null && blockedTime > 0.5) {
                        blockedTime = 0.0;
                        recomputePath(new Point(nextQ, nextR));
                    }
                    return;
                }
                blockedTime = 0.0;
                frameReserved.add(destKey);
                GridManager.transitOccupied.add(destKey);
                startSegment(nextQ, nextR, true);
                leapCooldown = 10.0;
                return;
            }

            if (occupiedByOther || alreadyReserved || transitConflict) {
                blockedTime += dt;
                if (currentOrder != null && blockedTime > 0.5) {
                    blockedTime = 0.0;
                    recomputePath(new Point(nextQ, nextR));
                }
                return;
            }
            blockedTime = 0.0;
            frameReserved.add(destKey);
            GridManager.transitOccupied.add(destKey);
            startSegment(nextQ, nextR, false);
        }

        if (movingSegment) processPixelInterpolation(dt);
    }

    private void processPixelInterpolation(double dt) {
        double dx = toPixelX - x, dy = toPixelY - y;
        double dist = Math.sqrt(dx * dx + dy * dy);
        double speed = type.speed;

        if (isLeaping) {
            speed *= 1.35;
            double fullDist = Math.sqrt((toPixelX - segmentStartX)*(toPixelX - segmentStartX) + (toPixelY - segmentStartY)*(toPixelY - segmentStartY));
            if (fullDist > 0.001) {
                leapProgress = 1.0 - (dist / fullDist);
                leapZ = Math.sin(leapProgress * Math.PI) * (CoordinateConverter.HEX_SIZE * 1.5);
            }
        }

        double step = (speed * (CoordinateConverter.HEX_SIZE * Math.sqrt(3))) * dt;
        if (dist <= step || dist < 1.0) {
            x = toPixelX; y = toPixelY;
            GridManager.transitOccupied.remove(GridManager.packKey(toQ, toR));
            if (GridManager.getUnitAt(q, r) == this) GridManager.markHex(q, r, null);
            GridManager.markHex(toQ, toR, this);
            q = toQ; r = toR;
            if (!path.isEmpty() && path.peekFirst().x == toQ && path.peekFirst().y == toR) path.pollFirst();
            stopSegment();
            int finalTargetQ = (formationTargetQ != null) ? formationTargetQ : (currentOrder != null ? currentOrder.targetQ : q);
            int finalTargetR = (formationTargetR != null) ? formationTargetR : (currentOrder != null ? currentOrder.targetR : r);
            if (q == finalTargetQ && r == finalTargetR) {
                currentOrder = null; formationTargetQ = null; formationTargetR = null;
                path.clear(); snapToCenter();
            }
        } else {
            x += (dx / dist) * step;
            y += (dy / dist) * step;
        }
    }



    private void recomputePath(Point avoidedHex) {
        if (currentOrder == null) { atBestReachable = true; return; }
        int targetQ = (formationTargetQ != null) ? formationTargetQ : currentOrder.targetQ;
        int targetR = (formationTargetR != null) ? formationTargetR : currentOrder.targetR;
        isWaitingForAsyncPath = true;
        pendingPathFuture = PathFinder.requestAsyncPath(this.q, this.r, targetQ, targetR, this, avoidedHex);
    }
}
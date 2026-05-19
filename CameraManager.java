import java.awt.Point;

public class CameraManager {
    public int offsetX = 0;
    public int offsetY = 0;
    public double zoom = 1.0;
    private volatile int shadowsReadyForTod = -1;
    private int lastMouseX, lastMouseY;
    private boolean isPanning;

    public void setShadowsReadyForTod(int todIndex) {
        this.shadowsReadyForTod = todIndex;
    }

    public void centerOn(int mapW, int mapH, int screenW, int screenH) {
        offsetX = - (mapW / 2 - screenW / 2);
        offsetY = - (mapH / 2 - screenH / 2);
        zoom = 1.0;
    }

    public void startPanning(int screenX, int screenY) {
        lastMouseX = screenX;
        lastMouseY = screenY;
        isPanning = true;
    }

    public void pan(int mouseX, int mouseY, int maxW, int maxH, int screenW, int screenH) {
        if (isPanning) {
            int dx = (int)((mouseX - lastMouseX) / zoom);
            int dy = (int)((mouseY - lastMouseY) / zoom);

            offsetX += dx;
            offsetY += dy;

            clampOffsets(screenW, screenH, maxW, maxH);

            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }
    }

    public void stopPanning() {
        isPanning = false;
    }

    /**
     * NEW: Focal point zoom manager. Recalculates camera offsets on the fly
     * so zooming anchors beautifully to the center of your monitor instead of sliding away.
     */
    public void applyZoomChange(double newZoom, int screenW, int screenH, int maxW, int maxH) {
        int centerX = screenW / 2;
        int centerY = screenH / 2;

        // Trace where the screen center points to in world space before the scale shift
        double worldCXBefore = (centerX / this.zoom) - this.offsetX;
        double worldCYBefore = (centerY / this.zoom) - this.offsetY;

        // Apply the new scale profile safely
        this.zoom = newZoom;

        // Recalculate offsets to align the new zoom matrix perfectly with the old focal coordinates
        this.offsetX = (int) ((centerX / newZoom) - worldCXBefore);
        this.offsetY = (int) ((centerY / newZoom) - worldCYBefore);

        // Instantly verify boundaries so the zoom doesn't clip past your dead space boundaries
        clampOffsets(screenW, screenH, maxW, maxH);
    }

    /**
     * NEW: Unified boundary clamping method. Completely eliminates the 200px
     * void-leak padding and keeps your camera safely locked inside map boundaries.
     */
    public void clampOffsets(int screenW, int screenH, int maxW, int maxH) {
        int maxOffsetX = 0;
        int maxOffsetY = 0;

        int minOffsetX = (int) ((screenW / zoom) - maxW);
        int minOffsetY = (int) ((screenH / zoom) - maxH);

        // Structural cushion if you zoom out further than a tiny map's dimensions
        if (minOffsetX > 0) minOffsetX = 0;
        if (minOffsetY > 0) minOffsetY = 0;

        if (offsetX > maxOffsetX) offsetX = maxOffsetX;
        if (offsetX < minOffsetX) offsetX = minOffsetX;
        if (offsetY > maxOffsetY) offsetY = maxOffsetY;
        if (offsetY < minOffsetY) offsetY = minOffsetY;
    }

    public Point toWorld(int screenX, int screenY) {
        int worldX = (int)(screenX / zoom) - offsetX;
        int worldY = (int)(screenY / zoom) - offsetY;
        return new Point(worldX, worldY);
    }
}
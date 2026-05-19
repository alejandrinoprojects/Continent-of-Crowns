import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.HashSet;
import java.util.Set;

public class TownHall {
    public final int q;
    public final int r;
    public final boolean isPlayer;

    public int maxHp = 2000;
    public int currentHp = 2000;

    public static final int FOOTPRINT_RADIUS = 4;
    public static final int TOTAL_HEIGHT = 265;

    private double lastDamageTime = 0.0;
    private boolean showHealthBar = false;

    private Set<Long> footprintHexes = new HashSet<>();

    public TownHall(int q, int r, boolean isPlayer) {
        this.q = q;
        this.r = r;
        this.isPlayer = isPlayer;
        buildFootprint();
    }

    private void buildFootprint() {
        footprintHexes.clear();
        for (int dq = -FOOTPRINT_RADIUS; dq <= FOOTPRINT_RADIUS; dq++) {
            int drMin = Math.max(-FOOTPRINT_RADIUS, -dq - FOOTPRINT_RADIUS);
            int drMax = Math.min(FOOTPRINT_RADIUS, -dq + FOOTPRINT_RADIUS);
            for (int dr = drMin; dr <= drMax; dr++) {
                footprintHexes.add(GridManager.packKey(q + dq, r + dr));
            }
        }
    }

    public Set<Long> getFootprintHexes() {
        return footprintHexes;
    }

    public boolean containsHex(int hexQ, int hexR) {
        return footprintHexes.contains(GridManager.packKey(hexQ, hexR));
    }

    public void registerShadowHeights() {
        for (int dq = -FOOTPRINT_RADIUS; dq <= FOOTPRINT_RADIUS; dq++) {
            int drMin = Math.max(-FOOTPRINT_RADIUS, -dq - FOOTPRINT_RADIUS);
            int drMax = Math.min(FOOTPRINT_RADIUS, -dq + FOOTPRINT_RADIUS);
            for (int dr = drMin; dr <= drMax; dr++) {
                long key = GridManager.packKey(q + dq, r + dr);
                GridManager.townhallFootprints.add(key);
            }
        }
    }

    public void takeDamage(int amount) {
        currentHp = Math.max(0, currentHp - amount);
        lastDamageTime = System.nanoTime() / 1_000_000_000.0;
        showHealthBar = true;
    }

    public void repair(int amount) {
        currentHp = Math.min(maxHp, currentHp + amount);
    }

    public boolean isDestroyed() {
        return currentHp <= 0;
    }

    public void updateHealthBarVisibility() {
        if (currentHp >= maxHp) {
            if (System.nanoTime() / 1_000_000_000.0 - lastDamageTime > 10.0) {
                showHealthBar = false;
            }
        } else {
            showHealthBar = true;
        }
    }

    public boolean shouldShowHealthBar() {
        return showHealthBar;
    }

    public void drawHealthBar(Graphics2D g2d, int hexSize, int terrainZ) {
        if (!showHealthBar) return;

        int pX = CoordinateConverter.getPixelX(q, r);
        int pY = CoordinateConverter.getPixelY(q, r);

        int barW = 120;
        int barH = 8;
        int barX = pX - barW / 2;
        int barY = pY - terrainZ - hexSize * 4 - 20;

        g2d.setColor(new Color(0, 0, 0, 180));
        g2d.fillRect(barX - 1, barY - 1, barW + 2, barH + 2);

        g2d.setColor(new Color(80, 20, 20));
        g2d.fillRect(barX, barY, barW, barH);

        double hpPct = (double) currentHp / maxHp;
        Color hpColor = hpPct > 0.5 ? new Color(40, 200, 60) : (hpPct > 0.25 ? new Color(220, 180, 30) : new Color(220, 40, 40));
        g2d.setColor(hpColor);
        g2d.fillRect(barX, barY, (int) (barW * hpPct), barH);

        g2d.setColor(new Color(180, 180, 180));
        g2d.setStroke(new BasicStroke(1.0f));
        g2d.drawRect(barX, barY, barW, barH);
    }

    public void drawFootprintBoundary(Graphics2D g2d, int hexSize, int terrainZ) {
        int pX = CoordinateConverter.getPixelX(q, r);
        int pY = CoordinateConverter.getPixelY(q, r);

        // Slightly inflate radius for the halo to encompass the building smoothly
        int footprintRadiusPx = (int)(FOOTPRINT_RADIUS * hexSize * 1.05);

        int[] hx = new int[6];
        int[] hy = new int[6];
        for (int i = 0; i < 6; i++) {
            // PERFECT FLAT TOP
            double angle = Math.PI / 3 * i;
            hx[i] = pX + (int) (footprintRadiusPx * Math.cos(angle));
            hy[i] = pY + (int) (footprintRadiusPx * Math.sin(angle)) - terrainZ;
        }

        // Inner Polygon Base
        g2d.setColor(new Color(0, 60, 20, 80));
        g2d.fillPolygon(hx, hy, 6);

        // Outer Glow / Halo (Thick, soft stroke)
        g2d.setColor(isPlayer ? new Color(0, 255, 120, 50) : new Color(255, 60, 60, 50));
        g2d.setStroke(new java.awt.BasicStroke(14.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawPolygon(hx, hy, 6);

        // Core Halo Ring (Thin, bright stroke)
        g2d.setColor(isPlayer ? new Color(0, 220, 80, 200) : new Color(220, 40, 40, 200));
        g2d.setStroke(new java.awt.BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawPolygon(hx, hy, 6);

        g2d.setStroke(new java.awt.BasicStroke(1.0f)); // Reset
    }
}
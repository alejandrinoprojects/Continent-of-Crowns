import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.awt.GradientPaint;
import java.awt.BasicStroke;

public class RTSHUD {

    public static final int HUD_HEIGHT = 200;
    public static final int MINIMAP_DIM = 360;

    private static final int GOLD_THICKNESS = 10;
    private static final int TOTAL_BORDER_OFFSET = GOLD_THICKNESS;

    public final Rectangle minimapBounds = new Rectangle();
    public final Rectangle commandCardBounds = new Rectangle();

    public final Rectangle btnW = new Rectangle();
    public final Rectangle btnA = new Rectangle();
    public final Rectangle btnS = new Rectangle();
    public final Rectangle btnD = new Rectangle();

    private final GamePanel panel;
    private BufferedImage minimapCache = null;
    private BufferedImage cachedHudBackground = null;
    private int cachedHudWidth = -1;

    private int minMapX = 0;
    private int maxMapX = 1;
    private int minMapY = 0;
    private int maxMapY = 1;

    public RTSHUD(GamePanel panel) {
        this.panel = panel;
    }

    public void updateLayoutBounds(int w, int h) {
        int hudY = h - HUD_HEIGHT;

        int totalMinimapSize = MINIMAP_DIM + (TOTAL_BORDER_OFFSET * 2);
        minimapBounds.setBounds(10, h - (totalMinimapSize + 10), totalMinimapSize, totalMinimapSize);

        int cmdX = w - 240;
        commandCardBounds.setBounds(cmdX, hudY + 10, 230, 180);

        int btnW_Dim = 100;
        int btnH_Dim = 75;

        btnW.setBounds(cmdX + 10, hudY + 20, btnW_Dim, btnH_Dim);
        btnA.setBounds(cmdX + 120, hudY + 20, btnW_Dim, btnH_Dim);
        btnS.setBounds(cmdX + 10, hudY + 105, btnW_Dim, btnH_Dim);
        btnD.setBounds(cmdX + 120, hudY + 105, btnW_Dim, btnH_Dim);
    }

    public void bakeMinimap() {
        minimapCache = new BufferedImage(MINIMAP_DIM, MINIMAP_DIM, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = minimapCache.createGraphics();

        this.minMapX = 0;
        this.maxMapX = CoordinateConverter.getMapWidth();
        this.minMapY = 0;
        this.maxMapY = CoordinateConverter.getMapHeight();

        double worldW = maxMapX - minMapX;
        double worldH = maxMapY - minMapY;

        double scaleX = (double) MINIMAP_DIM / worldW;
        double scaleY = (double) MINIMAP_DIM / worldH;

        int brushW = (int) Math.ceil(2.5 * GamePanel.hexSize * scaleX);
        int brushH = (int) Math.ceil(2.5 * GamePanel.hexSize * scaleY);

        if (brushW < 3) brushW = 3;
        if (brushH < 3) brushH = 3;

        g.setColor(new Color(50, 95, 210));
        g.fillRect(0, 0, MINIMAP_DIM, MINIMAP_DIM);

        for (HexTile tile : GridManager.hexes.values()) {
            TileType tt = tile.getTileType();
            if (tt == TileType.WATER) continue;

            int pX = CoordinateConverter.getPixelX(tile.q, tile.r);
            int pY = CoordinateConverter.getPixelY(tile.q, tile.r);
            int mx = (int) ((pX - minMapX) * scaleX);
            int my = (int) ((pY - minMapY) * scaleY);

            if (mx < 0 || mx >= MINIMAP_DIM || my < 0 || my >= MINIMAP_DIM) continue;

            Color c;
            switch (tt) {
                case ROCK:       c = new Color(110, 110, 110); break;
                case TREES:      c = new Color(45, 80, 30); break;
                case SAND:
                    switch (tile.getBiomeType()) {
                        case DESERT:   c = new Color(200, 170, 110); break;
                        case SAVANNAH: c = new Color(160, 140, 55); break;
                        default:       c = new Color(225, 195, 140); break;
                    }
                    break;
                case SNOW:
                    switch (tile.getBiomeType()) {
                        case TUNDRA: c = new Color(210, 225, 235); break;
                        case TAIGA:  c = new Color(55, 85, 35); break;
                        default:     c = new Color(210, 225, 235); break;
                    }
                    break;
                case ICE:        c = new Color(195, 215, 235); break;
                case PLAIN:
                default:         c = new Color(95, 130, 35); break;
            }
            g.setColor(c);
            g.fillRect(mx - (brushW / 2), my - (brushH / 2), brushW, brushH);
        }
        g.dispose();
    }

    public void renderHUD(Graphics2D g2d, int w, int h) {
        int hudY = h - HUD_HEIGHT;
        updateLayoutBounds(w, h);

        // ==========================================
        // 1. DRAW TOP HUD BAR
        // ==========================================
        int topBarHeight = 40;
        g2d.setColor(new Color(15, 18, 24, 230));
        g2d.fillRect(0, 0, w, topBarHeight);

        g2d.setColor(new Color(212, 175, 55));
        g2d.setStroke(new BasicStroke(2.0f));
        g2d.drawLine(0, topBarHeight, w, topBarHeight);

        g2d.setFont(new Font("SansSerif", Font.BOLD, 14));

        // --- DAY BOX ---
        String dayText = "DAY " + panel.currentDay;
        int dayBoxW = 75;
        int dayBoxX = 15;
        g2d.setColor(new Color(40, 45, 55));
        g2d.fillRoundRect(dayBoxX, 8, dayBoxW, 24, 6, 6);
        g2d.setColor(new Color(100, 110, 130));
        g2d.drawRoundRect(dayBoxX, 8, dayBoxW, 24, 6, 6);
        g2d.setColor(new Color(175, 215, 255));
        int dTxtW = g2d.getFontMetrics().stringWidth(dayText);
        g2d.drawString(dayText, dayBoxX + (dayBoxW - dTxtW) / 2, 25);

        // --- PROGRESS BAR ---
        double cycleProgress = panel.currentCycleTime / GamePanel.DAY_NIGHT_CYCLE_SECONDS;
        boolean isDaytime = cycleProgress < 0.5;

        int barX = dayBoxX + dayBoxW + 20;
        int barY = 13;
        int barW = 200;
        int barH = 14;

        g2d.setColor(new Color(20, 25, 35));
        g2d.fillRoundRect(barX, barY, barW, barH, 8, 8);

        int fillW = (int) (barW * cycleProgress);
        if (fillW > 0) {
            Color fillColor = isDaytime ? new Color(60, 140, 220) : new Color(40, 60, 140);
            g2d.setColor(fillColor);
            g2d.fillRoundRect(barX, barY, fillW, barH, 8, 8);
        }

        int iconR = 7;
        int iconX = barX + fillW;
        int iconY = barY + (barH / 2);

        if (isDaytime) {
            g2d.setColor(new Color(255, 200, 0, 80));
            g2d.fillOval(iconX - iconR - 2, iconY - iconR - 2, (iconR + 2) * 2, (iconR + 2) * 2);
            g2d.setColor(new Color(255, 230, 50));
            g2d.fillOval(iconX - iconR, iconY - iconR, iconR * 2, iconR * 2);
        } else {
            g2d.setColor(new Color(200, 220, 255, 80));
            g2d.fillOval(iconX - iconR - 2, iconY - iconR - 2, (iconR + 2) * 2, (iconR + 2) * 2);
            g2d.setColor(new Color(220, 235, 255));
            g2d.fillOval(iconX - iconR, iconY - iconR, iconR * 2, iconR * 2);
            g2d.setColor(new Color(170, 190, 220));
            g2d.fillOval(iconX - 3, iconY - 3, 3, 3);
            g2d.fillOval(iconX + 1, iconY + 1, 2, 2);
        }

        g2d.setColor(new Color(100, 110, 130));
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawRoundRect(barX, barY, barW, barH, 8, 8);

        // --- SESSION BOX ---
        int totalSeconds = (int) panel.totalSessionTime;
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        String sessionText = hours > 0
                ? String.format("SESSION | %d:%02d:%02d", hours, minutes, seconds)
                : String.format("SESSION | %02d:%02d", minutes, seconds);

        int sTxtW = g2d.getFontMetrics().stringWidth(sessionText);
        int sessionBoxW = sTxtW + 20;
        int sessionBoxX = barX + barW + 25;

        g2d.setColor(new Color(40, 45, 55));
        g2d.fillRoundRect(sessionBoxX, 8, sessionBoxW, 24, 6, 6);
        g2d.setColor(new Color(100, 110, 130));
        g2d.drawRoundRect(sessionBoxX, 8, sessionBoxW, 24, 6, 6);
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.drawString(sessionText, sessionBoxX + 10, 25);


        // ==========================================
        // UNIFIED HEXAGONAL TITLE (MULTI-POLYGON FORGE)
        // ==========================================
        int hexSize = 3;
        double hexHoriz = hexSize * 2;
        double hexVert = hexSize * Math.sqrt(3);
        String title = "CONTINENT OF CROWNS";

        int totalCols = 0;
        for(char c : title.toCharArray()) {
            if(c == ' ') totalCols += 3;
            else totalCols += 6;
        }

        int titleW_pixels = (int)(totalCols * hexHoriz);
        int titleH_pixels = (int)(5 * hexVert);

        int signPaddingX = 16;
        int signPaddingY = 12;

        int signW = titleW_pixels + (signPaddingX * 2);
        int signH = titleH_pixels + (signPaddingY * 2);
        int signX = (w - signW) / 2;
        int signY = -2;

        // 1. Textured Gold Frame
        g2d.setColor(new Color(130, 90, 20));
        g2d.fillRoundRect(signX, signY - 10, signW, signH + 10, 15, 15);
        g2d.setColor(new Color(240, 200, 80));
        g2d.fillRoundRect(signX + 4, signY - 10, signW - 8, signH + 6, 12, 12);
        g2d.setColor(new Color(180, 140, 40));
        g2d.fillRoundRect(signX + 8, signY - 10, signW - 16, signH + 2, 8, 8);

        g2d.setColor(new Color(90, 60, 10));
        for (int i = signX + 20; i < signX + signW - 20; i += 30) {
            g2d.fillOval(i, signH - 5, 4, 4);
            g2d.setColor(new Color(255, 230, 120));
            g2d.drawOval(i, signH - 5, 4, 4);
            g2d.setColor(new Color(90, 60, 10));
        }

        // 2. Gritty Wood Plank Background
        int woodX = signX + 12;
        int woodY = signY;
        int woodW = signW - 24;
        int woodH = signH - 6;

        g2d.setColor(new Color(60, 35, 15));
        g2d.fillRoundRect(woodX, woodY - 10, woodW, woodH + 10, 5, 5);

        Random signWoodRand = new Random(777);
        g2d.setStroke(new BasicStroke(1.0f));
        for(int i = 0; i < 60; i++) {
            int gx = woodX + signWoodRand.nextInt(Math.max(1, woodW - 30));
            int gy = woodY + signWoodRand.nextInt(woodH);
            int gw = 10 + signWoodRand.nextInt(30);
            g2d.setColor(new Color(40, 20, 10, 150));
            g2d.drawLine(gx, gy, gx + gw, gy);
        }

        for(int i = 0; i < 4; i++) {
            int kx = woodX + signWoodRand.nextInt(Math.max(1, woodW - 15));
            int ky = woodY + signWoodRand.nextInt(Math.max(1, woodH - 10));
            g2d.setColor(new Color(35, 15, 5, 180));
            g2d.fillOval(kx, ky, 10 + signWoodRand.nextInt(10), 3 + signWoodRand.nextInt(3));
        }

        g2d.setStroke(new BasicStroke(2.0f));
        for (int py = 20; py < woodH; py += 25) {
            int drift = signWoodRand.nextInt(3) - 1;
            g2d.setColor(new Color(30, 15, 5, 200));
            g2d.drawLine(woodX, py + drift, woodX + woodW, py + drift);
            g2d.setColor(new Color(90, 55, 25, 200));
            g2d.drawLine(woodX, py + drift + 1, woodX + woodW, py + drift + 1);
        }

        // 3. Multi-Pass Hexagonal Text Rendering
        int textStartX = signX + signPaddingX;
        int textStartY = signY + signPaddingY + (hexSize / 2);

        // Pass A: 3D Extrusion Dropshadow Blocks
        g2d.setColor(new Color(25, 15, 5, 220));
        g2d.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int depth = 5; depth >= 2; depth -= 1) {
            drawHexPass(g2d, title, textStartX, textStartY, hexHoriz, hexVert, hexSize, depth, depth, true, true);
        }

        // Pass B: Dark Outer Silhouette / Unified Border
        g2d.setColor(new Color(85, 45, 10)); // Dark Bronze/Brown
        g2d.setStroke(new BasicStroke(5.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        drawHexPass(g2d, title, textStartX, textStartY, hexHoriz, hexVert, hexSize, 0, 0, true, true);

        // Pass C: Forged Gold Faces (Merged Gradient)
        GradientPaint goldGradient = new GradientPaint(
                0, textStartY, new Color(255, 240, 120),
                0, textStartY + titleH_pixels, new Color(190, 120, 10)
        );
        g2d.setPaint(goldGradient);
        g2d.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        drawHexPass(g2d, title, textStartX, textStartY, hexHoriz, hexVert, hexSize, 0, 0, true, true);


        // ==========================================
        // RESOURCE UI (Longer boxes, Aligned Right)
        // ==========================================
        g2d.setFont(new Font("SansSerif", Font.BOLD, 14));

        // --- WOOD ---
        String woodText = "0";
        int rWoodBoxW = Math.max(90, g2d.getFontMetrics().stringWidth(woodText) + 40);
        int rWoodBoxX = w - 15 - rWoodBoxW;
        int rWoodIconX = rWoodBoxX - 32;

        g2d.setColor(new Color(120, 75, 45));
        g2d.fillRoundRect(rWoodIconX + 2, 11, 22, 9, 4, 4);
        g2d.setColor(new Color(160, 105, 60));
        g2d.fillOval(rWoodIconX + 2, 11, 7, 9);
        g2d.setColor(new Color(100, 60, 35));
        g2d.fillRoundRect(rWoodIconX + 6, 17, 22, 9, 4, 4);
        g2d.setColor(new Color(140, 90, 50));
        g2d.fillOval(rWoodIconX + 6, 17, 7, 9);

        g2d.setColor(new Color(110, 70, 40));
        g2d.fillRoundRect(rWoodBoxX, 8, rWoodBoxW, 24, 6, 6);
        g2d.setColor(new Color(150, 100, 60));
        g2d.drawRoundRect(rWoodBoxX, 8, rWoodBoxW, 24, 6, 6);
        g2d.setColor(Color.WHITE);
        g2d.drawString(woodText, rWoodBoxX + (rWoodBoxW - g2d.getFontMetrics().stringWidth(woodText)) / 2, 25);

        // --- STONE ---
        String stoneText = "0";
        int rStoneBoxW = Math.max(90, g2d.getFontMetrics().stringWidth(stoneText) + 40);
        int rStoneBoxX = rWoodIconX - 15 - rStoneBoxW;
        int rStoneIconX = rStoneBoxX - 32;

        g2d.setColor(new Color(120, 120, 120));
        g2d.fillOval(rStoneIconX + 2, 14, 15, 13);
        g2d.setColor(new Color(150, 150, 150));
        g2d.fillOval(rStoneIconX + 11, 10, 13, 15);
        g2d.setColor(new Color(90, 90, 90));
        g2d.fillOval(rStoneIconX + 8, 18, 15, 11);

        g2d.setColor(new Color(80, 85, 90));
        g2d.fillRoundRect(rStoneBoxX, 8, rStoneBoxW, 24, 6, 6);
        g2d.setColor(new Color(120, 130, 140));
        g2d.drawRoundRect(rStoneBoxX, 8, rStoneBoxW, 24, 6, 6);
        g2d.setColor(Color.WHITE);
        g2d.drawString(stoneText, rStoneBoxX + (rStoneBoxW - g2d.getFontMetrics().stringWidth(stoneText)) / 2, 25);

        // --- POPULATION ---
        String popText = "0 / 0";
        int rPopBoxW = Math.max(80, g2d.getFontMetrics().stringWidth(popText) + 20);
        int rPopBoxX = rStoneIconX - 15 - rPopBoxW;
        int rPopIconX = rPopBoxX - 25;

        g2d.setColor(new Color(200, 200, 200));
        g2d.fillOval(rPopIconX + 6, 10, 10, 10);
        g2d.fillRoundRect(rPopIconX + 2, 21, 18, 10, 4, 4);

        g2d.setColor(new Color(40, 45, 55));
        g2d.fillRoundRect(rPopBoxX, 8, rPopBoxW, 24, 6, 6);
        g2d.setColor(new Color(100, 110, 130));
        g2d.drawRoundRect(rPopBoxX, 8, rPopBoxW, 24, 6, 6);
        g2d.setColor(Color.WHITE);
        g2d.drawString(popText, rPopBoxX + (rPopBoxW - g2d.getFontMetrics().stringWidth(popText)) / 2, 25);


        // ==========================================
        // 2. DRAW BOTTOM HUD BACKDROP (CACHED TEXTURE)
        // ==========================================
        if (cachedHudBackground == null || cachedHudWidth != w) {
            if (cachedHudBackground != null) cachedHudBackground.flush();
            cachedHudBackground = new BufferedImage(w, HUD_HEIGHT, BufferedImage.TYPE_INT_ARGB);
            Graphics2D bg = cachedHudBackground.createGraphics();
            bg.setColor(new Color(80, 50, 25));
            bg.fillRect(0, 0, w, HUD_HEIGHT);

            Random mainWoodRand = new Random(8484);
            bg.setStroke(new BasicStroke(1.5f));
            for (int i = 0; i < 250; i++) {
                int gx = mainWoodRand.nextInt(w);
                int gy = mainWoodRand.nextInt(HUD_HEIGHT);
                int gw = 20 + mainWoodRand.nextInt(60);
                bg.setColor(new Color(60, 35, 15, 100));
                bg.drawLine(gx, gy, gx + gw, gy + mainWoodRand.nextInt(3) - 1);
            }
            for (int i = 0; i < 15; i++) {
                int kx = mainWoodRand.nextInt(w);
                int ky = mainWoodRand.nextInt(HUD_HEIGHT);
                bg.setColor(new Color(40, 20, 10, 160));
                bg.fillOval(kx, ky, 15 + mainWoodRand.nextInt(20), 6 + mainWoodRand.nextInt(5));
            }
            bg.setStroke(new BasicStroke(2.0f));
            for (int y = 15; y < HUD_HEIGHT; y += 30) {
                int drift = mainWoodRand.nextInt(3) - 1;
                bg.setColor(new Color(30, 15, 5, 230));
                bg.drawLine(0, y + drift, w, y + drift);
                bg.setColor(new Color(100, 65, 35, 200));
                bg.drawLine(0, y + drift + 1, w, y + drift + 1);
            }
            bg.setColor(new Color(45, 25, 10));
            bg.fillRect(0, 0, w, 10);
            bg.setColor(new Color(10, 5, 0));
            bg.drawLine(0, 0, w, 0);
            bg.drawLine(0, 10, w, 10);
            bg.dispose();
            cachedHudWidth = w;
        }
        g2d.drawImage(cachedHudBackground, 0, hudY, null);


        // ==========================================
        // 3. BEVELED MINIMAP FRAME
        // ==========================================
        int mxOuter = minimapBounds.x;
        int myOuter = minimapBounds.y;
        int dimOuter = minimapBounds.width;

        g2d.setColor(new Color(25, 15, 5));
        g2d.fillRoundRect(mxOuter - 4, myOuter - 4, dimOuter + 8, dimOuter + 8, 12, 12);

        g2d.setColor(new Color(180, 140, 40));
        g2d.fillRoundRect(mxOuter, myOuter, dimOuter, dimOuter, 8, 8);

        g2d.setColor(new Color(240, 200, 80));
        g2d.fillRoundRect(mxOuter, myOuter, dimOuter, 6, 8, 8);
        g2d.fillRoundRect(mxOuter, myOuter, 6, dimOuter, 8, 8);

        g2d.setColor(new Color(120, 80, 15));
        g2d.fillRoundRect(mxOuter, myOuter + dimOuter - 6, dimOuter, 6, 8, 8);
        g2d.fillRoundRect(mxOuter + dimOuter - 6, myOuter, 6, dimOuter, 8, 8);

        g2d.setColor(new Color(60, 40, 10));
        g2d.fillOval(mxOuter + 2, myOuter + 2, 8, 8);
        g2d.fillOval(mxOuter + dimOuter - 10, myOuter + 2, 8, 8);
        g2d.fillOval(mxOuter + 2, myOuter + dimOuter - 10, 8, 8);
        g2d.fillOval(mxOuter + dimOuter - 10, myOuter + dimOuter - 10, 8, 8);

        g2d.setColor(new Color(15, 15, 20));
        int coreX = mxOuter + TOTAL_BORDER_OFFSET;
        int coreY = myOuter + TOTAL_BORDER_OFFSET;
        g2d.fillRect(coreX, coreY, MINIMAP_DIM, MINIMAP_DIM);

        if (minimapCache != null) {
            g2d.drawImage(minimapCache, coreX, coreY, null);
        }

        // ==========================================
        // 4. DRAW DYNAMIC LIVE ELEMENTS ON MINIMAP (MERGED)
        // ==========================================
        double worldW = maxMapX - minMapX;
        double worldH = maxMapY - minMapY;

        if (worldW > 0 && worldH > 0) {
            double scaleX = (double) MINIMAP_DIM / worldW;
            double scaleY = (double) MINIMAP_DIM / worldH;

            // --- HIS FEATURE ADDITION: Render Town Halls on minimap ---
            renderTownHallOnMinimap(g2d, panel.getPlayerTownHall(), scaleX, scaleY, coreX, coreY);
            renderTownHallOnMinimap(g2d, panel.getEnemyTownHall(), scaleX, scaleY, coreX, coreY);

            // --- HIS FEATURE ADDITION: Live Team-Colored Unit Dots (Optimized) ---
            synchronized (panel.unitsLock) {
                for (Unit u : panel.units) {
                    int dotX = coreX + (int) ((u.x - minMapX) * scaleX);
                    int dotY = coreY + (int) ((u.y - minMapY) * scaleY);

                    if (dotX >= coreX && dotX < coreX + MINIMAP_DIM && dotY >= coreY && dotY < coreY + MINIMAP_DIM) {

                        // Instantly reads boolean instead of slow Map lookups!
                        g2d.setColor(u.isPlayerOwned ? new Color(0, 130, 255) : new Color(255, 45, 45));

                        if (u.selected) {
                            g2d.fillRect(dotX - 3, dotY - 3, 6, 6);
                            g2d.setColor(Color.YELLOW);
                            g2d.drawRect(dotX - 3, dotY - 3, 6, 6);
                        } else {
                            g2d.fillRect(dotX - 2, dotY - 2, 4, 4);
                        }
                    }
                }
            }

            double viewW_World = panel.getWidth() / panel.cameraManager.zoom;
            double viewH_World = panel.getHeight() / panel.cameraManager.zoom;
            double camWorldX = -panel.cameraManager.offsetX;
            double camWorldY = -panel.cameraManager.offsetY;

            int vx = coreX + (int) ((camWorldX - minMapX) * scaleX);
            int vy = coreY + (int) ((camWorldY - minMapY) * scaleY);
            int vw = (int) (viewW_World * scaleX);
            int vh = (int) (viewH_World * scaleY);

            java.awt.Shape oldClip = g2d.getClip();
            g2d.clipRect(coreX, coreY, MINIMAP_DIM, MINIMAP_DIM);

            g2d.setColor(new Color(255, 255, 255, 200));
            g2d.setStroke(new BasicStroke(2.0f));
            g2d.drawRect(vx, vy, vw, vh);

            g2d.setClip(oldClip);
        }

        g2d.setColor(new Color(30, 30, 30));
        g2d.setStroke(new BasicStroke(2.0f));
        g2d.drawRect(coreX, coreY, MINIMAP_DIM, MINIMAP_DIM);

        // ==========================================
        // 5. DRAW SELECTION SHELF AND COMMAND CARD
        // ==========================================
        int shelfX = mxOuter + dimOuter + 30;
        int shelfW = commandCardBounds.x - shelfX - 20;
        renderSelectionShelf(g2d, shelfX, hudY + 30, shelfW, HUD_HEIGHT - 30);

        g2d.setColor(new Color(60, 35, 15));
        g2d.fillRoundRect(commandCardBounds.x, commandCardBounds.y, commandCardBounds.width, commandCardBounds.height, 10, 10);
        g2d.setColor(new Color(30, 15, 5));
        g2d.drawRoundRect(commandCardBounds.x, commandCardBounds.y, commandCardBounds.width, commandCardBounds.height, 10, 10);

        drawCommandButton(g2d, btnW, "Move ( W )", panel.activeCommandMode == CommandType.MOVE, 0);
        drawCommandButton(g2d, btnA, "Attack ( A )", panel.activeCommandMode == CommandType.ATTACK, 1);
        drawCommandButton(g2d, btnS, "Hold ( S )", panel.activeCommandMode == CommandType.HOLD_POSITION, 2);
        drawCommandButton(g2d, btnD, "Stop ( D )", false, 3);
    }

    // --- HIS FEATURE ADDITION: Town Hall Minimap Render Helper ---
    private void renderTownHallOnMinimap(Graphics2D g2d, TownHall townHall, double scaleX, double scaleY, int coreX, int coreY) {
        if (townHall == null || townHall.isDestroyed()) return;

        int wX = CoordinateConverter.getPixelX(townHall.q, townHall.r);
        int wY = CoordinateConverter.getPixelY(townHall.q, townHall.r);
        int thMx = coreX + (int) ((wX - minMapX) * scaleX);
        int thMy = coreY + (int) ((wY - minMapY) * scaleY);

        if (thMx >= coreX && thMx < coreX + MINIMAP_DIM && thMy >= coreY && thMy < coreY + MINIMAP_DIM) {
            g2d.setColor(townHall.isPlayer ? Color.BLUE : Color.RED);
            g2d.fillRect(thMx - 3, thMy - 3, 7, 7);
            g2d.setColor(Color.WHITE);
            g2d.drawRect(thMx - 3, thMy - 3, 7, 7);
        }
    }

    // --- MULTI-PASS HEX TEXT ENGINE ---

    private void drawHexPass(Graphics2D g2d, String text, int startX, int startY, double hexHoriz, double hexVert, int size, int offsetX, int offsetY, boolean fill, boolean stroke) {
        int cursorCol = 0;
        for(char c : text.toCharArray()) {
            if(c == ' ') { cursorCol += 3; continue; }
            String[] glyph = getHexGlyph(c);
            if(glyph != null) {
                for(int row = 0; row < 5; row++) {
                    for(int col = 0; col < 5; col++) {
                        if(glyph[row].charAt(col) == 'X') {
                            double cx = startX + (cursorCol + col) * hexHoriz;
                            double cy = startY + row * hexVert;
                            if ((cursorCol + col) % 2 != 0) cy += hexVert / 2.0;

                            Polygon hex = new Polygon();
                            for (int i = 0; i < 6; i++) {
                                double angle = Math.PI / 3 * i;
                                hex.addPoint((int)(cx + offsetX + size * Math.cos(angle)),
                                        (int)(cy + offsetY + size * Math.sin(angle)));
                            }
                            if(fill) g2d.fillPolygon(hex);
                            if(stroke) g2d.drawPolygon(hex);
                        }
                    }
                }
                cursorCol += 6;
            }
        }
    }

    private String[] getHexGlyph(char c) {
        switch(c) {
            case 'C': return new String[]{".XXX.", "X...X", "X....", "X...X", ".XXX."};
            case 'O': return new String[]{".XXX.", "X...X", "X...X", "X...X", ".XXX."};
            case 'N': return new String[]{"X...X", "XX..X", "X.X.X", "X..XX", "X...X"};
            case 'T': return new String[]{"XXXXX", "..X..", "..X..", "..X..", "..X.."};
            case 'I': return new String[]{".XXX.", "..X..", "..X..", "..X..", ".XXX."};
            case 'E': return new String[]{"XXXX.", "X....", "XXX..", "X....", "XXXX."};
            case 'F': return new String[]{"XXXX.", "X....", "XXX..", "X....", "X...."};
            case 'R': return new String[]{"XXXX.", "X...X", "XXXX.", "X.X..", "X..XX"};
            case 'W': return new String[]{"X...X", "X...X", "X.X.X", "X.X.X", ".X.X."};
            case 'S': return new String[]{".XXXX", "X....", ".XXX.", "....X", "XXXX."};
            default: return null;
        }
    }

    private void renderSelectionShelf(Graphics2D g2d, int startX, int startY, int maxW, int maxH) {
        if (panel.selectedUnits.isEmpty()) {
            g2d.setColor(new Color(255, 255, 255, 150));
            g2d.setFont(new Font("SansSerif", Font.ITALIC, 16));
            g2d.drawString("No units currently selected.", startX + 20, startY + 60);
            return;
        }

        Map<UnitType, Integer> selectedCounts = new HashMap<>();
        for (Unit u : panel.selectedUnits) {
            selectedCounts.put(u.type, selectedCounts.getOrDefault(u.type, 0) + 1);
        }

        int cardX = startX;
        int cardWidth = 90;
        int cardHeight = 125;

        for (UnitType type : UnitType.values()) {
            boolean isPresentInSelection = selectedCounts.containsKey(type);

            if (isPresentInSelection) {
                g2d.setColor(new Color(235, 225, 205));
                g2d.fillRoundRect(cardX, startY, cardWidth, cardHeight, 10, 10);
                g2d.setColor(new Color(140, 100, 50));
                g2d.setStroke(new BasicStroke(3.0f));
                g2d.drawRoundRect(cardX, startY, cardWidth, cardHeight, 10, 10);

                g2d.setColor(new Color(180, 190, 200));
                g2d.fillRect(cardX + 10, startY + 10, cardWidth - 20, 60);
                g2d.setColor(new Color(100, 100, 100));
                g2d.setStroke(new BasicStroke(1.0f));
                g2d.drawRect(cardX + 10, startY + 10, cardWidth - 20, 60);

                g2d.setColor(new Color(40, 30, 20));
                g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
                int tW = g2d.getFontMetrics().stringWidth(type.name());
                g2d.drawString(type.name(), cardX + (cardWidth - tW) / 2, startY + 90);

                int count = selectedCounts.get(type);
                g2d.setColor(new Color(180, 40, 40));
                g2d.fillOval(cardX + cardWidth / 2 - 14, startY + 98, 28, 22);
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("SansSerif", Font.BOLD, 14));
                int cW = g2d.getFontMetrics().stringWidth(String.valueOf(count));
                g2d.drawString(String.valueOf(count), cardX + cardWidth / 2 - cW / 2, startY + 114);
            } else {
                g2d.setColor(new Color(90, 70, 50));
                g2d.fillRoundRect(cardX, startY, cardWidth, cardHeight, 10, 10);
                g2d.setColor(new Color(50, 35, 20));
                g2d.setStroke(new BasicStroke(3.0f));
                g2d.drawRoundRect(cardX, startY, cardWidth, cardHeight, 10, 10);

                g2d.setColor(new Color(70, 50, 35));
                g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
                int tW = g2d.getFontMetrics().stringWidth(type.name());
                g2d.drawString(type.name(), cardX + (cardWidth - tW) / 2, startY + 90);
            }

            cardX += cardWidth + 15;
            if (cardX + cardWidth > startX + maxW) break;
        }
    }

    private void drawCommandButton(Graphics2D g2d, Rectangle r, String text, boolean isSelectedToggled, int actionId) {
        if (isSelectedToggled) {
            g2d.setColor(new Color(115, 155, 65));
            g2d.fillRoundRect(r.x, r.y, r.width, r.height, 8, 8);
            g2d.setColor(Color.WHITE);
        } else {
            g2d.setColor(new Color(40, 45, 55));
            g2d.fillRoundRect(r.x, r.y, r.width, r.height, 8, 8);
            g2d.setColor(new Color(200, 200, 200));
        }

        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(2.0f));
        g2d.drawRoundRect(r.x, r.y, r.width, r.height, 8, 8);

        int cx = r.x + r.width / 2;
        int cy = r.y + 30;

        g2d.setStroke(new BasicStroke(2.0f));

        switch(actionId) {
            case 0:
                g2d.setColor(new Color(80, 220, 80));
                g2d.fillRect(cx - 4, cy - 2, 8, 12);
                Polygon arrowHead = new Polygon(new int[]{cx - 10, cx + 10, cx}, new int[]{cy - 2, cy - 2, cy - 14}, 3);
                g2d.fillPolygon(arrowHead);
                break;
            case 1:
                g2d.setColor(new Color(220, 60, 60));
                g2d.fillRect(cx - 2, cy - 12, 4, 18);
                g2d.fillPolygon(new int[]{cx-2, cx+2, cx}, new int[]{cy-12, cy-12, cy-16}, 3);
                g2d.setColor(new Color(180, 160, 60));
                g2d.fillRect(cx - 8, cy + 4, 16, 4);
                g2d.setColor(new Color(100, 60, 40));
                g2d.fillRect(cx - 2, cy + 8, 4, 6);
                break;
            case 2:
                g2d.setColor(new Color(60, 120, 220));
                Polygon shield = new Polygon(new int[]{cx - 10, cx + 10, cx + 10, cx, cx - 10}, new int[]{cy - 12, cy - 12, cy + 4, cy + 14, cy + 4}, 5);
                g2d.fillPolygon(shield);
                g2d.setColor(new Color(200, 200, 200));
                g2d.drawPolygon(shield);
                break;
            case 3:
                g2d.setColor(new Color(220, 50, 50));
                int[] ox = {cx-5, cx+5, cx+12, cx+12, cx+5, cx-5, cx-12, cx-12};
                int[] oy = {cy-12, cy-12, cy-5, cy+5, cy+12, cy+12, cy+5, cy-5};
                g2d.fillPolygon(ox, oy, 8);
                g2d.setColor(Color.WHITE);
                g2d.setStroke(new BasicStroke(3.0f));
                g2d.drawLine(cx - 6, cy - 6, cx + 6, cy + 6);
                g2d.drawLine(cx - 6, cy + 6, cx + 6, cy - 6);
                break;
        }

        g2d.setColor(isSelectedToggled ? Color.WHITE : Color.LIGHT_GRAY);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
        int tw = g2d.getFontMetrics().stringWidth(text);
        g2d.drawString(text, cx - (tw / 2), r.y + 60);
    }

    public boolean checkHUDLeftClick(Point p) {
        if (minimapBounds.contains(p)) {
            processMinimapClick(p);
            return true;
        }

        if (btnW.contains(p)) {
            panel.activeCommandMode = CommandType.MOVE;
            return true;
        }
        if (btnA.contains(p)) {
            panel.activeCommandMode = CommandType.ATTACK;
            return true;
        }
        if (btnS.contains(p)) {
            panel.activeCommandMode = CommandType.HOLD_POSITION;
            for (Unit u : panel.selectedUnits) {
                u.applyHoldPosition();
            }
            return true;
        }
        if (btnD.contains(p)) {
            for (Unit u : panel.selectedUnits) {
                u.applyHoldPosition();
            }
            return true;
        }

        return commandCardBounds.contains(p);
    }

    public void processMinimapClick(Point screenPoint) {
        int mx = screenPoint.x - (minimapBounds.x + TOTAL_BORDER_OFFSET);
        int my = screenPoint.y - (minimapBounds.y + TOTAL_BORDER_OFFSET);

        mx = Math.max(0, Math.min(mx, MINIMAP_DIM));
        my = Math.max(0, Math.min(my, MINIMAP_DIM));

        double pctX = (double) mx / MINIMAP_DIM;
        double pctY = (double) my / MINIMAP_DIM;

        double targetWorldX = minMapX + (pctX * (maxMapX - minMapX));
        double targetWorldY = minMapY + (pctY * (maxMapY - minMapY));

        panel.cameraManager.offsetX = (int) ((panel.getWidth() / (2.0 * panel.cameraManager.zoom)) - targetWorldX);
        panel.cameraManager.offsetY = (int) ((panel.getHeight() / (2.0 * panel.cameraManager.zoom)) - targetWorldY);
    }
}
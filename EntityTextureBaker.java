import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Random;

public class EntityTextureBaker {

    // --- Static definitions from old GameRenderer ---
    private static final GraphicsConfiguration GFX_CONFIG = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getDefaultScreenDevice().getDefaultConfiguration();

    private static final int NUM_FRAMES = 8;
    private static final int NUM_VARIATIONS = 25;

    public static BufferedImage bakeTownHallDay(int hexSize) {
        int imgW = hexSize * 22;
        int imgH = hexSize * 26;
        int cachedImgCX = imgW / 2;
        int cachedImgCY = imgH - (hexSize * 8);
        int terrainZ = 0; // Relative Z

        BufferedImage img = GFX_CONFIG.createCompatibleImage(imgW, imgH, Transparency.TRANSLUCENT);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        
        renderProceduralTownHall(g, cachedImgCX, cachedImgCY, terrainZ, hexSize, imgW, imgH, false);
        g.dispose();
        return img;
    }

    public static BufferedImage bakeTownHallNight(int hexSize) {
        int imgW = hexSize * 22;
        int imgH = hexSize * 26;
        int cachedImgCX = imgW / 2;
        int cachedImgCY = imgH - (hexSize * 8);
        int terrainZ = 0;

        BufferedImage img = GFX_CONFIG.createCompatibleImage(imgW, imgH, Transparency.TRANSLUCENT);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        
        renderProceduralTownHall(g, cachedImgCX, cachedImgCY, terrainZ, hexSize, imgW, imgH, true);
        g.dispose();
        return img;
    }

    private static void renderProceduralTownHall(Graphics2D g2d, int cx, int cy, int terrainZ,
                                          int hexSize, int imgW, int imgH, boolean isNight) {
        int rx       = (int)(hexSize * 5.8);
        int ryFront  = (int)(hexSize * 5.8);
        int ryBack   = (int)(hexSize * 2.9);

        Color stoneTop      = new Color(195, 185, 170);
        Color stoneSideL    = new Color(165, 155, 140);
        Color stoneSideR    = new Color(145, 135, 120);
        Color nobleGreenRoof    = new Color( 35, 110,  65);
        Color vibrantGreenFlag  = new Color( 25, 145,  55);

        int outerTowerRx       = (int)(hexSize * 1.2);
        int outerTowerRyFront  = (int)(hexSize * 1.2);
        int outerTowerRyBack   = (int)(hexSize * 0.6);
        int outerTowerH        = 45;

        drawSingleTower(g2d, cx, cy, terrainZ, rx, ryFront, ryBack,
                outerTowerRx, outerTowerRyFront, outerTowerRyBack, outerTowerH,
                3, stoneTop, stoneSideL, stoneSideR, nobleGreenRoof, vibrantGreenFlag, isNight);

        drawSingleTower(g2d, cx, cy, terrainZ, rx, ryFront, ryBack,
                outerTowerRx, outerTowerRyFront, outerTowerRyBack, outerTowerH,
                2, stoneTop, stoneSideL, stoneSideR, nobleGreenRoof, vibrantGreenFlag, isNight);
        drawSingleTower(g2d, cx, cy, terrainZ, rx, ryFront, ryBack,
                outerTowerRx, outerTowerRyFront, outerTowerRyBack, outerTowerH,
                4, stoneTop, stoneSideL, stoneSideR, nobleGreenRoof, vibrantGreenFlag, isNight);

        int currentZ = terrainZ;

        int foundationHeight = 35;
        drawTexturedIsoBlock(g2d, cx, cy, currentZ, rx, ryFront, ryBack,
                foundationHeight, stoneTop, stoneSideL, stoneSideR, true, false, isNight);
        currentZ += foundationHeight;

        int hallHeight = 65;
        int hallRx      = (int)(rx * 0.85);
        int hallRyFront = (int)(ryFront * 0.85);
        int hallRyBack  = (int)(ryBack  * 0.85);
        drawTexturedIsoBlock(g2d, cx, cy, currentZ, hallRx, hallRyFront, hallRyBack,
                hallHeight, stoneTop, stoneSideL, stoneSideR, true, true, isNight);
        currentZ += hallHeight;

        int lowerRoofHeight      = 40;
        int lowerRoofRx_Bot      = (int)(rx * 0.90);
        int lowerRoofRyFront_Bot = (int)(ryFront * 0.90);
        int lowerRoofRyBack_Bot  = (int)(ryBack  * 0.90);
        int lowerRoofRx_Top      = (int)(rx * 0.45);
        int lowerRoofRyFront_Top = (int)(ryFront * 0.45);
        int lowerRoofRyBack_Top  = (int)(ryBack  * 0.45);
        drawTexturedIsoRoofFrustum(g2d, cx, cy, currentZ,
                lowerRoofRx_Bot, lowerRoofRyFront_Bot, lowerRoofRyBack_Bot,
                lowerRoofRx_Top, lowerRoofRyFront_Top, lowerRoofRyBack_Top,
                lowerRoofHeight, nobleGreenRoof);
        currentZ += lowerRoofHeight;

        int keepHeight = 40;
        int keepRx      = (int)(rx * 0.45);
        int keepRyFront = (int)(ryFront * 0.45);
        int keepRyBack  = (int)(ryBack  * 0.45);
        drawTexturedIsoBlock(g2d, cx, cy, currentZ, keepRx, keepRyFront, keepRyBack,
                keepHeight, stoneTop, stoneSideL, stoneSideR, true, true, isNight);
        currentZ += keepHeight;

        int spireHeight = 85;
        int spireRx      = (int)(rx * 0.50);
        int spireRyFront = (int)(ryFront * 0.50);
        int spireRyBack  = (int)(ryBack  * 0.50);
        drawTexturedIsoSpire(g2d, cx, cy, currentZ, spireRx, spireRyFront, spireRyBack,
                spireHeight, nobleGreenRoof, vibrantGreenFlag, true);

        drawSingleTower(g2d, cx, cy, terrainZ, rx, ryFront, ryBack,
                outerTowerRx, outerTowerRyFront, outerTowerRyBack, outerTowerH,
                1, stoneTop, stoneSideL, stoneSideR, nobleGreenRoof, vibrantGreenFlag, isNight);
        drawSingleTower(g2d, cx, cy, terrainZ, rx, ryFront, ryBack,
                outerTowerRx, outerTowerRyFront, outerTowerRyBack, outerTowerH,
                5, stoneTop, stoneSideL, stoneSideR, nobleGreenRoof, vibrantGreenFlag, isNight);

        drawSingleTower(g2d, cx, cy, terrainZ, rx, ryFront, ryBack,
                outerTowerRx, outerTowerRyFront, outerTowerRyBack, outerTowerH,
                0, stoneTop, stoneSideL, stoneSideR, nobleGreenRoof, vibrantGreenFlag, isNight);
    }

    private static void drawSingleTower(Graphics2D g2d, int cx, int cy, int terrainZ,
                                 int rx, int ryFront, int ryBack,
                                 int outerTowerRx, int outerTowerRyFront, int outerTowerRyBack,
                                 int outerTowerH, int i,
                                 Color stoneTop, Color stoneSideL, Color stoneSideR,
                                 Color nobleGreenRoof, Color vibrantGreenFlag, boolean isNight) {
        double a    = Math.PI / 3 * i + Math.PI / 2;
        int    txX  = cx + (int)(rx * Math.cos(a));
        double sinA = Math.sin(a);
        int    txY  = cy + (int)((sinA >= 0 ? ryFront : ryBack) * sinA);

        drawTexturedIsoBlock(g2d, txX, txY, terrainZ,
                outerTowerRx, outerTowerRyFront, outerTowerRyBack, outerTowerH,
                stoneTop, stoneSideL, stoneSideR, true, true, isNight);
        drawTexturedIsoSpire(g2d, txX, txY, terrainZ + outerTowerH,
                outerTowerRx + 4, outerTowerRyFront + 4, outerTowerRyBack + 4,
                35, nobleGreenRoof, vibrantGreenFlag, false);
    }

    private static void drawTexturedIsoBlock(Graphics2D g, int cx, int cy, int z,
                                      int rx, int ryFront, int ryBack, int h,
                                      Color top, Color left, Color right,
                                      boolean isStone, boolean drawWindows, boolean isNight) {
        int[] tx = new int[6], ty = new int[6], bx = new int[6], by = new int[6];
        for (int i = 0; i < 6; i++) {
            double a    = Math.PI / 3 * i + Math.PI / 2;
            int    dx   = (int)(rx * Math.cos(a));
            double sinA = Math.sin(a);
            int    dy   = (int)((sinA >= 0 ? ryFront : ryBack) * sinA);
            tx[i] = cx + dx; ty[i] = cy + dy - z - h;
            bx[i] = cx + dx; by[i] = cy + dy - z;
        }

        Polygon faceLeft      = new Polygon(new int[]{ tx[2],tx[1],bx[1],bx[2] }, new int[]{ ty[2],ty[1],by[1],by[2] }, 4);
        Polygon faceFrontLeft  = new Polygon(new int[]{ tx[1],tx[0],bx[0],bx[1] }, new int[]{ ty[1],ty[0],by[0],by[1] }, 4);
        Polygon faceFrontRight = new Polygon(new int[]{ tx[0],tx[5],bx[5],bx[0] }, new int[]{ ty[0],ty[5],by[5],by[0] }, 4);
        Polygon faceRight      = new Polygon(new int[]{ tx[5],tx[4],bx[4],bx[5] }, new int[]{ ty[5],ty[4],by[4],by[5] }, 4);

        g.setColor(blendColor(left, Color.BLACK, 0.25f)); g.fillPolygon(faceLeft);
        g.setColor(left);                                  g.fillPolygon(faceFrontLeft);
        g.setColor(right);                                 g.fillPolygon(faceFrontRight);
        g.setColor(blendColor(right, Color.BLACK, 0.15f)); g.fillPolygon(faceRight);

        if (isStone) {
            applyVectorBrickTexture(g, 2, 1, h, bx, by, true);
            applyVectorBrickTexture(g, 1, 0, h, bx, by, false);
            applyVectorBrickTexture(g, 0, 5, h, bx, by, false);
            applyVectorBrickTexture(g, 5, 4, h, bx, by, true);
        }

        if (drawWindows) {
            drawIsometricWindow(g, 1, 0, bx, by, h, isNight);
            drawIsometricWindow(g, 0, 5, bx, by, h, isNight);
        }

        g.setColor(new Color(110, 100, 85));
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(tx[2], ty[2], bx[2], by[2]);
        g.drawLine(tx[1], ty[1], bx[1], by[1]);
        g.drawLine(tx[0], ty[0], bx[0], by[0]);
        g.drawLine(tx[5], ty[5], bx[5], by[5]);
        g.drawLine(tx[4], ty[4], bx[4], by[4]);

        g.setColor(top);
        g.fillPolygon(tx, ty, 6);
        g.setColor(new Color(80, 75, 65, 80));
        g.setStroke(new BasicStroke(1.0f));
        g.drawPolygon(tx, ty, 6);
    }

    private static void applyVectorBrickTexture(Graphics2D g, int sIdx, int eIdx, int h,
                                         int[] bx, int[] by, boolean dark) {
        g.setColor(dark ? new Color(60, 55, 50, 45) : new Color(60, 55, 50, 25));
        g.setStroke(new BasicStroke(1.0f));

        int brickH = 8;
        int brickW = 16;

        int x1 = bx[sIdx], y1 = by[sIdx];
        int x2 = bx[eIdx], y2 = by[eIdx];
        double faceWidth = Math.abs(x2 - x1);
        if (faceWidth < 1) faceWidth = 1;

        int row = 0;
        for (int offset = 0; offset < h + brickH; offset += brickH) {
            g.drawLine(x1, y1 - offset, x2, y2 - offset);
            if (offset > 0) {
                int stagger = (row % 2 == 0) ? 0 : brickW / 2;
                double steps = faceWidth / brickW + 2;
                for (int k = -1; k < steps; k++) {
                    double currX = Math.min(x1, x2) + (k * brickW) + stagger;
                    double t = (currX - x1) / (x2 - x1);
                    if (t >= 0.0 && t <= 1.0) {
                        int xJoint  = (int) currX;
                        int yBottom = (int)(y1 - (offset - brickH) + t * (y2 - y1));
                        int yTop    = (int)(y1 - offset             + t * (y2 - y1));
                        g.drawLine(xJoint, yTop, xJoint, yBottom);
                    }
                }
            }
            row++;
        }
    }

    private static void drawIsometricWindow(Graphics2D g, int sIdx, int eIdx,
                                     int[] bx, int[] by, int h, boolean isNight) {
        int x1 = bx[sIdx], y1 = by[sIdx];
        int x2 = bx[eIdx], y2 = by[eIdx];

        int cxWin = (int)((x1 + x2) / 2.0);
        int cyWin = (int)(((y1 + y2) / 2.0) - h / 2.0);

        double ux      = (x2 - x1) * 0.14;
        double uy      = (y2 - y1) * 0.14;
        int    winHalfH = 12;

        int[] wx = { (int)(cxWin - ux), (int)(cxWin + ux), (int)(cxWin + ux), (int)(cxWin - ux) };
        int[] wy = { (int)(cyWin - uy - winHalfH), (int)(cyWin + uy - winHalfH),
                (int)(cyWin + uy + winHalfH), (int)(cyWin - uy + winHalfH) };
        Polygon winPoly = new Polygon(wx, wy, 4);

        g.setColor(new Color(218, 165, 32));
        g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawPolygon(winPoly);

        g.setColor(isNight ? new Color(255, 235, 60) : new Color(180, 230, 255));
        g.fillPolygon(winPoly);

        g.setColor(isNight ? new Color(130, 90, 20, 160) : new Color(70, 120, 160, 120));
        g.setStroke(new BasicStroke(1.0f));
        g.drawLine(cxWin, cyWin - winHalfH, cxWin, cyWin + winHalfH);
        g.drawLine((int)(cxWin - ux), (int)(cyWin - uy), (int)(cxWin + ux), (int)(cyWin + uy));
    }

    private static void drawTexturedIsoRoofFrustum(Graphics2D g, int cx, int cy, int z,
                                            int rxBot, int ryBotFront, int ryBotBack,
                                            int rxTop, int ryTopFront, int ryTopBack,
                                            int h, Color roofColor) {
        int[] tx = new int[6], ty = new int[6], bx = new int[6], by = new int[6];
        for (int i = 0; i < 6; i++) {
            double a    = Math.PI / 3 * i + Math.PI / 2;
            int    bdx  = (int)(rxBot * Math.cos(a));
            double sinA = Math.sin(a);
            int    bdy  = (int)((sinA >= 0 ? ryBotFront : ryBotBack) * sinA);
            int    tdx  = (int)(rxTop * Math.cos(a));
            int    tdy  = (int)((sinA >= 0 ? ryTopFront : ryTopBack) * sinA);
            tx[i] = cx + tdx; ty[i] = cy + tdy - z - h;
            bx[i] = cx + bdx; by[i] = cy + bdy - z;
        }

        Polygon pLeft       = new Polygon(new int[]{ tx[2],tx[1],bx[1],bx[2] }, new int[]{ ty[2],ty[1],by[1],by[2] }, 4);
        Polygon pFrontLeft  = new Polygon(new int[]{ tx[1],tx[0],bx[0],bx[1] }, new int[]{ ty[1],ty[0],by[0],by[1] }, 4);
        Polygon pFrontRight = new Polygon(new int[]{ tx[0],tx[5],bx[5],bx[0] }, new int[]{ ty[0],ty[5],by[5],by[0] }, 4);
        Polygon pRight      = new Polygon(new int[]{ tx[5],tx[4],bx[4],bx[5] }, new int[]{ ty[5],ty[4],by[4],by[5] }, 4);

        g.setColor(blendColor(roofColor, Color.BLACK, 0.35f)); g.fillPolygon(pLeft);
        applyTiltedRoofTiles(g, 2, 1, bx, by, tx, ty);

        g.setColor(blendColor(roofColor, Color.BLACK, 0.10f)); g.fillPolygon(pFrontLeft);
        applyTiltedRoofTiles(g, 1, 0, bx, by, tx, ty);

        g.setColor(blendColor(roofColor, Color.WHITE, 0.05f)); g.fillPolygon(pFrontRight);
        applyTiltedRoofTiles(g, 0, 5, bx, by, tx, ty);

        g.setColor(blendColor(roofColor, Color.BLACK, 0.25f)); g.fillPolygon(pRight);
        applyTiltedRoofTiles(g, 5, 4, bx, by, tx, ty);

        g.setColor(blendColor(roofColor, Color.BLACK, 0.4f));
        g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawPolygon(pLeft);
        g.drawPolygon(pFrontLeft);
        g.drawPolygon(pFrontRight);
        g.drawPolygon(pRight);
    }

    private static void drawTexturedIsoSpire(Graphics2D g, int cx, int cy, int z,
                                      int rx, int ryFront, int ryBack, int h,
                                      Color roofColor, Color flagColor, boolean isMain) {
        int[] bx = new int[6], by = new int[6];
        for (int i = 0; i < 6; i++) {
            double a    = Math.PI / 3 * i + Math.PI / 2;
            int    dx   = (int)(rx * Math.cos(a));
            double sinA = Math.sin(a);
            int    dy   = (int)((sinA >= 0 ? ryFront : ryBack) * sinA);
            bx[i] = cx + dx; by[i] = cy + dy - z;
        }
        int peakX = cx, peakY = cy - z - h;

        int[] tx = { peakX, peakX, peakX, peakX, peakX, peakX };
        int[] ty = { peakY, peakY, peakY, peakY, peakY, peakY };

        Polygon pLeft       = new Polygon(new int[]{ peakX, bx[2], bx[1] }, new int[]{ peakY, by[2], by[1] }, 3);
        Polygon pFrontLeft  = new Polygon(new int[]{ peakX, bx[1], bx[0] }, new int[]{ peakY, by[1], by[0] }, 3);
        Polygon pFrontRight = new Polygon(new int[]{ peakX, bx[0], bx[5] }, new int[]{ peakY, by[0], by[5] }, 3);
        Polygon pRight      = new Polygon(new int[]{ peakX, bx[5], bx[4] }, new int[]{ peakY, by[5], by[4] }, 3);

        g.setColor(blendColor(roofColor, Color.BLACK, 0.35f)); g.fillPolygon(pLeft);
        applyTiltedRoofTiles(g, 2, 1, bx, by, tx, ty);

        g.setColor(blendColor(roofColor, Color.BLACK, 0.10f)); g.fillPolygon(pFrontLeft);
        applyTiltedRoofTiles(g, 1, 0, bx, by, tx, ty);

        g.setColor(blendColor(roofColor, Color.WHITE, 0.05f)); g.fillPolygon(pFrontRight);
        applyTiltedRoofTiles(g, 0, 5, bx, by, tx, ty);

        g.setColor(blendColor(roofColor, Color.BLACK, 0.25f)); g.fillPolygon(pRight);
        applyTiltedRoofTiles(g, 5, 4, bx, by, tx, ty);

        g.setColor(blendColor(roofColor, Color.BLACK, 0.4f));
        g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(peakX, peakY, bx[2], by[2]); g.drawLine(peakX, peakY, bx[1], by[1]);
        g.drawLine(peakX, peakY, bx[0], by[0]); g.drawLine(peakX, peakY, bx[5], by[5]);
        g.drawLine(peakX, peakY, bx[4], by[4]);
        g.drawLine(bx[2], by[2], bx[1], by[1]); g.drawLine(bx[1], by[1], bx[0], by[0]);
        g.drawLine(bx[0], by[0], bx[5], by[5]); g.drawLine(bx[5], by[5], bx[4], by[4]);

        Color polishedGold = new Color(218, 165, 32);
        if (isMain) {
            g.setColor(polishedGold);
            g.fillPolygon(
                    new int[]{ peakX - 14, peakX, peakX + 14, peakX },
                    new int[]{ peakY,      peakY - 28, peakY,  peakY + 8 }, 4);
            drawHexShape(g, peakX, peakY - 14, 7);
        } else {
            g.setColor(new Color(110, 90, 70));
            g.fillRect(peakX - 1, peakY - 35, 2, 35);
            g.setColor(polishedGold);
            drawHexShape(g, peakX, peakY - 35, 4);
            g.setColor(flagColor);
            g.fillPolygon(
                    new int[]{ peakX,      peakX + 30, peakX + 20, peakX + 30, peakX      },
                    new int[]{ peakY - 35, peakY - 35, peakY - 24, peakY - 13, peakY - 13 }, 5);
        }
    }

    private static void applyTiltedRoofTiles(Graphics2D g, int sIdx, int eIdx,
                                      int[] bx, int[] by, int[] tx, int[] ty) {
        Shape oldClip = g.getClip();
        Polygon segmentMask = new Polygon(
                new int[]{ bx[sIdx], bx[eIdx], tx[eIdx], tx[sIdx] },
                new int[]{ by[sIdx], by[eIdx], ty[eIdx], ty[sIdx] }, 4);
        g.setClip(segmentMask);

        g.setColor(new Color(15, 40, 25, 100));
        g.setStroke(new BasicStroke(1.2f));

        int tileH = 7;
        int tileW = 10;

        int minY = Math.min(Math.min(ty[sIdx], ty[eIdx]), Math.min(by[sIdx], by[eIdx]));
        int maxY = Math.max(Math.max(ty[sIdx], ty[eIdx]), Math.max(by[sIdx], by[eIdx]));
        int renderSpan = maxY - minY + tileH;

        int row = 0;
        for (int offset = 0; offset < renderSpan; offset += tileH) {
            int x1 = bx[sIdx], y1 = by[sIdx] - offset;
            int x2 = bx[eIdx], y2 = by[eIdx] - offset;
            g.drawLine(x1, y1, x2, y2);

            int stagger = (row % 2 == 0) ? 0 : tileW / 2;
            int boundL = Math.min(x1, x2) - tileW;
            int boundR = Math.max(x1, x2) + tileW;
            for (int sx = boundL; sx < boundR; sx += tileW) {
                int    currX   = sx + stagger;
                double t       = (double)(currX - x1) / (x2 - x1);
                int    yBottom = (int)(y1 + t * (y2 - y1));
                g.drawLine(currX, yBottom, currX, yBottom - tileH);
            }
            row++;
        }
        g.setClip(oldClip);
    }

    private static void drawHexShape(Graphics2D g, int cx, int cy, int radius) {
        int[] hx = new int[6];
        int[] hy = new int[6];
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 3 * i;
            hx[i] = cx + (int)(radius * Math.cos(a));
            hy[i] = cy + (int)(radius * Math.sin(a));
        }
        g.fillPolygon(hx, hy, 6);
    }

    private static Color blendColor(Color base, Color mix, float ratio) {
        int r = (int)(base.getRed()   * (1 - ratio) + mix.getRed()   * ratio);
        int g = (int)(base.getGreen() * (1 - ratio) + mix.getGreen() * ratio);
        int b = (int)(base.getBlue()  * (1 - ratio) + mix.getBlue()  * ratio);
        return new Color(r, g, b);
    }
}

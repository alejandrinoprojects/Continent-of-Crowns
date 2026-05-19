import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

public class MenuRenderer {

    private static final Color BG_DARK       = new Color(15, 18, 24);
    private static final Color BG_PANEL      = new Color(30, 35, 45);
    private static final Color BG_BUTTON     = new Color(40, 45, 55);
    private static final Color BG_BUTTON_HOV = new Color(55, 62, 75);
    private static final Color GOLD_ACCENT   = new Color(212, 175, 55);
    private static final Color TEXT_PRIMARY  = new Color(235, 225, 205);
    private static final Color TEXT_SECONDARY= new Color(160, 170, 185);
    private static final Color TEXT_DIM      = new Color(100, 110, 130);
    private static final Color BTN_GREEN     = new Color(75, 130, 60);
    private static final Color BTN_BLUE      = new Color(60, 100, 160);
    private static final Color BTN_RED       = new Color(150, 50, 50);

    private final Rectangle playWorldBtn;
    private final Rectangle playTesterBtn;
    private final Rectangle quitBtn;
    private long lastCursorToggle = 0;
    private boolean cursorVisible = true;

    private Rectangle hoverBtn = null;

    public MenuRenderer(Rectangle playWorldBtn, Rectangle playTesterBtn, Rectangle quitBtn) {
        this.playWorldBtn = playWorldBtn;
        this.playTesterBtn = playTesterBtn;
        this.quitBtn = quitBtn;
    }

    public void setHoveredButton(Rectangle btn) { this.hoverBtn = btn; }

    public void drawMainMenu(Graphics2D g2d, int width, int height) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background gradient
        GradientPaint bgGrad = new GradientPaint(0, 0, new Color(12, 15, 22), 0, height, new Color(20, 24, 34));
        g2d.setPaint(bgGrad);
        g2d.fillRect(0, 0, width, height);

        // Subtle hex grid pattern overlay
        drawHexPattern(g2d, width, height);

        // Center panel
        int panelW = 520;
        int panelH = 420;
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2 - 30;

        g2d.setColor(BG_PANEL);
        g2d.fillRoundRect(panelX, panelY, panelW, panelH, 12, 12);

        g2d.setColor(GOLD_ACCENT);
        g2d.setStroke(new BasicStroke(2.0f));
        g2d.drawRoundRect(panelX, panelY, panelW, panelH, 12, 12);

        // Gold accent line under title area
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawLine(panelX + 40, panelY + 95, panelX + panelW - 40, panelY + 95);

        // Title
        g2d.setColor(TEXT_PRIMARY);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 38));
        String title = "CONTINENTAL CONQUEST";
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString(title, panelX + (panelW - fm.stringWidth(title)) / 2, panelY + 60);

        // Subtitle
        g2d.setColor(TEXT_SECONDARY);
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 14));
        String subtitle = "Hex-Based Real-Time Strategy";
        fm = g2d.getFontMetrics();
        g2d.drawString(subtitle, panelX + (panelW - fm.stringWidth(subtitle)) / 2, panelY + 82);

        // Buttons
        int btnW = 280;
        int btnH = 48;
        int btnX = panelX + (panelW - btnW) / 2;
        int btnGap = 14;
        int startY = panelY + 120;

        playWorldBtn.setBounds(btnX, startY, btnW, btnH);
        playTesterBtn.setBounds(btnX, startY + btnH + btnGap, btnW, btnH);
        quitBtn.setBounds(btnX, startY + (btnH + btnGap) * 2, btnW, btnH);

        drawStyledButton(g2d, playWorldBtn, "PLAY WORLD", BTN_GREEN, hoverBtn == playWorldBtn);
        drawStyledButton(g2d, playTesterBtn, "PLAY TESTER", BTN_BLUE, hoverBtn == playTesterBtn);
        drawStyledButton(g2d, quitBtn, "QUIT", BTN_RED, hoverBtn == quitBtn);

        // Footer text
        g2d.setColor(TEXT_DIM);
        g2d.setFont(new Font("SansSerif", Font.ITALIC, 11));
        String footer = "v1.0  |  Procedural World Generation";
        fm = g2d.getFontMetrics();
        g2d.drawString(footer, panelX + (panelW - fm.stringWidth(footer)) / 2, panelY + panelH - 20);
    }

    private void drawHexPattern(Graphics2D g2d, int w, int h) {
        g2d.setColor(new Color(255, 255, 255, 4));
        g2d.setStroke(new BasicStroke(0.5f));
        int hexR = 25;
        double sqrt3 = Math.sqrt(3);
        for (int row = -1; row < h / (hexR * 3) + 1; row++) {
            for (int col = -1; col < w / (hexR * 3) + 1; col++) {
                int cx = (int)(col * hexR * 3);
                int cy = (int)(row * hexR * sqrt3 * 1.5 + (col % 2) * hexR * sqrt3 * 0.75);
                int[] hx = new int[6];
                int[] hy = new int[6];
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI / 3 * i - Math.PI / 6;
                    hx[i] = cx + (int)(hexR * Math.cos(a));
                    hy[i] = cy + (int)(hexR * Math.sin(a));
                }
                g2d.drawPolygon(hx, hy, 6);
            }
        }
    }

    private void drawStyledButton(Graphics2D g2d, Rectangle r, String text, Color baseColor, boolean hovered) {
        Color fill = hovered ? BG_BUTTON_HOV : BG_BUTTON;
        g2d.setColor(fill);
        g2d.fillRoundRect(r.x, r.y, r.width, r.height, 8, 8);

        // Left accent bar
        g2d.setColor(baseColor);
        g2d.fillRoundRect(r.x, r.y, 4, r.height, 8, 8);

        // Border
        g2d.setColor(hovered ? TEXT_DIM : new Color(60, 65, 75));
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawRoundRect(r.x, r.y, r.width, r.height, 8, 8);

        // Text
        g2d.setColor(TEXT_PRIMARY);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 16));
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString(text, r.x + (r.width - fm.stringWidth(text)) / 2, r.y + 30);
    }

    public void drawWorldSettingsMenu(Graphics2D g2d, int w, int h, int currentPercentage, String seedStr,
                                      Rectangle minusBtn, Rectangle plusBtn, Rectangle start, Rectangle back) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        GradientPaint bgGrad = new GradientPaint(0, 0, new Color(12, 15, 22), 0, h, new Color(20, 24, 34));
        g2d.setPaint(bgGrad);
        g2d.fillRect(0, 0, w, h);

        drawHexPattern(g2d, w, h);

        // Center panel
        int panelW = 560;
        int panelH = 500;
        int panelX = (w - panelW) / 2;
        int panelY = (h - panelH) / 2 - 20;

        g2d.setColor(BG_PANEL);
        g2d.fillRoundRect(panelX, panelY, panelW, panelH, 12, 12);

        g2d.setColor(GOLD_ACCENT);
        g2d.setStroke(new BasicStroke(2.0f));
        g2d.drawRoundRect(panelX, panelY, panelW, panelH, 12, 12);

        // Gold accent line
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawLine(panelX + 40, panelY + 65, panelX + panelW - 40, panelY + 65);

        // Title
        g2d.setColor(TEXT_PRIMARY);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 28));
        String title = "WORLD GENERATION";
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString(title, panelX + (panelW - fm.stringWidth(title)) / 2, panelY + 50);

        // Section 1: World Size
        int secY = panelY + 90;
        g2d.setColor(TEXT_SECONDARY);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 13));
        g2d.drawString("WORLD SIZE", panelX + 40, secY);

        // Percentage display in a rounded box
        int pctBoxW = 160;
        int pctBoxH = 44;
        int pctBoxX = panelX + (panelW - pctBoxW) / 2;
        int pctBoxY = secY + 15;

        g2d.setColor(new Color(20, 25, 35));
        g2d.fillRoundRect(pctBoxX, pctBoxY, pctBoxW, pctBoxH, 8, 8);
        g2d.setColor(TEXT_DIM);
        g2d.drawRoundRect(pctBoxX, pctBoxY, pctBoxW, pctBoxH, 8, 8);

        g2d.setColor(TEXT_PRIMARY);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 24));
        String pctText = currentPercentage + "%";
        fm = g2d.getFontMetrics();
        g2d.drawString(pctText, pctBoxX + (pctBoxW - fm.stringWidth(pctText)) / 2, pctBoxY + 31);

        // Minus / Plus buttons
        int ctrlBtnW = 44;
        int ctrlBtnH = 44;
        int ctrlY = pctBoxY;
        int minusX = pctBoxX - ctrlBtnW - 12;
        int plusX = pctBoxX + pctBoxW + 12;

        minusBtn.setBounds(minusX, ctrlY, ctrlBtnW, ctrlBtnH);
        plusBtn.setBounds(plusX, ctrlY, ctrlBtnW, ctrlBtnH);

        drawStyledButton(g2d, minusBtn, "\u2212", BTN_BLUE, hoverBtn == minusBtn);
        drawStyledButton(g2d, plusBtn, "+", BTN_BLUE, hoverBtn == plusBtn);

        // Estimated dimensions
        g2d.setColor(TEXT_DIM);
        g2d.setFont(new Font("SansSerif", Font.ITALIC, 12));
        int estTiles = (int) (450 * (currentPercentage / 100.0));
        String dimText = estTiles + " x " + estTiles + " tiles";
        fm = g2d.getFontMetrics();
        g2d.drawString(dimText, panelX + (panelW - fm.stringWidth(dimText)) / 2, pctBoxY + pctBoxH + 22);

        // Section 2: Seed
        int seedY = pctBoxY + pctBoxH + 55;
        g2d.setColor(TEXT_SECONDARY);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 13));
        g2d.drawString("SEED (blank = random)", panelX + 40, seedY);

        // Seed input box
        int seedBoxW = 300;
        int seedBoxH = 40;
        int seedBoxX = panelX + (panelW - seedBoxW) / 2;
        int seedBoxY = seedY + 12;

        g2d.setColor(new Color(20, 22, 28));
        g2d.fillRoundRect(seedBoxX, seedBoxY, seedBoxW, seedBoxH, 8, 8);
        g2d.setColor(TEXT_DIM);
        g2d.drawRoundRect(seedBoxX, seedBoxY, seedBoxW, seedBoxH, 8, 8);

        g2d.setColor(new Color(80, 220, 80));
        g2d.setFont(new Font("Monospaced", Font.BOLD, 16));
        long now = System.currentTimeMillis();
        if (now - lastCursorToggle > 500) { lastCursorToggle = now; cursorVisible = !cursorVisible; }
        String seedDisplay = seedStr.isEmpty() ? "random" : seedStr;
        if (!seedStr.isEmpty() && cursorVisible) seedDisplay += "_";
        g2d.drawString(seedDisplay, seedBoxX + 14, seedBoxY + 26);

        // Action buttons
        int btnW = 200;
        int btnH = 44;
        int btnY = seedBoxY + seedBoxH + 30;
        int btnX = panelX + (panelW - btnW) / 2;

        start.setBounds(btnX, btnY, btnW, btnH);
        back.setBounds(btnX, btnY + btnH + 12, btnW, btnH);

        drawStyledButton(g2d, start, "GENERATE WORLD", BTN_GREEN, hoverBtn == start);
        drawStyledButton(g2d, back, "BACK", BTN_RED, hoverBtn == back);
    }

    public void drawLoadingScreen(Graphics2D g2d, int width, int height, int loadingProgress, String loadingStatus) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        GradientPaint bgGrad = new GradientPaint(0, 0, new Color(12, 15, 22), 0, height, new Color(20, 24, 34));
        g2d.setPaint(bgGrad);
        g2d.fillRect(0, 0, width, height);

        drawHexPattern(g2d, width, height);

        // Center panel
        int panelW = 500;
        int panelH = 200;
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;

        g2d.setColor(BG_PANEL);
        g2d.fillRoundRect(panelX, panelY, panelW, panelH, 12, 12);

        g2d.setColor(GOLD_ACCENT);
        g2d.setStroke(new BasicStroke(2.0f));
        g2d.drawRoundRect(panelX, panelY, panelW, panelH, 12, 12);

        // Title
        g2d.setColor(TEXT_PRIMARY);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 22));
        String title = "GENERATING WORLD";
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString(title, panelX + (panelW - fm.stringWidth(title)) / 2, panelY + 45);

        // Progress bar
        int barW = 380;
        int barH = 18;
        int barX = panelX + (panelW - barW) / 2;
        int barY = panelY + 70;

        g2d.setColor(new Color(20, 25, 35));
        g2d.fillRoundRect(barX, barY, barW, barH, 9, 9);

        int fillW = (int) (barW * (loadingProgress / 100.0));
        if (fillW > 0) {
            g2d.setColor(GOLD_ACCENT);
            g2d.fillRoundRect(barX, barY, fillW, barH, 9, 9);
        }

        g2d.setColor(TEXT_DIM);
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawRoundRect(barX, barY, barW, barH, 9, 9);

        // Percentage
        g2d.setColor(TEXT_PRIMARY);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 14));
        String pctText = loadingProgress + "%";
        fm = g2d.getFontMetrics();
        g2d.drawString(pctText, barX + (barW - fm.stringWidth(pctText)) / 2, barY + barH + 25);

        // Status
        g2d.setColor(TEXT_SECONDARY);
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 12));
        fm = g2d.getFontMetrics();
        g2d.drawString(loadingStatus, panelX + (panelW - fm.stringWidth(loadingStatus)) / 2, barY + barH + 50);
    }
}

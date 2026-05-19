import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

public class PauseManager {
    private boolean paused = false;

    public boolean isPaused() { return paused; }
    public void togglePause() { paused = !paused; }

    public void drawPauseOverlay(Graphics2D g2d, int width, int height, StatsManager stats) {
        if (!paused) return;

        g2d.setColor(new Color(0, 0, 0, 180));
        g2d.fillRect(0, 0, width, height);

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 48));
        String mainText = "GAME PAUSED";
        FontMetrics fm = g2d.getFontMetrics();
        int mainX = (width - fm.stringWidth(mainText)) / 2;
        int mainY = 100;
        g2d.drawString(mainText, mainX, mainY);

        g2d.setFont(new Font("SansSerif", Font.PLAIN, 18));
        String subText = "Press 'P' to Resume";
        FontMetrics fmSub = g2d.getFontMetrics();
        int subX = (width - fmSub.stringWidth(subText)) / 2;
        int subY = mainY + 40;
        g2d.drawString(subText, subX, subY);

        int boxW = 450; int boxH = 320;
        int boxX = (width - boxW) / 2;
        int boxY = subY + 50;

        g2d.setColor(new Color(45, 45, 45, 230));
        g2d.fillRect(boxX, boxY, boxW, boxH);
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.drawRect(boxX, boxY, boxW, boxH);

        g2d.setColor(Color.YELLOW);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 22));
        g2d.drawString("CURRENT PLAYER STATS", boxX + 25, boxY + 40);

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 18));
        int startValX = boxX + boxW - 140;
        int currentY = boxY + 95;

        String[] labels = { "Total Kills:", "Total Losses:", "Damage Dealt:", "Damage Taken:", "Healing Done:", "Kill / Death Ratio:" };
        String[] values = {
                String.valueOf(stats.getTotalKills()),
                String.valueOf(stats.getTotalLosses()),
                String.format("%.1f", stats.getTotalDamageDealt()),
                String.format("%.1f", stats.getTotalDamageTaken()),
                String.valueOf(stats.getTotalHealingDone()),
                String.format("%.2f", stats.getKillDeathRatio())
        };

        for (int i = 0; i < labels.length; i++) {
            g2d.drawString(labels[i], boxX + 35, currentY);
            g2d.drawString(values[i], startValX, currentY);
            currentY += 35;
        }
    }
}
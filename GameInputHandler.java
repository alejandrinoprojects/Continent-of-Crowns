import javax.swing.SwingUtilities;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

public class GameInputHandler implements MouseListener, MouseMotionListener, MouseWheelListener, KeyListener {

    private final GamePanel panel;
    private boolean isMinimapDragging = false;

    public GameInputHandler(GamePanel panel) {
        this.panel = panel;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (panel.currentState == GameState.MENU) {
            Point clickPoint = e.getPoint();
            if (panel.playWorldBtn.contains(clickPoint)) {
                panel.currentState = GameState.WORLD_SETTINGS;
                panel.repaint();
            } else if (panel.playTesterBtn.contains(clickPoint)) {
                panel.enterTesterMode();
            } else if (panel.quitBtn.contains(clickPoint)) {
                System.exit(0);
            }
            return;
        }

        // --- PROCESS MINUS / PLUS REGIONS ---
        if (panel.currentState == GameState.WORLD_SETTINGS) {
            Point p = e.getPoint();
            if (panel.sizeMinusBtn.contains(p)) {
                panel.worldSizePercentage = Math.max(25, panel.worldSizePercentage - 25);
            }
            else if (panel.sizePlusBtn.contains(p)) {
                panel.worldSizePercentage = Math.min(500, panel.worldSizePercentage + 25);
            }
            else if (panel.startWorldBtn.contains(p)) { panel.enterWorldMode(); }
            else if (panel.settingsBackBtn.contains(p)) { panel.currentState = GameState.MENU; }
            panel.repaint();
            return;
        }

        if (panel.currentState == GameState.LOADING) return;

        boolean hitBaseHUD = e.getY() >= panel.getHeight() - RTSHUD.HUD_HEIGHT;
        boolean hitMinimap = panel.hudManager.minimapBounds.contains(e.getPoint());

        if (hitBaseHUD || hitMinimap) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                if (hitMinimap) {
                    isMinimapDragging = true;
                }
                panel.hudManager.checkHUDLeftClick(e.getPoint());
            }
            return;
        }

        if (SwingUtilities.isMiddleMouseButton(e)) {
            panel.isMiddleDragging = true;
            panel.cameraManager.startPanning(e.getX(), e.getY());
            return;
        }

        Point worldPos = panel.cameraManager.toWorld(e.getX(), e.getY());

        if (SwingUtilities.isRightMouseButton(e)) {
            if (!panel.selectedUnits.isEmpty()) {
                Point hex = GamePanel.pixelToHex(worldPos.x, worldPos.y);
                boolean onUnit;
                synchronized (panel.unitsLock) {
                    onUnit = panel.selectionManager.isPointOnAnyUnit(worldPos.x, worldPos.y, panel.units);
                }

                CommandType cmd = CommandType.MOVE;
                if (panel.activeCommandMode == CommandType.ATTACK) {
                    cmd = CommandType.ATTACK;
                } else if (panel.activeCommandMode == CommandType.HOLD_POSITION) {
                    cmd = CommandType.MOVE;
                    panel.activeCommandMode = CommandType.MOVE;
                }

                if (onUnit) {
                    cmd = CommandType.ATTACK;
                }

                if (panel.selectedUnits.size() == 1) {
                    panel.selectedUnits.get(0).applyNewOrder(new Order(cmd, hex.x, hex.y));
                } else {
                    panel.movementController.issueGroupMove(panel.selectedUnits, hex.x, hex.y, cmd);
                }
            }
            return;
        }

        panel.dragStartX = worldPos.x;
        panel.dragStartY = worldPos.y;
        panel.dragging = true;
        panel.selectionManager.startDrag(panel.dragStartX, panel.dragStartY);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            panel.currentState = GameState.MENU;
            panel.repaint();
            return;
        }

        if (panel.currentState == GameState.WORLD_SETTINGS) {
            if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                if (panel.seedInputString.length() > 0) {
                    panel.seedInputString = panel.seedInputString.substring(0, panel.seedInputString.length() - 1);
                }
            } else {
                char c = e.getKeyChar();
                if (Character.isDigit(c) && panel.seedInputString.length() < 12) {
                    panel.seedInputString += c;
                }
            }
            panel.repaint();
            return;
        }

        if (panel.currentState == GameState.PLAY_WORLD || panel.currentState == GameState.PLAY_TESTER) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_W: panel.activeCommandMode = CommandType.MOVE; panel.repaint(); return;
                case KeyEvent.VK_A: panel.activeCommandMode = CommandType.ATTACK; panel.repaint(); return;
                case KeyEvent.VK_S:
                    panel.activeCommandMode = CommandType.HOLD_POSITION;
                    for (Unit u : panel.selectedUnits) u.applyHoldPosition();
                    panel.repaint(); return;
                case KeyEvent.VK_D:
                    for (Unit u : panel.selectedUnits) u.applyHoldPosition();
                    panel.repaint(); return;
            }
        }

        if (e.getKeyCode() == KeyEvent.VK_R) {
            if (panel.currentState == GameState.PLAY_WORLD) {
                panel.enterWorldMode();
            } else if (panel.currentState == GameState.PLAY_TESTER) {
                panel.enterTesterMode();
            }
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (panel.currentState == GameState.MENU || panel.currentState == GameState.WORLD_SETTINGS || panel.currentState == GameState.LOADING) return;
        if (isMinimapDragging) { isMinimapDragging = false; return; }

        boolean hitBaseHUD = e.getY() >= panel.getHeight() - RTSHUD.HUD_HEIGHT;
        boolean hitMinimap = panel.hudManager.minimapBounds.contains(e.getPoint());
        if (hitBaseHUD || hitMinimap) {
            if (panel.dragging) {
                panel.dragging = false;
                Point finalPos = panel.cameraManager.toWorld(e.getX(), e.getY());
                panel.dragEndX = finalPos.x;
                panel.dragEndY = finalPos.y;
                synchronized (panel.unitsLock) {
                    panel.selectionManager.endDrag(panel.dragEndX, panel.dragEndY, panel.units);
                }
            }
            return;
        }

        if (SwingUtilities.isMiddleMouseButton(e)) {
            panel.isMiddleDragging = false;
            panel.cameraManager.stopPanning();
            return;
        }
        if (SwingUtilities.isRightMouseButton(e)) return;

        Point worldPos = panel.cameraManager.toWorld(e.getX(), e.getY());
        panel.dragEndX = worldPos.x;
        panel.dragEndY = worldPos.y;
        panel.dragging = false;
        synchronized (panel.unitsLock) {
            panel.selectionManager.endDrag(panel.dragEndX, panel.dragEndY, panel.units);
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (panel.currentState == GameState.MENU || panel.currentState == GameState.WORLD_SETTINGS || panel.currentState == GameState.LOADING) return;
        if (isMinimapDragging) {
            Point p = e.getPoint();
            p.x = Math.max(panel.hudManager.minimapBounds.x, Math.min(p.x, panel.hudManager.minimapBounds.x + panel.hudManager.minimapBounds.width));
            p.y = Math.max(panel.hudManager.minimapBounds.y, Math.min(p.y, panel.hudManager.minimapBounds.y + panel.hudManager.minimapBounds.height));
            panel.hudManager.processMinimapClick(p);
            return;
        }
        if (panel.isMiddleDragging) {
            int mapW = panel.currentDestBoundsWidth();
            int mapH = panel.currentDestBoundsHeight();
            panel.cameraManager.pan(e.getX(), e.getY(), mapW, mapH, panel.getWidth(), panel.getHeight());
            return;
        }

        boolean hitBaseHUD = e.getY() >= panel.getHeight() - RTSHUD.HUD_HEIGHT;
        boolean hitMinimap = panel.hudManager.minimapBounds.contains(e.getPoint());
        if (hitBaseHUD || hitMinimap) return;

        Point worldPos = panel.cameraManager.toWorld(e.getX(), e.getY());
        panel.dragEndX = worldPos.x;
        panel.dragEndY = worldPos.y;
        panel.selectionManager.updateDrag(panel.dragEndX, panel.dragEndY);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (panel.currentState == GameState.MENU || panel.currentState == GameState.WORLD_SETTINGS || panel.currentState == GameState.LOADING) return;

        boolean hitBaseHUD = e.getY() >= panel.getHeight() - RTSHUD.HUD_HEIGHT;
        boolean hitMinimap = panel.hudManager.minimapBounds.contains(e.getPoint());
        if (hitBaseHUD || hitMinimap) return;

        if (SwingUtilities.isRightMouseButton(e) || SwingUtilities.isMiddleMouseButton(e)) return;
        Point worldPos = panel.cameraManager.toWorld(e.getX(), e.getY());
        synchronized (panel.unitsLock) {
            if (e.getClickCount() == 2) {
                panel.selectionManager.doubleSelect(worldPos.x, worldPos.y, panel.units, 20);
            } else if (e.getClickCount() == 1) {
                panel.selectionManager.singleSelect(worldPos.x, worldPos.y, panel.units);
            }
        }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (panel.currentState == GameState.MENU || panel.currentState == GameState.WORLD_SETTINGS || panel.currentState == GameState.LOADING) return;

        boolean hitBaseHUD = e.getY() >= panel.getHeight() - RTSHUD.HUD_HEIGHT;
        boolean hitMinimap = panel.hudManager.minimapBounds.contains(e.getPoint());
        if (hitBaseHUD || hitMinimap) return;

        if (e.isControlDown()) {
            double HARDCODED_MIN_ZOOM = 0.45;
            double absoluteMinZoomX = (double) panel.getWidth() / panel.currentDestBoundsWidth();
            double absoluteMinZoomY = (double) panel.getHeight() / panel.currentDestBoundsHeight();
            double voidSafetyZoom = Math.max(absoluteMinZoomX, absoluteMinZoomY);
            double smallestZoomPossible = Math.max(HARDCODED_MIN_ZOOM, voidSafetyZoom);
            double maxZoomPossible = smallestZoomPossible * 12.0;

            double currentZoom = panel.cameraManager.zoom;
            if (e.getWheelRotation() < 0) {
                currentZoom *= 1.1;
            } else {
                currentZoom /= 1.1;
            }
            double finalZoom = Math.max(smallestZoomPossible, Math.min(currentZoom, maxZoomPossible));

            panel.cameraManager.applyZoomChange(
                    finalZoom,
                    panel.getWidth(),
                    panel.getHeight(),
                    panel.currentDestBoundsWidth(),
                    panel.currentDestBoundsHeight()
            );

            panel.repaint();
        }
    }

    @Override public void keyTyped(KeyEvent e) {}
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e)  {}
    @Override public void mouseMoved(MouseEvent e) {
        if (panel.currentState == GameState.MENU) {
            Rectangle hover = null;
            if (panel.playWorldBtn.contains(e.getPoint())) hover = panel.playWorldBtn;
            else if (panel.playTesterBtn.contains(e.getPoint())) hover = panel.playTesterBtn;
            else if (panel.quitBtn.contains(e.getPoint())) hover = panel.quitBtn;
            // panel.menuRenderer.setHoveredButton(hover);

            panel.repaint();
        } else if (panel.currentState == GameState.WORLD_SETTINGS) {
            Rectangle hover = null;
            if (panel.sizeMinusBtn.contains(e.getPoint())) hover = panel.sizeMinusBtn;
            else if (panel.sizePlusBtn.contains(e.getPoint())) hover = panel.sizePlusBtn;
            else if (panel.startWorldBtn.contains(e.getPoint())) hover = panel.startWorldBtn;
            else if (panel.settingsBackBtn.contains(e.getPoint())) hover = panel.settingsBackBtn;
            // panel.menuRenderer.setHoveredButton(hover);

            panel.repaint();
        }
    }
}
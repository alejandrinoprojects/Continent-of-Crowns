import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class SelectionManager {
    public final List<Unit> selectedUnits = new ArrayList<>();
    public final Stack<List<Unit>> selectionHistory = new Stack<>();
    private final Rectangle hitRect = new Rectangle();

    public int dragStartX, dragStartY, dragEndX, dragEndY;
    public boolean dragging = false;

    public void startDrag(int x, int y) {
        dragStartX = x;
        dragStartY = y;
        dragging = true;
    }

    public void updateDrag(int x, int y) {
        dragEndX = x;
        dragEndY = y;
    }

    public void endDrag(int x, int y, List<Unit> allUnits) {
        dragEndX = x;
        dragEndY = y;
        dragging = false;
        selectUnitsInBox(allUnits);
    }

    public void selectUnitsInBox(List<Unit> allUnits) {
        Rectangle box = new Rectangle(
                Math.min(dragStartX, dragEndX), Math.min(dragStartY, dragEndY),
                Math.abs(dragStartX - dragEndX), Math.abs(dragStartY - dragEndY)
        );
        selectedUnits.clear();
        for (Unit u : allUnits) {
            hitRect.setBounds((int)u.x - 10, (int)u.y - 10, 20, 20);
            u.selected = box.contains(hitRect);
            if (u.selected) selectedUnits.add(u);
        }
        selectionHistory.push(new ArrayList<>(selectedUnits));
    }

    public void clearSelection(List<Unit> allUnits) {
        for (Unit u : allUnits) u.selected = false;
        selectedUnits.clear();
    }

    public boolean singleSelect(int x, int y, List<Unit> allUnits) {
        for (Unit u : allUnits) {
            hitRect.setBounds((int)u.x - 10, (int)u.y - 10, 20, 20);
            if (hitRect.contains(x, y)) {
                clearSelection(allUnits);
                u.selected = true;
                selectedUnits.add(u);
                selectionHistory.push(new ArrayList<>(selectedUnits));
                return true;
            }
        }
        clearSelection(allUnits);
        return false;
    }

    // --- NEW: Selects all units of the same type within a specific hex radius ---
    public boolean doubleSelect(int x, int y, List<Unit> allUnits, int radius) {
        Unit targetUnit = null;
        for (Unit u : allUnits) {
            hitRect.setBounds((int)u.x - 10, (int)u.y - 10, 20, 20);
            if (hitRect.contains(x, y)) {
                targetUnit = u;
                break;
            }
        }

        if (targetUnit != null) {
            clearSelection(allUnits);
            for (Unit u : allUnits) {
                if (u.type == targetUnit.type) {
                    int dist = HexMath.hexDistance(targetUnit.q, targetUnit.r, u.q, u.r);
                    if (dist <= radius) {
                        u.selected = true;
                        selectedUnits.add(u);
                    }
                }
            }
            if (!selectedUnits.isEmpty()) {
                selectionHistory.push(new ArrayList<>(selectedUnits));
            }
            return true;
        }
        clearSelection(allUnits);
        return false;
    }

    public boolean isPointOnAnyUnit(int x, int y, List<Unit> allUnits) {
        for (Unit u : allUnits) {
            hitRect.setBounds((int)u.x - 10, (int)u.y - 10, 20, 20);
            if (hitRect.contains(x, y)) {
                return true;
            }
        }
        return false;
    }
}
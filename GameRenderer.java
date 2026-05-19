import org.joml.Matrix4f;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;

public class GameRenderer implements Renderer {
    private final List<Renderer> renderers = new ArrayList<>();

    public void addRenderer(Renderer renderer) {
        renderers.add(renderer);
    }

    @Override
    public void init() {
        for (Renderer renderer : renderers) {
            renderer.init();
        }
    }

    @Override
    public void render(Matrix4f projection, Matrix4f view) {
        for (Renderer renderer : renderers) {
            renderer.render(projection, view);
        }
    }

    public void clearSpriteCache() {}

    public void renderDynamicEntities(Graphics2D g2d, List<Unit> unitBatch, Object unitsLock, SelectionManager selectionManager, double viewWorldX, double viewWorldY, double viewWorldW, double viewWorldH, double margin) {}

    public void drawTownHall(Graphics2D g2d, TownHall th, int tz, int hexSize, Color color) {}
}

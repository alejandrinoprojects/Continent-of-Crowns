import org.joml.Matrix4f;
import static org.lwjgl.opengl.GL11.*;
import java.util.List;
import java.util.ArrayList;

public class EntityRenderer implements Renderer {
    private WorldState worldState;
    private QuadRenderer quadRenderer;
    private Shader entityShader;
    private Texture whiteTexture;
    private Matrix4f model = new Matrix4f();

    public EntityRenderer(WorldState worldState) {
        this.worldState = worldState;
        this.quadRenderer = new QuadRenderer();
    }

    @Override
    public void init() {
        String vertexSource = "#version 120\n" +
            "uniform mat4 projection;\n" +
            "uniform mat4 view;\n" +
            "uniform mat4 model;\n" +
            "void main() {\n" +
            "    gl_TexCoord[0] = gl_MultiTexCoord0;\n" +
            "    gl_Position = projection * view * model * gl_Vertex;\n" +
            "}\n";

        String fragmentSource = "#version 120\n" +
            "uniform sampler2D texture0;\n" +
            "uniform vec4 colorTint;\n" +
            "void main() {\n" +
            "    vec4 texColor = texture2D(texture0, gl_TexCoord[0].st);\n" +
            "    gl_FragColor = texColor * colorTint;\n" +
            "}\n";

        entityShader = new Shader(vertexSource, fragmentSource);
        
        byte[] whitePixel = new byte[] { (byte)255, (byte)255, (byte)255, (byte)255 };
        whiteTexture = new Texture(1, 1, whitePixel);
    }

    @Override
    public void render(Matrix4f projection, Matrix4f view) {
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL); // Ensure closer things cover further things
        
        // Render TownHalls
        if (worldState.playerTownHall != null) {
            renderTownHall(worldState.playerTownHall, projection, view, 0.1f, 0.5f, 0.2f, 1.0f); // Green
        }
        if (worldState.enemyTownHall != null) {
            renderTownHall(worldState.enemyTownHall, projection, view, 0.8f, 0.1f, 0.1f, 1.0f); // Red
        }

        // Render Units
        List<Unit> unitsCopy;
        synchronized (worldState.unitsLock) {
            unitsCopy = new ArrayList<>(worldState.units);
        }

        for (Unit u : unitsCopy) {
            boolean isPlayer = worldState.unitTeams.getOrDefault(u, true);
            float r = isPlayer ? 0.2f : 0.8f;
            float g = isPlayer ? 0.5f : 0.2f;
            float b = isPlayer ? 0.8f : 0.2f;

            if (u.selected) {
                r = 1.0f; g = 1.0f; b = 0.0f; // Yellow
            } else if (u.isHoldingPosition) {
                r = 1.0f; g = 0.5f; b = 0.0f; // Orange
            }

            HexTile tile = GridManager.hexes.get(GridManager.packKey(u.q, u.r));
            float terrainZ = (tile != null) ? tile.getZ() : 0;
            float renderY = (float)u.y - terrainZ - (float)u.leapZ;
            
            float depthZ = renderY * 0.001f; // depth based on Y screen coordinate

            model.identity()
                .translate((float)u.x, renderY, depthZ)
                .scale(30.0f, 30.0f, 1.0f);

            entityShader.bind();
            entityShader.setUniform("colorTint", r, g, b, 1.0f);
            quadRenderer.render(entityShader, whiteTexture, projection, view, model);
        }
        
        glDisable(GL_DEPTH_TEST);
    }
    
    private void renderTownHall(TownHall th, Matrix4f projection, Matrix4f view, float r, float g, float b, float a) {
        int px = CoordinateConverter.getPixelX(th.q, th.r);
        int py = CoordinateConverter.getPixelY(th.q, th.r);
        HexTile tile = GridManager.hexes.get(GridManager.packKey(th.q, th.r));
        float terrainZ = (tile != null) ? tile.getZ() : 0;
        
        float renderY = py - terrainZ;
        float depthZ = renderY * 0.001f;

        model.identity()
            .translate(px, renderY - 30.0f, depthZ)
            .scale(80.0f, 80.0f, 1.0f);

        entityShader.bind();
        entityShader.setUniform("colorTint", r, g, b, a);
        quadRenderer.render(entityShader, whiteTexture, projection, view, model);
    }
}

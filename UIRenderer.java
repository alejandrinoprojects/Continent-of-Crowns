import org.joml.Matrix4f;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import java.nio.FloatBuffer;
import org.lwjgl.system.MemoryUtil;

public class UIRenderer implements Renderer {
    private int width = 1920;
    private int height = 1080;
    
    private int minimapTextureId = -1;
    private int vaoId = -1;
    private int vboId = -1;
    private Shader uiShader;
    
    private Matrix4f orthoProjection = new Matrix4f();

    public void setResolution(int w, int h) {
        this.width = w;
        this.height = h;
        orthoProjection.setOrtho2D(0, width, height, 0);
    }

    @Override
    public void init() {
        // Initialize UI shader
        String vertexSource = "#version 330 core\n" +
            "layout(location = 0) in vec3 aPos;\n" +
            "layout(location = 1) in vec2 aTexCoord;\n" +
            "uniform mat4 projection;\n" +
            "out vec2 vTexCoord;\n" +
            "void main() {\n" +
            "    vTexCoord = aTexCoord;\n" +
            "    gl_Position = projection * vec4(aPos, 1.0);\n" +
            "}\n";
        
        String fragmentSource = "#version 330 core\n" +
            "in vec2 vTexCoord;\n" +
            "uniform sampler2D texture0;\n" +
            "uniform vec4 colorTint;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    vec4 texColor = texture(texture0, vTexCoord);\n" +
            "    fragColor = texColor * colorTint;\n" +
            "}\n";
        
        uiShader = new Shader(vertexSource, fragmentSource);
        
        // Create VAO/VBO for UI quads
        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);
        
        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        
        float[] vertices = new float[] {
            // pos               // texcoord
            -0.5f, -0.5f, 0.0f,  0.0f, 0.0f,
             0.5f, -0.5f, 0.0f,  1.0f, 0.0f,
             0.5f,  0.5f, 0.0f,  1.0f, 1.0f,
            -0.5f,  0.5f, 0.0f,  0.0f, 1.0f
        };
        
        FloatBuffer buffer = MemoryUtil.memAllocFloat(vertices.length);
        buffer.put(vertices);
        buffer.flip();
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
        MemoryUtil.memFree(buffer);
        
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 5 * Float.BYTES, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        
        generateMinimapTexture();
        orthoProjection.setOrtho2D(0, width, height, 0);
    }

    private void generateMinimapTexture() {
        int dim = 360;
        ByteBuffer buffer = MemoryUtil.memAlloc(dim * dim * 4);

        int minMapX = 0;
        int maxMapX = CoordinateConverter.getMapWidth();
        int minMapY = 0;
        int maxMapY = CoordinateConverter.getMapHeight();

        double worldW = maxMapX - minMapX;
        double worldH = maxMapY - minMapY;

        double scaleX = (double) dim / worldW;
        double scaleY = (double) dim / worldH;

        // Fill with water color (50, 95, 210)
        for (int i = 0; i < dim * dim; i++) {
            buffer.put((byte) 50).put((byte) 95).put((byte) 210).put((byte) 255);
        }

        for (HexTile tile : GridManager.hexes.values()) {
            TileType tt = tile.getTileType();
            if (tt == TileType.WATER) continue;

            int pX = CoordinateConverter.getPixelX(tile.q, tile.r);
            int pY = CoordinateConverter.getPixelY(tile.q, tile.r);
            int mx = (int) ((pX - minMapX) * scaleX);
            int my = (int) ((pY - minMapY) * scaleY);

            if (mx < 0 || mx >= dim || my < 0 || my >= dim) continue;

            byte r = 0, g = 0, b = 0;
            switch (tt) {
                case ROCK:       r = 110; g = 110; b = 110; break;
                case TREES:      r = 45; g = 80; b = 30; break;
                case SAND:
                    switch (tile.getBiomeType()) {
                        case DESERT:   r = (byte)200; g = (byte)170; b = 110; break;
                        case SAVANNAH: r = (byte)160; g = (byte)140; b = 55; break;
                        default:       r = (byte)225; g = (byte)195; b = (byte)140; break;
                    }
                    break;
                case SNOW:
                    switch (tile.getBiomeType()) {
                        case TUNDRA: r = (byte)210; g = (byte)225; b = (byte)235; break;
                        case TAIGA:  r = 55; g = 85; b = 35; break;
                        default:     r = (byte)240; g = (byte)245; b = (byte)255; break;
                    }
                    break;
                case ICE:        r = (byte)190; g = (byte)220; b = (byte)250; break;
                default: // PLAIN
                    switch (tile.getBiomeType()) {
                        case DESERT:   r = (byte)210; g = (byte)185; b = (byte)130; break;
                        case SAVANNAH: r = (byte)185; g = (byte)170; b = 80; break;
                        case TUNDRA:   r = (byte)180; g = (byte)200; b = (byte)215; break;
                        case TAIGA:    r = 70; g = 100; b = 50; break;
                        default:       r = 100; g = (byte)160; b = 60; break;
                    }
                    break;
            }

            for (int dy = 0; dy < 2; dy++) {
                for (int dx = 0; dx < 2; dx++) {
                    int px = mx + dx;
                    int py = my + dy;
                    if (px >= 0 && px < dim && py >= 0 && py < dim) {
                        int idx = (py * dim + px) * 4;
                        buffer.put(idx, r);
                        buffer.put(idx + 1, g);
                        buffer.put(idx + 2, b);
                        buffer.put(idx + 3, (byte) 255);
                    }
                }
            }
        }

        buffer.flip();

        minimapTextureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, minimapTextureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, dim, dim, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        glBindTexture(GL_TEXTURE_2D, 0);

        MemoryUtil.memFree(buffer);
    }

    @Override
    public void render(Matrix4f projection, Matrix4f view) {
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        uiShader.bind();
        uiShader.setUniform("projection", orthoProjection);
        
        // Draw HUD base
        int hudHeight = 200;
        int hudY = height - hudHeight;
        drawQuad(0, hudY, width, height, 0.1f, 0.1f, 0.1f, 0.9f, null);

        // Draw Minimap background
        int minimapDim = 180;
        int minimapX = 20;
        int minimapY = height - minimapDim - 10;
        drawQuad(minimapX - 5, minimapY - 5, minimapX + minimapDim + 5, minimapY + minimapDim + 5, 
                 0.3f, 0.3f, 0.3f, 1.0f, null);

        // Draw Minimap texture
        if (minimapTextureId != -1) {
            drawTexturedQuad(minimapX, minimapY, minimapX + minimapDim, minimapY + minimapDim, minimapTextureId);
        }

        // Command Card Area
        int cmdWidth = 230;
        int cmdHeight = 180;
        int cmdX = width - cmdWidth - 10;
        int cmdY = hudY + 10;
        drawQuad(cmdX, cmdY, cmdX + cmdWidth, cmdY + cmdHeight, 0.2f, 0.2f, 0.2f, 1.0f, null);

        glDisable(GL_BLEND);
        uiShader.unbind();
    }
    
    private void drawQuad(float x1, float y1, float x2, float y2, float r, float g, float b, float a, Integer textureId) {
        float centerX = (x1 + x2) / 2.0f;
        float centerY = (y1 + y2) / 2.0f;
        float halfW = (x2 - x1) / 2.0f;
        float halfH = (y2 - y1) / 2.0f;
        
        uiShader.setUniform("colorTint", r, g, b, a);
        
        if (textureId != null) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, textureId);
            uiShader.setUniform("texture0", 0);
        } else {
            uiShader.setUniform("texture0", -1);
        }
        
        Matrix4f model = new Matrix4f().translate(centerX, centerY, 0).scale(halfW, halfH, 1);
        uiShader.setUniform("model", model);
        
        glBindVertexArray(vaoId);
        glDrawArrays(GL_TRIANGLE_FAN, 0, 4);
        glBindVertexArray(0);
        
        if (textureId != null) {
            glBindTexture(GL_TEXTURE_2D, 0);
        }
    }
    
    private void drawTexturedQuad(float x1, float y1, float x2, float y2, int textureId) {
        drawQuad(x1, y1, x2, y2, 1.0f, 1.0f, 1.0f, 1.0f, textureId);
    }
    
    public void dispose() {
        if (vaoId != -1) {
            glDeleteVertexArrays(vaoId);
            vaoId = -1;
        }
        if (vboId != -1) {
            glDeleteBuffers(vboId);
            vboId = -1;
        }
        if (minimapTextureId != -1) {
            glDeleteTextures(minimapTextureId);
            minimapTextureId = -1;
        }
        if (uiShader != null) {
            uiShader.dispose();
        }
    }
}

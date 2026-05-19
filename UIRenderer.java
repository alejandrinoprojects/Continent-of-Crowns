import org.joml.Matrix4f;
import java.nio.ByteBuffer;
import static org.lwjgl.opengl.GL11.*;
import org.lwjgl.system.MemoryUtil;

public class UIRenderer implements Renderer {
    private int width = 1920;
    private int height = 1080;
    
    private int minimapTextureId = -1;
    
    public void setResolution(int w, int h) {
        this.width = w;
        this.height = h;
    }

    @Override
    public void init() {
        generateMinimapTexture();
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
                        default:       r = 100; g = (byte)160; b = 60; break; // GRASSLAND
                    }
                    break;
            }
            
            // Draw a small 2x2 brush
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
        // We ignore the view and projection matrix for UI, we just use orthographic projection
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        // Set orthographic 2D projection
        glOrtho(0, width, height, 0, -1, 1);
        
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        // Disable depth test to draw over terrain
        glDisable(GL_DEPTH_TEST);
        // Enable blending for transparency
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Draw HUD base
        int hudHeight = 200;
        int hudY = height - hudHeight;
        
        glColor4f(0.1f, 0.1f, 0.1f, 0.9f);
        glBegin(GL_QUADS);
        glVertex2f(0, hudY);
        glVertex2f(width, hudY);
        glVertex2f(width, height);
        glVertex2f(0, height);
        glEnd();
        
        // Draw Minimap
        int minimapDim = 180;
        int minimapX = 20;
        int minimapY = height - minimapDim - 10;
        
        glColor4f(0.3f, 0.3f, 0.3f, 1.0f);
        glBegin(GL_QUADS);
        glVertex2f(minimapX - 5, minimapY - 5);
        glVertex2f(minimapX + minimapDim + 5, minimapY - 5);
        glVertex2f(minimapX + minimapDim + 5, minimapY + minimapDim + 5);
        glVertex2f(minimapX - 5, minimapY + minimapDim + 5);
        glEnd();
        
        if (minimapTextureId != -1) {
            glEnable(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, minimapTextureId);
            glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            glBegin(GL_QUADS);
            glTexCoord2f(0, 0); glVertex2f(minimapX, minimapY);
            glTexCoord2f(1, 0); glVertex2f(minimapX + minimapDim, minimapY);
            glTexCoord2f(1, 1); glVertex2f(minimapX + minimapDim, minimapY + minimapDim);
            glTexCoord2f(0, 1); glVertex2f(minimapX, minimapY + minimapDim);
            glEnd();
            glBindTexture(GL_TEXTURE_2D, 0);
            glDisable(GL_TEXTURE_2D);
        }
        
        // Command Card Area (Right side)
        int cmdWidth = 230;
        int cmdHeight = 180;
        int cmdX = width - cmdWidth - 10;
        int cmdY = hudY + 10;
        
        glColor4f(0.2f, 0.2f, 0.2f, 1.0f);
        glBegin(GL_QUADS);
        glVertex2f(cmdX, cmdY);
        glVertex2f(cmdX + cmdWidth, cmdY);
        glVertex2f(cmdX + cmdWidth, cmdY + cmdHeight);
        glVertex2f(cmdX, cmdY + cmdHeight);
        glEnd();

        glDisable(GL_BLEND);
        
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }
}
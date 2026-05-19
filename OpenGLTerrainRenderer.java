import org.joml.Matrix4f;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class OpenGLTerrainRenderer implements Renderer {
    private Shader terrainShader;
    private final ChunkManager chunkManager;
    private int elevationTextureId = -1;

    public OpenGLTerrainRenderer(ChunkManager chunkManager) {
        this.chunkManager = chunkManager;
    }

    @Override
    public void init() {
        this.terrainShader = new Shader(TerrainShaderSource.VERTEX_SHADER, TerrainShaderSource.FRAGMENT_SHADER);
        
        // Upload elevation texture
        ByteBuffer elevationData = chunkManager.getElevationData();
        if (elevationData != null) {
            elevationTextureId = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, elevationTextureId);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            
            // MAP_COLS and MAP_ROWS are 600
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, 600, 600, 0, GL_RED, GL_UNSIGNED_BYTE, elevationData);
            glBindTexture(GL_TEXTURE_2D, 0);
        }
    }

    @Override
    public void render(Matrix4f projection, Matrix4f view) {
        if (terrainShader == null) return;
        
        terrainShader.bind();
        terrainShader.setUniform("projection", projection);
        terrainShader.setUniform("view", view);
        terrainShader.setUniform("model", new Matrix4f().identity());
        
        if (elevationTextureId != -1) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, elevationTextureId);
            terrainShader.setUniform("elevationTex", 0);
        }

        int cols = chunkManager.getCols();
        int rows = chunkManager.getRows();

        for (int cx = 0; cx < cols; cx++) {
            for (int cy = 0; cy < rows; cy++) {
                ChunkManager.ChunkMesh mesh = chunkManager.getChunkMesh(cx, cy);
                if (mesh == null) continue;

                if (mesh.vaoId == -1) {
                    uploadMesh(mesh);
                }

                glBindVertexArray(mesh.vaoId);
                glDrawArrays(GL_TRIANGLES, 0, mesh.vertexCount);
            }
        }
        
        glBindVertexArray(0);
        terrainShader.unbind();
    }

    private void uploadMesh(ChunkManager.ChunkMesh mesh) {
        mesh.vaoId = glGenVertexArrays();
        glBindVertexArray(mesh.vaoId);

        mesh.vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, mesh.vboId);
        glBufferData(GL_ARRAY_BUFFER, mesh.vertices, GL_STATIC_DRAW);

        int stride = 8 * Float.BYTES;
        
        // aPos (vec3)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        
        // aType (float)
        glVertexAttribPointer(1, 1, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        
        // aBiome (float)
        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 4L * Float.BYTES);
        glEnableVertexAttribArray(2);
        
        // aElevation (float)
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 5L * Float.BYTES);
        glEnableVertexAttribArray(3);
        
        // aVariation (float)
        glVertexAttribPointer(4, 1, GL_FLOAT, false, stride, 6L * Float.BYTES);
        glEnableVertexAttribArray(4);
        
        // aFrame (float)
        glVertexAttribPointer(5, 1, GL_FLOAT, false, stride, 7L * Float.BYTES);
        glEnableVertexAttribArray(5);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }
    
    public void dispose() {
        if (terrainShader != null) {
            terrainShader.dispose();
        }
        if (elevationTextureId != -1) {
            glDeleteTextures(elevationTextureId);
        }
    }
}
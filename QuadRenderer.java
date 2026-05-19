import org.joml.Matrix4f;
import java.nio.FloatBuffer;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class QuadRenderer {
    private int vaoId = -1;
    private int vboId = -1;
    
    public void init() {
        // Create VAO and VBO for a quad
        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);
        
        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        
        // Quad vertices: position (x, y, z) + texcoord (u, v)
        float[] vertices = new float[] {
            // pos           // texcoord
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
        
        // Position attribute (location = 0)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        
        // TexCoord attribute (location = 1)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 5 * Float.BYTES, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }
    
    public void render(Shader shader, Texture texture, Matrix4f projection, Matrix4f view, Matrix4f model) {
        if (vaoId == -1) {
            init();
        }
        
        shader.bind();
        texture.bind();

        shader.setUniform("projection", projection);
        shader.setUniform("view", view);
        shader.setUniform("model", model);
        shader.setUniform("texture0", 0);

        glBindVertexArray(vaoId);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

        texture.unbind();
        shader.unbind();
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
    }
}

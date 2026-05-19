import org.joml.Matrix4f;
import static org.lwjgl.opengl.GL11.*;

public class QuadRenderer {
    public void render(Shader shader, Texture texture, Matrix4f projection, Matrix4f view, Matrix4f model) {
        shader.bind();
        texture.bind();

        shader.setUniform("projection", projection);
        shader.setUniform("view", view);
        shader.setUniform("model", model);
        shader.setUniform("texture0", 0);

        glBegin(GL_QUADS);
        glTexCoord2f(0, 0); glVertex2f(-0.5f, -0.5f);
        glTexCoord2f(1, 0); glVertex2f(0.5f, -0.5f);
        glTexCoord2f(1, 1); glVertex2f(0.5f, 0.5f);
        glTexCoord2f(0, 1); glVertex2f(-0.5f, 0.5f);
        glEnd();

        texture.unbind();
        shader.unbind();
    }

    public void dispose() {
        // No resources to dispose here as we are using immediate mode for now
    }
}

import org.joml.Matrix4f;
import static org.lwjgl.opengl.GL11.*;

public class SimpleColorRenderer implements Renderer {
    @Override
    public void init() {}

    @Override
    public void render(Matrix4f projection, Matrix4f view) {
        // For testing purposes, let's just draw something.
        // This is a very primitive way to draw in OpenGL.
        glBegin(GL_QUADS);
        glColor3f(1.0f, 0.0f, 0.0f); // Red
        glVertex2f(-0.5f, -0.5f);
        glVertex2f(0.5f, -0.5f);
        glVertex2f(0.5f, 0.5f);
        glVertex2f(-0.5f, 0.5f);
        glEnd();
    }
}

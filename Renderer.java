import org.joml.Matrix4f;

public interface Renderer {
    void init();
    void render(Matrix4f projection, Matrix4f view);
}

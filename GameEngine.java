import org.joml.Matrix4f;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;

public class GameEngine {
    private long window;
    private int width = 1920;
    private int height = 1080;
    private String title = "Hex RTS";

    private GameRenderer gameRenderer;
    private WorldState worldState;
    private Matrix4f projection = new Matrix4f();
    private Matrix4f view = new Matrix4f();
    
    // Camera variables
    private float cameraX = 0.0f;
    private float cameraY = 0.0f;
    private float zoom = 1.0f;

    // Mouse state
    private double mouseX = 0;
    private double mouseY = 0;
    private boolean mouseLeftDown = false;
    private boolean minimapClicked = false;

    // Keyboard state
    private boolean[] keys = new boolean[GLFW_KEY_LAST + 1];

    public void setRenderer(GameRenderer renderer) {
        this.gameRenderer = renderer;
    }

    public void setWorldState(WorldState worldState) {
        this.worldState = worldState;
    }

    public void start() {
        init();
        loop();

        // Free the window callbacks and destroy the window
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);

        // Terminate GLFW and free the error callback
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    private void init() {
        // Setup an error callback. The default implementation
        // will print the error message in System.err.
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if ( !glfwInit() )
            throw new IllegalStateException("Unable to initialize GLFW");

        // Configure GLFW
        glfwDefaultWindowHints(); // optional, the current window hints are already the default
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // the window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // the window will be resizable

        // Create the window
        window = glfwCreateWindow(width, height, title, NULL, NULL);
        if ( window == NULL )
            throw new RuntimeException("Failed to create the GLFW window");

        // Make the OpenGL context current
        glfwMakeContextCurrent(window);
        // Enable v-sync
        glfwSwapInterval(1);

        // Make the window visible
        glfwShowWindow(window);

        // Setup key callback
        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(window, true); // We will detect this in the rendering loop
            }
            if (key >= 0 && key < GLFW_KEY_LAST) {
                if (action == GLFW_PRESS) {
                    keys[key] = true;
                } else if (action == GLFW_RELEASE) {
                    keys[key] = false;
                }
            }
        });

        // Setup mouse callbacks
        glfwSetCursorPosCallback(window, (window, xpos, ypos) -> {
            mouseX = xpos;
            mouseY = ypos;
        });
        
        glfwSetMouseButtonCallback(window, (window, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW_PRESS) {
                    mouseLeftDown = true;
                    // Check if clicked in minimap
                    int minimapDim = 180;
                    int minimapX = 20;
                    int minimapY = height - minimapDim - 10;
                    
                    if (mouseX >= minimapX && mouseX <= minimapX + minimapDim &&
                        mouseY >= minimapY && mouseY <= minimapY + minimapDim) {
                        minimapClicked = true;
                    }
                } else if (action == GLFW_RELEASE) {
                    mouseLeftDown = false;
                    minimapClicked = false;
                }
            }
        });

        // Setup scroll callback for zoom
        glfwSetScrollCallback(window, (window, xoffset, yoffset) -> {
            float zoomSpeed = 0.1f;
            if (yoffset > 0) {
                zoom += zoomSpeed;
            } else if (yoffset < 0) {
                zoom -= zoomSpeed;
            }
            zoom = Math.max(0.1f, Math.min(zoom, 5.0f));
        });

        // This line is critical for LWJGL's interoperation with GLFW's
        // OpenGL context, or any context that is managed externally.
        // LWJGL detects the context that is current in the current thread,
        // creates the GLCapabilities instance and makes the OpenGL
        // bindings available for use.
        GL.createCapabilities();

        if (gameRenderer != null) {
            gameRenderer.init();
        }

        // Set the clear color
        glClearColor(0.2f, 0.3f, 0.3f, 0.0f);

        // Initialize projection matrix
        projection.setOrtho(0, width, height, 0, -1, 1);
    }

    private void updateInput(float deltaTime) {
        float cameraSpeed = 500.0f * deltaTime / zoom;
        if (keys[GLFW_KEY_W] || keys[GLFW_KEY_UP]) {
            cameraY -= cameraSpeed;
        }
        if (keys[GLFW_KEY_S] || keys[GLFW_KEY_DOWN]) {
            cameraY += cameraSpeed;
        }
        if (keys[GLFW_KEY_A] || keys[GLFW_KEY_LEFT]) {
            cameraX -= cameraSpeed;
        }
        if (keys[GLFW_KEY_D] || keys[GLFW_KEY_RIGHT]) {
            cameraX += cameraSpeed;
        }
        
        if (minimapClicked) {
            int minimapDim = 180;
            int minimapX = 20;
            int minimapY = height - minimapDim - 10;
            
            float mapRatioX = (float) (mouseX - minimapX) / minimapDim;
            float mapRatioY = (float) (mouseY - minimapY) / minimapDim;
            
            int maxMapX = CoordinateConverter.getMapWidth();
            int maxMapY = CoordinateConverter.getMapHeight();
            
            cameraX = mapRatioX * maxMapX;
            cameraY = mapRatioY * maxMapY;
        }
    }

    private void loop() {
        long lastTime = System.nanoTime();
        
        while ( !glfwWindowShouldClose(window) ) {
            long currentTime = System.nanoTime();
            float deltaTime = (currentTime - lastTime) / 1_000_000_000.0f;
            lastTime = currentTime;
            
            updateInput(deltaTime);
            
            if (worldState != null) {
                worldState.updateSimulation(deltaTime);
            }
            
            // Update view matrix based on camera position and zoom
            view.identity()
                .translate(width / 2.0f, height / 2.0f, 0.0f) // Center screen
                .scale(zoom, zoom, 1.0f)
                .translate(-cameraX, -cameraY, 0.0f);
            
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // clear the framebuffer

            // Run rendering code here...
            if (gameRenderer != null) {
                gameRenderer.render(projection, view);
            }

            glfwSwapBuffers(window); // swap the color buffers

            // Poll for window events. The key callback above will only be
            // invoked during this call.
            glfwPollEvents();
        }
    }
}

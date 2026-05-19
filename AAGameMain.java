public class AAGameMain {
    public static void main(String[] args) {
        // Initialize Core Engine and Coordinates
        CoordinateConverter.computeOrigin(1920, 1080);
        
        // Generate the Map Data
        long mapSeed = 12345L; // Example seed
        GridManager.generateMap(mapSeed);

        // Initialize WorldState and Entities
        WorldState worldState = new WorldState();
        worldState.setupInitialState();

        // Initialize Renderers and ChunkManager
        GameRenderer gameRenderer = new GameRenderer();
        ChunkManager chunkManager = new ChunkManager();
        chunkManager.buildChunks(gameRenderer);

        OpenGLTerrainRenderer terrainRenderer = new OpenGLTerrainRenderer(chunkManager);
        gameRenderer.addRenderer(terrainRenderer);
        
        EntityRenderer entityRenderer = new EntityRenderer(worldState);
        gameRenderer.addRenderer(entityRenderer);
        
        UIRenderer uiRenderer = new UIRenderer();
        gameRenderer.addRenderer(uiRenderer);

        GameEngine engine = new GameEngine();
        engine.setRenderer(gameRenderer);
        engine.setWorldState(worldState);
        
        System.out.println("Starting engine...");
        engine.start();
    }
}

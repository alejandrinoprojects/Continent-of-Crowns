import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Polygon;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class GamePanel extends JPanel {

    GameState currentState = GameState.MENU;
    CommandType activeCommandMode = CommandType.MOVE;

    int worldSizePercentage = 100;
    String seedInputString = "";

    int loadingProgress = 0;
    String loadingStatus = "Initializing...";

    final Rectangle playWorldBtn    = new Rectangle(860, 420, 200, 50);
    final Rectangle playTesterBtn   = new Rectangle(860, 500, 200, 50);
    final Rectangle quitBtn         = new Rectangle(860, 580, 200, 50);
    final Rectangle sizeMinusBtn    = new Rectangle(820, 360,  50, 40);
    final Rectangle sizePlusBtn     = new Rectangle(1050,360,  50, 40);
    final Rectangle startWorldBtn   = new Rectangle(810, 600, 300, 45);
    final Rectangle settingsBackBtn = new Rectangle(810, 665, 300, 45);
    // final MenuRenderer menuRenderer;


    public final ArrayList<Unit> units = new ArrayList<>();
    public final Object unitsLock = new Object();
    public final Queue<Command>             commandQueue = new LinkedList<>();
    public final Map<Unit, Boolean>         unitTeams    = new ConcurrentHashMap<>();

    final SelectionManager   selectionManager   = new SelectionManager();
    final MovementController movementController = new MovementController();
    final CameraManager      cameraManager      = new CameraManager();
    final RTSHUD             hudManager;
    private final GameRenderer  renderer     = new GameRenderer();
    private final ChunkManager  chunkManager = new ChunkManager();

    private final PauseManager pauseManager  = new PauseManager();
    private final StatsManager playerStats   = new StatsManager();
    private final CombatManager combatManager = new CombatManager();
    private TownHall playerTownHall;
    private TownHall enemyTownHall;
    private boolean gameOver        = false;
    private boolean postGameFreePlay = false;

    public TownHall getPlayerTownHall()  { return this.playerTownHall; }
    public TownHall getEnemyTownHall()   { return this.enemyTownHall; }

    public static final int hexSize = CoordinateConverter.HEX_SIZE;
    List<Unit> selectedUnits = selectionManager.selectedUnits;

    int dragStartX, dragStartY, dragEndX, dragEndY;
    boolean dragging         = false;
    boolean isMiddleDragging = false;

    private long   lastTime  = System.nanoTime();
    private static final double TARGET_FPS = 60.0;
    private Thread simulationThread;
    private boolean isRunning = false;

    public static final double DAY_NIGHT_CYCLE_SECONDS = 120;
    public double currentCycleTime = 0.0;
    public int    currentDay       = 1;
    public double totalSessionTime = 0.0;

    public static double getCurrentGameTime() {
        return System.nanoTime() / 1_000_000_000.0;
    }

    private Color  activeNightOverlayColor = new Color(0, 0, 0, 0);
    private int    lastWidth   = -1;
    private int    lastHeight  = -1;
    private int    lastTodStep = -1;
    private boolean chunkRebuildPending = false;

    private static final Color TWILIGHT_GLOW = new Color(210, 90,  20);
    private static final Color DEEP_MIDNIGHT = new Color( 10, 15,  40);
    private static final Color COLOR_CLEAR   = new Color(  0,  0,   0, 0);
    private Color cachedNightOverlay = new Color(0, 0, 0, 0);
    private int   lastTargetAlpha    = -1;
    private float lastCycleProgress  = -1f;

    private static final int   TOWNHALL_SHADOW_RADIUS = 4;
    private static final Color TOWNHALL_SHADOW_COLOR  = new Color(0, 0, 0, 110);

    private static final java.awt.BasicStroke STROKE_SELECTED = new java.awt.BasicStroke(3.0f);
    private static final java.awt.BasicStroke STROKE_NORMAL   = new java.awt.BasicStroke(1.5f);
    private static final java.awt.BasicStroke STROKE_DEFAULT  = new java.awt.BasicStroke(1.0f);
    private static final Color COLOR_PLAYER_UNIT   = new Color(  0, 130, 255);
    private static final Color COLOR_ENEMY_UNIT    = new Color(255,  45,  45);
    private static final Color COLOR_HP_BACKGROUND = new Color(110,  25,  25);
    private static final Color COLOR_HP_FILL       = new Color( 40, 225,  75);
    private static final Color COLOR_LABEL_SHADOW  = new Color(  0,   0,   0, 180);
    private static final Color COLOR_SEL_BOX_FILL  = new Color(255, 255,   0,  35);
    private static final Color COLOR_PLAYER_TH     = new Color( 35, 120,  65);
    private static final Color COLOR_ENEMY_TH      = new Color(180,  40,  40);
    private static final Font  UNIT_FONT           = new Font("SansSerif", Font.BOLD, 11);
    private FontMetrics cachedFontMetrics = null;
    private boolean renderHintsCached = false;

    private static final int TOWNHALL_SAND_BUFFER    = 3;
    private static final int TOWNHALL_MIN_SEPARATION = 40;

    private final Color[] groundGlowColorCache = new Color[256];
    private Unit[] renderUnitSnapshot = new Unit[0];

    // --- REPLACED: GlowTile is now GlowTier, unifying hexes into seamless areas ---
    private static class GlowTier {
        int maxAlpha;
        Area area;
        Rectangle bounds;
        GlowTier(int alpha) {
            this.maxAlpha = alpha;
            this.area = new Area();
        }
    }

    private final List<GlowTier> playerGlowCache = new ArrayList<>();
    private final List<GlowTier> enemyGlowCache  = new ArrayList<>();

    public GamePanel() {
        setBackground(Color.DARK_GRAY);
        setPreferredSize(new Dimension(1920, 1080));
        setFocusable(true);

        // this.menuRenderer = new MenuRenderer(playWorldBtn, playTesterBtn, quitBtn);


        this.hudManager   = new RTSHUD(this);

        for (int i = 0; i < 256; i++) groundGlowColorCache[i] = new Color(255, 230, 140, i);

        GameInputHandler inputHandler = new GameInputHandler(this);
        addMouseListener(inputHandler);
        addMouseMotionListener(inputHandler);
        addMouseWheelListener(inputHandler);
        addKeyListener(inputHandler);

        CoordinateConverter.computeOrigin(1920, 1080);
        initializeGameLoops();
    }

    private void buildBaseGlowCache(TownHall th, List<GlowTier> cache) {
        cache.clear();
        if (th == null) return;

        Map<Integer, GlowTier> tiers = new HashMap<>();

        for (int dq = -11; dq <= 11; dq++) {
            for (int dr = Math.max(-11,-dq-11); dr <= Math.min(11,-dq+11); dr++) {
                int distance   = hexDist(0,0,dq,dr);
                int lightLevel = 5 - (distance / 3);
                if (lightLevel > 0) {
                    int targetQ = th.q+dq, targetR = th.r+dr;
                    HexTile tile = GridManager.hexes.get(GridManager.packKey(targetQ, targetR));
                    if (tile != null) {
                        int aMax = (int)((lightLevel/5.0f)*0.88f*255);
                        GlowTier tier = tiers.computeIfAbsent(aMax, k -> new GlowTier(aMax));

                        int px = CoordinateConverter.getPixelX(targetQ, targetR);
                        int py = CoordinateConverter.getPixelY(targetQ, targetR);

                        Polygon p = new Polygon();
                        for (int i = 0; i < 6; i++) {
                            double angle = Math.PI / 3 * i;
                            p.addPoint(px + (int)(hexSize * Math.cos(angle)),
                                    py + (int)(hexSize * Math.sin(angle)));
                        }
                        tier.area.add(new Area(p));
            }
        }

        if (!unitBatch.isEmpty()) {
            // renderer.renderDynamicEntities(g2d, unitBatch, unitsLock, selectionManager, viewWorldX, viewWorldY, viewWorldW, viewWorldH, margin);
            drawUnitTokens(g2d, unitBatch, fm);
            unitBatch.clear();
        }

        g2d.setTransform(oldTransform);

        // 3. Draw Night Overlay AFTER Entities
        if (activeNightOverlayColor.getAlpha() > 0) {
            g2d.setColor(activeNightOverlayColor);
            g2d.fillRect(0, 0, getWidth(), getHeight());

            g2d.scale(cameraManager.zoom, cameraManager.zoom);
            g2d.translate(cameraManager.offsetX, cameraManager.offsetY);

            float alphaRatio = activeNightOverlayColor.getAlpha() / 255.0f;

            if (playerTHVisible) drawGlowTiles(g2d, playerGlowCache, viewWorldX, viewWorldY, viewWorldW, viewWorldH, margin, alphaRatio);
            if (enemyTHVisible) drawGlowTiles(g2d, enemyGlowCache, viewWorldX, viewWorldY, viewWorldW, viewWorldH, margin, alphaRatio);

            g2d.setTransform(oldTransform);
        }

        // 4. Re-draw TownHalls on top of night overlay so they are not darkened
        if (!townHallBatch.isEmpty()) {
            g2d.scale(cameraManager.zoom, cameraManager.zoom);
            g2d.translate(cameraManager.offsetX, cameraManager.offsetY);

            for (TownHall th : townHallBatch) {
                HexTile t = GridManager.hexes.get(GridManager.packKey(th.q, th.r));
                int tz = t != null ? t.getZ() : 0;
                // renderer.drawTownHall(g2d, th, tz, hexSize, th.isPlayer ? COLOR_PLAYER_TH : COLOR_ENEMY_TH);
                th.drawHealthBar(g2d, hexSize, tz);
            }

            g2d.setTransform(oldTransform);
        }

        // 4. Draw UI Overlays
        if (selectionManager.dragging) {
            int bx = Math.min(selectionManager.dragStartX, selectionManager.dragEndX);
            int by = Math.min(selectionManager.dragStartY, selectionManager.dragEndY);
            int bw = Math.abs(selectionManager.dragStartX - selectionManager.dragEndX);
            int bh = Math.abs(selectionManager.dragStartY - selectionManager.dragEndY);
            g2d.setColor(COLOR_SEL_BOX_FILL);
            g2d.fillRect(bx, by, bw, bh);
            g2d.setColor(Color.YELLOW);
            g2d.setStroke(STROKE_NORMAL);
            g2d.drawRect(bx, by, bw, bh);
            g2d.setStroke(STROKE_DEFAULT);
        }

        hudManager.renderHUD(g2d, getWidth(), getHeight());
        pauseManager.drawPauseOverlay(g2d, getWidth(), getHeight(), playerStats);
        java.awt.Toolkit.getDefaultToolkit().sync();
    }

        }

        for (GlowTier t : tiers.values()) {
            t.bounds = t.area.getBounds();
            cache.add(t);
        }
    }

    private void updateGlowCaches() {
        buildBaseGlowCache(playerTownHall, playerGlowCache);
        buildBaseGlowCache(enemyTownHall,  enemyGlowCache);
    }

    private void initializeGameLoops() {
        isRunning = true;
        lastTime  = System.nanoTime();
        simAccumulator = 0.0;

        simulationThread = new Thread(() -> {
            while (isRunning) {
                long now = System.nanoTime();
                double frameDt = Math.min((now - lastTime) / 1_000_000_000.0, 0.1);
                lastTime = now;

                if ((currentState == GameState.PLAY_WORLD || currentState == GameState.PLAY_TESTER)
                        && !pauseManager.isPaused() && !gameOver) {

                    simAccumulator += frameDt;
                    int steps = 0;
                    while (simAccumulator >= SIM_STEP && steps < 4) {
                        movementController.updateSimulation(SIM_STEP, units, unitsLock, commandQueue);
                        combatManager.updateCombat(SIM_STEP, units, unitsLock, playerTownHall, enemyTownHall, playerStats, unitTeams);

                        totalSessionTime += SIM_STEP;
                        currentCycleTime += SIM_STEP;
                        if (currentCycleTime >= DAY_NIGHT_CYCLE_SECONDS) {
                            currentCycleTime = 0.0;
                            currentDay++;
                            GridManager.currentDay = this.currentDay;
                        }

                        float cycleProgress  = (float)(currentCycleTime / DAY_NIGHT_CYCLE_SECONDS);
                        float continuousTime = (cycleProgress < 0.50f)
                                ? (cycleProgress * 2.0f) : (2.0f - (cycleProgress * 2.0f));
                        GridManager.setTimeOfDay(continuousTime);
                        float normalizedTime  = continuousTime / 2.0f;
                        int   currentTodStep  = Math.max(0, Math.min(ChunkManager.NUM_TOD_STEPS-1,
                                (int)(normalizedTime * ChunkManager.NUM_TOD_STEPS)));

                        if (currentTodStep != lastTodStep) {
                            lastTodStep = currentTodStep;
                        }

                        updateNightOverlay(cycleProgress);

                        simAccumulator -= SIM_STEP;
                        steps++;
                    }

                    if (!postGameFreePlay) {
                        if (enemyTownHall != null && enemyTownHall.isDestroyed()) {
                            gameOver = true;
                            SwingUtilities.invokeLater(() -> triggerPostGameSelectionPopup("VICTORY! The enemy TownHall has fallen!"));
                        } else if (playerTownHall != null && playerTownHall.isDestroyed()) {
                            gameOver = true;
                            SwingUtilities.invokeLater(() -> triggerPostGameSelectionPopup("DEFEAT! Your TownHall was destroyed!"));
                        }
                    }

                    needsRepaint = true;
                }

                if (needsRepaint) {
                    needsRepaint = false;
                    SwingUtilities.invokeLater(this::repaint);
                }

                try { Thread.sleep(8); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }, "RTS-SimulationEngine");
        simulationThread.start();
    }

    private volatile boolean needsRepaint = false;
    private double simAccumulator = 0.0;
    private static final double SIM_STEP = 1.0 / 60.0;

    private void updateNightOverlay(float cycleProgress) {
        if (Math.abs(cycleProgress - lastCycleProgress) > 0.002f) {
            lastCycleProgress = cycleProgress;

            float maxDarkness = 0.65f;
            float calculatedDarkness;
            int r, g, b;

            if (cycleProgress < 0.05f) {
                float ratio = cycleProgress / 0.05f;
                calculatedDarkness = maxDarkness * (1.0f - ratio);
                r = (int)(DEEP_MIDNIGHT.getRed()   + (TWILIGHT_GLOW.getRed()   - DEEP_MIDNIGHT.getRed())   * ratio);
                g = (int)(DEEP_MIDNIGHT.getGreen() + (TWILIGHT_GLOW.getGreen() - DEEP_MIDNIGHT.getGreen()) * ratio);
                b = (int)(DEEP_MIDNIGHT.getBlue()  + (TWILIGHT_GLOW.getBlue()  - DEEP_MIDNIGHT.getBlue())  * ratio);
            } else if (cycleProgress < 0.50f) {
                calculatedDarkness = 0.0f;
                r = 0; g = 0; b = 0;
            } else if (cycleProgress < 0.55f) {
                float ratio = (cycleProgress - 0.50f) / 0.05f;
                calculatedDarkness = maxDarkness * ratio;
                r = (int)(TWILIGHT_GLOW.getRed()   + (DEEP_MIDNIGHT.getRed()   - TWILIGHT_GLOW.getRed())   * ratio);
                g = (int)(TWILIGHT_GLOW.getGreen() + (DEEP_MIDNIGHT.getGreen() - TWILIGHT_GLOW.getGreen()) * ratio);
                b = (int)(TWILIGHT_GLOW.getBlue()  + (DEEP_MIDNIGHT.getBlue()  - TWILIGHT_GLOW.getBlue())  * ratio);
            } else {
                calculatedDarkness = maxDarkness;
                r = DEEP_MIDNIGHT.getRed();
                g = DEEP_MIDNIGHT.getGreen();
                b = DEEP_MIDNIGHT.getBlue();
            }

            int targetAlpha = (int)(calculatedDarkness * 255);
            if (targetAlpha != lastTargetAlpha || r != (cachedNightOverlay.getRed()) || g != cachedNightOverlay.getGreen() || b != cachedNightOverlay.getBlue()) {
                lastTargetAlpha = targetAlpha;
                cachedNightOverlay = new Color(r, g, b, targetAlpha);
            }
        }
        activeNightOverlayColor = cachedNightOverlay;
    }

    private void updateLoading(int progress, String status) {
        SwingUtilities.invokeLater(() -> {
            this.loadingProgress = progress;
            this.loadingStatus   = status;
            repaint();
        });
    }

    private int getInitialTodStep() {
        float initialContinuousTime = 0.10f * 2.0f;
        float normalizedTime        = initialContinuousTime / 2.0f;
        return Math.max(0, Math.min(ChunkManager.NUM_TOD_STEPS-1, (int)(normalizedTime*ChunkManager.NUM_TOD_STEPS)));
    }

    private void spawnTownHalls() {
        int cols = CoordinateConverter.MAP_COLS;
        int rows = CoordinateConverter.MAP_ROWS;
        int centerQ = cols / 2;
        int centerR = rows / 2 - (centerQ >> 1);

        int[][] dirs = { {1,0},{1,-1},{0,-1},{-1,0},{-1,1},{0,1} };
        int dirIdx = new Random().nextInt(3);
        int dqAxis = dirs[dirIdx][0];
        int drAxis = dirs[dirIdx][1];

        int searchRadius = Math.min(cols, rows) / 2 - 10;

        HexTile pSpawn = findPlainsAlongAxis(centerQ, centerR, dqAxis, drAxis, searchRadius, true);
        HexTile eSpawn = findPlainsAlongAxis(centerQ, centerR, dqAxis, drAxis, searchRadius, false);

        if (pSpawn == null) pSpawn = findFallbackPlains(centerQ, centerR);
        if (eSpawn == null) eSpawn = findFallbackPlainsOpposite(centerQ, centerR, pSpawn);

        if (pSpawn != null) {
            forcePlainsAroundTownHall(pSpawn.q, pSpawn.r, TownHall.FOOTPRINT_RADIUS);
            playerTownHall = new TownHall(pSpawn.q, pSpawn.r, true);
            playerTownHall.registerShadowHeights();
        }
        if (eSpawn != null) {
            forcePlainsAroundTownHall(eSpawn.q, eSpawn.r, TownHall.FOOTPRINT_RADIUS);
            enemyTownHall = new TownHall(eSpawn.q, eSpawn.r, false);
            enemyTownHall.registerShadowHeights();
        }
    }

    private void forcePlainsAroundTownHall(int q, int r, int footprintRadius) {
        int totalRadius = footprintRadius + 3;
        for (int dq = -totalRadius; dq <= totalRadius; dq++) {
            int drMin = Math.max(-totalRadius, -dq - totalRadius);
            int drMax = Math.min(totalRadius, -dq + totalRadius);
            for (int dr = drMin; dr <= drMax; dr++) {
                HexTile t = GridManager.hexes.get(GridManager.packKey(q + dq, r + dr));
                if (t != null) {
                    t.setTileType(TileType.PLAIN);
                    t.setBiomeType(BiomeType.GRASSLAND);
                    t.customZ = -1;
                }
            }
        }
    }

    private HexTile findPlainsAlongAxis(int cq, int cr, int dq, int dr, int maxR, boolean positiveDir) {
        int step = positiveDir ? 1 : -1;
        int bestDist = 0;
        HexTile best = null;

        for (int dist = maxR; dist >= 15; dist--) {
            int tq = cq + dq * dist * step;
            int tr = cr + dr * dist * step;
            HexTile t = GridManager.hexes.get(GridManager.packKey(tq, tr));
            if (t == null || t.getTileType() != TileType.PLAIN) continue;

            if (isOpenPlains(t, TownHall.FOOTPRINT_RADIUS) && isFarFromSand(t, TOWNHALL_SAND_BUFFER)) {
                int d = hexDist(cq, cr, tq, tr);
                if (d > bestDist) {
                    bestDist = d;
                    best = t;
                }
                break;
            }
        }
        return best;
    }

    private HexTile findFallbackPlains(int cq, int cr) {
        for (HexTile t : GridManager.hexes.values()) {
            if (isFlatWalkable(t) && isOpenPlains(t, TownHall.FOOTPRINT_RADIUS) && isFarFromSand(t, 2)) return t;
        }
        for (HexTile t : GridManager.hexes.values()) {
            if (isFlatWalkable(t) && isOpenPlains(t, 2)) return t;
        }
        return null;
    }

    private HexTile findFallbackPlainsOpposite(int cq, int cr, HexTile pSpawn) {
        if (pSpawn == null) return findFallbackPlains(cq, cr);
        int bestDist = 0;
        HexTile best = null;
        for (HexTile t : GridManager.hexes.values()) {
            int d = hexDist(pSpawn.q, pSpawn.r, t.q, t.r);
            if (d >= TOWNHALL_MIN_SEPARATION && d > bestDist && isFlatWalkable(t) && isFarFromSand(t, 2)) {
                bestDist = d;
                best = t;
            }
        }
        return best;
    }

    private boolean isOpenPlains(HexTile center, int radius) {
        int baseZ = center.getZ();
        for (int dq = -radius; dq <= radius; dq++) {
            for (int dr = -radius; dr <= radius; dr++) {
                if (hexDist(0,0,dq,dr) > radius) continue;
                HexTile n = GridManager.hexes.get(GridManager.packKey(center.q+dq, center.r+dr));
                if (n == null || !isFlatWalkable(n) || n.getZ() != baseZ) return false;
            }
        }
        return true;
    }

    private boolean isFarFromSand(HexTile center, int buffer) {
        for (int dq = -buffer; dq <= buffer; dq++) {
            for (int dr = -buffer; dr <= buffer; dr++) {
                if (hexDist(0,0,dq,dr) > buffer) continue;
                HexTile n = GridManager.hexes.get(GridManager.packKey(center.q+dq, center.r+dr));
                if (n != null && n.getTileType() == TileType.SAND) return false;
            }
        }
        return true;
    }

    private boolean isFlatWalkable(HexTile t) {
        TileType tt = t.getTileType();
        return tt == TileType.PLAIN || tt == TileType.SAND || tt == TileType.SNOW;
    }

    void enterWorldMode() {
        currentState      = GameState.LOADING;
        activeCommandMode = CommandType.MOVE;
        gameOver          = false;
        postGameFreePlay  = false;
        playerStats.reset();

        // renderer.clearSpriteCache();

        synchronized (unitsLock) { units.clear(); }
        unitTeams.clear();
        commandQueue.clear();
        selectionManager.selectedUnits.clear();
        selectionManager.selectionHistory.clear();

        currentCycleTime = 0.05 * DAY_NIGHT_CYCLE_SECONDS;
        currentDay       = 1;
        GridManager.currentDay = 1;
        totalSessionTime = 0.0;
        activeNightOverlayColor = COLOR_CLEAR;
        cachedNightOverlay      = COLOR_CLEAR;
        lastTargetAlpha         = -1;
        lastCycleProgress       = -1f;
        lastTodStep             = -1;

        GridManager.setTimeOfDay(0.10f);

        int sizeVal = (int)(450 * (worldSizePercentage / 100.0));
        CoordinateConverter.MAP_COLS = sizeVal;
        CoordinateConverter.MAP_ROWS = sizeVal;
        CoordinateConverter.computeOrigin(getWidth(), getHeight());

        long finalSeed = seedInputString.isEmpty()
                ? new Random().nextLong() : Long.parseLong(seedInputString);

        updateLoading(0, "Preparing world generation...");

        new Thread(() -> {
            try {
                updateLoading(2, "Clearing simulation memory grids...");
                Thread.sleep(30);

                updateLoading(5, "Generating procedural landmass from seed: " + finalSeed + "...");
                GridManager.generateMap(finalSeed);

                updateLoading(45, "Calculating optimal starting coordinates for faction Town Halls...");
                spawnTownHalls();

                updateLoading(50, "Calculating directional lighting and shadows...");
                GridManager.calculateShadows();

                updateLoading(60, "Building base glow cache...");
                updateGlowCaches();

                updateLoading(65, "Initializing dynamic chunk indexing structures...");
                chunkManager.buildChunks(renderer);

                updateLoading(75, "Assigning tiles to chunk buckets...");
                updateLoading(80, "Sorting chunk tile arrays...");

                Point spawnPixel = null;
                if (playerTownHall != null)
                    spawnPixel = new Point(CoordinateConverter.getPixelX(playerTownHall.q, playerTownHall.r), CoordinateConverter.getPixelY(playerTownHall.q, playerTownHall.r));

                int initialTod = getInitialTodStep();
                chunkManager.setShadowsReadyForTod(initialTod);

                updateLoading(85, "Pre-rendering local world view assets...");
                chunkManager.preBakeInitialMap(initialTod, spawnPixel);

                updateLoading(92, "Baking high-performance static minimap overlay textures...");
                hudManager.bakeMinimap();

                updateLoading(96, "Assembling starting faction armies...");

                SwingUtilities.invokeLater(() -> {
                    if (playerTownHall != null) spawnInitialUnitsAround(playerTownHall.q, playerTownHall.r, true);
                    if (enemyTownHall  != null) spawnInitialUnitsAround(enemyTownHall.q,  enemyTownHall.r,  false);
                    updateLoading(97, "Centering camera...");
                    centerCameraOnPlayerTownhall();
                    lastTime     = System.nanoTime();
                    updateLoading(100, "World ready!");
                    currentState = GameState.PLAY_WORLD;
                    repaint();
                });
            } catch (Exception ex) { ex.printStackTrace(); }
        }).start();
    }

    void enterTesterMode() {
        currentState      = GameState.LOADING;
        activeCommandMode = CommandType.MOVE;
        gameOver          = false;
        postGameFreePlay  = false;
        playerStats.reset();

        // renderer.clearSpriteCache();

        synchronized (unitsLock) { units.clear(); }
        unitTeams.clear();
        commandQueue.clear();
        selectionManager.selectedUnits.clear();
        selectionManager.selectionHistory.clear();

        currentCycleTime = 0.05 * DAY_NIGHT_CYCLE_SECONDS;
        currentDay       = 1;
        GridManager.currentDay = 1;
        totalSessionTime = 0.0;
        activeNightOverlayColor = COLOR_CLEAR;
        cachedNightOverlay      = COLOR_CLEAR;
        lastTargetAlpha         = -1;
        lastCycleProgress       = -1f;
        lastTodStep             = -1;
        GridManager.setTimeOfDay(0.10f);

        CoordinateConverter.MAP_COLS = 600;
        CoordinateConverter.MAP_ROWS = 600;
        CoordinateConverter.computeOrigin(getWidth(), getHeight());

        updateLoading(10, "Prepping building test sandbox...");

        new Thread(() -> {
            try {
                Thread.sleep(50);
                updateLoading(30, "Flattening full-scale uniform terrain matrix...");
                BuildTesterWorld.setup(units, unitsLock);
                GridManager.calculateShadows();

                updateLoading(50, "Initializing sandbox chunk frameworks...");
                chunkManager.buildChunks(renderer);

                int   initialTod        = getInitialTodStep();
                chunkManager.setShadowsReadyForTod(initialTod);
                Point sandboxCenterPixel = new Point((int)GridManager.landCenterPX, (int)GridManager.landCenterPY);
                updateLoading(70, "Pre-rendering sandbox viewport terrain segments...");
                chunkManager.preBakeInitialMap(initialTod, sandboxCenterPixel);

                updateLoading(85, "Baking sandbox minimap vectors...");
                hudManager.bakeMinimap();

                updateLoading(95, "Spawning builder squad...");

                SwingUtilities.invokeLater(() -> {
                    Random rand = new Random();
                    int pQ = rand.nextInt(CoordinateConverter.MAP_COLS - 100) + 50;
                    int pR = rand.nextInt(CoordinateConverter.MAP_ROWS - 100) + 50 - (pQ / 2);
                    int eQ = pQ, eR = pR;
                    while (hexDist(pQ, pR, eQ, eR) < 40) {
                        eQ = rand.nextInt(CoordinateConverter.MAP_COLS - 100) + 50;
                        eR = rand.nextInt(CoordinateConverter.MAP_ROWS - 100) + 50 - (eQ / 2);
                    }
                    playerTownHall = new TownHall(pQ, pR, true);
                    enemyTownHall  = new TownHall(eQ, eR, false);
                    updateGlowCaches();
                    spawnInitialUnitsAround(playerTownHall.q, playerTownHall.r, true);
                    spawnInitialUnitsAround(enemyTownHall.q,  enemyTownHall.r,  false);
                    centerCameraOnPlayerTownhall();
                    lastTime     = System.nanoTime();
                    currentState = GameState.PLAY_TESTER;
                    repaint();
                });
            } catch (Exception ex) { ex.printStackTrace(); }
        }).start();
    }

    private void centerCameraOnPlayerTownhall() {
        int screenCX = getWidth()  / 2;
        int screenCY = (getHeight() - RTSHUD.HUD_HEIGHT) / 2;
        if (playerTownHall != null) {
            int pX = CoordinateConverter.getPixelX(playerTownHall.q, playerTownHall.r);
            int pY = CoordinateConverter.getPixelY(playerTownHall.q, playerTownHall.r);
            cameraManager.offsetX = (int)(screenCX - (pX * cameraManager.zoom));
            cameraManager.offsetY = (int)(screenCY - (pY * cameraManager.zoom));
        } else {
            cameraManager.offsetX = (int)(screenCX - (GridManager.landCenterPX * cameraManager.zoom));
            cameraManager.offsetY = (int)(screenCY - (GridManager.landCenterPY * cameraManager.zoom));
        }
    }

    private void spawnInitialUnitsAround(int baseQ, int baseR, boolean isPlayerOwned) {
        UnitType[] types = UnitType.values();
        int idx = 0;
        for (HexTile tile : GridManager.hexes.values()) {
            TileType tt = tile.getTileType();
            if (tt == TileType.PLAIN || tt == TileType.SAND || tt == TileType.SNOW) {
                if (hexDist(baseQ, baseR, tile.q, tile.r) <= 6
                        && hexDist(baseQ, baseR, tile.q, tile.r) > 1) {
                    Unit u = new Unit(tile.q, tile.r, types[idx % types.length], isPlayerOwned);
                    synchronized (unitsLock) { units.add(u); }
                    unitTeams.put(u, isPlayerOwned);
                    idx++;
                    if (idx >= 20) break;
                }
            }
        }
    }

    private void triggerPostGameSelectionPopup(String outcomeMessage) {
        Object[] choices = {"Continue Playing", "Main Menu", "Quit Game"};
        int result = JOptionPane.showOptionDialog(this,
                outcomeMessage + "\nWhat would you like to do next?",
                "Match Concluded", JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
        if (result == 0) {
            this.gameOver = false; this.postGameFreePlay = true;
        } else if (result == 1) {
            this.currentState = GameState.MENU;
            if (pauseManager.isPaused()) pauseManager.togglePause();
            repaint();
        } else {
            System.exit(0);
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        Dimension s = getSize();
        if (s.width > 0 && s.height > 0 && (s.width != lastWidth || s.height != lastHeight)) {
            lastWidth  = s.width;
            lastHeight = s.height;
            CoordinateConverter.computeOrigin(s.width, s.height);
            if (currentState == GameState.MENU || currentState == GameState.WORLD_SETTINGS) {
                cameraManager.centerOn(CoordinateConverter.getMapWidth(),
                        CoordinateConverter.getMapHeight(), s.width, s.height);
            } else if (currentState != GameState.LOADING && !GridManager.hexes.isEmpty()) {
                chunkRebuildPending = true;
                int screenCX = s.width  / 2;
                int screenCY = (s.height - RTSHUD.HUD_HEIGHT) / 2;
                cameraManager.offsetX = (int)(screenCX - (GridManager.landCenterPX * cameraManager.zoom));
                cameraManager.offsetY = (int)(screenCY - (GridManager.landCenterPY * cameraManager.zoom));
            }
        }
    }

    private static class RenderNode implements Comparable<RenderNode> {
        double sortY;
        Object entity;
        public RenderNode(double y, Object e) {
            this.sortY = y;
            this.entity = e;
        }
        @Override
        public int compareTo(RenderNode o) {
            return Double.compare(this.sortY, o.sortY);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;

        if (!renderHintsCached) {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,  RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING,      RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,   RenderingHints.VALUE_ANTIALIAS_ON);
            renderHintsCached = true;
        }

        if (currentState == GameState.MENU) {
            // menuRenderer.drawMainMenu(g2d, getWidth(), getHeight()); return;
        }
        if (currentState == GameState.WORLD_SETTINGS) {
            // menuRenderer.drawWorldSettingsMenu(g2d, getWidth(), getHeight(),
            //         worldSizePercentage, seedInputString,
            //         sizeMinusBtn, sizePlusBtn, startWorldBtn, settingsBackBtn); return;
        }
        if (currentState == GameState.LOADING) {
            // menuRenderer.drawLoadingScreen(g2d, getWidth(), getHeight(),
            //         loadingProgress, loadingStatus); return;
        }

        if (chunkRebuildPending && !GridManager.hexes.isEmpty()) {
            chunkRebuildPending = false;
            chunkManager.buildChunks(renderer);
        }

        selectionManager.dragStartX = this.dragStartX;
        selectionManager.dragStartY = this.dragStartY;
        selectionManager.dragEndX   = this.dragEndX;
        selectionManager.dragEndY   = this.dragEndY;
        selectionManager.dragging   = this.dragging;

        double viewWorldX = -cameraManager.offsetX;
        double viewWorldY = -cameraManager.offsetY;
        double viewWorldW = getWidth() / cameraManager.zoom;
        double viewWorldH = getHeight() / cameraManager.zoom;
        double margin = 80;

        java.awt.geom.AffineTransform oldTransform = g2d.getTransform();

        // 1. Draw Ground Terrain
        g2d.scale(cameraManager.zoom, cameraManager.zoom);
        g2d.translate(cameraManager.offsetX, cameraManager.offsetY);
        chunkManager.renderChunks(g2d, cameraManager.offsetX, cameraManager.offsetY,
                getWidth(), getHeight(), cameraManager.zoom);

        // 1.5. Draw Collective Shadow Overlay (one seamless shadow, not per-tile)
        int targetLod = (cameraManager.zoom >= 0.55) ? 0 : (cameraManager.zoom >= 0.30 ? 1 : 2);
        chunkManager.drawCollectiveShadowOverlay(g2d, targetLod);

        g2d.setTransform(oldTransform);

        // 2. Build Depth-Sorted Entities
        g2d.scale(cameraManager.zoom, cameraManager.zoom);
        g2d.translate(cameraManager.offsetX, cameraManager.offsetY);

        List<RenderNode> renderNodes = new ArrayList<>();
        boolean playerTHVisible = playerTownHall != null && !playerTownHall.isDestroyed();
        boolean enemyTHVisible = enemyTownHall != null && !enemyTownHall.isDestroyed();

        if (playerTHVisible) {
            HexTile pTile = GridManager.hexes.get(GridManager.packKey(playerTownHall.q, playerTownHall.r));
            int tz = pTile != null ? pTile.getZ() : 0;
            playerTownHall.drawFootprintBoundary(g2d, hexSize, tz);
        }
        if (enemyTHVisible) {
            HexTile eTile = GridManager.hexes.get(GridManager.packKey(enemyTownHall.q, enemyTownHall.r));
            int tz = eTile != null ? eTile.getZ() : 0;
            enemyTownHall.drawFootprintBoundary(g2d, hexSize, tz);
        }

        if (playerTHVisible) {
            int thPxX = CoordinateConverter.getPixelX(playerTownHall.q, playerTownHall.r);
            int thPxY = CoordinateConverter.getPixelY(playerTownHall.q, playerTownHall.r);
            if (thPxX + 200 > viewWorldX - margin && thPxX - 200 < viewWorldX + viewWorldW + margin
                    && thPxY + 200 > viewWorldY - margin && thPxY - 200 < viewWorldY + viewWorldH + margin) {
                HexTile pTile = GridManager.hexes.get(GridManager.packKey(playerTownHall.q, playerTownHall.r));
                int tz = pTile != null ? pTile.getZ() : 0;
                renderNodes.add(new RenderNode(thPxY - tz, playerTownHall));
            }
        }
        if (enemyTHVisible) {
            int thPxX = CoordinateConverter.getPixelX(enemyTownHall.q, enemyTownHall.r);
            int thPxY = CoordinateConverter.getPixelY(enemyTownHall.q, enemyTownHall.r);
            if (thPxX + 200 > viewWorldX - margin && thPxX - 200 < viewWorldX + viewWorldW + margin
                    && thPxY + 200 > viewWorldY - margin && thPxY - 200 < viewWorldY + viewWorldH + margin) {
                HexTile eTile = GridManager.hexes.get(GridManager.packKey(enemyTownHall.q, enemyTownHall.r));
                int tz = eTile != null ? eTile.getZ() : 0;
                renderNodes.add(new RenderNode(thPxY - tz, enemyTownHall));
            }
        }

        int uSize;
        synchronized (unitsLock) { uSize = units.size(); }
        if (renderUnitSnapshot.length != uSize) {
            renderUnitSnapshot = new Unit[uSize];
        }
        synchronized (unitsLock) {
            for(int idx = 0; idx < uSize; idx++) {
                renderUnitSnapshot[idx] = units.get(idx);
            }
        }

        for (int i = 0; i < uSize; i++) {
            Unit u = renderUnitSnapshot[i];
            if (u == null) continue;
            if (u.x - 30 > viewWorldX + viewWorldW + margin || u.x + 30 < viewWorldX - margin
                    || u.y - 30 > viewWorldY + viewWorldH + margin || u.y + 30 < viewWorldY - margin) continue;
            renderNodes.add(new RenderNode(u.y, u));
        }

        Collections.sort(renderNodes);

        g2d.setFont(UNIT_FONT);
        if (cachedFontMetrics == null) cachedFontMetrics = g2d.getFontMetrics();
        FontMetrics fm = cachedFontMetrics;

        List<Unit> unitBatch = new ArrayList<>();
        List<TownHall> townHallBatch = new ArrayList<>();

        for (RenderNode node : renderNodes) {
            if (node.entity instanceof Unit) {
                unitBatch.add((Unit) node.entity);
            } else if (node.entity instanceof TownHall) {
                if (!unitBatch.isEmpty()) {
                    renderer.renderDynamicEntities(g2d, unitBatch, unitsLock, selectionManager, viewWorldX, viewWorldY, viewWorldW, viewWorldH, margin);
                    drawUnitTokens(g2d, unitBatch, fm);
                    unitBatch.clear();
                }
            }

            TownHall th = (TownHall) node.entity;
            HexTile t = GridManager.hexes.get(GridManager.packKey(th.q, th.r));
            int tz = t != null ? t.getZ() : 0;
            // renderer.drawTownHall(g2d, th, tz, hexSize, th.isPlayer ? COLOR_PLAYER_TH : COLOR_ENEMY_TH);
            th.drawHealthBar(g2d, hexSize, tz);
            townHallBatch.add(th);
        }

        if (!unitBatch.isEmpty()) {
            // renderer.renderDynamicEntities(g2d, unitBatch, unitsLock, selectionManager, viewWorldX, viewWorldY, viewWorldW, viewWorldH, margin);
            drawUnitTokens(g2d, unitBatch, fm);
            unitBatch.clear();
        }

        g2d.setTransform(oldTransform);

        // 3. Draw Night Overlay AFTER Entities
        if (activeNightOverlayColor.getAlpha() > 0) {
            g2d.setColor(activeNightOverlayColor);
            g2d.fillRect(0, 0, getWidth(), getHeight());

            g2d.scale(cameraManager.zoom, cameraManager.zoom);
            g2d.translate(cameraManager.offsetX, cameraManager.offsetY);

            float alphaRatio = activeNightOverlayColor.getAlpha() / 255.0f;

            if (playerTHVisible) drawGlowTiles(g2d, playerGlowCache, viewWorldX, viewWorldY, viewWorldW, viewWorldH, margin, alphaRatio);
            if (enemyTHVisible) drawGlowTiles(g2d, enemyGlowCache, viewWorldX, viewWorldY, viewWorldW, viewWorldH, margin, alphaRatio);

            g2d.setTransform(oldTransform);
        }

        // 4. Re-draw TownHalls on top of night overlay so they are not darkened
        if (!townHallBatch.isEmpty()) {
            g2d.scale(cameraManager.zoom, cameraManager.zoom);
            g2d.translate(cameraManager.offsetX, cameraManager.offsetY);

            for (TownHall th : townHallBatch) {
                HexTile t = GridManager.hexes.get(GridManager.packKey(th.q, th.r));
                int tz = t != null ? t.getZ() : 0;
                // renderer.drawTownHall(g2d, th, tz, hexSize, th.isPlayer ? COLOR_PLAYER_TH : COLOR_ENEMY_TH);
                th.drawHealthBar(g2d, hexSize, tz);
            }

            g2d.setTransform(oldTransform);
        }

        // 4. Draw UI Overlays
        if (selectionManager.dragging) {
            int bx = Math.min(selectionManager.dragStartX, selectionManager.dragEndX);
            int by = Math.min(selectionManager.dragStartY, selectionManager.dragEndY);
            int bw = Math.abs(selectionManager.dragStartX - selectionManager.dragEndX);
            int bh = Math.abs(selectionManager.dragStartY - selectionManager.dragEndY);
            g2d.setColor(COLOR_SEL_BOX_FILL);
            g2d.fillRect(bx, by, bw, bh);
            g2d.setColor(Color.YELLOW);
            g2d.setStroke(STROKE_NORMAL);
            g2d.drawRect(bx, by, bw, bh);
            g2d.setStroke(STROKE_DEFAULT);
        }

        hudManager.renderHUD(g2d, getWidth(), getHeight());
        pauseManager.drawPauseOverlay(g2d, getWidth(), getHeight(), playerStats);
        java.awt.Toolkit.getDefaultToolkit().sync();
    }


    private void drawUnitTokens(Graphics2D g2d, List<Unit> batch, FontMetrics fm) {
        for (Unit u : batch) {
            boolean isPlayer = unitTeams.getOrDefault(u, true);
            g2d.setColor(isPlayer ? COLOR_PLAYER_UNIT : COLOR_ENEMY_UNIT);
            int tr = 15;
            g2d.fillOval((int)u.x - tr, (int)u.y - tr, tr*2, tr*2);

            g2d.setColor(u.selected ? Color.YELLOW : Color.BLACK);
            g2d.setStroke(u.selected ? STROKE_SELECTED : STROKE_NORMAL);
            g2d.drawOval((int)u.x - tr, (int)u.y - tr, tr*2, tr*2);

            int hp   = combatManager.getUnitHp(u);
            double hpPct = hp / 100.0;
            int bw = 30, bh = 4, bx = (int)u.x - 15, by = (int)u.y - 23;
            g2d.setColor(COLOR_HP_BACKGROUND); g2d.fillRect(bx, by, bw, bh);
            g2d.setColor(COLOR_HP_FILL);       g2d.fillRect(bx, by, (int)(bw*hpPct), bh);
            g2d.setColor(Color.BLACK);
            g2d.setStroke(STROKE_DEFAULT);     g2d.drawRect(bx, by, bw, bh);

            String lbl = u.type.name();
            int lx = (int)u.x - (fm.stringWidth(lbl)/2), ly = (int)u.y + 27;
            g2d.setColor(COLOR_LABEL_SHADOW); g2d.drawString(lbl, lx+1, ly+1);
            g2d.setColor(Color.WHITE);        g2d.drawString(lbl, lx,   ly);
        }
        g2d.setStroke(STROKE_DEFAULT);
    }

    private void drawGlowTiles(Graphics2D g2d, List<GlowTier> cache,
                               double viewX, double viewY, double viewW, double viewH,
                               double margin, float alphaRatio) {
        for (GlowTier tier : cache) {
            Rectangle bounds = tier.bounds;
            if (bounds.x - 50 > viewX + viewW + margin || bounds.x + bounds.width + 50 < viewX - margin
                    || bounds.y - 50 > viewY + viewH + margin || bounds.y + bounds.height + 50 < viewY - margin) continue;

            int a = Math.max(0, Math.min(255, (int)(tier.maxAlpha * alphaRatio)));
            g2d.setColor(groundGlowColorCache[a]);
            g2d.fill(tier.area);
        }
    }

    private static int hexDist(int q1, int r1, int q2, int r2) {
        int dq = q2-q1, dr = r2-r1;
        return (Math.abs(dq) + Math.abs(dr) + Math.abs(dq+dr)) / 2;
    }

    int currentDestBoundsWidth()  { return CoordinateConverter.getMapWidth()  + 10; }
    int currentDestBoundsHeight() { return CoordinateConverter.getMapHeight() + 250; }

    public static Point pixelToHex(int x, int y) {
        return CoordinateConverter.pixelToHex(x, y);
    }
}
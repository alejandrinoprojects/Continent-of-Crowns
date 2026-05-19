import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

public class WorldState {
    public final ArrayList<Unit> units = new ArrayList<>();
    public final Object unitsLock = new Object();
    public final Queue<Command> commandQueue = new LinkedList<>();
    public final Map<Unit, Boolean> unitTeams = new ConcurrentHashMap<>();

    public final SelectionManager selectionManager = new SelectionManager();
    public final MovementController movementController = new MovementController();
    public final CombatManager combatManager = new CombatManager();
    public final StatsManager playerStats = new StatsManager();
    
    public TownHall playerTownHall;
    public TownHall enemyTownHall;
    
    public void setupInitialState() {
        // Find best spots for townhalls (simplified logic from GamePanel)
        HexTile pt = findValidTownhallSpot(50);
        if (pt != null) {
            playerTownHall = new TownHall(pt.q, pt.r, true);
            spawnInitialUnitsAround(playerTownHall.q, playerTownHall.r, true);
        }
        
        HexTile et = findValidTownhallSpot(CoordinateConverter.getMapWidth() - 50);
        if (et != null) {
            enemyTownHall = new TownHall(et.q, et.r, false);
            spawnInitialUnitsAround(enemyTownHall.q, enemyTownHall.r, false);
        }
    }
    
    private HexTile findValidTownhallSpot(int targetX) {
        for (HexTile tile : GridManager.hexes.values()) {
            if (tile.getTileType() != TileType.WATER && tile.getTileType() != TileType.ROCK) {
                int px = CoordinateConverter.getPixelX(tile.q, tile.r);
                if (Math.abs(px - targetX) < 100) {
                    return tile;
                }
            }
        }
        return null; // fallback
    }

    private void spawnInitialUnitsAround(int baseQ, int baseR, boolean isPlayerOwned) {
        int[] dq = {1, 1, 0, -1, -1, 0};
        int[] dr = {0, -1, -1, 0, 1, 1};

        for (int i = 0; i < 6; i++) {
            int q = baseQ + dq[i];
            int r = baseR + dr[i];
            HexTile tile = GridManager.hexes.get(GridManager.packKey(q, r));
            if (tile != null && tile.getTileType() != TileType.WATER && tile.getTileType() != TileType.ROCK) {
                int px = CoordinateConverter.getPixelX(q, r);
                int py = CoordinateConverter.getPixelY(q, r);
                Unit u = new Unit(q, r, UnitType.KNIGHT, isPlayerOwned);
                synchronized (unitsLock) { 
                    units.add(u); 
                    unitTeams.put(u, isPlayerOwned);
                }
            }
        }
    }

    public void updateSimulation(double dt) {
        movementController.updateSimulation(dt, units, unitsLock, commandQueue);
        combatManager.updateCombat(dt, units, unitsLock, playerTownHall, enemyTownHall, playerStats, unitTeams);
    }
}

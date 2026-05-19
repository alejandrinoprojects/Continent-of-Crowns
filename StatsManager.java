import java.util.EnumMap;
import java.util.Map;

public class StatsManager {
    private int totalKills = 0;
    private int totalLosses = 0;
    private double totalDamageDealt = 0.0;
    private double totalDamageTaken = 0.0;
    private int totalHealingDone = 0;

    private final Map<UnitType, Integer> killsByUnitType = new EnumMap<>(UnitType.class);
    private final Map<UnitType, Integer> lossesByUnitType = new EnumMap<>(UnitType.class);

    public StatsManager() { reset(); }

    public void reset() {
        totalKills = 0; totalLosses = 0;
        totalDamageDealt = 0.0; totalDamageTaken = 0.0; totalHealingDone = 0;
        for (UnitType type : UnitType.values()) {
            killsByUnitType.put(type, 0); lossesByUnitType.put(type, 0);
        }
    }

    public void logKill(UnitType victimType) {
        totalKills++; killsByUnitType.put(victimType, killsByUnitType.get(victimType) + 1);
    }

    public void logLoss(UnitType lostType) {
        totalLosses++; lossesByUnitType.put(lostType, lossesByUnitType.get(lostType) + 1);
    }

    public void logDamageDealt(double amount) { if (amount > 0) totalDamageDealt += amount; }
    public void logDamageTaken(double amount) { if (amount > 0) totalDamageTaken += amount; }
    public void logHealing(int amount) { if (amount > 0) totalHealingDone += amount; }

    public int getTotalKills() { return totalKills; }
    public int getTotalLosses() { return totalLosses; }
    public double getTotalDamageDealt() { return totalDamageDealt; }
    public double getTotalDamageTaken() { return totalDamageTaken; }
    public int getTotalHealingDone() { return totalHealingDone; }

    public double getKillDeathRatio() {
        if (totalLosses == 0) return totalKills;
        return (double) totalKills / totalLosses;
    }
}
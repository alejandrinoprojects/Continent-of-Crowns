import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CombatManager {

    private final Map<Unit, Integer> healthMap = new HashMap<>();
    private final Map<Unit, Double> attackCooldowns = new HashMap<>();

    /**
     * Helper mapping method to assign custom attack values to your UnitType enum.
     */
    private int getUnitAttack(UnitType type) {
        switch (type) {
            case MAGE:     return 40;
            case KNIGHT:   return 28;
            case CAVALRY:  return 22;
            case ARCHER:   return 18;
            case TANK:     return 15;
            case HEALER:   return 8;
            case VILLAGER:
            default:       return 5;
        }
    }

    /**
     * Helper mapping method to assign custom armor defense values to your UnitType enum.
     */
    private int getUnitDefense(UnitType type) {
        switch (type) {
            case TANK:     return 8;
            case KNIGHT:   return 4;
            case CAVALRY:  return 2;
            case HEALER:   return 2;
            case ARCHER:   return 1;
            case MAGE:
            case VILLAGER:
            default:       return 0;
        }
    }

    /**
     * Helper mapping method to assign custom hex ranges to your UnitType enum.
     */
    private int getUnitRange(UnitType type) {
        switch (type) {
            case ARCHER:   return 5;
            case MAGE:     return 4;
            case HEALER:   return 3;
            case CAVALRY:
            case KNIGHT:
            case TANK:
            case VILLAGER:
            default:       return 1; // Standard melee range
        }
    }

    /**
     * Public accessor allowing GamePanel to read the internal live health status
     * of a unit to dynamically scale its floating health bar width metrics.
     */
    public int getUnitHp(Unit unit) {
        return healthMap.getOrDefault(unit, 100);
    }

    /**
     * Main ticking loop for processing attacks.
     */
    public void updateCombat(double dt, List<Unit> units, Object unitsLock, TownHall playerBase, TownHall enemyBase, StatsManager stats, Map<Unit, Boolean> unitTeams) {
        // 1. Age down attack cooldown timers
        for (Map.Entry<Unit, Double> entry : attackCooldowns.entrySet()) {
            if (entry.getValue() > 0) {
                entry.setValue(entry.getValue() - dt);
            }
        }

        // Clean up dead units from local memory tracking pools
        synchronized (unitsLock) {
            healthMap.keySet().removeIf(unit -> !units.contains(unit));
            attackCooldowns.keySet().removeIf(unit -> !units.contains(unit));
        }

        List<Unit> unitsToKill = new ArrayList<>();

        // 2. Evaluate attack opportunities for every unit
        synchronized (unitsLock) {
            for (Unit attacker : units) {
                if (!healthMap.containsKey(attacker)) {
                    healthMap.put(attacker, 100);
                }

                // Skip if the unit recently attacked and is still recovering
                if (attackCooldowns.getOrDefault(attacker, 0.0) > 0) {
                    continue;
                }

                // Look for an optimal enemy target within attack range
                Object target = findValidTarget(attacker, units, playerBase, enemyBase, unitTeams);

                if (target != null) {
                    // Strike!
                    dealDamage(attacker, target, stats, unitsToKill, unitTeams);

                    // Reset attack cooldown timer (1.5 seconds between weapon swings)
                    attackCooldowns.put(attacker, 1.5);
                }
            }

            // 3. Purge units whose health reached zero from active simulation memory
            if (!unitsToKill.isEmpty()) {
                units.removeAll(unitsToKill);
                for (Unit victim : unitsToKill) {
                    unitTeams.remove(victim);
                }
            }
        }
    }

    private Object findValidTarget(Unit attacker, List<Unit> units, TownHall playerBase, TownHall enemyBase, Map<Unit, Boolean> unitTeams) {
        boolean attackerIsPlayer = unitTeams.getOrDefault(attacker, true);
        int attackRange = getUnitRange(attacker.type);

        // Step A: Spatial query — iterate hexes within attack range instead of scanning all units.
        // Uses GridManager.getUnitAt() for O(1) unit lookup per hex.
        // This is O(range²) ≈ O(25) for range 5, vs O(n) ≈ O(80) scanning all units.
        for (int dq = -attackRange; dq <= attackRange; dq++) {
            for (int dr = Math.max(-attackRange, -dq - attackRange); dr <= Math.min(attackRange, -dq + attackRange); dr++) {
                int hq = attacker.q + dq;
                int hr = attacker.r + dr;
                Unit potentialTarget = GridManager.getUnitAt(hq, hr);
                if (potentialTarget != null && potentialTarget != attacker) {
                    boolean targetIsPlayer = unitTeams.getOrDefault(potentialTarget, true);
                    if (attackerIsPlayer != targetIsPlayer) {
                        return potentialTarget;
                    }
                }
            }
        }

        // Step B: If no mobile units are in range, check if the enemy TownHall is within reach
        TownHall opponentBase = attackerIsPlayer ? enemyBase : playerBase;
        if (opponentBase != null) {
            int distToBase = HexMath.hexDistance(attacker.q, attacker.r, opponentBase.q, opponentBase.r);
            if (distToBase <= attackRange + 3) {
                return opponentBase;
            }
        }

        return null;
    }

    private void dealDamage(Unit attacker, Object target, StatsManager stats, List<Unit> unitsToKill, Map<Unit, Boolean> unitTeams) {
        boolean attackerIsPlayer = unitTeams.getOrDefault(attacker, true);

        // DYNAMIC: Gather the unique damage capability of the attacking unit
        int rawAttackPower = getUnitAttack(attacker.type);

        if (target instanceof Unit) {
            Unit victim = (Unit) target;

            // DYNAMIC: Gather the armor attribute of the victim and subtract it from incoming power
            int victimArmorValue = getUnitDefense(victim.type);
            int finalInflictedDamage = Math.max(1, rawAttackPower - victimArmorValue); // Always inflict at least 1 damage

            int currentHp = healthMap.getOrDefault(victim, 100);
            int nextHp = Math.max(0, currentHp - finalInflictedDamage);
            healthMap.put(victim, nextHp);

            if (attackerIsPlayer) {
                stats.logDamageDealt(finalInflictedDamage);
            } else {
                stats.logDamageTaken(finalInflictedDamage);
            }

            if (nextHp <= 0) {
                unitsToKill.add(victim);
                if (attackerIsPlayer) {
                    stats.logKill(victim.type);
                } else {
                    stats.logLoss(victim.type);
                }
                healthMap.remove(victim);
                attackCooldowns.remove(victim);
            }
        }
        else if (target instanceof TownHall) {
            TownHall baseTarget = (TownHall) target;

            // Base structures have a flat armor rating mitigation of 5
            int finalBuildingDamage = Math.max(1, rawAttackPower - 5);
            baseTarget.takeDamage(finalBuildingDamage);

            if (attackerIsPlayer) {
                stats.logDamageDealt(finalBuildingDamage);
            } else {
                stats.logDamageTaken(finalBuildingDamage);
            }
        }
    }
}
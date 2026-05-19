public enum UnitType {
    CAVALRY(4.5),   // Fastest scout/flanker archetype
    KNIGHT(3.5),    // Fast standard melee
    ARCHER(3.0),    // Standard ranged speed
    HEALER(3.0),    // Standard support speed
    MAGE(2.5),      // Slower heavy caster
    TANK(1.8),      // Slowest front-line anchor
    VILLAGER(3.5);  // Builder unit type with standard movement speed

    public final double speed;

    UnitType(double speed) {
        this.speed = speed;
    }
}
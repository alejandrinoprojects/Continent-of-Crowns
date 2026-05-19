public enum TerrainType {
    RIVER(0),
    ICE(0),
    SAND(3),
    DARK_SAND(3),
    GRASS(6),
    TUNDRA(6),
    SAVANNAH(6),
    DESERT(6),
    FOREST(6),
    TAIGA(6),
    ROCK(25);

    public final int zHeight;
    TerrainType(int zHeight) {
        this.zHeight = zHeight;
    }
}
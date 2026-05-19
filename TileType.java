public enum TileType {
    PLAIN(6),
    SAND(3),
    WATER(0),
    ROCK(25),
    TREES(6),
    SNOW(6),
    ICE(0);

    public final int zHeight;
    TileType(int zHeight) {
        this.zHeight = zHeight;
    }
}

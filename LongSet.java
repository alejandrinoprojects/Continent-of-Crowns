public class LongSet {
    private long[] keys;
    private boolean[] used;
    private int size;

    public LongSet(int cap) {
        int n = 1;
        while (n < cap) n <<= 1;
        keys = new long[n];
        used = new boolean[n];
        size = 0;
    }

    private int probe(long k) {
        int mask = keys.length - 1;
        int i = (int)(k ^ (k >>> 32)) & mask;
        while (used[i] && keys[i] != k) i = (i + 1) & mask;
        return i;
    }

    public void clear() {
        for (int i = 0; i < used.length; i++) used[i] = false;
        size = 0;
    }

    public boolean add(long k) {
        int i = probe(k);
        if (used[i]) return false;
        keys[i] = k;
        used[i] = true;
        size++;
        return true;
    }

    public boolean contains(long k) {
        int i = probe(k);
        return used[i] && keys[i] == k;
    }

    public boolean remove(long k) {
        int i = probe(k);
        if (!used[i] || keys[i] != k) return false;
        used[i] = false;
        size--;
        int mask = keys.length - 1;
        for (int j = (i + 1) & mask; used[j]; j = (j + 1) & mask) {
            long rk = keys[j];
            used[j] = false;
            size--;
            int ri = probe(rk);
            keys[ri] = rk;
            used[ri] = true;
            size++;
        }
        return true;
    }

    public int size() { return size; }
}

package com.example.chestesp.feature;

import com.example.chestesp.module.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scannt die Chunks um den Spieler nach und nach (mit festem Zeitbudget pro Tick,
 * damit es nicht ruckelt) und haelt die gefundenen Tunnel-Boxen bereit.
 */
public final class Tunnels {
    /** Maximale Rechenzeit pro Tick. */
    private static final long BUDGET_NANOS = 2_000_000L;

    private static final Map<Long, List<TunnelScanner.Box>> byChunk = new HashMap<>();
    private static volatile List<TunnelScanner.Box> all = List.of();
    private static long[] queue = new long[0];
    private static int queueIndex;
    private static ClientLevel lastLevel;
    private static String lastSignature = "";
    private static boolean dirty;

    private Tunnels() {}

    public static List<TunnelScanner.Box> boxes() {
        return all;
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            clear();
            lastLevel = level;
        }
        if (!Modules.TUNNEL.isEnabled() || level == null || mc.player == null) {
            if (!all.isEmpty() || !byChunk.isEmpty()) clear();
            return;
        }

        int minLen = Modules.TUNNEL.minLength.intValue();
        int yRange = Modules.TUNNEL.yRange.intValue();
        int radius = Math.min(Modules.TUNNEL.radius.intValue(), mc.options.renderDistance().get());
        String signature = minLen + ":" + yRange + ":" + radius;
        if (!signature.equals(lastSignature)) {
            clear();
            lastSignature = signature;
        }

        BlockPos pp = mc.player.blockPosition();
        int pcx = pp.getX() >> 4, pcz = pp.getZ() >> 4;

        if (queueIndex >= queue.length) {
            rebuildQueue(pcx, pcz, radius);
        }

        int worldMin = level.getMinY();
        int worldMax = level.getMaxY();
        int yMin = Math.max(worldMin, pp.getY() - yRange);
        int yMax = Math.min(worldMax, pp.getY() + yRange);

        long deadline = System.nanoTime() + BUDGET_NANOS;
        while (queueIndex < queue.length && System.nanoTime() < deadline) {
            long key = queue[queueIndex++];
            int cx = (int) (key >> 32), cz = (int) key;
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
            List<TunnelScanner.Box> found = chunk == null ? List.of()
                    : TunnelScanner.scan(new LevelProbe(level, chunk, cx, cz),
                    cx << 4, cz << 4, yMin, yMax, worldMin, worldMax, minLen);
            if (found.isEmpty()) {
                if (byChunk.remove(key) != null) dirty = true;
            } else {
                byChunk.put(key, found);
                dirty = true;
            }
        }

        if (dirty) {
            List<TunnelScanner.Box> flat = new ArrayList<>();
            for (Collection<TunnelScanner.Box> c : byChunk.values()) flat.addAll(c);
            all = flat;
            dirty = false;
        }
    }

    private static void rebuildQueue(int pcx, int pcz, int radius) {
        List<long[]> list = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                list.add(new long[]{dx * dx + dz * dz, key(pcx + dx, pcz + dz)});
            }
        }
        list.sort((a, b) -> Long.compare(a[0], b[0]));
        queue = new long[list.size()];
        Set<Long> keep = new HashSet<>();
        for (int i = 0; i < queue.length; i++) {
            queue[i] = list.get(i)[1];
            keep.add(queue[i]);
        }
        queueIndex = 0;
        if (byChunk.keySet().retainAll(keep)) dirty = true;
    }

    private static void clear() {
        byChunk.clear();
        all = List.of();
        queue = new long[0];
        queueIndex = 0;
        dirty = false;
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }

    /** Liest Bloecke direkt aus dem Chunk (schnell) und nur an den Raendern ueber die Welt. */
    private static final class LevelProbe implements TunnelScanner.Probe {
        private final ClientLevel level;
        private final LevelChunk chunk;
        private final int cx, cz;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        LevelProbe(ClientLevel level, LevelChunk chunk, int cx, int cz) {
            this.level = level;
            this.chunk = chunk;
            this.cx = cx;
            this.cz = cz;
        }

        private BlockState state(int x, int y, int z) {
            pos.set(x, y, z);
            if ((x >> 4) == cx && (z >> 4) == cz) return chunk.getBlockState(pos);
            return level.getBlockState(pos);
        }

        @Override public boolean open(int x, int y, int z) {
            BlockState s = state(x, y, z);
            return s.isAir() || (!s.blocksMotion() && s.getFluidState().isEmpty());
        }

        @Override public boolean solid(int x, int y, int z) {
            return state(x, y, z).blocksMotion();
        }
    }
}

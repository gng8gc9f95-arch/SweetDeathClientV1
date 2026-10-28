package com.example.chestesp.feature;

import com.example.chestesp.module.Modules;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Findet "sus" Chunks: Chunks mit auffaellig vielen vom Spieler gebauten Bloecken
 * (Kisten, Enderkisten, Shulker, Hopper, Oefen, Leuchtfeuer ...) -> vermutlich eine Base.
 * Jeder Block-Entity-Typ hat ein Gewicht, die Summe ergibt den Score eines Chunks.
 */
public final class SusChunks {
    public record Entry(int cx, int cz, float score, int minY, int maxY) {}

    private static volatile List<Entry> entries = List.of();
    private static final Set<Long> notified = new HashSet<>();
    private static ClientLevel lastLevel;
    private static int cooldown;

    private SusChunks() {}

    public static List<Entry> entries() {
        return entries;
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            notified.clear();
            entries = List.of();
            lastLevel = level;
        }
        if (!Modules.SUS.isEnabled() || level == null || mc.player == null) {
            if (!entries.isEmpty()) entries = List.of();
            return;
        }
        if (++cooldown < 20) return;   // jede Sekunde neu scannen
        cooldown = 0;

        float threshold = Modules.SUS.threshold.floatValue();
        int radius = Math.min(mc.options.renderDistance().get(), Modules.SUS.radius.intValue());
        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;

        List<Entry> result = new ArrayList<>();
        Set<Long> present = new HashSet<>();

        for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
            for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;

                float score = 0f;
                int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
                for (Map.Entry<net.minecraft.core.BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
                    float w = weight(e.getValue());
                    if (w <= 0f) continue;
                    score += w;
                    int y = e.getKey().getY();
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
                if (score < threshold) continue;

                result.add(new Entry(cx, cz, score, minY, maxY));
                long key = key(cx, cz);
                present.add(key);
                if (Modules.SUS.chat.on() && notified.add(key)) {
                    mc.player.displayClientMessage(Component.empty()
                            .append(Component.literal("● ").withStyle(ChatFormatting.LIGHT_PURPLE))
                            .append(Component.literal("Sus Chunk ").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                            .append(Component.literal(String.format("X %d  Z %d", cx * 16 + 8, cz * 16 + 8))
                                    .withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(String.format("  ·  Score %.0f  ·  Y %d–%d", score, minY, maxY))
                                    .withStyle(ChatFormatting.DARK_GRAY)), false);
                }
            }
        }

        // Chunks, die nicht mehr sus sind (oder weit weg), duerfen spaeter erneut melden
        notified.retainAll(present);
        entries = result;
    }

    private static float weight(BlockEntity be) {
        if (be instanceof BeaconBlockEntity) return 6f;
        if (be instanceof EnderChestBlockEntity || be instanceof ShulkerBoxBlockEntity) return 3f;
        if (be instanceof HopperBlockEntity || be instanceof EnchantingTableBlockEntity) return 2f;
        if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity
                || be instanceof AbstractFurnaceBlockEntity || be instanceof DispenserBlockEntity
                || be instanceof BrewingStandBlockEntity) return 1f;
        return 0f;   // Spawner, Betten, Schilder usw. zaehlen nicht
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }
}

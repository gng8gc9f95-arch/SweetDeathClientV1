package com.example.chestesp.feature;

import com.example.chestesp.gui.Notifications;
import com.example.chestesp.module.Modules;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Meldet Spawner in der Naehe (einmal pro Spawner, solange man in Reichweite bleibt)
 * mit Popup inkl. Bild und Mob-Typ. Alle Funde der Sitzung landen in einer Liste,
 * die im Menue unter "Funde" angezeigt wird.
 */
public final class SpawnerAlert {
    /** Ein gefundener Spawner. */
    public record Find(BlockPos pos, String mob, ItemStack egg, boolean trial, long time) {
        public ItemStack icon() {
            return new ItemStack(trial ? Items.TRIAL_SPAWNER : Items.SPAWNER);
        }

        public String title() {
            if (trial) return "Trial Spawner";
            return mob == null ? "Leerer Spawner" : mob + "-Spawner";
        }
    }

    private static final int MAX_FINDS = 64;
    private static final List<Find> finds = new ArrayList<>();
    private static final Set<BlockPos> notified = new HashSet<>();
    private static ClientLevel lastLevel;
    private static int cooldown;

    private SpawnerAlert() {}

    /** Neueste zuerst. */
    public static List<Find> finds() {
        return finds;
    }

    public static void clearFinds() {
        finds.clear();
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            notified.clear();
            finds.clear();
            lastLevel = level;
        }
        if (!Modules.SPAWNER.isEnabled() || level == null || mc.player == null) return;
        if (++cooldown < 10) return;   // alle 0,5 Sekunden
        cooldown = 0;

        int radius = Modules.SPAWNER.radius.intValue();
        boolean trial = Modules.SPAWNER.trial.on();
        BlockPos pp = mc.player.blockPosition();
        int pcx = pp.getX() >> 4;
        int pcz = pp.getZ() >> 4;
        int cr = (radius >> 4) + 1;
        double r2 = (double) radius * radius;

        Set<BlockPos> inRange = new HashSet<>();

        for (int cx = pcx - cr; cx <= pcx + cr; cx++) {
            for (int cz = pcz - cr; cz <= pcz + cr; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
                    BlockEntity be = e.getValue();
                    boolean isTrial = be instanceof TrialSpawnerBlockEntity;
                    if (!(be instanceof SpawnerBlockEntity) && !(trial && isTrial)) continue;
                    BlockPos pos = e.getKey().immutable();
                    if (pos.distSqr(pp) > r2) continue;
                    inRange.add(pos);
                    if (notified.add(pos)) {
                        alert(mc, level, pos, be, isTrial, Math.sqrt(pos.distSqr(pp)));
                    }
                }
            }
        }

        // Spawner, die ausser Reichweite sind, duerfen spaeter erneut melden
        notified.retainAll(inRange);
    }

    private static void alert(Minecraft mc, ClientLevel level, BlockPos pos, BlockEntity be,
                              boolean trial, double dist) {
        String mob = null;
        ItemStack egg = ItemStack.EMPTY;
        if (be instanceof SpawnerBlockEntity spawner) {
            try {
                Entity display = spawner.getSpawner().getOrCreateDisplayEntity(level, pos);
                if (display != null) {
                    EntityType<?> type = display.getType();
                    mob = type.getDescription().getString();
                    egg = spawnEgg(type);
                }
            } catch (RuntimeException ignored) {
                // Spawner ohne lesbare Daten -> "Leerer Spawner"
            }
        }

        Find find = new Find(pos, mob, egg, trial, System.currentTimeMillis());
        finds.removeIf(f -> f.pos().equals(pos));
        finds.add(0, find);
        while (finds.size() > MAX_FINDS) finds.remove(finds.size() - 1);

        if (Modules.SPAWNER.chat.on() && mc.player != null) {
            mc.player.displayClientMessage(Component.empty()
                    .append(Component.literal("● ").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(find.title()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                    .append(Component.literal(String.format("  X %d  Y %d  Z %d", pos.getX(), pos.getY(), pos.getZ()))
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format("  ·  %.0f Blöcke", dist))
                            .withStyle(ChatFormatting.DARK_GRAY)), false);
        }
        if (Modules.SPAWNER.popup.on()) {
            Notifications.spawner(find);
        }
        if (Modules.SPAWNER.sound.on()) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f));
        }
    }

    /** Sucht das passende Spawn-Ei ueber die Registry ("zombie" -> "zombie_spawn_egg"). */
    private static ItemStack spawnEgg(EntityType<?> type) {
        Identifier id = EntityType.getKey(type);
        Identifier eggId = Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_spawn_egg");
        Item item = BuiltInRegistries.ITEM.getValue(eggId);
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item);
    }
}

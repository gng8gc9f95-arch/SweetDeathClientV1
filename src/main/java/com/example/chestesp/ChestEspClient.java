package com.example.chestesp;

import com.example.chestesp.feature.Freecam;
import com.example.chestesp.feature.SpawnerAlert;
import com.example.chestesp.feature.SusChunks;
import com.example.chestesp.feature.Tunnels;
import com.example.chestesp.gui.Hud;
import com.example.chestesp.gui.Notifications;
import com.example.chestesp.module.Module;
import com.example.chestesp.module.Modules;
import com.example.chestesp.render.EspRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChestEspClient implements ClientModInitializer {
    public static final String MOD_ID = "chestesp";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "main"));

        Modules.init(category);
        Config.load();

        // START_CLIENT_TICK laeuft vor der Tastenverarbeitung des Spiels
        ClientTickEvents.START_CLIENT_TICK.register(ChestEspClient::onTick);

        WorldRenderEvents.BEFORE_TRANSLUCENT.register(EspRenderer::render);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), Hud::render);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            Config.save();
            EspRenderer.close();
        });
    }

    private static void onTick(Minecraft mc) {
        boolean changed = false;
        for (Module m : Modules.all()) {
            while (m.key.consumeClick()) {
                if (m == Modules.FREECAM && (mc.player == null || mc.level == null)) continue;
                boolean before = m.isEnabled();
                m.onKey();
                if (m.toggleable && before != m.isEnabled()) {
                    Notifications.toggled(m);
                    changed = true;
                }
            }
        }
        if (changed) Config.save();

        Freecam.tick(mc);
        SpawnerAlert.tick(mc);
        SusChunks.tick(mc);
        Tunnels.tick(mc);
    }
}

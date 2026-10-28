package com.example.chestesp.feature;

import com.example.chestesp.module.Modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

/**
 * Freecam: Die Kamera wird von der Spielfigur geloest.
 * - Eine unsichtbare Hilfs-Entity (nicht in der Welt) dient als Kamera.
 * - Die Eingabe der echten Spielfigur wird waehrenddessen durch eine leere ersetzt,
 *   die Figur bleibt also stehen. Maus-Drehung wird auf die Kamera uebertragen.
 * - Angriff / Benutzen sind gesperrt, damit nichts "aus der Ferne" ausgeloest wird.
 */
public final class Freecam {
    private static final double SPRINT_FACTOR = 2.5;

    private static boolean active;
    private static LocalPlayer player;
    private static ClientInput savedInput;
    private static float savedYaw, savedPitch;
    private static ArmorStand cam;
    private static Vec3 eye = Vec3.ZERO;

    private Freecam() {}

    public static boolean isActive() {
        return active;
    }

    /** Startet die Freecam. Gibt false zurueck, wenn keine Welt geladen ist. */
    public static boolean enable(Minecraft mc) {
        if (active) return true;
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return false;

        player = p;
        savedInput = p.input;
        savedYaw = p.getYRot();
        savedPitch = p.getXRot();

        cam = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
        eye = p.getEyePosition();
        placeCamera();
        cam.xo = cam.getX();
        cam.yo = cam.getY();
        cam.zo = cam.getZ();
        applyRotation(p.getYRot(), p.getXRot());

        mc.setCameraEntity(cam);
        p.input = new ClientInput() {};   // leere Eingabe -> Figur bleibt stehen
        active = true;
        return true;
    }

    public static void disable(Minecraft mc) {
        if (!active) return;
        active = false;

        LocalPlayer p = mc.player;
        if (p != null && p == player) {
            p.input = savedInput;
            p.setYRot(savedYaw);
            p.setXRot(savedPitch);
            p.yRotO = savedYaw;
            p.xRotO = savedPitch;
            p.setYHeadRot(savedYaw);
            mc.setCameraEntity(p);
        } else if (p != null) {
            mc.setCameraEntity(p);
        }
        cam = null;
        player = null;
        savedInput = null;
    }

    public static void tick(Minecraft mc) {
        if (!active) return;

        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || p != player) {
            // Welt verlassen / Respawn -> still beenden
            active = false;
            cam = null;
            player = null;
            savedInput = null;
            Modules.FREECAM.forceState(false);
            return;
        }

        // Interaktion sperren
        mc.options.keyAttack.setDown(false);
        mc.options.keyUse.setDown(false);
        while (mc.options.keyAttack.consumeClick()) { }
        while (mc.options.keyUse.consumeClick()) { }
        while (mc.options.keyPickItem.consumeClick()) { }

        float yaw = p.getYRot();
        float pitch = p.getXRot();
        double yawR = Math.toRadians(yaw);
        double pitchR = Math.toRadians(pitch);

        // Blickrichtung und "rechts"-Vektor
        double lx = -Math.sin(yawR) * Math.cos(pitchR);
        double ly = -Math.sin(pitchR);
        double lz = Math.cos(yawR) * Math.cos(pitchR);
        double rx = -Math.cos(yawR);
        double rz = -Math.sin(yawR);

        double fwd = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
        double side = (mc.options.keyRight.isDown() ? 1 : 0) - (mc.options.keyLeft.isDown() ? 1 : 0);
        double up = (mc.options.keyJump.isDown() ? 1 : 0) - (mc.options.keyShift.isDown() ? 1 : 0);

        double speed = Modules.FREECAM.speed.value() * (mc.options.keySprint.isDown() ? SPRINT_FACTOR : 1.0);

        double mx = lx * fwd + rx * side;
        double my = ly * fwd + up;
        double mz = lz * fwd + rz * side;
        double len = Math.sqrt(mx * mx + my * my + mz * mz);
        if (len > 1.0e-6) {
            mx = mx / len * speed;
            my = my / len * speed;
            mz = mz / len * speed;
        } else {
            mx = my = mz = 0;
        }

        // vorherige Position fuer die Interpolation merken
        cam.xo = cam.getX();
        cam.yo = cam.getY();
        cam.zo = cam.getZ();

        eye = eye.add(mx, my, mz);
        placeCamera();
        applyRotation(yaw, pitch);
    }

    /** Pro Frame aufgerufen: Maus-Drehung der Figur auf die Kamera uebertragen. */
    public static void syncRotation(Minecraft mc) {
        if (active && cam != null && mc.player == player && player != null) {
            applyRotation(player.getYRot(), player.getXRot());
        }
    }

    private static void placeCamera() {
        cam.setPos(eye.x, eye.y - cam.getEyeHeight(), eye.z);
    }

    private static void applyRotation(float yaw, float pitch) {
        cam.setYRot(yaw);
        cam.setXRot(pitch);
        cam.yRotO = yaw;
        cam.xRotO = pitch;
        cam.setYHeadRot(yaw);
    }
}

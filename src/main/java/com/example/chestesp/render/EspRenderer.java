package com.example.chestesp.render;

import com.example.chestesp.ChestEspClient;
import com.example.chestesp.feature.Freecam;
import com.example.chestesp.feature.SusChunks;
import com.example.chestesp.feature.TunnelScanner;
import com.example.chestesp.feature.Tunnels;
import com.example.chestesp.module.ColorSetting;
import com.example.chestesp.module.Modules;
import com.example.chestesp.module.ToggleColorSetting;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Zeichnet Umrandungen, Flaechen und Tracer durch Waende.
 *
 * Linien werden als duenne Flaechen gebaut, deren Breite an jedem Endpunkt mit der
 * Entfernung zur Kamera waechst. Dadurch ist jede Linie auf dem Bildschirm ueberall
 * gleich duenn (z. B. genau 1 Pixel) - auch direkt am Fadenkreuz, wo der Tracer beginnt.
 */
public final class EspRenderer {
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(ChestEspClient.MOD_ID, "pipeline/esp_through_walls"))
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                    .withCull(false)
                    .withDepthWrite(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build());

    private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(1 << 20);
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    private static BufferBuilder buffer;
    private static MappableRingBuffer vertexBuffer;
    private static Matrix4fc pose;
    private static int quads;

    /** Welt-Einheiten pro Bildschirmpixel in 1 Block Entfernung. */
    private static double pixel;

    /** Anzahl der ESP-Bloecke im letzten Frame (fuer die Statistik im Menue). */
    public static volatile int lastEspCount;

    private EspRenderer() {}

    // ------------------------------------------------------------------ Extraktion

    public static void render(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        Freecam.syncRotation(mc);

        boolean doEsp = Modules.ESP.isEnabled();
        boolean doSus = Modules.SUS.isEnabled() && !SusChunks.entries().isEmpty();
        boolean doTunnel = Modules.TUNNEL.isEnabled() && !Tunnels.boxes().isEmpty();
        if (!doEsp) lastEspCount = 0;
        if (!doEsp && !doSus && !doTunnel) return;

        ClientLevel level = mc.level;
        Entity camEntity = mc.getCameraEntity();
        if (level == null || camEntity == null || mc.player == null) return;

        Vec3 cam = context.worldState().cameraRenderState.pos;
        pose = context.matrices().last().pose();

        // Pixelgroesse aus Sichtfeld und Fensterhoehe
        double fov = Math.toRadians(mc.options.fov().get());
        pixel = 2.0 * Math.tan(fov / 2.0) / Math.max(1, mc.getWindow().getHeight());

        // Startpunkt der Tracer: knapp vor der Kamera in Blickrichtung (= Fadenkreuz)
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double yawR = Math.toRadians(camEntity.getViewYRot(partial));
        double pitchR = Math.toRadians(camEntity.getViewXRot(partial));
        double sx = -Math.sin(yawR) * Math.cos(pitchR);
        double sy = -Math.sin(pitchR);
        double sz = Math.cos(yawR) * Math.cos(pitchR);

        boolean tracers = Modules.TRACERS.isEnabled();
        float tracerPx = Modules.TRACERS.thickness.floatValue();
        float tracerA = Modules.TRACERS.opacity.floatValue() / 100f;
        boolean ownColor = Modules.TRACERS.colorMode.is(1);
        ColorSetting own = Modules.TRACERS.color;

        buffer = new BufferBuilder(ALLOCATOR, PIPELINE.getVertexFormatMode(), PIPELINE.getVertexFormat());
        quads = 0;

        // ---------------------------------------------------------- Chest ESP
        if (doEsp) {
            float linePx = Modules.ESP.lineWidth.floatValue();
            float fill = Modules.ESP.fill.floatValue() / 100f;
            int viewDist = mc.options.renderDistance().get();
            int ccx = Mth.floor(cam.x) >> 4;
            int ccz = Mth.floor(cam.z) >> 4;
            int count = 0;

            for (int cx = ccx - viewDist; cx <= ccx + viewDist; cx++) {
                for (int cz = ccz - viewDist; cz <= ccz + viewDist; cz++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) continue;

                    for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
                        ToggleColorSetting c = colorFor(e.getValue());
                        if (c == null || !c.on()) continue;
                        BlockPos p = e.getKey();
                        count++;

                        double x1 = p.getX() - cam.x, y1 = p.getY() - cam.y, z1 = p.getZ() - cam.z;
                        float r = c.r(), g = c.g(), b = c.b(), a = c.a();
                        if (fill > 0.001f) {
                            boxFaces(x1, y1, z1, x1 + 1, y1 + 1, z1 + 1, r, g, b, a * fill);
                        }
                        boxEdges(x1, y1, z1, x1 + 1, y1 + 1, z1 + 1, r, g, b, a, linePx);

                        if (tracers && Modules.TRACERS.toBlocks.on()) {
                            tracer(sx, sy, sz, x1 + 0.5, y1 + 0.5, z1 + 0.5,
                                    ownColor ? own : c, tracerA, tracerPx);
                        }
                    }
                }
            }
            lastEspCount = count;
        }

        // ---------------------------------------------------------- Sus Chunks (flach)
        if (doSus) {
            ColorSetting c = Modules.SUS.color;
            float r = c.r(), g = c.g(), b = c.b(), a = c.a();
            boolean outline = Modules.SUS.outline.on();
            for (SusChunks.Entry s : SusChunks.entries()) {
                double y = switch (Modules.SUS.height.index()) {
                    case 1 -> Math.floor(mc.player.getY());
                    case 2 -> Modules.SUS.fixedY.value();
                    default -> s.minY();
                };
                double x1 = s.cx() * 16.0 - cam.x;
                double z1 = s.cz() * 16.0 - cam.z;
                double yy = y + 0.02 - cam.y;
                flat(x1, yy, z1, x1 + 16, z1 + 16, r, g, b, a);
                if (outline) {
                    float la = Math.min(1f, a * 2.2f + 0.25f);
                    line(x1, yy, z1, x1 + 16, yy, z1, r, g, b, la, 1.5f);
                    line(x1 + 16, yy, z1, x1 + 16, yy, z1 + 16, r, g, b, la, 1.5f);
                    line(x1 + 16, yy, z1 + 16, x1, yy, z1 + 16, r, g, b, la, 1.5f);
                    line(x1, yy, z1 + 16, x1, yy, z1, r, g, b, la, 1.5f);
                }
                if (tracers && Modules.TRACERS.toSus.on()) {
                    tracer(sx, sy, sz, x1 + 8, yy, z1 + 8, ownColor ? own : c, tracerA, tracerPx);
                }
            }
        }

        // ---------------------------------------------------------- Tunnel
        if (doTunnel) {
            boolean outline = Modules.TUNNEL.outline.on();
            for (TunnelScanner.Box t : Tunnels.boxes()) {
                ToggleColorSetting c = switch (t.type()) {
                    case TunnelScanner.TYPE_1X1 -> Modules.TUNNEL.oneByOne;
                    case TunnelScanner.TYPE_SHAFT -> Modules.TUNNEL.shafts;
                    default -> Modules.TUNNEL.oneByTwo;
                };
                if (!c.on()) continue;
                double x1 = t.x1() - cam.x, y1 = t.y1() - cam.y, z1 = t.z1() - cam.z;
                double x2 = t.x2() - cam.x, y2 = t.y2() - cam.y, z2 = t.z2() - cam.z;
                float r = c.r(), g = c.g(), b = c.b(), a = c.a();
                boxFaces(x1, y1, z1, x2, y2, z2, r, g, b, a);
                if (outline) boxEdges(x1, y1, z1, x2, y2, z2, r, g, b, Math.min(1f, a * 2f + 0.3f), 1f);
                if (tracers && Modules.TRACERS.toTunnels.on()) {
                    tracer(sx, sy, sz, (x1 + x2) * 0.5, (y1 + y2) * 0.5, (z1 + z2) * 0.5,
                            ownColor ? own : c, tracerA, tracerPx);
                }
            }
        }

        if (quads == 0) {
            MeshData empty = buffer.build();
            if (empty != null) empty.close();
            buffer = null;
            return;
        }

        draw(mc);
    }

    private static ToggleColorSetting colorFor(BlockEntity be) {
        if (be instanceof SpawnerBlockEntity || be instanceof TrialSpawnerBlockEntity) return Modules.ESP.spawners;
        if (be instanceof TrappedChestBlockEntity) return Modules.ESP.trapped;
        if (be instanceof ChestBlockEntity) return Modules.ESP.chests;
        if (be instanceof EnderChestBlockEntity) return Modules.ESP.ender;
        if (be instanceof BarrelBlockEntity) return Modules.ESP.barrels;
        if (be instanceof ShulkerBoxBlockEntity) return Modules.ESP.shulkers;
        return null;
    }

    // ------------------------------------------------------------------ Geometrie

    private static void tracer(double sx, double sy, double sz, double tx, double ty, double tz,
                               ColorSetting c, float alpha, float px) {
        line(sx, sy, sz, tx, ty, tz, c.r(), c.g(), c.b(), alpha * Math.max(0.15f, c.a()), px);
    }

    /** Die 12 Kanten einer Box (Koordinaten relativ zur Kamera). */
    private static void boxEdges(double x1, double y1, double z1, double x2, double y2, double z2,
                                 float r, float g, float b, float a, float px) {
        line(x1, y1, z1, x2, y1, z1, r, g, b, a, px);
        line(x2, y1, z1, x2, y1, z2, r, g, b, a, px);
        line(x2, y1, z2, x1, y1, z2, r, g, b, a, px);
        line(x1, y1, z2, x1, y1, z1, r, g, b, a, px);
        line(x1, y2, z1, x2, y2, z1, r, g, b, a, px);
        line(x2, y2, z1, x2, y2, z2, r, g, b, a, px);
        line(x2, y2, z2, x1, y2, z2, r, g, b, a, px);
        line(x1, y2, z2, x1, y2, z1, r, g, b, a, px);
        line(x1, y1, z1, x1, y2, z1, r, g, b, a, px);
        line(x2, y1, z1, x2, y2, z1, r, g, b, a, px);
        line(x2, y1, z2, x2, y2, z2, r, g, b, a, px);
        line(x1, y1, z2, x1, y2, z2, r, g, b, a, px);
    }

    /** Die 6 Flaechen einer Box. */
    private static void boxFaces(double x1, double y1, double z1, double x2, double y2, double z2,
                                 float r, float g, float b, float a) {
        if (a <= 0.003f) return;
        quad(x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);   // unten
        quad(x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b, a);   // oben
        quad(x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r, g, b, a);   // Norden
        quad(x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, a);   // Sueden
        quad(x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, r, g, b, a);   // Westen
        quad(x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, a);   // Osten
    }

    /** Waagerechte Flaeche (fuer Sus Chunks). */
    private static void flat(double x1, double y, double z1, double x2, double z2,
                             float r, float g, float b, float a) {
        if (a <= 0.003f) return;
        quad(x1, y, z1, x1, y, z2, x2, y, z2, x2, y, z1, r, g, b, a);
    }

    /**
     * Linie mit konstanter Bildschirmbreite (px). An jedem Endpunkt wird die halbe
     * Breite aus dessen Entfernung zur Kamera berechnet.
     */
    private static void line(double x1, double y1, double z1, double x2, double y2, double z2,
                             float r, float g, float b, float a, float px) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double k = 0.5 * px * pixel;

        // Seitenvektor am Startpunkt: Richtung x (Kamera -> Punkt)
        double ax = dy * z1 - dz * y1, ay = dz * x1 - dx * z1, az = dx * y1 - dy * x1;
        double al = Math.sqrt(ax * ax + ay * ay + az * az);
        double bx = dy * z2 - dz * y2, by = dz * x2 - dx * z2, bz = dx * y2 - dy * x2;
        double bl = Math.sqrt(bx * bx + by * by + bz * bz);
        if (al < 1.0e-9 && bl < 1.0e-9) return;
        if (al < 1.0e-9) { ax = bx; ay = by; az = bz; al = bl; }
        if (bl < 1.0e-9) { bx = ax; by = ay; bz = az; bl = al; }

        double d1 = Math.sqrt(x1 * x1 + y1 * y1 + z1 * z1);
        double d2 = Math.sqrt(x2 * x2 + y2 * y2 + z2 * z2);
        double h1 = Math.max(0.0001, d1 * k) / al;
        double h2 = Math.max(0.0001, d2 * k) / bl;
        ax *= h1; ay *= h1; az *= h1;
        bx *= h2; by *= h2; bz *= h2;

        quad(x1 + ax, y1 + ay, z1 + az,
                x1 - ax, y1 - ay, z1 - az,
                x2 - bx, y2 - by, z2 - bz,
                x2 + bx, y2 + by, z2 + bz, r, g, b, a);
    }

    private static void quad(double x1, double y1, double z1, double x2, double y2, double z2,
                             double x3, double y3, double z3, double x4, double y4, double z4,
                             float r, float g, float b, float a) {
        vtx(x1, y1, z1, r, g, b, a);
        vtx(x2, y2, z2, r, g, b, a);
        vtx(x3, y3, z3, r, g, b, a);
        vtx(x4, y4, z4, r, g, b, a);
        quads++;
    }

    private static void vtx(double x, double y, double z, float r, float g, float b, float a) {
        buffer.addVertex(pose, (float) x, (float) y, (float) z).setColor(r, g, b, a);
    }

    // ------------------------------------------------------------------ Zeichnen

    private static void draw(Minecraft client) {
        MeshData built = buffer.buildOrThrow();
        MeshData.DrawState state = built.drawState();
        VertexFormat format = state.format();

        GpuBuffer vertices = upload(state, format, built);
        drawBuffer(client, built, state, vertices);

        vertexBuffer.rotate();
        buffer = null;
    }

    private static GpuBuffer upload(MeshData.DrawState state, VertexFormat format, MeshData built) {
        int size = state.vertexCount() * format.getVertexSize();

        if (vertexBuffer == null || vertexBuffer.size() < size) {
            if (vertexBuffer != null) vertexBuffer.close();
            vertexBuffer = new MappableRingBuffer(() -> ChestEspClient.MOD_ID + " esp vertex buffer",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView view = encoder.mapBuffer(
                vertexBuffer.currentBuffer().slice(0, built.vertexBuffer().remaining()), false, true)) {
            MemoryUtil.memCopy(built.vertexBuffer(), view.data());
        }
        return vertexBuffer.currentBuffer();
    }

    private static void drawBuffer(Minecraft client, MeshData built, MeshData.DrawState state, GpuBuffer vertices) {
        // Quads -> Dreiecke ueber den gemeinsamen Index-Puffer (keine Sortierung noetig,
        // da ohne Tiefentest gezeichnet wird)
        RenderSystem.AutoStorageIndexBuffer seq = RenderSystem.getSequentialBuffer(PIPELINE.getVertexFormatMode());
        GpuBuffer indices = seq.getBuffer(state.indexCount());
        VertexFormat.IndexType indexType = seq.type();

        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> ChestEspClient.MOD_ID + " esp rendering",
                client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
                client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices, indexType);
            pass.drawIndexed(0, 0, state.indexCount(), 1);
        }

        built.close();
    }

    public static void close() {
        ALLOCATOR.close();
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
    }
}

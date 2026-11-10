package com.lycoris.loconautics.foundation.particle;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class LateParticles {

    private static final List<Particle> LIVE = new ArrayList<>();
    private static final Queue<Particle> PENDING = new ConcurrentLinkedQueue<>();

    private static volatile int liveCount;

    public static int liveCount() {
        return liveCount;
    }

    private record Ranked(Particle particle, double distanceSqr) {
    }

    private LateParticles() {
    }

    public static void add(Particle particle) {
        PENDING.add(particle);
    }

    public static void clear() {
        PENDING.clear();
        LIVE.clear();
    }

    private static double distanceSqr(Particle particle, Vec3 eye) {
        AABB box = particle.getBoundingBox();
        double dx = (box.minX + box.maxX) * 0.5 - eye.x;
        double dy = (box.minY + box.maxY) * 0.5 - eye.y;
        double dz = (box.minZ + box.maxZ) * 0.5 - eye.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public static void renderAll(Camera camera, Matrix4f modelView, float partialTick) {
        for (Particle added = PENDING.poll(); added != null; added = PENDING.poll()) {
            LIVE.add(added);
        }
        LIVE.removeIf(particle -> !particle.isAlive());
        liveCount = LIVE.size();
        if (LIVE.isEmpty()) {
            return;
        }
        Vec3 eye = camera.getPosition();
        List<Ranked> ranked = new ArrayList<>(LIVE.size());
        for (Particle particle : LIVE) {
            ranked.add(new Ranked(particle, distanceSqr(particle, eye)));
        }
        ranked.sort(Comparator.comparingDouble(entry -> -entry.distanceSqr()));
        Minecraft minecraft = Minecraft.getInstance();
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.set(modelView);
        RenderSystem.applyModelViewMatrix();

        minecraft.gameRenderer.lightTexture().turnOnLightLayer();
        RenderSystem.setShader(GameRenderer::getParticleShader);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
        RenderSystem.enableDepthTest();
        RenderTarget particlesTarget = Minecraft.useShaderTransparency()
                ? minecraft.levelRenderer.getParticlesTarget() : null;
        if (particlesTarget != null) {
            particlesTarget.bindWrite(false);
        }
        RenderSystem.depthMask(particlesTarget != null);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        for (Ranked entry : ranked) {
            entry.particle().render(buffer, camera, partialTick);
        }
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        if (particlesTarget != null) {
            minecraft.getMainRenderTarget().bindWrite(false);
        }
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        minecraft.gameRenderer.lightTexture().turnOffLightLayer();
        modelViewStack.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }
}

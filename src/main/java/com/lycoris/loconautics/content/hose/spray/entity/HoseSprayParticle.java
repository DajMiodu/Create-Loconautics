package com.lycoris.loconautics.content.hose.spray.entity;


import com.lycoris.loconautics.content.hose.spray.HoseSprayOptions;
import com.lycoris.loconautics.foundation.particle.LateParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

public class HoseSprayParticle extends TextureSheetParticle {

    private static final double JET_SPEED = 0.18;
    private static final double SUCK_BACK_OFFSET = 0.3;
    private static final float SUCK_FADE_IN = 0.35f;

    private final SpriteSet sprites;
    private final boolean jet;
    private final boolean suck;
    private final double startX, startY, startZ;
    private final double endX, endY, endZ;
    private double ctrlX, ctrlY, ctrlZ;
    private float startSize;

    HoseSprayParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                      SpriteSet sprites, int color, boolean suck) {
        super(level, x, y, z);
        this.sprites = sprites;
        LateParticles.add(this);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        double speed = Math.sqrt(xd * xd + yd * yd + zd * zd);
        this.jet = !suck && speed > JET_SPEED;

        this.friction = suck ? 1.0f : jet ? 0.90f : 0.82f;
        this.gravity = suck ? 0.0f : jet ? 0.35f : 0.7f;
        this.hasPhysics = !suck;

        this.suck = suck;
        double back = suck ? SUCK_BACK_OFFSET : 0.0;
        this.startX = x - xd * back;
        this.startY = y - yd * back;
        this.startZ = z - zd * back;
        this.endX = x + xd;
        this.endY = y + yd;
        this.endZ = z + zd;
        if (suck) {
            this.setPos(startX, startY, startZ);
            this.xo = startX;
            this.yo = startY;
            this.zo = startZ;
        }
        if (suck) {
            this.xd = 0.0;
            this.yd = 0.0;
            this.zd = 0.0;
            double distance = Math.sqrt(xd * xd + yd * yd + zd * zd);
            this.lifetime = 6 + (int) (distance * 3.0) + this.random.nextInt(3);
            this.quadSize = 0.07f + this.random.nextFloat() * 0.16f;
            this.startSize = this.quadSize;
            this.setSprite(sprites.get(0, 9));
            double bend = distance * (0.25 + this.random.nextDouble() * 0.35);
            double rx = this.random.nextDouble() - 0.5;
            double ry = this.random.nextDouble() - 0.5;
            double rz = this.random.nextDouble() - 0.5;
            // cross with the travel direction so the control point always sits off to the side
            double px = ry * zd - rz * yd;
            double py = rz * xd - rx * zd;
            double pz = rx * yd - ry * xd;
            double plen = Math.sqrt(px * px + py * py + pz * pz);
            double k = plen < 1.0e-6 ? 0.0 : bend / plen;
            this.ctrlX = startX + (endX - startX) * 0.5 + px * k;
            this.ctrlY = startY + (endY - startY) * 0.5 + py * k;
            this.ctrlZ = startZ + (endZ - startZ) * 0.5 + pz * k;
        } else if (jet) {
            this.lifetime = 14 + this.random.nextInt(10);
            this.quadSize = 0.24f + (float) Math.min(0.22, speed * 0.6);
            this.setSpriteFromAge(sprites);
        } else {
            this.lifetime = 6 + this.random.nextInt(8);
            this.quadSize = 0.08f + this.random.nextFloat() * 0.10f;
            this.setSprite(sprites.get(0, 9));
        }

        float baseR = ((color >> 16) & 0xFF) / 255.0f;
        float baseG = ((color >> 8) & 0xFF) / 255.0f;
        float baseB = (color & 0xFF) / 255.0f;
        float k = 0.75f + this.random.nextFloat() * 0.25f;
        float foam = this.random.nextFloat() * 0.30f;
        this.setColor(
                Math.min(1.0f, baseR * k + foam),
                Math.min(1.0f, baseG * k + foam),
                Math.min(1.0f, baseB * k + foam));
        this.setAlpha(suck ? 0.0f : 0.85f);
    }

    @Override
    public void tick() {
        if (suck) {
            tickSuck();
        } else {
            super.tick();
        }
        if (jet && !this.removed) {
            this.setSpriteFromAge(sprites);
        }
        float t = (float) this.age / this.lifetime;
        if (suck && t < SUCK_FADE_IN) {
            this.setAlpha(0.85f * t / SUCK_FADE_IN);
        } else if (t > 0.66f) {
            this.setAlpha(0.85f * (1.0f - (t - 0.66f) / 0.34f));
        }
    }

    private void tickSuck() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        // quadratic bezier from the spawn point to the nozzle bottom, easing in so it speeds up near the tip
        double t = (double) this.age / this.lifetime;
        double u = t * t;
        this.quadSize = startSize * (1.0f - 0.8f * (float) t);
        double a = (1.0 - u) * (1.0 - u);
        double b = 2.0 * (1.0 - u) * u;
        double c = u * u;
        this.setPos(
                a * startX + b * ctrlX + c * endX,
                a * startY + b * ctrlY + c * endY,
                a * startZ + b * ctrlZ + c * endZ);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.NO_RENDER;
    }

    public static class Provider implements ParticleProvider<HoseSprayOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(HoseSprayOptions type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new HoseSprayParticle(level, x, y, z, xd, yd, zd, sprites, type.color(), type.suck());
        }
    }
}

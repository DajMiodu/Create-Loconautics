package com.lycoris.loconautics.foundation.smoke;

import com.lycoris.loconautics.foundation.particle.LateParticles;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class SmokeParticle extends TextureSheetParticle {

    public static final int VARIANTS = 3;
    public static final int STAGES = 6;

    private static final float GROWTH_CURVE = 1.6F;
    private static final float LIFT_THIN = 1.6F;
    private static final float CEILING_SPREAD = 0.018F;
    private static final float MIN_VISIBLE_ALPHA = 1.0F / 255.0F;

    private final SpriteSet sprites;
    private final SmokeStyle style;
    private final float thinning;
    private final BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
    private final float[] air = new float[3];
    private final float startSize;
    private final float endSize;
    private final float peakAlpha;
    private final float spin;
    private final float baseR;
    private final float baseG;
    private final float baseB;
    private final int variant;
    private final float spreadX;
    private final float spreadZ;
    private final float lift;
    private float oQuadSize;
    private double driftX;
    private double driftY;
    private double driftZ;
    private int stage = -1;
    private boolean ceilingHit;

    SmokeParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                  SpriteSet sprites, SmokeOptions options) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.style = options.style();
        this.thinning = SmokeEmitter.thinning();
        LateParticles.add(this);

        float scale = options.scale();
        this.lift = options.lift();
        this.variant = this.random.nextInt(VARIANTS);
        this.startSize = Mth.lerp(this.random.nextFloat(), style.startMin, style.startMax) * scale;
        this.endSize = style.endSize * scale * (0.85F + this.random.nextFloat() * 0.3F);
        this.peakAlpha = style.alpha * (0.9F + this.random.nextFloat() * 0.2F);
        this.lifetime = Math.round(style.lifetime * (0.75F + this.random.nextFloat() * 0.5F)
                * (0.6F + 0.4F * Mth.sqrt(scale)) * Mth.sqrt(lift));
        this.quadSize = this.startSize;
        this.oQuadSize = this.startSize;
        this.hasPhysics = false;
        this.friction = 1.0F;
        this.gravity = 0.0F;

        this.driftX = xd * style.inherit;
        this.driftY = yd * style.inherit;
        this.driftZ = zd * style.inherit;

        double angle = this.random.nextDouble() * Mth.TWO_PI;
        this.spreadX = (float) Math.cos(angle) * CEILING_SPREAD;
        this.spreadZ = (float) Math.sin(angle) * CEILING_SPREAD;

        this.roll = this.random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        this.spin = (this.random.nextFloat() - 0.5F) * 0.02F;

        int color = options.color();
        this.baseR = ((color >> 16) & 0xFF) / 255.0F;
        this.baseG = ((color >> 8) & 0xFF) / 255.0F;
        this.baseB = (color & 0xFF) / 255.0F;
        applyLook(0.0F);
        this.setAlpha(0.0F);
        pickSprite(0.0F);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.oRoll = this.roll;
        this.oQuadSize = this.quadSize;

        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        float t = (float) this.age / this.lifetime;
        double buoyancy = (style.riseFloor + (style.rise - style.riseFloor) * Math.exp(-this.age / style.coolTicks)) * lift;

        SmokeAir.wind(this.level, this.y, air);
        float windWeight = 1.0F - (float) Math.exp(-this.age / 30.0);
        float windX = air[0] * windWeight;
        float windZ = air[1] * windWeight;
        SmokeAir.turbulence(this.level, this.x, this.y, this.z, air);
        float stir = style.turbulence * (0.4F + 1.6F * t);

        double vx = driftX + windX + air[0] * stir;
        double vy = driftY + buoyancy + air[1] * stir * 0.5F;
        double vz = driftZ + windZ + air[2] * stir;
        if (ceilingHit) {
            vx += spreadX;
            vz += spreadZ;
        }
        driftX *= style.drag;
        driftY *= style.drag;
        driftZ *= style.drag;

        step(vx, vy, vz);

        this.roll += this.spin;
        float growth = 1.0F - (float) Math.pow(1.0F - t, GROWTH_CURVE);
        this.quadSize = this.startSize + (this.endSize - this.startSize) * growth;
        pickSprite(t);
        applyLook(t);
    }

    private void step(double vx, double vy, double vz) {
        double nx = this.x + vx;
        double ny = this.y + vy;
        double nz = this.z + vz;
        if (vy > 0.0 && solidAt(this.x, ny, this.z)) {
            ny = this.y;
            ceilingHit = true;
        }
        if (solidAt(nx, ny, this.z)) {
            nx = this.x;
        }
        if (solidAt(nx, ny, nz)) {
            nz = this.z;
        }
        this.setPos(nx, ny, nz);
    }

    private boolean solidAt(double x, double y, double z) {
        cell.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        BlockState state = this.level.getBlockState(cell);
        return state.isCollisionShapeFullBlock(this.level, cell);
    }

    private void pickSprite(float t) {
        int next = Math.min(STAGES - 1, (int) (t * STAGES));
        if (next == stage) {
            return;
        }
        stage = next;
        this.setSprite(sprites.get(variant * STAGES + stage, VARIANTS * STAGES - 1));
    }

    private void applyLook(float t) {
        float shade = style.soot + (1.0F - style.soot) * (float) Math.pow(t, 0.6);
        this.setColor(baseR * shade, baseG * shade, baseB * shade);

        float fadeIn = Mth.clamp(t / style.fadeIn, 0.0F, 1.0F);
        fadeIn = fadeIn * fadeIn * (3.0F - 2.0F * fadeIn);
        float out = Mth.clamp((1.0F - t) / 0.65F, 0.0F, 1.0F);
        out = out * out * (3.0F - 2.0F * out);
        float spread = (this.quadSize - startSize) / Math.max(0.001F, endSize - startSize);
        float thin = 1.0F / (1.0F + LIFT_THIN * Mth.clamp(spread, 0.0F, 1.0F));
        if (this.age > 0) {
            float shown = peakAlpha * fadeIn * out * thin;
            if (thinning > 1.0F) {
                shown = 1.0F - (float) Math.pow(1.0F - shown, thinning);
            }
            this.setAlpha(shown);
        }
    }

    @Override
    protected void renderRotatedQuad(VertexConsumer buffer, Quaternionf quaternion, float x, float y, float z,
                                     float partialTicks) {
        if (this.alpha < MIN_VISIBLE_ALPHA) {
            return;
        }
        float size = this.getQuadSize(partialTicks);
        int light = this.getLightColor(partialTicks);
        SmokeSpriteBounds fit = SmokeSpriteBounds.of(this.sprite);
        vertex(buffer, quaternion, x, y, z, size, fit.right, fit.bottom, light);
        vertex(buffer, quaternion, x, y, z, size, fit.right, fit.top, light);
        vertex(buffer, quaternion, x, y, z, size, fit.left, fit.top, light);
        vertex(buffer, quaternion, x, y, z, size, fit.left, fit.bottom, light);
    }

    private void vertex(VertexConsumer buffer, Quaternionf quaternion, float x, float y, float z, float size,
                        float fx, float fy, int light) {
        float u = Mth.lerp(fx, this.sprite.getU0(), this.sprite.getU1());
        float v = Mth.lerp(fy, this.sprite.getV0(), this.sprite.getV1());
        Vector3f corner = new Vector3f(2.0F * fx - 1.0F, 1.0F - 2.0F * fy, 0.0F).rotate(quaternion).mul(size).add(x, y, z);
        buffer.addVertex(corner.x(), corner.y(), corner.z()).setUv(u, v)
                .setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(light);
    }

    @Override
    public float getQuadSize(float partialTicks) {
        return Mth.lerp(partialTicks, this.oQuadSize, this.quadSize);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.NO_RENDER;
    }

    public static class Provider implements ParticleProvider<SmokeOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SmokeOptions options, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new SmokeParticle(level, x, y, z, xd, yd, zd, sprites, options);
        }
    }
}

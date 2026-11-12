package com.lycoris.loconautics.content.combustion;

import com.lycoris.loconautics.registry.LoconauticsSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SmallEngineSound extends AbstractTickableSoundInstance {

    private static final float FULL_VOLUME = 0.8f;
    private static final float QUIET_SHARE = 0.25f;
    private static final float SLOW_PITCH = 0.55f;
    private static final float FULL_PITCH = 1.05f;
    private static final float FADE_BELOW = 0.12f;

    private static final int LOUD_ENGINES = 6;
    private static final double CROWD_RANGE = 24.0;

    private static long countedAt = -1L;
    private static int counting;
    private static int crowd;

    private final SmallEngineBlockEntity engine;
    private float spin;

    public SmallEngineSound(SmallEngineBlockEntity engine) {
        super(LoconauticsSounds.SMALL_ENGINE_LOOP.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.engine = engine;
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0e-4f;
        this.x = engine.getBlockPos().getX() + 0.5;
        this.y = engine.getBlockPos().getY() + 0.5;
        this.z = engine.getBlockPos().getZ() + 0.5;
    }

    @Override
    public void tick() {
        if (engine.isRemoved()) {
            stop();
            return;
        }
        spin = engine.getShownSpin();
        if (!engine.isRunning() && spin <= 0.002f) {
            stop();
            return;
        }
        pitch = Mth.lerp(spin, SLOW_PITCH, FULL_PITCH);
        volume = FULL_VOLUME * Mth.lerp(spin, QUIET_SHARE, 1f) * Mth.clamp(spin / FADE_BELOW, 0f, 1f)
                * crowdShare(engine.getLevel().getGameTime(), engine.getBlockPos());
    }

    private static float crowdShare(long gameTime, BlockPos at) {
        if (gameTime != countedAt) {
            countedAt = gameTime;
            crowd = counting;
            counting = 0;
        }
        if (Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(Vec3.atCenterOf(at))
                <= CROWD_RANGE * CROWD_RANGE) {
            counting++;
        }
        return crowd <= LOUD_ENGINES ? 1f : (float) LOUD_ENGINES / crowd;
    }
}

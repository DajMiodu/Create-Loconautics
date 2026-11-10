package com.lycoris.loconautics.trainphysics.fx;

import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import com.lycoris.loconautics.trainphysics.visual.BogeyWheelAnimator;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public final class SableTrainSounds {

    private static final Map<UUID, SableTrainSounds> ACTIVE = new HashMap<>();

    public static void tickAll() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            stopAll();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        Set<UUID> ids = SableTrainClientRegistry.ids();
        ACTIVE.entrySet().removeIf(e -> {
            if (!ids.contains(e.getKey())) {
                e.getValue().stop();
                return true;
            }
            return false;
        });
        if (ids.isEmpty()) {
            return;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(mc.level);
        if (container == null) {
            return;
        }
        for (UUID id : ids) {
            SubLevel sub = container.getSubLevel(id);
            if (!(sub instanceof ClientSubLevel clientSub) || sub.isRemoved()) {
                SableTrainSounds sounds = ACTIVE.remove(id);
                if (sounds != null) {
                    sounds.stop();
                }
                continue;
            }
            ACTIVE.computeIfAbsent(id, k -> new SableTrainSounds(id)).tick(mc, clientSub);
        }
    }

    public static void stopAll() {
        ACTIVE.values().forEach(SableTrainSounds::stop);
        ACTIVE.clear();
    }

    private static final double SPEED_SCALE = 3.0;

    private final LerpedFloat speedFactor = LerpedFloat.linear();
    private final LerpedFloat distanceFactor = LerpedFloat.linear();
    private final LerpedFloat approachFactor = LerpedFloat.linear();
    private final float pitchModifier;
    private static final int SLIP_FADE_TICKS = 2;

    private static final double SLIP_PITCH_SCALE = 16.0;

    private LoopingSound minecartEsqueSound;
    private LoopingSound wheelSound;
    private LoopingSound slipSound;
    private int slipFadeTicks;
    private float slipPitchJitter;
    private Vector3d lastPos;
    private int tick;
    private boolean wasDerailed;
    private int lastOffTrackBogeys;

    private SableTrainSounds(UUID id) {
        this.pitchModifier = (float) (Math.abs(id.hashCode()) * 10 % 13) / 36.0f;
        SableTrainClientRegistry.TrainMarker marker = SableTrainClientRegistry.get(id);
        this.lastOffTrackBogeys = marker != null ? marker.offTrackBogeys() : 0;
        this.wasDerailed = marker != null && marker.derailed();
    }

    private void tick(Minecraft mc, ClientSubLevel sub) {
        Entity camEntity = mc.cameraEntity;
        if (camEntity == null) {
            return;
        }
        Vector3dc p = sub.renderPose().position();
        Vector3d pos = new Vector3d(p);
        Vector3d motion = lastPos == null ? new Vector3d() : pos.sub(lastPos, new Vector3d());
        lastPos = pos;
        tick++;

        if (BogeyWheelAnimator.bogeysOf(sub).isEmpty()) {
            stopLoops();
            return;
        }

        SableTrainClientRegistry.TrainMarker marker = SableTrainClientRegistry.get(sub.getUniqueId());
        double rawSpeed = marker != null ? Math.abs(marker.speed()) : motion.length();
        float effSpeed = (float) Math.min(rawSpeed * SPEED_SCALE, 2.0);
        int offTrack = marker != null ? marker.offTrackBogeys() : 0;

        if (marker != null && marker.derailed()) {
            if (!wasDerailed) {
                wasDerailed = true;
                stopLoops();
                playCrash(mc, pos, marker.endOfTrack());
            }
            lastOffTrackBogeys = offTrack;
            double sliding = motion.length();
            if (sliding > 0.02 && tick % 3 == 0) {
                float intensity = (float) Math.min(1.0, sliding * 3.0);
                grindAtGroundedBogeys(mc, sub, intensity, 0.6f + intensity * 0.5f);
            }
            return;
        }
        wasDerailed = false;
        if (offTrack > lastOffTrackBogeys) {
            playCrash(mc, pos, marker != null && marker.endOfTrack());
        }
        lastOffTrackBogeys = offTrack;

        if (marker != null && marker.brakeHard()) {
            double sliding = motion.length();
            if (sliding > 0.02 && tick % 6 == 0) {
                float intensity = (float) Math.min(1.0, sliding * 3.0);
                grindAtGroundedBogeys(mc, sub, intensity, 0.4f + intensity * 0.3f);
            }
        }

        Vec3 cam = camEntity.getEyePosition();
        Vec3 soundLocation = new Vec3(pos.x, pos.y, pos.z);
        tickSlip(mc, marker, soundLocation);
        Vec3 toTrain = soundLocation.subtract(cam);
        double distance = toTrain.length();
        Vec3 camMotion = camEntity.getDeltaMovement();
        Vec3 combinedMotion = new Vec3(motion.x - camMotion.x, motion.y - camMotion.y, motion.z - camMotion.z);
        double dot = distance < 1.0e-3 || combinedMotion.lengthSqr() < 1.0e-9
                ? 0.0 : toTrain.normalize().dot(combinedMotion.normalize());

        speedFactor.chase(effSpeed, 0.25, LerpedFloat.Chaser.exp(0.05f));
        distanceFactor.chase(Mth.clampedLerp(100.0, 0.0, (distance - 3.0) / 64.0), 0.25, LerpedFloat.Chaser.exp(50.0));
        approachFactor.chase(Mth.clampedLerp(50.0, 200.0, 0.5 * (dot + 1.0)), 0.25, LerpedFloat.Chaser.exp(10.0));
        speedFactor.tickChaser();
        distanceFactor.tickChaser();
        approachFactor.tickChaser();

        minecartEsqueSound = playIfMissing(mc, minecartEsqueSound, AllSoundEvents.TRAIN.getMainEvent());
        wheelSound = playIfMissing(mc, wheelSound, AllSoundEvents.TRAIN2.getMainEvent());

        float volume = Math.min(speedFactor.getValue(), approachFactor.getValue() / 300.0f + 0.0125f);
        float pitch = Mth.clamp(speedFactor.getValue() * 2.0f + 0.25f, 0.75f, 1.95f) - pitchModifier;
        minecartEsqueSound.setPitch(pitch * 1.5f);
        volume = Math.min(volume, distanceFactor.getValue() / 1000.0f);

        minecartEsqueSound.setLocation(soundLocation);
        wheelSound.setLocation(soundLocation);

        minecartEsqueSound.setVolume(volume / 2.0f);
        float wheelVolume = Math.min(volume, Math.max((speedFactor.getValue() - 0.25f) / 4.0f + 0.01f, 0.0f));
        wheelSound.setVolume(wheelVolume * 1.5f);
    }

    private void playCrash(Minecraft mc, Vector3dc at, boolean endOfTrack) {
        if (!endOfTrack) {
            mc.level.playLocalSound(at.x(), at.y(), at.z(),
                    SoundEvents.NETHERITE_BLOCK_BREAK, SoundSource.NEUTRAL, 0.4f, 0.85f, false);
            AllSoundEvents.STEAM.playAt(mc.level, new Vec3(at.x(), at.y(), at.z()), 0.25f, 0.8f, false);
            return;
        }
        mc.level.playLocalSound(at.x(), at.y(), at.z(),
                SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 1.0f, 0.55f, false);
        mc.level.playLocalSound(at.x(), at.y(), at.z(),
                SoundEvents.NETHERITE_BLOCK_BREAK, SoundSource.NEUTRAL, 1.0f, 0.7f, false);
        AllSoundEvents.STEAM.playAt(mc.level, new Vec3(at.x(), at.y(), at.z()), 1.0f, 0.6f, false);
    }

    private void grindAtGroundedBogeys(Minecraft mc, ClientSubLevel sub, float intensity, float pitch) {
        for (AbstractBogeyBlockEntity be : BogeyWheelAnimator.bogeysOf(sub)) {
            Vec3 c = sub.renderPose().transformPosition(Vec3.atCenterOf(be.getBlockPos()));
            if (!BogeyWheelAnimator.isGroundedAt(sub.getLevel(), c.x, c.y, c.z, BogeyWheelAnimator.railSide(be))) {
                continue;
            }
            mc.level.playLocalSound(c.x, c.y, c.z, SoundEvents.GRINDSTONE_USE, SoundSource.NEUTRAL,
                    intensity, pitch, false);
        }
    }

    private void tickSlip(Minecraft mc, SableTrainClientRegistry.TrainMarker marker, Vec3 at) {
        if (marker == null || marker.slippingBogeys().isEmpty()) {
            if (slipSound != null) {
                slipSound.stopSound();
                slipSound = null;
                slipFadeTicks = 0;
            }
            return;
        }
        if (tick % 4 == 0) {
            slipPitchJitter = (mc.level.random.nextFloat() - 0.5f) * 0.04f;
        }
        slipFadeTicks = Math.min(slipFadeTicks + 1, SLIP_FADE_TICKS);
        double spin = maxSlipSpeed(marker);
        float pitch = Mth.clamp(0.3f + (float) (spin * SLIP_PITCH_SCALE), 0.3f, 0.55f) + slipPitchJitter;
        slipSound = playIfMissing(mc, slipSound, SoundEvents.GRINDSTONE_USE);
        slipSound.setLocation(at);
        slipSound.setPitch(pitch);
        slipSound.setVolume(0.3f * (slipFadeTicks / (float) SLIP_FADE_TICKS));
    }

    private static double maxSlipSpeed(SableTrainClientRegistry.TrainMarker marker) {
        double worst = 0.0;
        for (BlockPos pos : marker.slippingBogeys()) {
            Float rate = marker.drivenBogeys().get(pos);
            if (rate != null) {
                worst = Math.max(worst, Math.abs(rate));
            }
        }
        return worst;
    }

    private LoopingSound playIfMissing(Minecraft mc, LoopingSound sound, SoundEvent event) {
        if (sound == null) {
            sound = new LoopingSound(event);
            mc.getSoundManager().play(sound);
        }
        return sound;
    }

    private void stopLoops() {
        if (minecartEsqueSound != null) {
            minecartEsqueSound.stopSound();
            minecartEsqueSound = null;
        }
        if (wheelSound != null) {
            wheelSound.stopSound();
            wheelSound = null;
        }
        if (slipSound != null) {
            slipSound.stopSound();
            slipSound = null;
            slipFadeTicks = 0;
        }
    }

    private void stop() {
        stopLoops();
        lastPos = null;
    }

    private static final class LoopingSound extends AbstractTickableSoundInstance {

        private LoopingSound(SoundEvent event) {
            super(event, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.looping = true;
            this.delay = 0;
            this.volume = 1.0e-4f;
        }

        @Override
        public void tick() {
        }

        private void setVolume(float volume) {
            this.volume = volume;
        }

        private void setPitch(float pitch) {
            this.pitch = pitch;
        }

        private void setLocation(Vec3 location) {
            this.x = location.x;
            this.y = location.y;
            this.z = location.z;
        }

        private void stopSound() {
            Minecraft.getInstance().getSoundManager().stop(this);
        }
    }
}

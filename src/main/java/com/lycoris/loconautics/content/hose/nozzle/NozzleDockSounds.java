package com.lycoris.loconautics.content.hose.nozzle;

import com.lycoris.loconautics.LoconauticsConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

public final class NozzleDockSounds {

    private static final ResourceLocation DOCK =
            ResourceLocation.fromNamespaceAndPath("simulated", "block.docking_connector.dock");
    private static final ResourceLocation UNDOCK =
            ResourceLocation.fromNamespaceAndPath("simulated", "block.docking_connector.retract");

    private static final float DOCK_BODY_VOLUME = 0.32f;
    private static final float DOCK_BODY_PITCH = 0.62f;
    private static final float DOCK_GRIT_VOLUME = 0.2f;
    private static final float DOCK_GRIT_PITCH = 0.66f;
    private static final float DOCK_RING_VOLUME = 0.07f;
    private static final float DOCK_RING_PITCH = 1.7f;

    private static final float UNDOCK_BODY_VOLUME = 0.3f;
    private static final float UNDOCK_BODY_PITCH = 0.78f;
    private static final float UNDOCK_GRIT_VOLUME = 0.18f;
    private static final float UNDOCK_GRIT_PITCH = 0.83f;
    private static final float UNDOCK_RING_VOLUME = 0.06f;
    private static final float UNDOCK_RING_PITCH = 1.55f;

    private NozzleDockSounds() {
    }

    public static void docked(Level level, BlockPos pos) {
        layer(level, pos, DOCK, DOCK_BODY_VOLUME, DOCK_BODY_PITCH);
        layer(level, pos, DOCK, DOCK_GRIT_VOLUME, DOCK_GRIT_PITCH);
        layer(level, pos, DOCK, DOCK_RING_VOLUME, DOCK_RING_PITCH);
    }

    public static void undocked(Level level, BlockPos pos) {
        layer(level, pos, UNDOCK, UNDOCK_BODY_VOLUME, UNDOCK_BODY_PITCH);
        layer(level, pos, UNDOCK, UNDOCK_GRIT_VOLUME, UNDOCK_GRIT_PITCH);
        layer(level, pos, UNDOCK, UNDOCK_RING_VOLUME, UNDOCK_RING_PITCH);
    }

    private static void layer(Level level, BlockPos pos, ResourceLocation id, float volume, float pitch) {
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null);
        if (event == null) {
            LoconauticsConstants.LOGGER.warn("[nozzlesound] missing sound event {}", id);
            return;
        }
        level.playSound(null, pos, event, SoundSource.BLOCKS, volume, pitch);
    }
}

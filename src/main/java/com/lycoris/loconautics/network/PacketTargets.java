package com.lycoris.loconautics.network;

import com.simibubi.create.foundation.utility.AdventureUtil;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class PacketTargets {

    private static final double STATION_REACH = 20.0;

    private PacketTargets() {
    }

    @Nullable
    public static <T extends BlockEntity> T blockEntity(Level level, BlockPos pos, Class<T> type) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!type.isInstance(be)) {
            SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
            be = subLevel == null ? null : subLevel.getLevel().getBlockEntity(pos);
        }
        return type.isInstance(be) ? type.cast(be) : null;
    }

    public static boolean finite(float v) {
        return Float.isFinite(v);
    }

    public static boolean withinReach(ServerPlayer player, BlockEntity be) {
        Vec3 world = Vec3.atCenterOf(be.getBlockPos());
        SubLevel sub = Sable.HELPER.getContaining(be);
        if (sub != null) {
            Vector3d lifted = sub.logicalPose().transformPosition(
                    new Vector3d(world.x, world.y, world.z), new Vector3d());
            world = new Vec3(lifted.x, lifted.y, lifted.z);
        }
        return player.position().closerThan(world, 12.0);
    }

    public static boolean canEditStation(ServerPlayer player, BlockPos pos) {
        if (player.isSpectator() || AdventureUtil.isAdventure(player)) return false;
        Level level = player.level();
        if (!level.isLoaded(pos)) return false;
        Vec3 world = Vec3.atCenterOf(pos);
        SubLevel sub = Sable.HELPER.getContaining(level, pos);
        if (sub != null) {
            Vector3d lifted = sub.logicalPose().transformPosition(
                    new Vector3d(world.x, world.y, world.z), new Vector3d());
            world = new Vec3(lifted.x, lifted.y, lifted.z);
        }
        return player.position().closerThan(world, STATION_REACH);
    }
}

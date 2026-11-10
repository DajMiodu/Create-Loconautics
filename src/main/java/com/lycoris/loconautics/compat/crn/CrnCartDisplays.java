package com.lycoris.loconautics.compat.crn;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import com.simibubi.create.AllEntityTypes;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public final class CrnCartDisplays {

    private static final Map<UUID, CarriageContraption> STANDINS = new ConcurrentHashMap<>();

    private static final Method CONTRAPTION_TICK = resolve();

    private static final java.util.Set<BlockPos> SEEN = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private CrnCartDisplays() {
    }

    private static Method resolve() {
        try {
            return Class.forName("de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity")
                    .getMethod("contraptionTick", Level.class, BlockPos.class, BlockState.class, Contraption.class);
        } catch (ReflectiveOperationException e) {
            LoconauticsConstants.LOGGER.debug("Railways Navigator display tick lookup failed", e);
            return null;
        }
    }

    public static void clear() {
        STANDINS.clear();
        SEEN.clear();
    }

    public static void tick(BlockEntity display) {
        Level level = display.getLevel();
        UUID trainId = level != null && level.isClientSide ? trainIdOf(display) : null;
        if (SEEN.add(display.getBlockPos().immutable())) {
        }
        if (CONTRAPTION_TICK == null || trainId == null) {
            return;
        }
        try {
            CONTRAPTION_TICK.invoke(display, level, display.getBlockPos(), display.getBlockState(),
                    STANDINS.computeIfAbsent(trainId, id -> standIn(level, id)));
        } catch (ReflectiveOperationException e) {
            LoconauticsConstants.LOGGER.debug("Railways Navigator display tick failed", e);
        }
    }

    private static UUID trainIdOf(BlockEntity display) {
        if (!(Sable.HELPER.getContaining(display) instanceof SubLevel sub)) {
            return null;
        }
        return SableTrainClientRegistry.trainIdOf(sub.getUniqueId());
    }

    private static CarriageContraption standIn(Level level, UUID trainId) {
        CarriageContraption contraption = new CarriageContraption(Direction.NORTH);
        CarriageContraptionEntity entity =
                new CarriageContraptionEntity(AllEntityTypes.CARRIAGE_CONTRAPTION.get(), level);
        entity.trainId = trainId;
        contraption.entity = entity;
        return contraption;
    }
}

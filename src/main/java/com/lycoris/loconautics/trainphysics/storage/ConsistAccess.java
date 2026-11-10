package com.lycoris.loconautics.trainphysics.storage;

import com.lycoris.loconautics.trainphysics.TrainConsist;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public final class ConsistAccess {

    public static final int WHOLE_CONSIST = -1;

    private ConsistAccess() {
    }

    public static List<UUID> carriages(ServerLevel level, UUID headId) {
        return TrainConsist.orderedChain(level, headId);
    }

    @Nullable
    public static ServerSubLevel carriage(ServerLevel level, UUID headId, int index) {
        List<UUID> chain = carriages(level, headId);
        if (index < 0 || index >= chain.size()) {
            return null;
        }
        return subLevel(level, chain.get(index));
    }

    public static boolean hasCarriage(ServerLevel level, UUID headId, int index) {
        return index == WHOLE_CONSIST || carriage(level, headId, index) != null;
    }

    public static List<IItemHandler> itemsIn(ServerLevel level, UUID headId, int carriageIndex) {
        return CarriageStorageAccount.itemsFor(level, headId, carriageIndex);
    }

    public static List<IFluidHandler> fluidsIn(ServerLevel level, UUID headId, int carriageIndex) {
        return CarriageStorageAccount.fluidsFor(level, headId, carriageIndex);
    }

    public static boolean hasPlayerAboard(ServerLevel level, UUID headId, int carriageIndex) {
        return countPlayersAboard(level, headId, carriageIndex) > 0;
    }

    public static int countPlayersAboard(ServerLevel level, UUID headId, int carriageIndex) {
        int total = 0;
        for (UUID id : addressed(level, headId, carriageIndex)) {
            ServerSubLevel sub = subLevel(level, id);
            if (sub == null) {
                continue;
            }
            for (Player player : sub.getLevel().players()) {
                if (player.getVehicle() == null) {
                    continue;
                }
                BlockPos seat = BlockPos.containing(player.getVehicle().position());
                if (sub.getPlot().getBoundingBox().contains(seat.getX(), seat.getY(), seat.getZ())) {
                    total++;
                }
            }
        }
        return total;
    }

    public static int countItems(ServerLevel level, UUID headId, int carriageIndex,
                                 java.util.function.Predicate<net.minecraft.world.item.ItemStack> filter) {
        int total = 0;
        for (IItemHandler handler : itemsIn(level, headId, carriageIndex)) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                var stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && filter.test(stack)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    public static int countFluid(ServerLevel level, UUID headId, int carriageIndex,
                                 java.util.function.Predicate<net.neoforged.neoforge.fluids.FluidStack> filter) {
        int total = 0;
        for (IFluidHandler handler : fluidsIn(level, headId, carriageIndex)) {
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                var stack = handler.getFluidInTank(tank);
                if (!stack.isEmpty() && filter.test(stack)) {
                    total += stack.getAmount();
                }
            }
        }
        return total;
    }

    private static List<UUID> addressed(ServerLevel level, UUID headId, int carriageIndex) {
        List<UUID> chain = carriages(level, headId);
        if (carriageIndex == WHOLE_CONSIST) {
            return chain;
        }
        return carriageIndex >= 0 && carriageIndex < chain.size()
                ? List.of(chain.get(carriageIndex))
                : List.of();
    }

    @Nullable
    private static ServerSubLevel subLevel(ServerLevel level, UUID id) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || id == null) {
            return null;
        }
        return container.getSubLevel(id) instanceof ServerSubLevel sub && !sub.isRemoved() ? sub : null;
    }
}

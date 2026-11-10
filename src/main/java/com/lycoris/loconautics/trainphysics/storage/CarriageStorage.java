package com.lycoris.loconautics.trainphysics.storage;

import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceBlockEntity;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CarriageStorage {

    private CarriageStorage() {
    }

    public static Map<String, Map<String, Integer>> tallyFluids(List<IFluidHandler> handlers) {
        Map<String, Map<String, Integer>> out = new HashMap<>();
        for (IFluidHandler h : handlers) {
            for (int tank = 0; tank < h.getTanks(); tank++) {
                var stack = h.getFluidInTank(tank);
                if (stack.isEmpty()) {
                    continue;
                }
                String id = NeoForgeRegistries.FLUID_TYPES.getKey(stack.getFluid().getFluidType()).toString();
                Map<String, Integer> entry = out.computeIfAbsent(id, k -> new HashMap<>());
                entry.merge("amount", stack.getAmount(), Integer::sum);
                entry.merge("capacity", h.getTankCapacity(tank), Integer::sum);
            }
        }
        return out;
    }

    public static List<IItemHandler> items(ServerSubLevel sub) {
        List<IItemHandler> out = new ArrayList<>();
        Set<IItemHandler> seen = identitySet();
        for (BlockEntity be : reachable(sub, false, null)) {
            IItemHandler handler = handlerOf(sub, be);
            if (handler != null && seen.add(handler)) {
                out.add(handler);
            }
        }
        return out;
    }

    public static List<IItemHandlerModifiable> exposedItems(ServerSubLevel sub) {
        List<IItemHandlerModifiable> out = new ArrayList<>();
        Set<IItemHandler> seen = identitySet();
        for (BlockEntity be : reachable(sub, true, null)) {
            if (handlerOf(sub, be) instanceof IItemHandlerModifiable modifiable && seen.add(modifiable)) {
                out.add(modifiable);
            }
        }
        return out;
    }

    public static List<IFluidHandler> fluids(ServerSubLevel sub) {
        return fluids(sub, false);
    }

    public static List<IFluidHandler> exposedFluids(ServerSubLevel sub) {
        return fluids(sub, true);
    }

    public static boolean insert(ServerSubLevel sub, @Nullable BlockPos skipPos, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (BlockEntity be : reachable(sub, true, skipPos)) {
            IItemHandler handler = handlerOf(sub, be);
            if (handler == null) {
                continue;
            }
            ItemStack left = ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false);
            stack.setCount(left.getCount());
            if (stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static List<IFluidHandler> fluids(ServerSubLevel sub, boolean skipInterfaces) {
        List<IFluidHandler> out = new ArrayList<>();
        Set<IFluidHandler> seen = identitySet();
        for (BlockEntity be : reachable(sub, skipInterfaces, null)) {
            IFluidHandler handler = sub.getLevel()
                    .getCapability(Capabilities.FluidHandler.BLOCK, be.getBlockPos(), null);
            if (handler != null && seen.add(handler)) {
                out.add(handler);
            }
        }
        return out;
    }

    private static <T> Set<T> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static List<BlockEntity> reachable(ServerSubLevel sub, boolean skipInterfaces, @Nullable BlockPos skipPos) {
        List<BlockEntity> out = new ArrayList<>();
        if (sub == null || sub.isRemoved()) {
            return out;
        }
        for (BlockEntity be : SubLevelBlocks.blockEntities(sub)) {
            if (skipInterfaces && be instanceof PortableStorageInterfaceBlockEntity) {
                continue;
            }
            BlockState state = be.getBlockState();
            if (be instanceof ChestBlockEntity && state.hasProperty(ChestBlock.TYPE)
                    && state.getValue(ChestBlock.TYPE) == ChestType.LEFT) {
                continue;
            }
            if (be.getBlockPos().equals(skipPos)) {
                continue;
            }
            out.add(be);
        }
        return out;
    }

    @Nullable
    private static IItemHandler handlerOf(ServerSubLevel sub, BlockEntity be) {
        ServerLevel level = sub.getLevel();
        return level.getCapability(Capabilities.ItemHandler.BLOCK, be.getBlockPos(), null);
    }
}

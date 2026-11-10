package com.lycoris.loconautics.compat.create.psi;

import com.lycoris.loconautics.trainphysics.storage.CarriageStorage;
import com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceBlockEntity;
import com.simibubi.create.foundation.fluid.CombinedTankWrapper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class SablePsiHandlers {

    private static final int PASS_THROUGH_CAPACITY = 8000;

    private SablePsiHandlers() {
    }

    public static IItemHandlerModifiable carriageItems(ServerSubLevel sub, Runnable onTransfer) {
        return new NotifyingItems(new TickCached<>(sub, () -> {
            List<IItemHandlerModifiable> found = CarriageStorage.exposedItems(sub);
            return found.isEmpty()
                    ? new ItemStackHandler(0)
                    : new CombinedInvWrapper(found.toArray(new IItemHandlerModifiable[0]));
        }), onTransfer);
    }

    public static IFluidHandler carriageFluids(ServerSubLevel sub, Runnable onTransfer) {
        return new NotifyingFluids(new TickCached<>(sub, () ->
                new CombinedTankWrapper(CarriageStorage.exposedFluids(sub).toArray(new IFluidHandler[0]))), onTransfer);
    }

    public static IItemHandlerModifiable passThroughItems(ServerLevel level, BlockPos farPos, Runnable onTransfer) {
        return new PassThroughItems(level, farPos, onTransfer);
    }

    public static IFluidHandler passThroughFluids(ServerLevel level, BlockPos farPos, Runnable onTransfer) {
        return new PassThroughFluids(level, farPos, onTransfer);
    }

    static List<IItemHandler> itemNeighbors(ServerLevel level, BlockPos pos) {
        List<IItemHandler> out = new ArrayList<>();
        for (Direction side : Direction.values()) {
            BlockPos neighbor = pos.relative(side);
            if (level.getBlockEntity(neighbor) instanceof PortableStorageInterfaceBlockEntity) {
                continue;
            }
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, neighbor, side.getOpposite());
            if (handler != null) {
                out.add(handler);
            }
        }
        return out;
    }

    static List<IFluidHandler> fluidNeighbors(ServerLevel level, BlockPos pos) {
        List<IFluidHandler> out = new ArrayList<>();
        for (Direction side : Direction.values()) {
            BlockPos neighbor = pos.relative(side);
            if (level.getBlockEntity(neighbor) instanceof PortableStorageInterfaceBlockEntity) {
                continue;
            }
            IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, neighbor, side.getOpposite());
            if (handler != null) {
                out.add(handler);
            }
        }
        return out;
    }

    static final class TickCached<T> {

        private final ServerSubLevel sub;
        private final Supplier<T> builder;
        private T value;
        private long builtAt = Long.MIN_VALUE;

        TickCached(ServerSubLevel sub, Supplier<T> builder) {
            this.sub = sub;
            this.builder = builder;
        }

        T get() {
            long now = sub.getLevel().getGameTime();
            if (value == null || now != builtAt) {
                value = builder.get();
                builtAt = now;
            }
            return value;
        }
    }

    static final class NotifyingItems implements IItemHandlerModifiable {

        private final TickCached<IItemHandlerModifiable> wrapped;
        private final Runnable onTransfer;

        NotifyingItems(TickCached<IItemHandlerModifiable> wrapped, Runnable onTransfer) {
            this.wrapped = wrapped;
            this.onTransfer = onTransfer;
        }

        @Override
        public int getSlots() {
            return wrapped.get().getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return wrapped.get().getStackInSlot(slot);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            wrapped.get().setStackInSlot(slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return wrapped.get().getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return wrapped.get().isItemValid(slot, stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            ItemStack left = wrapped.get().insertItem(slot, stack, simulate);
            if (!simulate && !ItemStack.matches(left, stack)) {
                onTransfer.run();
            }
            return left;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack taken = wrapped.get().extractItem(slot, amount, simulate);
            if (!simulate && !taken.isEmpty()) {
                onTransfer.run();
            }
            return taken;
        }
    }

    static final class NotifyingFluids implements IFluidHandler {

        private final TickCached<IFluidHandler> wrapped;
        private final Runnable onTransfer;

        NotifyingFluids(TickCached<IFluidHandler> wrapped, Runnable onTransfer) {
            this.wrapped = wrapped;
            this.onTransfer = onTransfer;
        }

        @Override
        public int getTanks() {
            return wrapped.get().getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return wrapped.get().getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return wrapped.get().getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return wrapped.get().isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            int filled = wrapped.get().fill(resource, action);
            if (filled > 0 && action.execute()) {
                onTransfer.run();
            }
            return filled;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            FluidStack drained = wrapped.get().drain(resource, action);
            if (!drained.isEmpty() && action.execute()) {
                onTransfer.run();
            }
            return drained;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            FluidStack drained = wrapped.get().drain(maxDrain, action);
            if (!drained.isEmpty() && action.execute()) {
                onTransfer.run();
            }
            return drained;
        }
    }

    static final class PassThroughItems implements IItemHandlerModifiable {

        private final ServerLevel level;
        private final BlockPos farPos;
        private final Runnable onTransfer;

        PassThroughItems(ServerLevel level, BlockPos farPos, Runnable onTransfer) {
            this.level = level;
            this.farPos = farPos;
            this.onTransfer = onTransfer;
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            throw new UnsupportedOperationException("PassThroughItems has no backing slot to set");
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            ItemStack left = stack;
            for (IItemHandler handler : itemNeighbors(level, farPos)) {
                left = ItemHandlerHelper.insertItemStacked(handler, left, simulate);
                if (left.isEmpty()) {
                    break;
                }
            }
            if (!simulate && left.getCount() != stack.getCount()) {
                onTransfer.run();
            }
            return left;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            for (IItemHandler handler : itemNeighbors(level, farPos)) {
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack taken = handler.extractItem(i, amount, simulate);
                    if (taken.isEmpty()) {
                        continue;
                    }
                    if (!simulate) {
                        onTransfer.run();
                    }
                    return taken;
                }
            }
            return ItemStack.EMPTY;
        }
    }

    static final class PassThroughFluids implements IFluidHandler {

        private final ServerLevel level;
        private final BlockPos farPos;
        private final Runnable onTransfer;

        PassThroughFluids(ServerLevel level, BlockPos farPos, Runnable onTransfer) {
            this.level = level;
            this.farPos = farPos;
            this.onTransfer = onTransfer;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return PASS_THROUGH_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return true;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            FluidStack left = resource.copy();
            int total = 0;
            for (IFluidHandler handler : fluidNeighbors(level, farPos)) {
                int moved = handler.fill(left, action);
                if (moved <= 0) {
                    continue;
                }
                total += moved;
                left.shrink(moved);
                if (left.isEmpty()) {
                    break;
                }
            }
            if (total > 0 && action.execute()) {
                onTransfer.run();
            }
            return total;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            for (IFluidHandler handler : fluidNeighbors(level, farPos)) {
                FluidStack drained = handler.drain(resource, action);
                if (drained.isEmpty()) {
                    continue;
                }
                if (action.execute()) {
                    onTransfer.run();
                }
                return drained;
            }
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            for (IFluidHandler handler : fluidNeighbors(level, farPos)) {
                FluidStack drained = handler.drain(maxDrain, action);
                if (drained.isEmpty()) {
                    continue;
                }
                if (action.execute()) {
                    onTransfer.run();
                }
                return drained;
            }
            return FluidStack.EMPTY;
        }
    }
}
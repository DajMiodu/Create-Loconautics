package com.lycoris.loconautics.content.boiler.firebox;


import com.lycoris.loconautics.common.dock.HorizontalDockPoint;
import com.lycoris.loconautics.common.dock.IDockable;
import com.lycoris.loconautics.content.boiler.BoilerTags;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

public class FireboxStockerBlockEntity extends SmartBlockEntity implements IDockable, IHaveGoggleInformation {

    public static final int SLOTS = 9;

    private static final int FEED_TICKS = 10;

    private static final int MAX_FIREBOX_SEARCH = 64;

    private final ItemStackHandler inventory = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return FireboxBlockEntity.isFuel(stack);
        }
    };

    private final IItemHandler insertOnly = new IItemHandler() {
        @Override
        public int getSlots() {
            return inventory.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return inventory.isItemValid(slot, stack);
        }
    };


    private boolean docked;
    private boolean primary;

    @Nullable private BlockPos controllerPos;

    private int feedCooldown = FEED_TICKS;

    public FireboxStockerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;

        rescan();

        if (--feedCooldown > 0) return;
        feedCooldown = FEED_TICKS;
        if (primary) feedFirebox();
    }

    private void rescan() {
        BlockPos found = null;
        List<BlockPos> group = fireboxGroup();
        for (BlockPos firebox : group) {
            BlockState state = level.getBlockState(firebox);
            if (!state.getValue(FireboxBlock.EXTENSION)
                    && level.getBlockEntity(firebox) instanceof FireboxBlockEntity) {
                found = firebox;
                break;
            }
        }

        boolean wasPrimary = primary;
        BlockPos wasController = controllerPos;
        controllerPos = found;
        primary = found != null && isPrimaryStocker(group);

        if (wasPrimary != primary || !Objects.equals(wasController, controllerPos)) {
            setChanged();
            sendData();
        }
    }

    private boolean isPrimaryStocker(List<BlockPos> group) {
        long best = Long.MAX_VALUE;
        for (BlockPos firebox : group) {
            BlockPos below = firebox.below();
            if (level.getBlockEntity(below) instanceof FireboxStockerBlockEntity) {
                best = Math.min(best, below.asLong());
            }
        }
        return best == worldPosition.asLong();
    }

    private List<BlockPos> fireboxGroup() {
        List<BlockPos> found = new ArrayList<>();
        BlockPos start = worldPosition.above();
        if (!level.getBlockState(start).is(BoilerTags.BOILER_FIREBOX)) return found;

        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> frontier = new ArrayDeque<>();
        visited.add(start);
        frontier.add(start);

        while (!frontier.isEmpty() && found.size() < MAX_FIREBOX_SEARCH) {
            BlockPos current = frontier.poll();
            found.add(current);
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos neighbor = current.relative(dir);
                if (!visited.add(neighbor)) continue;
                if (level.getBlockState(neighbor).is(BoilerTags.BOILER_FIREBOX)) {
                    frontier.add(neighbor);
                }
            }
        }
        return found;
    }

    @Nullable
    private FireboxBlockEntity controller() {
        if (controllerPos == null) return null;
        return level.getBlockEntity(controllerPos) instanceof FireboxBlockEntity be ? be : null;
    }

    private void feedFirebox() {
        FireboxBlockEntity firebox = controller();
        if (firebox == null) return;

        ItemStackHandler target = firebox.getInventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) continue;

            ItemStack one = stack.copyWithCount(1);
            if (!ItemHandlerHelper.insertItem(target, one, true).isEmpty()) return;
            ItemHandlerHelper.insertItem(target, one, false);
            inventory.extractItem(slot, 1, false);
            firebox.setChanged();
            return;
        }
    }

    private Direction dockFace() {
        return getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
    }

    private Vec3 dockAnchorLocal() {
        return HorizontalDockPoint.anchorLocal(this, dockFace(), dockSeatOffset());
    }

    @Override
    public Vec3 getDockPoint() {
        return HorizontalDockPoint.worldPoint(this, dockAnchorLocal());
    }

    @Override
    public Quaterniond getDockOrientation() {
        return HorizontalDockPoint.orientation(dockFace());
    }

    @Override
    public double dockRadius() {
        return 0.75;
    }

    @Override
    public double dockSeatOffset() {
        return 1.0;
    }

    @Override
    public boolean canDock() {
        return primary && !docked;
    }

    @Override
    public void onDocked() {
        docked = true;
        setChanged();
    }

    @Override
    public void onUndocked() {
        docked = false;
        setChanged();
    }

    @Override
    @Nullable
    public net.neoforged.neoforge.fluids.capability.IFluidHandler getDockedFluidHandler() {
        return null;
    }

    @Override
    @Nullable
    public IItemHandler getDockedItemHandler() {
        return inventory;
    }

    @Override
    public boolean allowsItem(ItemStack stack) {
        return FireboxBlockEntity.isFuel(stack);
    }

    public IItemHandler inputHandler() {
        return insertOnly;
    }

    public boolean isPrimary() {
        return primary;
    }

    public SimpleContainer dropContents() {
        SimpleContainer container = new SimpleContainer(inventory.getSlots());
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            container.setItem(slot, inventory.getStackInSlot(slot));
        }
        return container;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        LoconauticsLang.translate("gui.goggles.stocker_stats").forGoggles(tooltip);
        if (controllerPos == null) {
            LoconauticsLang.translate("gui.goggles.stocker_no_firebox")
                    .style(ChatFormatting.RED).forGoggles(tooltip);
            return true;
        }
        if (!primary) {
            LoconauticsLang.translate("gui.goggles.stocker_duplicate")
                    .style(ChatFormatting.RED).forGoggles(tooltip);
            return true;
        }
        int held = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            held += inventory.getStackInSlot(slot).getCount();
        }
        LoconauticsLang.translate("gui.goggles.stocker_fuel")
                .style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.builder().text(String.valueOf(held))
                .style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putBoolean("Docked", docked);
        tag.putBoolean("Primary", primary);
        if (controllerPos != null) tag.putLong("Controller", controllerPos.asLong());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        docked = tag.getBoolean("Docked");
        primary = tag.getBoolean("Primary");
        controllerPos = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
    }
}

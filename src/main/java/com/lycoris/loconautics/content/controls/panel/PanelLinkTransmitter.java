package com.lycoris.loconautics.content.controls.panel;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class PanelLinkTransmitter implements IRedstoneLinkable {

    private final BlockEntity owner;

    private final ItemStack first;

    private final ItemStack second;

    private final Couple<Frequency> key;

    private int strength;

    private boolean registered;

    public PanelLinkTransmitter(BlockEntity owner, ItemStack first, ItemStack second) {
        this.owner = owner;
        this.first = first.copy();
        this.second = second.copy();
        this.key = Couple.create(Frequency.of(first), Frequency.of(second));
    }

    public static boolean isEmpty(ItemStack first, ItemStack second) {
        return first.isEmpty() && second.isEmpty();
    }

    public static PanelLinkTransmitter read(BlockEntity owner, CompoundTag tag, HolderLookup.Provider registries) {
        return new PanelLinkTransmitter(owner,
                ItemStack.parseOptional(registries, tag.getCompound("First")),
                ItemStack.parseOptional(registries, tag.getCompound("Second")));
    }

    public CompoundTag write(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("First", first.saveOptional(registries));
        tag.put("Second", second.saveOptional(registries));
        return tag;
    }

    public ItemStack first() {
        return first;
    }

    public ItemStack second() {
        return second;
    }

    public void transmit(Level level, int signal) {
        if (!registered) {
            strength = signal;
            registered = true;
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, this);
            return;
        }
        if (signal == strength) return;
        strength = signal;
        Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, this);
    }

    public void release(Level level) {
        if (!registered) return;
        registered = false;
        strength = 0;
        Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, this);
    }

    @Override
    public int getTransmittedStrength() {
        return strength;
    }

    @Override
    public void setReceivedStrength(int power) {
    }

    @Override
    public boolean isListening() {
        return false;
    }

    @Override
    public boolean isAlive() {
        return registered && !owner.isRemoved();
    }

    @Override
    public Couple<Frequency> getNetworkKey() {
        return key;
    }

    @Override
    public BlockPos getLocation() {
        return owner.getBlockPos();
    }
}

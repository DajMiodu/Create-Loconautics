package com.lycoris.loconautics.compat.computercraft;

import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlockEntity;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

public class CcFrequencyLink implements IRedstoneLinkable {

    private final BearingAxleBlockEntity axle;
    private final ServerLevel level;
    private final Couple<Frequency> key;
    private final boolean listening;
    private int receivedStrength;
    private int transmitStrength;

    public CcFrequencyLink(BearingAxleBlockEntity axle, ServerLevel level, ItemStack first, ItemStack second,
                           boolean listening) {
        this.axle = axle;
        this.level = level;
        this.key = Couple.create(Frequency.of(first), Frequency.of(second));
        this.listening = listening;
    }

    public void addToNetwork() {
        Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, this);
    }

    public void removeFromNetwork() {
        Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, this);
    }

    public int getReceivedStrength() {
        return receivedStrength;
    }

    public void setTransmitStrength(int strength) {
        int clamped = Math.max(0, Math.min(15, strength));
        if (clamped == transmitStrength) {
            return;
        }
        transmitStrength = clamped;
        Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, this);
    }

    @Override
    public int getTransmittedStrength() {
        return transmitStrength;
    }

    @Override
    public boolean isListening() {
        return listening;
    }

    @Override
    public boolean isAlive() {
        return !axle.isRemoved();
    }

    @Override
    public Couple<Frequency> getNetworkKey() {
        return key;
    }

    @Override
    public BlockPos getLocation() {
        return axle.getBlockPos();
    }

    @Override
    public void setReceivedStrength(int strength) {
        this.receivedStrength = strength;
    }
}

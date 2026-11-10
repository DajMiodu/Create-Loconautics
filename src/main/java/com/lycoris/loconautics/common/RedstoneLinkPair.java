package com.lycoris.loconautics.common;

import com.lycoris.loconautics.trainphysics.portal.PortalTwins;
import net.minecraft.server.level.ServerLevel;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntConsumer;

public final class RedstoneLinkPair {

    private final BlockEntity owner;
    private final IntConsumer onFirstStrength;
    private final IntConsumer onSecondStrength;

    private ItemStack firstFreqA = ItemStack.EMPTY;
    private ItemStack firstFreqB = ItemStack.EMPTY;
    private ItemStack secondFreqA = ItemStack.EMPTY;
    private ItemStack secondFreqB = ItemStack.EMPTY;

    @Nullable private Receiver firstReceiver;
    @Nullable private Receiver secondReceiver;
    @Nullable private Level registeredIn;

    public RedstoneLinkPair(BlockEntity owner, IntConsumer onFirstStrength, IntConsumer onSecondStrength) {
        this.owner = owner;
        this.onFirstStrength = onFirstStrength;
        this.onSecondStrength = onSecondStrength;
    }

    public void register(Level level) {
        unregister(level);
        if (level instanceof ServerLevel server && PortalTwins.masterBlock(server, owner.getBlockPos()) != null) {
            return;
        }

        RedstoneLinkNetworkHandler.Frequency a1 = RedstoneLinkNetworkHandler.Frequency.of(firstFreqA);
        RedstoneLinkNetworkHandler.Frequency a2 = RedstoneLinkNetworkHandler.Frequency.of(firstFreqB);
        RedstoneLinkNetworkHandler.Frequency b1 = RedstoneLinkNetworkHandler.Frequency.of(secondFreqA);
        RedstoneLinkNetworkHandler.Frequency b2 = RedstoneLinkNetworkHandler.Frequency.of(secondFreqB);

        boolean hasFirst = a1 != RedstoneLinkNetworkHandler.Frequency.EMPTY
                || a2 != RedstoneLinkNetworkHandler.Frequency.EMPTY;
        boolean hasSecond = b1 != RedstoneLinkNetworkHandler.Frequency.EMPTY
                || b2 != RedstoneLinkNetworkHandler.Frequency.EMPTY;

        if (hasFirst || hasSecond) {
            registeredIn = level;
        }
        if (hasFirst) {
            firstReceiver = new Receiver(owner.getBlockPos(), Couple.create(a1, a2), owner, onFirstStrength);
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, firstReceiver);
            deliverCurrent(level, firstReceiver);
        }
        if (hasSecond) {
            secondReceiver = new Receiver(owner.getBlockPos(), Couple.create(b1, b2), owner, onSecondStrength);
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, secondReceiver);
            deliverCurrent(level, secondReceiver);
        }
    }

    // create only pushes a new strength to the other members, so a receiver that joins never hears the current one
    private static void deliverCurrent(Level level, Receiver receiver) {
        int power = 0;
        for (IRedstoneLinkable other : Create.REDSTONE_LINK_NETWORK_HANDLER.getNetworkOf(level, receiver)) {
            if (other != receiver && other.isAlive() && RedstoneLinkNetworkHandler.withinRange(receiver, other)) {
                power = Math.max(power, other.getTransmittedStrength());
            }
        }
        receiver.callback().accept(Math.min(power, 15));
    }

    public void unregister(Level level) {
        Level from = registeredIn != null ? registeredIn : level;
        if (firstReceiver != null) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(from, firstReceiver);
            firstReceiver = null;
        }
        if (secondReceiver != null) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(from, secondReceiver);
            secondReceiver = null;
        }
        if (registeredIn != null) {
            registeredIn = null;
        }
    }

    public void setFirstFrequency(ItemStack a, ItemStack b) {
        firstFreqA = a.copy();
        firstFreqB = b.copy();
    }

    public void setSecondFrequency(ItemStack a, ItemStack b) {
        secondFreqA = a.copy();
        secondFreqB = b.copy();
    }

    public ItemStack getFirstFreqA() {
        return firstFreqA;
    }

    public ItemStack getFirstFreqB() {
        return firstFreqB;
    }

    public ItemStack getSecondFreqA() {
        return secondFreqA;
    }

    public ItemStack getSecondFreqB() {
        return secondFreqB;
    }

    private record Receiver(BlockPos pos, Couple<RedstoneLinkNetworkHandler.Frequency> key, BlockEntity be,
                            IntConsumer callback) implements IRedstoneLinkable {

        @Override
        public int getTransmittedStrength() {
            return 0;
        }

        @Override
        public boolean isListening() {
            return true;
        }

        @Override
        public boolean isAlive() {
            return !be.isRemoved();
        }

        @Override
        public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
            return key;
        }

        @Override
        public BlockPos getLocation() {
            return pos;
        }

            @Override
            public void setReceivedStrength(int strength) {
                callback.accept(strength);
            }
        }
}

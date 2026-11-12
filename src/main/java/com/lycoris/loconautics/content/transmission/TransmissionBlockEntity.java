package com.lycoris.loconautics.content.transmission;

import com.lycoris.loconautics.common.LinkHolder;
import com.lycoris.loconautics.common.RedstoneLinkPair;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.lycoris.loconautics.trainphysics.debug.TransmissionTrace;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TransmissionBlockEntity extends SplitShaftBlockEntity implements MenuProvider, LinkHolder {

    private final RedstoneLinkPair links = new RedstoneLinkPair(this,
            this::setRedstonePower, s -> setDirectionActive(s > 0));

    private int redstonePower;

    private boolean directionActive;

    private boolean kineticRefreshQueued;

    private boolean reattachPending;

    private Float computerRpm;

    private Boolean computerReversed;

    private static final int PANEL_HOLD_TICKS = 5;

    private int panelSpeed;

    private long panelSpeedTick = -PANEL_HOLD_TICKS - 1;

    private boolean panelReversed;

    private long panelDirectionTick = -PANEL_HOLD_TICKS - 1;

    public TransmissionBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level == null || level.isClientSide) return;
        TransmissionTrace.note(this, "initialize before links");
        links.register(level);
        TransmissionTrace.note(this, "initialize after links");
    }

    @Override
    public void releaseLinks() {
        if (level != null) links.unregister(level);
    }

    @Override
    public void invalidate() {
        TransmissionTrace.removed(this);
        if (level != null) links.unregister(level);
        super.invalidate();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null) links.unregister(level);
    }

    @Override
    public float getRotationSpeedModifier(Direction face) {
        if (!hasSource()) return 1.0f;
        if (face == getSourceFacing()) return 1.0f;

        float inputSpeed = getTheoreticalSpeed();
        if (inputSpeed == 0f) return 0f;

        float targetRPM = computeTargetRPM();
        if (targetRPM == 0f) return 0f;

        float sign = isReversed() ? -1f : 1f;
        return sign * targetRPM / Math.abs(inputSpeed);
    }

    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        if (level == null || level.isClientSide || !hasNetwork()) return;
        getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
    }

    @Override
    public float calculateStressApplied() {
        float impact = com.lycoris.loconautics.config.command.ConfigWalker.getOrDefault(
                () -> LoconauticsConfig.TRANSMISSION_SU_IMPACT.get().floatValue(), 4.0f, "transmission_su_impact");

        float inputSpeed = Math.abs(getTheoreticalSpeed());
        if (inputSpeed == 0f) return impact;
        return impact * computeTargetRPM() / inputSpeed;
    }

    @Override
    public float calculateAddedStressCapacity() {
        return 0f;
    }

    private float computeTargetRPM() {
        if (computerRpm != null) return computerRpm;
        int power = effectivePower();
        if (power == 0) return 0f;
        return (float) Math.ceil(power * 256.0 / 15.0);
    }

    private boolean panelSpeedLive() {
        return level != null && level.getGameTime() - panelSpeedTick <= PANEL_HOLD_TICKS;
    }

    private boolean panelDirectionLive() {
        return level != null && level.getGameTime() - panelDirectionTick <= PANEL_HOLD_TICKS;
    }

    private int effectivePower() {
        return panelSpeedLive() ? panelSpeed : redstonePower;
    }

    public int getPower() {
        if (computerRpm != null) return Math.round(computerRpm * 15f / 256f);
        return effectivePower();
    }

    public float getTargetRpm() {
        return computeTargetRPM();
    }

    public boolean isReversed() {
        if (computerReversed != null) return computerReversed;
        return panelDirectionLive() ? panelReversed : directionActive;
    }

    public void driveSpeedFromPanel(int signal) {
        if (level == null || level.isClientSide) return;
        int clamped = Math.max(0, Math.min(15, signal));
        long now = level.getGameTime();
        boolean changed = panelSpeedLive() ? clamped != panelSpeed : clamped != redstonePower;
        panelSpeed = panelSpeedTick == now ? Math.max(panelSpeed, clamped) : clamped;
        panelSpeedTick = now;
        if (changed) kineticRefreshQueued = true;
    }

    public void driveDirectionFromPanel(boolean reversed) {
        if (level == null || level.isClientSide) return;
        long now = level.getGameTime();
        boolean changed = isReversed() != reversed;
        panelReversed = panelDirectionTick == now ? panelReversed || reversed : reversed;
        panelDirectionTick = now;
        if (changed) kineticRefreshQueued = true;
    }

    public boolean isComputerDriven() {
        return computerRpm != null;
    }

    public float getComputerRpm() {
        return computerRpm == null ? 0f : computerRpm;
    }

    public boolean isComputerReversed() {
        return computerReversed != null && computerReversed;
    }

    public void setComputerDrive(float rpm, boolean reversed) {
        float clamped = Math.max(0f, Math.min(256f, rpm));
        if (computerRpm != null && computerRpm == clamped && computerReversed == reversed) return;
        computerRpm = clamped;
        computerReversed = reversed;
        kineticRefreshQueued = true;
    }

    public void clearComputerDrive() {
        if (computerRpm == null) return;
        computerRpm = null;
        computerReversed = null;
        kineticRefreshQueued = true;
    }

    public void setRedstonePower(int power) {
        int clamped = Math.max(0, Math.min(15, power));
        TransmissionTrace.note(this, "setRedstonePower " + this.redstonePower + " to " + clamped);
        if (this.redstonePower == clamped) return;
        this.redstonePower = clamped;
        kineticRefreshQueued = true;
        setChanged();
        sendData();
    }

    public void setDirectionActive(boolean active) {
        TransmissionTrace.note(this, "setDirectionActive " + this.directionActive + " to " + active);
        if (this.directionActive == active) return;
        this.directionActive = active;
        kineticRefreshQueued = true;
        setChanged();
        sendData();
    }

    @Override
    public void tick() {
        if (reattachPending && level != null && !level.isClientSide) {
            reattachPending = false;
            TransmissionTrace.note(this, "reattach");
            RotationPropagator.handleAdded(level, worldPosition, this);
        }
        super.tick();
        if (level == null || level.isClientSide) return;
        long age = level.getGameTime();
        if (age - panelSpeedTick == PANEL_HOLD_TICKS + 1 || age - panelDirectionTick == PANEL_HOLD_TICKS + 1) {
            kineticRefreshQueued = true;
        }
        if (!kineticRefreshQueued) return;
        kineticRefreshQueued = false;
        applyKineticState();
    }

    // detach before the state is written, and never from a link callback, that runs inside creates network walk. the reattach rides the block entity tick and is saved, so an unload between the two cannot strand the block.
    private void applyKineticState() {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof TransmissionBlock block)) return;
        TransmissionTrace.note(this, "apply detach");
        block.detachKinetics(level, worldPosition);
        reattachPending = true;
        setChanged();
        BlockState updated = state.setValue(TransmissionBlock.POWERED, computeTargetRPM() > 0f);
        if (!updated.equals(state)) {
            level.setBlock(worldPosition, updated, 2);
        }
    }

    public void setSpeedFrequency(ItemStack first, ItemStack second) {
        links.setFirstFrequency(first, second);
        if (level != null && !level.isClientSide) links.register(level);
        setChanged();
        sendData();
    }

    public void setDirectionFrequency(ItemStack first, ItemStack second) {
        links.setSecondFrequency(first, second);
        if (level != null && !level.isClientSide) links.register(level);
        setChanged();
        sendData();
    }

    public ItemStack getSpeedFreqFirst()  { return links.getFirstFreqA(); }
    public ItemStack getSpeedFreqSecond() { return links.getFirstFreqB(); }
    public ItemStack getDirFreqFirst()    { return links.getSecondFreqA(); }
    public ItemStack getDirFreqSecond()   { return links.getSecondFreqB(); }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.loconautics.transmission");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new TransmissionMenu(
                LoconauticsRegistries.TRANSMISSION_MENU.get(), id, inventory, this);
    }

    @Override
    public void sendToMenu(RegistryFriendlyByteBuf buffer) {
        ComponentSerialization.TRUSTED_STREAM_CODEC
                .encode(buffer, Component.translatable(TransmissionMenu.SPEED_KEY));
        ComponentSerialization.TRUSTED_STREAM_CODEC
                .encode(buffer, Component.translatable(TransmissionMenu.DIRECTION_KEY));
        super.sendToMenu(buffer);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("RedstonePower", redstonePower);
        tag.putBoolean("DirectionActive", directionActive);
        if (!clientPacket && reattachPending) tag.putBoolean("ReattachPending", true);

        writeFrequencies(tag, registries);
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        writeFrequencies(tag, registries);
    }

    private void writeFrequencies(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag freq = new CompoundTag();
        freq.put("SpeedFirst",  links.getFirstFreqA().saveOptional(registries));
        freq.put("SpeedSecond", links.getFirstFreqB().saveOptional(registries));
        freq.put("DirFirst",    links.getSecondFreqA().saveOptional(registries));
        freq.put("DirSecond",   links.getSecondFreqB().saveOptional(registries));
        tag.put("Frequency", freq);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        redstonePower   = tag.getInt("RedstonePower");
        directionActive = tag.getBoolean("DirectionActive");
        if (!clientPacket) {
            reattachPending = tag.getBoolean("ReattachPending");
            TransmissionTrace.note(this, "read from tag");
        }

        if (tag.contains("Frequency")) {
            CompoundTag freq = tag.getCompound("Frequency");
            links.setFirstFrequency(
                    ItemStack.parseOptional(registries, freq.getCompound("SpeedFirst")),
                    ItemStack.parseOptional(registries, freq.getCompound("SpeedSecond")));
            links.setSecondFrequency(
                    ItemStack.parseOptional(registries, freq.getCompound("DirFirst")),
                    ItemStack.parseOptional(registries, freq.getCompound("DirSecond")));
        }
    }
}
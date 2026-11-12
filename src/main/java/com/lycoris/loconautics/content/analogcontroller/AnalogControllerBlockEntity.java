package com.lycoris.loconautics.content.analogcontroller;

import com.lycoris.loconautics.common.LinkHolder;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class AnalogControllerBlockEntity extends SmartBlockEntity implements MenuProvider, LinkHolder {

    private static final int RAISE_TICKS = 10;

    private static final int COUNTER_TICKS = RAISE_TICKS / 2;

    private static final int MANUAL_RAISE_TICKS = 20;

    private static final int MANUAL_RELEASE_TICKS = 16;

    private static final int MANUAL_COUNTER_TICKS = 10;

    private static final int MAX_POWER = 15;

    private static final int HORN_HOLD_TICKS = 4;

    private static final int KEY_EXPIRE_TICKS = 30;


    public final LerpedFloat animSpeed = LerpedFloat.linear().startWithValue(0.0);

    public final LerpedFloat animSteer = LerpedFloat.linear().startWithValue(0.0);

    public final LerpedFloat animEquip = LerpedFloat.linear().startWithValue(0.0);

    @Nullable
    private UUID currentUser;

    private boolean locked;

    private boolean raisingSignal;
    private boolean loweringSignal;
    private int steerInput;
    private boolean spaceSignal;
    private int driveInput;
    private boolean lockSignal;
    private long sprintDownAt;
    private long lastKeyTick;
    private boolean sprintHeldForHorn;
    @Nullable
    private BlockPos horn;
    private double cruiseSpeed = Double.NaN;

    private int currentPower;

    private boolean scheduleControlled;
    private Integer scheduleTargetPower;
    private int scheduleRampCounter;
    private int manualRampCounter;

    private int maxPower = MAX_POWER;

    private net.minecraft.world.item.ItemStack frequencyFirst =
            net.minecraft.world.item.ItemStack.EMPTY;
    private net.minecraft.world.item.ItemStack frequencySecond =
            net.minecraft.world.item.ItemStack.EMPTY;

    private net.minecraft.world.item.ItemStack frequencyBackFirst =
            net.minecraft.world.item.ItemStack.EMPTY;
    private net.minecraft.world.item.ItemStack frequencyBackSecond =
            net.minecraft.world.item.ItemStack.EMPTY;

    private net.minecraft.world.item.ItemStack frequencyLeftFirst =
            net.minecraft.world.item.ItemStack.EMPTY;
    private net.minecraft.world.item.ItemStack frequencyLeftSecond =
            net.minecraft.world.item.ItemStack.EMPTY;
    private net.minecraft.world.item.ItemStack frequencyRightFirst =
            net.minecraft.world.item.ItemStack.EMPTY;
    private net.minecraft.world.item.ItemStack frequencyRightSecond =
            net.minecraft.world.item.ItemStack.EMPTY;

    @Nullable private AnalogLinkEntry linkEntry;
    @Nullable private AnalogLinkEntry backLinkEntry;
    @Nullable private AnalogLinkEntry leftLinkEntry;
    @Nullable private AnalogLinkEntry rightLinkEntry;

    public AnalogControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null || level.isClientSide) return;
        updateNetwork(level);
        updateBlockState(level, getBlockState());
    }

    public static void tick(Level level, BlockPos pos, BlockState state,
                            AnalogControllerBlockEntity be) {
        if (level.isClientSide) {
            be.clientTick();
            return;
        }
        be.serverTick(level, state);
    }

    private void clientTick() {
        if (hasUser()) {
            animSpeed.chase(AnalogControllerClientHandler.speedBarRatio(this), 0.5, LerpedFloat.Chaser.EXP);
            animSteer.chase(steerInput, 0.2, LerpedFloat.Chaser.EXP);
            animEquip.chase(1.0, 0.2, LerpedFloat.Chaser.EXP);
        } else {
            animSpeed.chase(AnalogControllerClientHandler.idleRatio(this), 0.5, LerpedFloat.Chaser.EXP);
            animSteer.chase(0.0, 0.0, LerpedFloat.Chaser.EXP);
            animEquip.chase(0.0, 0.2, LerpedFloat.Chaser.EXP);
        }

        animSpeed.tickChaser();
        animSteer.tickChaser();
        animEquip.tickChaser();
    }

    private static int leverTicks(int power, int delta) {
        return power != 0 && Integer.signum(power) != Integer.signum(delta) ? COUNTER_TICKS : RAISE_TICKS;
    }

    private void serverTick(Level level, BlockState state) {
        if (currentUser != null && level.getPlayerByUUID(currentUser) == null) {
            disconnectUser(level);
        }
        if (currentUser != null && anyKeyHeld() && level.getGameTime() - lastKeyTick > KEY_EXPIRE_TICKS) {
            expireHeldKeys(level);
        }

        int previousPower = currentPower;

        if (currentUser == null && !locked && !scheduleControlled && currentPower != 0) {
            if (--decayCounter <= 0) {
                decayCounter = getDecayTicks();
                currentPower -= Integer.signum(currentPower);
            }
        } else {
            decayCounter = getDecayTicks();
        }

        if (scheduleControlled && scheduleTargetPower != null && currentPower != scheduleTargetPower) {
            int interval = leverTicks(currentPower, Integer.signum(scheduleTargetPower - currentPower));
            if (--scheduleRampCounter <= 0) {
                scheduleRampCounter = interval;
                currentPower += Integer.signum(scheduleTargetPower - currentPower);
            }
        }

        if (!scheduleControlled && (currentUser != null || locked) && currentPower != manualTarget()) {
            int step = Integer.signum(manualTarget() - currentPower);
            if (--manualRampCounter <= 0) {
                manualRampCounter = manualInterval(step);
                currentPower += step;
            }
        }

        if (currentPower != previousPower) {
            updateNetwork(level);
            updateBlockState(level, state);
            setChanged();
            sendData();
        }
    }

    private int decayCounter = RAISE_TICKS;

    private int getDecayTicks() {
        return com.lycoris.loconautics.config.command.ConfigWalker.getOrDefault(
                com.lycoris.loconautics.config.LoconauticsConfig.ANALOG_DECAY_TICKS::get, 30, "analog_decay_ticks");
    }

    private static final int NEGATIVE_CAP = 5;
    
    private static final long TOGGLE_DEBOUNCE_TICKS = 3;
    private long lastToggleTick = -TOGGLE_DEBOUNCE_TICKS;

    public void toggleUser(Player player) {
        if (level == null) return;
        long now = level.getGameTime();
        if (now - lastToggleTick < TOGGLE_DEBOUNCE_TICKS) return;
        lastToggleTick = now;

        if (currentUser != null) {
            if (currentUser.equals(player.getUUID())) disconnectUser(level);
            return;
        }
        connectUser(player);
    }

    public void resumeUser(ServerPlayer player) {
        connectUser(player);
        if (!locked) restartRamp();
    }

    // only the current driver can release, unlike toggleuser which would connect a stranger
    public void releaseUser(UUID user) {
        if (level == null || !user.equals(currentUser)) return;
        disconnectUser(level);
    }

    private boolean anyKeyHeld() {
        return raisingSignal || loweringSignal || steerInput != 0 || spaceSignal || lockSignal;
    }

    // a release that never arrived must not leave a key held, the client resends held keys every 5 ticks
    private void expireHeldKeys(Level level) {
        if (sprintHeldForHorn && horn != null && level.getPlayerByUUID(currentUser) instanceof ServerPlayer player) {
            TrainHorn.sound(player, horn, false);
        }
        raisingSignal = false;
        loweringSignal = false;
        spaceSignal = false;
        lockSignal = false;
        sprintHeldForHorn = false;
        horn = null;
        setSteerInput(0);
        updateDriveInput();
    }

    private void connectUser(Player player) {
        currentUser    = player.getUUID();
        lastKeyTick    = level.getGameTime();
        raisingSignal  = false;
        loweringSignal = false;
        spaceSignal    = false;
        driveInput     = 0;
        updateBlockState(level, getBlockState());
        setChanged();
        sendData();
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            sp.displayClientMessage(
                    Component.translatable("block.loconautics.analog_controller.start"), true);
            com.lycoris.loconautics.network.LoconauticsNetwork.sendMount(sp, true, worldPosition);
        }
    }

    private void disconnectUser(Level level) {
        if (currentUser != null && level.getServer() != null) {
            ServerPlayer sp = level.getServer().getPlayerList().getPlayer(currentUser);
            if (sp != null) {
                sp.displayClientMessage(
                        Component.translatable("block.loconautics.analog_controller.stop"), true);
                com.lycoris.loconautics.network.LoconauticsNetwork.sendMount(
                        sp, false, net.minecraft.core.BlockPos.ZERO);
            }
        }
        currentUser    = null;
        raisingSignal  = false;
        loweringSignal = false;
        spaceSignal    = false;
        steerInput     = 0;
        lockSignal     = false;
        sprintHeldForHorn = false;
        horn           = null;
        if (!locked) {
            driveInput = 0;
        }
        updateNetwork(level);
        updateBlockState(level, getBlockState());
        setChanged();
        sendData();
    }

    public void onKeyEvent(UUID user, int keyIndex, boolean pressed) {
        if (!user.equals(currentUser)) return;
        if (pressed) lastKeyTick = level.getGameTime();
        switch (keyIndex) {
            case 0 -> {
                raisingSignal = pressed;
                if (pressed) loweringSignal = false;
                updateDriveInput();
            }
            case 1 -> {
                loweringSignal = pressed;
                if (pressed) raisingSignal = false;
                updateDriveInput();
            }
            case 2 -> {
                if (pressed) setSteerInput(-1);
                else if (steerInput == -1) setSteerInput(0);
            }
            case 3 -> {
                if (pressed) setSteerInput(1);
                else if (steerInput == 1) setSteerInput(0);
            }
            case 4 -> {
                spaceSignal = pressed;
            }
            case 5 -> {
                if (pressed) disconnectUser(level);
            }
            case 6 -> {
                onSprint(user, pressed);
                lockSignal = pressed;
            }
        }
    }

    private void onSprint(UUID user, boolean pressed) {
        long now = level.getGameTime();
        if (pressed && !lockSignal) {
            sprintDownAt = now;
            sprintHeldForHorn = false;
            return;
        }
        if (pressed && now - sprintDownAt >= HORN_HOLD_TICKS) {
            if (!sprintHeldForHorn && level instanceof ServerLevel serverLevel) {
                horn = TrainHorn.nearestWhistle(serverLevel, worldPosition);
            }
            sprintHeldForHorn = true;
            if (horn != null && level.getPlayerByUUID(user) instanceof ServerPlayer player) {
                TrainHorn.sound(player, horn, true);
            }
            return;
        }
        if (pressed || !lockSignal) {
            return;
        }
        if (sprintHeldForHorn) {
            if (horn != null && level.getPlayerByUUID(user) instanceof ServerPlayer player) {
                TrainHorn.sound(player, horn, false);
            }
            horn = null;
            sprintHeldForHorn = false;
            return;
        }
        locked = !locked;
        cruiseSpeed = Double.NaN;
        updateDriveInput();
        setChanged();
        sendData();
    }

    private void updateDriveInput() {
        int held = heldInput();
        if (held != 0 || !locked) {
            driveInput = held;
        }
        restartRamp();
    }

    private void restartRamp() {
        manualRampCounter = 0;
    }

    private int manualTarget() {
        if (locked && heldInput() == 0) return currentPower;
        return driveInput > 0 ? maxPower : driveInput < 0 ? -Math.min(maxPower, NEGATIVE_CAP) : 0;
    }

    private int manualInterval(int step) {
        if (heldInput() == 0) return MANUAL_RELEASE_TICKS;
        return currentPower != 0 && Integer.signum(currentPower) != step ? MANUAL_COUNTER_TICKS : MANUAL_RAISE_TICKS;
    }

    public int heldInput() {
        return raisingSignal ? 1 : loweringSignal ? -1 : 0;
    }

    public double cruiseSpeed() {
        return cruiseSpeed;
    }

    public void setCruiseSpeed(double speed) {
        cruiseSpeed = speed;
    }

    public boolean isSpaceHeld() {
        return spaceSignal;
    }

    public int getSteerInput() {
        return steerInput;
    }

    private void setSteerInput(int value) {
        if (steerInput == value) return;
        steerInput = value;
        if (level != null && !level.isClientSide) updateNetwork(level);
        setChanged();
        sendData();
    }

    public boolean isForwardHeld() {
        return raisingSignal;
    }

    public boolean isReverseHeld() {
        return loweringSignal;
    }

    public void setScheduleTargetPower(int target) {
        scheduleControlled = true;
        scheduleTargetPower = Math.max(-MAX_POWER, Math.min(MAX_POWER, target));
    }

    public void setScheduleSteer(int dir) {
        scheduleControlled = true;
        setSteerInput(Integer.signum(dir));
    }

    public void setCurrentPower(int power) {
        int clamped = Math.max(-MAX_POWER, Math.min(MAX_POWER, power));
        if (currentPower == clamped) return;
        currentPower = clamped;
        if (level != null && !level.isClientSide) {
            updateNetwork(level);
            updateBlockState(level, getBlockState());
        }
        setChanged();
        sendData();
    }

    public void setLocked(boolean value) {
        if (locked == value) {
            return;
        }
        locked = value;
        cruiseSpeed = Double.NaN;
        if (!locked) {
            driveInput = heldInput();
        }
        setChanged();
        sendData();
    }

    public void clearScheduleControl() {
        if (!scheduleControlled) return;
        scheduleControlled = false;
        scheduleTargetPower = null;
        setSteerInput(0);
        restartRamp();
    }

    public boolean isScheduleControlled() {
        return scheduleControlled;
    }

    private void updateNetwork(Level level) {
        if (level == null || level.isClientSide) return;

        RedstoneLinkNetworkHandler.Frequency fwdFirst  =
                RedstoneLinkNetworkHandler.Frequency.of(frequencyFirst);
        RedstoneLinkNetworkHandler.Frequency fwdSecond =
                RedstoneLinkNetworkHandler.Frequency.of(frequencySecond);
        RedstoneLinkNetworkHandler.Frequency backFirst  =
                RedstoneLinkNetworkHandler.Frequency.of(frequencyBackFirst);
        RedstoneLinkNetworkHandler.Frequency backSecond =
                RedstoneLinkNetworkHandler.Frequency.of(frequencyBackSecond);

        boolean hasFwdFreq  = fwdFirst  != RedstoneLinkNetworkHandler.Frequency.EMPTY
                || fwdSecond != RedstoneLinkNetworkHandler.Frequency.EMPTY;
        boolean hasBackFreq = backFirst  != RedstoneLinkNetworkHandler.Frequency.EMPTY
                || backSecond != RedstoneLinkNetworkHandler.Frequency.EMPTY;

        RedstoneLinkNetworkHandler.Frequency leftFirst  =
                RedstoneLinkNetworkHandler.Frequency.of(frequencyLeftFirst);
        RedstoneLinkNetworkHandler.Frequency leftSecond =
                RedstoneLinkNetworkHandler.Frequency.of(frequencyLeftSecond);
        RedstoneLinkNetworkHandler.Frequency rightFirst  =
                RedstoneLinkNetworkHandler.Frequency.of(frequencyRightFirst);
        RedstoneLinkNetworkHandler.Frequency rightSecond =
                RedstoneLinkNetworkHandler.Frequency.of(frequencyRightSecond);

        boolean hasLeftFreq  = leftFirst  != RedstoneLinkNetworkHandler.Frequency.EMPTY
                || leftSecond != RedstoneLinkNetworkHandler.Frequency.EMPTY;
        boolean hasRightFreq = rightFirst  != RedstoneLinkNetworkHandler.Frequency.EMPTY
                || rightSecond != RedstoneLinkNetworkHandler.Frequency.EMPTY;

        int fwdPower  = (currentPower != 0) ? Math.abs(currentPower) : 0;
        boolean goingBack = currentPower < 0;

        AnalogLinkEntry newLink = null;
        AnalogLinkEntry newBackLink = null;
        AnalogLinkEntry newLeftLink = null;
        AnalogLinkEntry newRightLink = null;

        if (hasFwdFreq && fwdPower > 0) {
            newLink = new AnalogLinkEntry(this, worldPosition,
                    Couple.create(fwdFirst, fwdSecond), fwdPower);
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, newLink);
        }

        if (hasBackFreq && goingBack) {
            newBackLink = new AnalogLinkEntry(this, worldPosition,
                    Couple.create(backFirst, backSecond), 15);
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, newBackLink);
        }

        if (hasLeftFreq && steerInput < 0) {
            newLeftLink = new AnalogLinkEntry(this, worldPosition,
                    Couple.create(leftFirst, leftSecond), 15);
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, newLeftLink);
        }
        if (hasRightFreq && steerInput > 0) {
            newRightLink = new AnalogLinkEntry(this, worldPosition,
                    Couple.create(rightFirst, rightSecond), 15);
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, newRightLink);
        }

        if (linkEntry != null) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, linkEntry);
        }
        if (backLinkEntry != null) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, backLinkEntry);
        }
        if (leftLinkEntry != null) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, leftLinkEntry);
        }
        if (rightLinkEntry != null) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, rightLinkEntry);
        }

        linkEntry      = newLink;
        backLinkEntry  = newBackLink;
        leftLinkEntry  = newLeftLink;
        rightLinkEntry = newRightLink;
    }

    private void updateBlockState(Level level, BlockState state) {
        boolean active  = currentUser != null;
        boolean powered = currentPower != 0;
        int power = Math.abs(currentPower);

        BlockState newState = state
                .setValue(AnalogControllerBlock.POWER,   power)
                .setValue(AnalogControllerBlock.ACTIVE,  active)
                .setValue(AnalogControllerBlock.POWERED, powered);

        if (!newState.equals(state)) {
            level.setBlockAndUpdate(worldPosition, newState);
            level.updateNeighborsAt(worldPosition, newState.getBlock());
        }
    }

    public void onRemoved() {
        if (level == null) return;
        if (currentUser != null && !level.isClientSide) {
            Player player = level.getPlayerByUUID(currentUser);
            if (player instanceof ServerPlayer sp) {
                com.lycoris.loconautics.network.LoconauticsNetwork.sendMount(
                        sp, false, net.minecraft.core.BlockPos.ZERO);
            }
            currentUser = null;
        }
        releaseLinks();
    }

    @Override
    public void releaseLinks() {
        if (level == null) return;
        if (linkEntry != null) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, linkEntry);
            linkEntry = null;
        }
        if (backLinkEntry != null) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, backLinkEntry);
            backLinkEntry = null;
        }
        if (leftLinkEntry != null) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, leftLinkEntry);
            leftLinkEntry = null;
        }
        if (rightLinkEntry != null) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, rightLinkEntry);
            rightLinkEntry = null;
        }
    }

    @Override
    public void invalidate() {
        releaseLinks();
        super.invalidate();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        releaseLinks();
    }

    public void setFrequency(net.minecraft.world.item.ItemStack first,
                             net.minecraft.world.item.ItemStack second) {
        this.frequencyFirst  = first.copy();
        this.frequencySecond = second.copy();
        if (level != null && !level.isClientSide) updateNetwork(level);
        setChanged();
        sendData();
    }

    public void setBackwardFrequency(net.minecraft.world.item.ItemStack first,
                                     net.minecraft.world.item.ItemStack second) {
        this.frequencyBackFirst  = first.copy();
        this.frequencyBackSecond = second.copy();
        if (level != null && !level.isClientSide) updateNetwork(level);
        setChanged();
        sendData();
    }

    public void setLeftFrequency(net.minecraft.world.item.ItemStack first,
                                 net.minecraft.world.item.ItemStack second) {
        this.frequencyLeftFirst  = first.copy();
        this.frequencyLeftSecond = second.copy();
        if (level != null && !level.isClientSide) updateNetwork(level);
        setChanged();
        sendData();
    }

    public void setRightFrequency(net.minecraft.world.item.ItemStack first,
                                  net.minecraft.world.item.ItemStack second) {
        this.frequencyRightFirst  = first.copy();
        this.frequencyRightSecond = second.copy();
        if (level != null && !level.isClientSide) updateNetwork(level);
        setChanged();
        sendData();
    }

    public net.minecraft.world.item.ItemStack getFrequencyFirst()       { return frequencyFirst; }
    public net.minecraft.world.item.ItemStack getFrequencySecond()      { return frequencySecond; }
    public net.minecraft.world.item.ItemStack getFrequencyBackFirst()   { return frequencyBackFirst; }
    public net.minecraft.world.item.ItemStack getFrequencyBackSecond()  { return frequencyBackSecond; }
    public net.minecraft.world.item.ItemStack getFrequencyLeftFirst()   { return frequencyLeftFirst; }
    public net.minecraft.world.item.ItemStack getFrequencyLeftSecond()  { return frequencyLeftSecond; }
    public net.minecraft.world.item.ItemStack getFrequencyRightFirst()  { return frequencyRightFirst; }
    public net.minecraft.world.item.ItemStack getFrequencyRightSecond() { return frequencyRightSecond; }

    public int     getCurrentPower()    { return currentPower; }
    public boolean isLocked()           { return locked; }
    public boolean hasUser()            { return currentUser != null; }
    public int     getMaxPower()        { return maxPower; }

    @Nullable
    public UUID getCurrentUser() { return currentUser; }

    public void onScroll(UUID user, int delta) {
        if (!user.equals(currentUser)) return;
        maxPower = Math.max(0, Math.min(MAX_POWER, maxPower + delta));
        if (driveInput != 0) restartRamp();
        setChanged();
        sendData();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.loconautics.analog_controller");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new AnalogControllerMenu(LoconauticsRegistries.ANALOG_CONTROLLER_MENU.get(), id, inventory, this);
    }

    @Override
    public void sendToMenu(net.minecraft.network.RegistryFriendlyByteBuf buffer) {
        net.minecraft.network.chat.ComponentSerialization.TRUSTED_STREAM_CODEC
                .encode(buffer, Component.translatable(AnalogControllerMenu.FORWARD_KEY));
        net.minecraft.network.chat.ComponentSerialization.TRUSTED_STREAM_CODEC
                .encode(buffer, Component.translatable(AnalogControllerMenu.BACKWARD_KEY));
        net.minecraft.network.chat.ComponentSerialization.TRUSTED_STREAM_CODEC
                .encode(buffer, Component.translatable(AnalogControllerMenu.LEFT_KEY));
        net.minecraft.network.chat.ComponentSerialization.TRUSTED_STREAM_CODEC
                .encode(buffer, Component.translatable(AnalogControllerMenu.RIGHT_KEY));
        super.sendToMenu(buffer);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Power",    currentPower);
        tag.putBoolean("Locked", locked);
        tag.putInt("MaxPower", maxPower);
        if (currentUser != null) tag.putUUID("User", currentUser);
        if (clientPacket) {
            tag.putBoolean("HasUser", currentUser != null);
            tag.putInt("Steer", steerInput);
        }

        writeFrequencies(tag, registries);
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        writeFrequencies(tag, registries);
    }

    private void writeFrequencies(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag freq = new CompoundTag();
        freq.put("First",       frequencyFirst.saveOptional(registries));
        freq.put("Second",      frequencySecond.saveOptional(registries));
        freq.put("BackFirst",   frequencyBackFirst.saveOptional(registries));
        freq.put("BackSecond",  frequencyBackSecond.saveOptional(registries));
        freq.put("LeftFirst",   frequencyLeftFirst.saveOptional(registries));
        freq.put("LeftSecond",  frequencyLeftSecond.saveOptional(registries));
        freq.put("RightFirst",  frequencyRightFirst.saveOptional(registries));
        freq.put("RightSecond", frequencyRightSecond.saveOptional(registries));
        tag.put("Frequency", freq);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        currentPower = tag.getInt("Power");
        locked       = tag.getBoolean("Locked");
        maxPower     = tag.contains("MaxPower") ? tag.getInt("MaxPower") : MAX_POWER;
        if (clientPacket && tag.contains("HasUser")) {
            currentUser = tag.getBoolean("HasUser") ? java.util.UUID.randomUUID() : null;
            steerInput = tag.getInt("Steer");
        } else {
            currentUser = null; // never restore stale user uuid on server
        }

        if (tag.contains("Frequency")) {
            CompoundTag freq = tag.getCompound("Frequency");
            frequencyFirst      = net.minecraft.world.item.ItemStack.parseOptional(registries, freq.getCompound("First"));
            frequencySecond     = net.minecraft.world.item.ItemStack.parseOptional(registries, freq.getCompound("Second"));
            frequencyBackFirst  = net.minecraft.world.item.ItemStack.parseOptional(registries, freq.getCompound("BackFirst"));
            frequencyBackSecond = net.minecraft.world.item.ItemStack.parseOptional(registries, freq.getCompound("BackSecond"));
            frequencyLeftFirst   = net.minecraft.world.item.ItemStack.parseOptional(registries, freq.getCompound("LeftFirst"));
            frequencyLeftSecond  = net.minecraft.world.item.ItemStack.parseOptional(registries, freq.getCompound("LeftSecond"));
            frequencyRightFirst  = net.minecraft.world.item.ItemStack.parseOptional(registries, freq.getCompound("RightFirst"));
            frequencyRightSecond = net.minecraft.world.item.ItemStack.parseOptional(registries, freq.getCompound("RightSecond"));
        }
    }

    public static class AnalogLinkEntry implements IRedstoneLinkable {

        private final AnalogControllerBlockEntity owner;
        private final BlockPos pos;
        private final Couple<RedstoneLinkNetworkHandler.Frequency> key;
        private final int power;

        public AnalogLinkEntry(AnalogControllerBlockEntity owner, BlockPos pos,
                               Couple<RedstoneLinkNetworkHandler.Frequency> key,
                               int power) {
            this.owner = owner;
            this.pos   = pos;
            this.key   = key;
            this.power = power;
        }

        @Override public int     getTransmittedStrength()          { return power; }
        @Override public void    setReceivedStrength(int strength) {}
        @Override public boolean isListening()                     { return false; }
        @Override public boolean isAlive() {
            return !owner.isRemoved() && owner.getLevel() != null && owner.getLevel().getBlockEntity(pos) == owner;
        }
        @Override public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() { return key; }
        @Override public BlockPos getLocation()                    { return pos; }
    }
}
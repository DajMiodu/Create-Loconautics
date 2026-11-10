package com.lycoris.loconautics.content.bearingaxle;

import com.lycoris.loconautics.common.LinkHolder;
import com.lycoris.loconautics.common.RedstoneLinkPair;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.lycoris.loconautics.trainphysics.drive.AxleGearing;
import com.lycoris.loconautics.trainphysics.drive.TrainPropulsion;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BearingAxleBlockEntity extends KineticBlockEntity
        implements IHaveGoggleInformation, MenuProvider, LinkHolder {

    private double totalMassKg;
    private double slopeAngleDeg;

    private boolean opposed;
    private int driveGroup = 1;
    private int groupSize;
    private double ownStrength;
    private double netStrength;
    private double slipFactor = 1.0;
    private long lastClassified = Long.MIN_VALUE;

    private int gear;
    private double gearRatio = 1.0;
    private double maxGearRatio = 1.0;

    private final RedstoneLinkPair links = new RedstoneLinkPair(this,
            s -> setLeftLink(s > 0), s -> setRightLink(s > 0));

    private boolean leftActive;
    private boolean rightActive;

    private boolean leftLink;
    private boolean leftRedstone;
    private boolean rightLink;
    private boolean rightRedstone;


    private boolean drivingBogey;

    private long placedTime;
    private BearingAxleValidity.Status validity = BearingAxleValidity.Status.VALID;
    @Nullable private BlockPos driverBogey;

    public void setPlacedTime(long time) {
        this.placedTime = time;
        setChanged();
    }

    public long getPlacedTime() {
        return placedTime;
    }

    public void setValidity(BearingAxleValidity.Status status, @Nullable BlockPos bogey) {
        this.driverBogey = bogey;
        if (level != null) {
            lastClassified = level.getGameTime();
        }
        if (this.validity == status) {
            return;
        }
        this.validity = status;
        setChanged();
        sendData();
    }

    public BearingAxleValidity.Status getValidity() {
        return validity;
    }

    @Nullable
    public BlockPos driverBogeyPos() {
        return driverBogey;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || (level.getGameTime() % 20L) != 0L) {
            return;
        }
        if (level.getGameTime() - lastClassified < 40L) {
            return;
        }
        BlockPos bogey = BearingAxleValidity.driverBogey(level, worldPosition);
        if (bogey == null) {
            setValidity(BearingAxleValidity.Status.NO_BOGEY, null);
            return;
        }
        setValidity(BogeyFacing.onAxis(level.getBlockState(bogey), BearingAxleValidity.axleFrontLocal(this))
                ? BearingAxleValidity.Status.VALID
                : BearingAxleValidity.Status.WRONG_AXIS, bogey);
    }

    public void setConsistLoad(double massShare, boolean opposed, int group,
                               double ownStrength, double netStrength, double slipFactor, int groupSize,
                               AxleGearing.Gearing gearing) {
        // a few su of drift shouldnt resync the whole network every tick
        boolean stressChanged = massShare != this.totalMassKg || opposed != this.opposed
                || Math.abs(gearing.ratio() - this.gearRatio) > this.gearRatio * 0.005;
        boolean displayChanged = group != this.driveGroup || groupSize != this.groupSize
                || Math.abs(ownStrength - this.ownStrength) > 0.5
                || Math.abs(netStrength - this.netStrength) > 0.5
                || Math.abs(slipFactor - this.slipFactor) > 0.01
                || Math.abs(gearing.maxRatio() - this.maxGearRatio) > this.maxGearRatio * 0.01;
        this.totalMassKg = massShare;
        this.opposed = opposed;
        if (stressChanged) {
            this.gearRatio = gearing.ratio();
        }
        this.maxGearRatio = gearing.maxRatio();
        this.driveGroup = group;
        this.ownStrength = ownStrength;
        this.netStrength = netStrength;
        this.slipFactor = slipFactor;
        this.groupSize = groupSize;
        if (stressChanged) {
            pushStress();
        }
        if (stressChanged || displayChanged) {
            setChanged();
            sendData();
        }
    }

    private void pushStress() {
        float stress = calculateStressApplied();
        if (this.hasNetwork()) {
            this.getOrCreateNetwork().updateStressFor(this, stress);
        }
        this.networkDirty = false;
    }

    public void setGearSignal(int signal) {
        int clamped = Math.max(0, Math.min(AxleGearing.MAX_GEAR, signal));
        if (clamped == gear) {
            return;
        }
        if (level != null && !level.isClientSide) {
        }
        gear = clamped;
        if (gear == 0) {
            resetGearing();
        }
        setChanged();
        sendData();
    }

    public void resetGearing() {
        maxGearRatio = 1.0;
        if (gearRatio == 1.0) {
            return;
        }
        gearRatio = 1.0;
        pushStress();
        setChanged();
        sendData();
    }

    public int gear() {
        return gear;
    }

    public double gearRatio() {
        return gearRatio;
    }

    public double maxGearRatio() {
        return maxGearRatio;
    }

    public double directImpact(double massShare, boolean opposed) {
        if (validity != BearingAxleValidity.Status.VALID) {
            return 0.0;
        }
        double slope = LoconauticsConfig.SLOPE_EFFECTS_ENABLED.get()
                ? LoconauticsConfig.SLOPE_STRESS_FACTOR.get() * slopeAngleDeg
                : 0.0;
        return AxleGearing.stressImpact(LoconauticsConfig.BASE_IMPACT.get(), massShare,
                LoconauticsConfig.MASS_DIVISOR.get(), slope,
                opposed ? LoconauticsConfig.OPPOSED_STRESS_FACTOR.get() : 1.0);
    }

    public double networkCapacity() {
        return capacity;
    }

    public double networkStress() {
        return stress;
    }

    public double ownNetworkStress() {
        return lastStressApplied * Math.abs(getTheoreticalSpeed());
    }

    public double getTrainMass() {
        return totalMassKg;
    }

    public float getNetworkStressAbsolute() {
        return stress;
    }

    public float getNetworkStressRatio() {
        if (capacity <= 0f) return 0f;
        return stress / capacity;
    }

    public void setSlopeAngle(double degrees) {
        double a = Math.max(0.0, degrees);
        // bodies wobble, a tighter band resyncs the kinetic network every tick
        if (Math.abs(a - this.slopeAngleDeg) < 0.25) {
            return;
        }
        this.slopeAngleDeg = a;
        pushStress();
        this.setChanged();
        this.sendData();
    }

    public double getSlopeAngle() {
        return slopeAngleDeg;
    }

    @Override
    public float calculateStressApplied() {
        if (validity != BearingAxleValidity.Status.VALID) {
            this.lastStressApplied = 0f;
            return 0f;
        }
        this.lastStressApplied = (float) (directImpact(totalMassKg, opposed) * gearRatio);
        return this.lastStressApplied;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);

        LoconauticsLang.translate("gui.goggles.bearing_axle_stats").forGoggles(tooltip);

        if (validity != BearingAxleValidity.Status.VALID) {
            LoconauticsLang.translate(switch (validity) {
                        case BOGEY_TAKEN -> "gui.goggles.bearing_axle_duplicate";
                        case WRONG_AXIS -> "gui.goggles.bearing_axle_wrong_axis";
                        default -> "gui.goggles.bearing_axle_no_bogey";
                    })
                    .style(ChatFormatting.RED)
                    .forGoggles(tooltip, 1);
        }

        LoconauticsLang.translate("gui.goggles.load_share")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);
        LoconauticsLang.number(totalMassKg)
                .translate("gui.goggles.unit_kg")
                .style(totalMassKg > 0 ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);

        float impact = calculateStressApplied();
        LoconauticsLang.translate("gui.goggles.stress_impact")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);
        LoconauticsLang.number(impact)
                .translate("gui.goggles.unit_su")
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);

        float stressAbs = getNetworkStressAbsolute();
        LoconauticsLang.translate("gui.goggles.network_stress")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);
        LoconauticsLang.number(stressAbs)
                .translate("gui.goggles.unit_su")
                .style(stressAbs > 0 ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);

        float stressRatio = getNetworkStressRatio();
        LoconauticsLang.translate("gui.goggles.network_stress_ratio")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);
        LoconauticsLang.number(stressRatio * 100f)
                .translate("gui.goggles.unit_percent")
                .style(stressRatio >= 1f ? ChatFormatting.RED
                        : stressRatio >= 0.75f ? ChatFormatting.GOLD
                        : ChatFormatting.GREEN)
                .forGoggles(tooltip, 1);

        LoconauticsLang.translate("gui.goggles.rpm")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);
        LoconauticsLang.number(Math.abs(getSpeed()))
                .translate("gui.goggles.unit_rpm")
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);

        addGearTooltip(tooltip);
        addConsistTooltip(tooltip);
        return true;
    }

    private void addGearTooltip(List<Component> tooltip) {
        if (validity != BearingAxleValidity.Status.VALID) {
            return;
        }
        LoconauticsLang.translate("gui.goggles.axle_gear")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);
        LoconauticsLang.translate("gui.goggles.axle_gear_value", gear, AxleGearing.MAX_GEAR)
                .style(gear == 0 ? ChatFormatting.DARK_GRAY : ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);

        LoconauticsLang.translate("gui.goggles.network_capacity")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);
        LoconauticsLang.number(capacity)
                .translate("gui.goggles.unit_su")
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);

        double baseline = TrainPropulsion.bearingAxleCapBpt() * 20.0;
        double ceiling = TrainPropulsion.speedCeilingMps();
        LoconauticsLang.translate("gui.goggles.geared_top_speed")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);
        LoconauticsLang.number(Math.min(ceiling, baseline * gearRatio))
                .translate("gui.goggles.unit_bps")
                .style(gearRatio > 1.0 ? ChatFormatting.GOLD : ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);

        LoconauticsLang.translate("gui.goggles.axle_target_speed")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);
        double rpm = isOverStressed() ? 0.0 : Math.abs(getSpeed());
        LoconauticsLang.number(Math.min(ceiling, AxleGearing.gearedSpeed(baseline, gearRatio, rpm)))
                .translate("gui.goggles.unit_bps")
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);
    }

    private void addConsistTooltip(List<Component> tooltip) {
        if (validity != BearingAxleValidity.Status.VALID) {
            return;
        }
        LoconauticsLang.translate("gui.goggles.drive_group")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);
        LoconauticsLang.translate(driveGroup < 0
                        ? "gui.goggles.drive_group_reverse"
                        : "gui.goggles.drive_group_forward", groupSize)
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);

        LoconauticsLang.translate("gui.goggles.axle_output")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);
        LoconauticsLang.number(ownStrength)
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);

        LoconauticsLang.translate("gui.goggles.consist_net")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);
        LoconauticsLang.number(netStrength)
                .style(Math.abs(netStrength) < 1.0e-3 ? ChatFormatting.RED : ChatFormatting.GREEN)
                .forGoggles(tooltip, 1);

        if (slipFactor < 1.0) {
            LoconauticsLang.translate("gui.goggles.wheel_slip")
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip);
            LoconauticsLang.number((1.0 - slipFactor) * 100.0)
                    .translate("gui.goggles.unit_percent")
                    .style(slipFactor <= 0.5 ? ChatFormatting.RED : ChatFormatting.GOLD)
                    .forGoggles(tooltip, 1);
        }
        if (opposed) {
            LoconauticsLang.translate("gui.goggles.axle_opposed")
                    .style(ChatFormatting.RED)
                    .forGoggles(tooltip, 1);
        }
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level == null || level.isClientSide) return;
        links.register(level);
    }

    @Override
    public void releaseLinks() {
        if (level != null) links.unregister(level);
    }

    @Override
    public void invalidate() {
        if (level != null) links.unregister(level);
        super.invalidate();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null) links.unregister(level);
    }

    public void setLeftLink(boolean active) {
        if (leftLink == active) return;
        leftLink = active;
        refreshIndicators();
    }

    public void setRightLink(boolean active) {
        if (rightLink == active) return;
        rightLink = active;
        refreshIndicators();
    }

    public void setLeftRedstone(boolean active) {
        if (leftRedstone == active) return;
        leftRedstone = active;
        refreshIndicators();
    }

    public void setRightRedstone(boolean active) {
        if (rightRedstone == active) return;
        rightRedstone = active;
        refreshIndicators();
    }

    public boolean isLeftLink() {
        return leftLink;
    }

    public boolean isRightLink() {
        return rightLink;
    }

    public boolean isLeftRedstone() {
        return leftRedstone;
    }

    public boolean isRightRedstone() {
        return rightRedstone;
    }

    private void refreshIndicators() {
        boolean l = leftLink || leftRedstone;
        boolean r = rightLink || rightRedstone;
        if (l == leftActive && r == rightActive) return;
        leftActive  = l;
        rightActive = r;
        setChanged();
        sendData();
    }

    public void setLeftFrequency(ItemStack first, ItemStack second) {
        links.setFirstFrequency(first, second);
        if (level != null && !level.isClientSide) links.register(level);
        setChanged();
        sendData();
    }

    public void setRightFrequency(ItemStack first, ItemStack second) {
        links.setSecondFrequency(first, second);
        if (level != null && !level.isClientSide) links.register(level);
        setChanged();
        sendData();
    }

    public int getSteerInput() {
        if (leftActive == rightActive) return 0;
        return leftActive ? -1 : 1;
    }

    public ItemStack getLeftFreqFirst()   { return links.getFirstFreqA(); }
    public ItemStack getLeftFreqSecond()  { return links.getFirstFreqB(); }
    public ItemStack getRightFreqFirst()  { return links.getSecondFreqA(); }
    public ItemStack getRightFreqSecond() { return links.getSecondFreqB(); }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.loconautics.bearing_axle");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new BearingAxleMenu(
                LoconauticsRegistries.BEARING_AXLE_MENU.get(), id, inventory, this);
    }

    @Override
    public void sendToMenu(RegistryFriendlyByteBuf buffer) {
        net.minecraft.network.chat.ComponentSerialization.TRUSTED_STREAM_CODEC
                .encode(buffer, Component.translatable(BearingAxleMenu.LEFT_KEY));
        net.minecraft.network.chat.ComponentSerialization.TRUSTED_STREAM_CODEC
                .encode(buffer, Component.translatable(BearingAxleMenu.RIGHT_KEY));
        super.sendToMenu(buffer);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putDouble("TotalMassKg", totalMassKg);
        tag.putDouble("SlopeAngle", slopeAngleDeg);
        tag.putBoolean("Opposed", opposed);
        tag.putInt("DriveGroup", driveGroup);
        tag.putInt("GroupSize", groupSize);
        tag.putDouble("OwnStrength", ownStrength);
        tag.putDouble("NetStrength", netStrength);
        tag.putDouble("SlipFactor", slipFactor);
        tag.putInt("Gear", gear);
        tag.putDouble("GearRatio", gearRatio);
        tag.putDouble("MaxGearRatio", maxGearRatio);

        tag.putBoolean("LeftActive", leftActive);
        tag.putBoolean("RightActive", rightActive);
        CompoundTag freq = new CompoundTag();
        freq.put("LeftFirst",   links.getFirstFreqA().saveOptional(registries));
        freq.put("LeftSecond",  links.getFirstFreqB().saveOptional(registries));
        freq.put("RightFirst",  links.getSecondFreqA().saveOptional(registries));
        freq.put("RightSecond", links.getSecondFreqB().saveOptional(registries));
        tag.put("Frequency", freq);

        tag.putLong("PlacedTime", placedTime);
        tag.putByte("Validity", (byte) validity.ordinal());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        totalMassKg = tag.getDouble("TotalMassKg");
        slopeAngleDeg = tag.getDouble("SlopeAngle");
        opposed = tag.getBoolean("Opposed");
        driveGroup = tag.contains("DriveGroup") ? tag.getInt("DriveGroup") : 1;
        groupSize = tag.getInt("GroupSize");
        ownStrength = tag.getDouble("OwnStrength");
        netStrength = tag.getDouble("NetStrength");
        slipFactor = tag.contains("SlipFactor") ? tag.getDouble("SlipFactor") : 1.0;
        gear = Math.max(0, Math.min(AxleGearing.MAX_GEAR, tag.getInt("Gear")));
        gearRatio = tag.contains("GearRatio") ? Math.max(1.0, tag.getDouble("GearRatio")) : 1.0;
        maxGearRatio = tag.contains("MaxGearRatio") ? Math.max(1.0, tag.getDouble("MaxGearRatio")) : 1.0;

        leftActive  = tag.getBoolean("LeftActive");
        rightActive = tag.getBoolean("RightActive");
        if (tag.contains("Frequency")) {
            CompoundTag freq = tag.getCompound("Frequency");
            links.setFirstFrequency(
                    ItemStack.parseOptional(registries, freq.getCompound("LeftFirst")),
                    ItemStack.parseOptional(registries, freq.getCompound("LeftSecond")));
            links.setSecondFrequency(
                    ItemStack.parseOptional(registries, freq.getCompound("RightFirst")),
                    ItemStack.parseOptional(registries, freq.getCompound("RightSecond")));
        }

        placedTime = tag.getLong("PlacedTime");
        int ordinal = tag.getByte("Validity");
        BearingAxleValidity.Status[] all = BearingAxleValidity.Status.values();
        validity = ordinal >= 0 && ordinal < all.length ? all[ordinal] : BearingAxleValidity.Status.VALID;

        if (clientPacket) {
            refreshBogeyModel();
        }
    }

    private void refreshBogeyModel() {
        boolean driving = validity == BearingAxleValidity.Status.VALID;
        if (level == null || driving == drivingBogey) {
            return;
        }
        drivingBogey = driving;
        BlockPos bogey = BearingAxleValidity.driverBogey(level, worldPosition);
        if (bogey == null) {
            return;
        }
        BlockState state = level.getBlockState(bogey);
        level.setBlocksDirty(bogey, state, state);
    }

    public BearingAxleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

}
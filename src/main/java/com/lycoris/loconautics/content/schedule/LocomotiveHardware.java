package com.lycoris.loconautics.content.schedule;

import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlock;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlockEntity;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleValidity;
import com.lycoris.loconautics.content.transmission.TransmissionBlockEntity;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;

public final class LocomotiveHardware {

    private LocomotiveHardware() {
    }

    public record Result(boolean ok, String messageKey) {
        static Result pass() {
            return new Result(true, null);
        }

        static Result fail(String key) {
            return new Result(false, key);
        }
    }

    public enum Link {
        OK, NOT_TOUCHING, WRONG_SIDE, WRONG_AXIS
    }

    public record Drivetrain(String problem, TransmissionBlockEntity transmission, BearingAxleBlockEntity axle) {
        public boolean ok() {
            return problem == null;
        }
    }

    public static Link link(TransmissionBlockEntity transmission, BearingAxleBlockEntity axle) {
        BlockState axleState = axle.getBlockState();
        if (!(axleState.getBlock() instanceof BearingAxleBlock)) {
            return Link.NOT_TOUCHING;
        }
        BlockPos shaftPos = axle.getBlockPos().relative(BearingAxleBlock.shaftFace(axleState));
        if (!transmission.getBlockPos().equals(shaftPos)) {
            return transmission.getBlockPos().distManhattan(axle.getBlockPos()) == 1
                    ? Link.WRONG_SIDE : Link.NOT_TOUCHING;
        }
        boolean aligned = transmission.getBlockState().getValue(BlockStateProperties.AXIS)
                == BearingAxleBlock.front(axleState).getAxis();
        return aligned ? Link.OK : Link.WRONG_AXIS;
    }

    public static String linkKey(Link link) {
        return switch (link) {
            case OK -> null;
            case NOT_TOUCHING -> "transmission_not_touching";
            case WRONG_SIDE -> "transmission_wrong_side";
            case WRONG_AXIS -> "transmission_wrong_axis";
        };
    }

    public static Drivetrain drivetrain(ServerSubLevel sub) {
        List<TransmissionBlockEntity> transmissions = SubLevelBlocks.findAll(sub, TransmissionBlockEntity.class);
        List<BearingAxleBlockEntity> axles = SubLevelBlocks.findAll(sub, BearingAxleBlockEntity.class);
        TransmissionBlockEntity transmission = transmissions.size() == 1 ? transmissions.get(0) : null;
        if (transmissions.isEmpty()) {
            return new Drivetrain("no_transmission", null, null);
        }
        if (transmissions.size() > 1) {
            return new Drivetrain("many_transmissions", null, null);
        }
        if (axles.isEmpty()) {
            return new Drivetrain("no_bearing_axle", transmission, null);
        }
        List<BearingAxleBlockEntity> valid = BearingAxleValidity.valid(sub);
        if (valid.isEmpty()) {
            BearingAxleBlockEntity first = axles.get(0);
            String key = first.getValidity() == BearingAxleValidity.Status.WRONG_AXIS
                    ? "bearing_axle_wrong_axis" : "bearing_axle_no_bogey";
            return new Drivetrain(key, transmission, first);
        }
        for (BearingAxleBlockEntity axle : valid) {
            if (link(transmission, axle) == Link.OK) {
                return new Drivetrain(null, transmission, axle);
            }
        }
        BearingAxleBlockEntity first = valid.get(0);
        return new Drivetrain(linkKey(link(transmission, first)), transmission, first);
    }

    public static Result validate(ServerSubLevel sub, AnalogControllerBlockEntity controller) {
        java.util.List<TransmissionBlockEntity> transmissions =
                SubLevelBlocks.findAll(sub, TransmissionBlockEntity.class);
        if (transmissions.isEmpty()) {
            return Result.fail("no_transmission");
        }
        if (transmissions.size() > 1) {
            return Result.fail("many_transmissions");
        }
        java.util.List<BearingAxleBlockEntity> axles = SubLevelBlocks.findAll(sub, BearingAxleBlockEntity.class);
        if (axles.isEmpty()) {
            return Result.fail("no_bearing_axle");
        }
        BearingAxleBlockEntity axle = BearingAxleValidity.resolve(sub.getLevel(), axles);
        if (axle == null) {
            return Result.fail("invalid_bearing_axle");
        }
        return validate(controller, transmissions.get(0), axle);
    }

    public static boolean hasTransmissionFor(ServerSubLevel sub, AnalogControllerBlockEntity controller) {
        for (TransmissionBlockEntity transmission : SubLevelBlocks.findAll(sub, TransmissionBlockEntity.class)) {
            if (pairMatches(controller.getFrequencyFirst(), controller.getFrequencySecond(),
                    transmission.getSpeedFreqFirst(), transmission.getSpeedFreqSecond())
                    && pairMatches(controller.getFrequencyBackFirst(), controller.getFrequencyBackSecond(),
                    transmission.getDirFreqFirst(), transmission.getDirFreqSecond())) {
                return true;
            }
        }
        return false;
    }

    public static Result validate(AnalogControllerBlockEntity controller, TransmissionBlockEntity transmission,
                                  BearingAxleBlockEntity axle) {
        if (!pairMatches(controller.getFrequencyFirst(), controller.getFrequencySecond(),
                transmission.getSpeedFreqFirst(), transmission.getSpeedFreqSecond())
                || !pairMatches(controller.getFrequencyBackFirst(), controller.getFrequencyBackSecond(),
                transmission.getDirFreqFirst(), transmission.getDirFreqSecond())) {
            return Result.fail("transmission_mismatch");
        }

        if (!pairMatches(controller.getFrequencyLeftFirst(), controller.getFrequencyLeftSecond(),
                axle.getLeftFreqFirst(), axle.getLeftFreqSecond())
                || !pairMatches(controller.getFrequencyRightFirst(), controller.getFrequencyRightSecond(),
                axle.getRightFreqFirst(), axle.getRightFreqSecond())) {
            return Result.fail("bearing_axle_mismatch");
        }

        return Result.pass();
    }

    private static boolean pairMatches(ItemStack a1, ItemStack a2, ItemStack b1, ItemStack b2) {
        Frequency fa1 = Frequency.of(a1);
        Frequency fa2 = Frequency.of(a2);
        boolean bound = fa1 != Frequency.EMPTY || fa2 != Frequency.EMPTY;
        return bound && fa1.equals(Frequency.of(b1)) && fa2.equals(Frequency.of(b2));
    }
}

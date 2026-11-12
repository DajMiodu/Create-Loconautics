package com.lycoris.loconautics.content.analogcontroller;

import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlockEntity;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleValidity;
import com.lycoris.loconautics.content.transmission.TransmissionBlockEntity;
import com.lycoris.loconautics.trainphysics.actors.TrainActorControls;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class AnalogControllerAutofill {

    private static final String KEY = "gui.loconautics.analog_controller.autofill";

    private static final int FORWARD_SLOT = 0;
    private static final int BACKWARD_SLOT = 2;
    private static final int LEFT_SLOT = 4;
    private static final int RIGHT_SLOT = 6;

    private AnalogControllerAutofill() {
    }

    public static void run(ServerPlayer player, AnalogControllerBlockEntity controller, ItemStackHandler ghost) {
        BlockPos pos = controller.getBlockPos();
        if (!(controller.getLevel() instanceof ServerLevel level) || !TrainActorControls.onTrain(level, pos)
                || !(Sable.HELPER.getContaining(level, pos) instanceof ServerSubLevel sub)) {
            tell(player, Component.translatable(KEY + ".not_train"));
            return;
        }
        Optional<BearingAxleBlockEntity> axle = firstWithFrequency(BearingAxleValidity.valid(sub), axleFrequencies());
        Optional<TransmissionBlockEntity> transmission = firstWithFrequency(
                SubLevelBlocks.findAll(sub, TransmissionBlockEntity.class), transmissionFrequencies());

        int rows = 0;
        if (transmission.isPresent()) {
            TransmissionBlockEntity t = transmission.get();
            rows += fill(ghost, FORWARD_SLOT, t.getSpeedFreqFirst(), t.getSpeedFreqSecond());
            rows += fill(ghost, BACKWARD_SLOT, t.getDirFreqFirst(), t.getDirFreqSecond());
        }
        if (axle.isPresent()) {
            BearingAxleBlockEntity a = axle.get();
            rows += fill(ghost, LEFT_SLOT, a.getLeftFreqFirst(), a.getLeftFreqSecond());
            rows += fill(ghost, RIGHT_SLOT, a.getRightFreqFirst(), a.getRightFreqSecond());
        }
        tell(player, rows == 0 ? Component.translatable(KEY + ".none")
                : Component.translatable(KEY + ".done", rows));
    }

    private static <T extends BlockEntity> Optional<T> firstWithFrequency(List<T> found,
                                                                          Predicate<T> carries) {
        return found.stream()
                .sorted(Comparator.comparingLong((T be) -> be.getBlockPos().asLong()))
                .filter(carries)
                .findFirst();
    }

    private static Predicate<BearingAxleBlockEntity> axleFrequencies() {
        return a -> !a.getLeftFreqFirst().isEmpty() || !a.getLeftFreqSecond().isEmpty()
                || !a.getRightFreqFirst().isEmpty() || !a.getRightFreqSecond().isEmpty();
    }

    private static Predicate<TransmissionBlockEntity> transmissionFrequencies() {
        return t -> !t.getSpeedFreqFirst().isEmpty() || !t.getSpeedFreqSecond().isEmpty()
                || !t.getDirFreqFirst().isEmpty() || !t.getDirFreqSecond().isEmpty();
    }

    private static int fill(ItemStackHandler ghost, int slot, ItemStack first, ItemStack second) {
        if (first.isEmpty() && second.isEmpty()) {
            return 0;
        }
        ghost.setStackInSlot(slot, first.copy());
        ghost.setStackInSlot(slot + 1, second.copy());
        return 1;
    }

    private static void tell(Player player, Component message) {
        player.displayClientMessage(message, true);
    }
}

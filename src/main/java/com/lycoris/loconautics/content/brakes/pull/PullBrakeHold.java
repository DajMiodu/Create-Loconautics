package com.lycoris.loconautics.content.brakes.pull;

import com.lycoris.loconautics.network.PacketTargets;
import com.lycoris.loconautics.network.packets.pullbrake.PullBrakeSetPacket;
import dev.simulated_team.simulated.index.SimClickInteractions;
import dev.simulated_team.simulated.util.hold_interaction.BlockHoldInteraction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PullBrakeHold extends BlockHoldInteraction {

    private static final PullBrakeHold INSTANCE = new PullBrakeHold();

    private static final float FULL_STROKE_DEGREES = 90f;

    private float value;

    private int sentPower;

    public static PullBrakeHold registered() {
        return SimClickInteractions.register(INSTANCE);
    }

    public static boolean grab(Level level, Player player, BlockPos pos, int power) {
        if (INSTANCE.isActive()) return false;
        INSTANCE.sentPower = power;
        INSTANCE.value = power / (float) PullBrakeBlockEntity.MAX_POWER;
        INSTANCE.startHold(level, player, pos);
        return true;
    }

    @Override
    public boolean activeTick(Level level, LocalPlayer player) {
        return getInteractionPos() == null || !inInteractionRange(player, getInteractionPos().getCenter(), 0.0);
    }

    @Override
    public boolean activeOnMouseMove(double yaw, double pitch) {
        value = Mth.clamp(value + (float) (pitch / FULL_STROKE_DEGREES), 0f, 1f);
        int power = Math.round(value * PullBrakeBlockEntity.MAX_POWER);
        if (power == sentPower) return true;
        sentPower = power;
        Level level = Minecraft.getInstance().level;
        PullBrakeBlockEntity brake = PacketTargets.blockEntity(level, getInteractionPos(), PullBrakeBlockEntity.class);
        if (brake != null) brake.setPower(power);
        PacketDistributor.sendToServer(new PullBrakeSetPacket(getInteractionPos(), power));
        return true;
    }
}

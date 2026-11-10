package com.lycoris.loconautics.content.analogcontroller;

import com.lycoris.loconautics.network.packets.train.TrainHornPacket;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.simibubi.create.content.decoration.steamWhistle.WhistleBlock;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class TrainHorn {

    private TrainHorn() {
    }

    @Nullable
    public static BlockPos nearestWhistle(ServerLevel level, BlockPos controllerPos) {
        if (!(Sable.HELPER.getContaining(level, controllerPos) instanceof ServerSubLevel own)) {
            return null;
        }
        Vec3 driver = own.logicalPose().transformPosition(Vec3.atCenterOf(controllerPos));
        BlockPos[] best = {null};
        double[] bestDistance = {Double.MAX_VALUE};
        for (UUID member : TrainConsist.connectedSubLevels(level, own.getUniqueId())) {
            ServerSubLevel sub = SubLevelData.resolve(level, member);
            SubLevelBlocks.forEachBlock(sub, (pos, state) -> {
                if (!(state.getBlock() instanceof WhistleBlock)) {
                    return true;
                }
                double distance = sub.logicalPose().transformPosition(Vec3.atCenterOf(pos)).distanceToSqr(driver);
                if (distance < bestDistance[0]) {
                    bestDistance[0] = distance;
                    best[0] = pos.immutable();
                }
                return true;
            });
        }
        return best[0];
    }

    public static void sound(ServerPlayer player, BlockPos whistle, boolean honk) {
        if (honk) {
            AllAdvancements.TRAIN_WHISTLE.awardTo(player);
            player.level().gameEvent(player, GameEvent.RESONATE_15, player.position());
        }
        blow(player.serverLevel(), whistle, honk);
    }

    public static void blow(ServerLevel level, BlockPos whistle, boolean honk) {
        PacketDistributor.sendToPlayersInDimension(level, new TrainHornPacket(whistle, honk));
    }
}

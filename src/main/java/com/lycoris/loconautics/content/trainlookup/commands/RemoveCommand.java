package com.lycoris.loconautics.content.trainlookup.commands;

import com.lycoris.loconautics.content.trainlookup.TrainLookup;
import com.lycoris.loconautics.content.trainlookup.history.HistoryEntry;
import com.lycoris.loconautics.content.trainlookup.history.HistoryEvent;
import com.lycoris.loconautics.content.trainlookup.history.TrainHistory;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.lycoris.loconautics.trainphysics.state.SableTrainPersistence;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public final class RemoveCommand {

    private RemoveCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("remove")
                .then(Commands.argument("train", StringArgumentType.greedyString())
                        .suggests(TrainArguments::suggestTrain)
                        .executes(RemoveCommand::run));
    }

    private static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        String raw = StringArgumentType.getString(context, "train");
        UUID trainId = TrainArguments.parseTrain(source, raw);
        if (trainId == null) {
            source.sendFailure(Component.literal("no train matches " + raw));
            return 0;
        }
        TrainLookup.TrainInfo info = TrainLookup.getTrain(level, trainId);
        if (info == null) {
            source.sendFailure(Component.literal("that train is not in the registry"));
            return 0;
        }
        Vec3 pos = TrainLookup.getDriverPosition(level, trainId);

        SableTrain live = SableTrainRegistry.get(trainId);
        int removed = 0;
        if (live == null) {
            SableTrainPersistence.unpersist(source.getServer(), trainId);
        } else {
            Set<UUID> consist = TrainConsist.connectedSubLevels(live.level(), live.car().subLevelId());
            for (SableTrain train : new ArrayList<>(SableTrainRegistry.all())) {
                if (train.car().subLevelId() != null && consist.contains(train.car().subLevelId())) {
                    SableTrainSpawner.remove(train);
                    removed++;
                }
            }
        }

        UUID actor = source.getEntity() == null ? new UUID(0L, 0L) : source.getEntity().getUUID();
        TrainHistory.get(source.getServer()).record(new HistoryEntry(
                System.currentTimeMillis(), HistoryEvent.REMOVE, trainId, info.name(), actor,
                source.getTextName(), info.dimension(), pos, HistoryEntry.NO_CARRIAGE, null,
                live == null ? "saved record only" : removed + " carriage(s) removed"));

        int count = removed;
        source.sendSuccess(() -> Component.literal(live == null
                ? "dropped the saved record of " + info.displayName()
                : "removed " + info.displayName() + " (" + count + " carriage(s))"), true);
        return 1;
    }
}

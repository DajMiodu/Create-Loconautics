package com.lycoris.loconautics.content.trainlookup.commands;

import com.lycoris.loconautics.common.ConnectorDetachers;
import com.lycoris.loconautics.content.relocator.RelocatorItemData;
import com.lycoris.loconautics.content.trainlookup.TrainLookup;
import com.lycoris.loconautics.content.trainlookup.TrainRetrieval;
import com.lycoris.loconautics.content.trainlookup.history.HistoryEntry;
import com.lycoris.loconautics.content.trainlookup.history.HistoryEvent;
import com.lycoris.loconautics.content.trainlookup.history.TrainHistory;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class RetrieveCommand {

    private RetrieveCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("retrieve")
                .then(Commands.argument("train", StringArgumentType.string())
                        .suggests(TrainArguments::suggestQuotedTrain)
                        .executes(context -> run(context, null))
                        .then(Commands.argument("carriage", StringArgumentType.string())
                                .suggests((context, builder) -> TrainArguments.suggestCarriage(context, builder,
                                        StringArgumentType.getString(context, "train")))
                                .executes(context -> run(context,
                                        StringArgumentType.getString(context, "carriage")))));
    }

    private static int run(CommandContext<CommandSourceStack> context, String carriageLabel) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("only a player can be handed a relocator"));
            return 0;
        }
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

        if (carriageLabel != null && trainId.equals(TrainArguments.parseTrain(level, carriageLabel))) {
            carriageLabel = null;
        }
        TrainLookup.CarriageInfo carriage = carriageLabel == null
                ? null
                : TrainArguments.carriageByLabel(level, trainId, carriageLabel);
        if (carriageLabel != null && carriage == null) {
            source.sendFailure(Component.literal("that train has no carriage called " + carriageLabel));
            return 0;
        }

        if (carriage != null) {
            source.sendSuccess(() -> Component.literal("Retrieving carriage " + carriage.label()
                            + " will split this train. Remaining carriages stay in place.")
                    .withStyle(ChatFormatting.YELLOW), false);
            ConnectorDetachers.detachAll(level, carriage.subLevelId());
        }

        int index = carriage == null ? -1 : carriage.index();
        TrainRetrieval.Result result = TrainRetrieval.validateAndRetrieve(level, trainId, index);
        if (!result.success()) {
            source.sendFailure(Component.literal(result.failure()));
            return 0;
        }

        double span = com.lycoris.loconautics.trainphysics.spawn.TrainStructures.consistLength(level,
                result.structures());
        ItemStack relocator = RelocatorItemData.forStored(trainId, info.name(), result.structures(), span);
        if (!player.getInventory().add(relocator)) {
            player.drop(relocator, false);
        }

        Vec3 pos = TrainLookup.getDriverPosition(level, trainId);
        TrainHistory.get(player.server).record(new HistoryEntry(
                System.currentTimeMillis(),
                carriage == null ? HistoryEvent.RETRIEVE_FULL : HistoryEvent.RETRIEVE_CARRIAGE,
                trainId, info.name(), player.getUUID(), player.getGameProfile().getName(),
                info.dimension(), pos, index, carriage == null ? null : carriage.label(),
                result.ridersEjected() > 0 ? result.ridersEjected() + " rider(s) ejected" : null));

        source.sendSuccess(() -> Component.literal("retrieved " + info.displayName()
                        + (carriage == null ? " (" + result.retrieved().size() + " carriage(s))"
                        : " carriage " + carriage.label()))
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}

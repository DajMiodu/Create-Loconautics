package com.lycoris.loconautics.content.trainlookup.commands;

import com.lycoris.loconautics.content.trainlookup.TrainLookup;
import com.lycoris.loconautics.content.trainlookup.history.HistoryEntry;
import com.lycoris.loconautics.content.trainlookup.history.HistoryEvent;
import com.lycoris.loconautics.content.trainlookup.history.TrainHistory;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class FindCommand {

    private static final int START_LIFT = 5;
    private static final int MAX_WALK = 20;
    private static final int GIVE_UP_LIFT = 25;

    private FindCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("find")
                .then(Commands.argument("train", StringArgumentType.greedyString())
                        .suggests(TrainArguments::suggestTrain)
                        .executes(FindCommand::run));
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
        source.sendSuccess(() -> header(info), false);
        source.sendSuccess(() -> Component.literal("Location: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(LookupChat.dimensionName(info.dimension()) + ", "
                                + (int) Math.floor(pos.x) + " " + (int) Math.floor(pos.y)
                                + " " + (int) Math.floor(pos.z))
                        .withStyle(ChatFormatting.WHITE)), false);
        if (!info.loaded()) {
            source.sendSuccess(() -> Component.literal("Not loaded, this is where it was parked")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        }
        source.sendSuccess(LookupChat::divider, false);
        source.sendSuccess(() -> actions(trainId), false);

        if (source.getEntity() instanceof ServerPlayer player) {
            TrainHistory.get(player.server).record(new HistoryEntry(
                    System.currentTimeMillis(), HistoryEvent.FIND_VIEW, trainId, info.name(),
                    player.getUUID(), player.getGameProfile().getName(), info.dimension(), pos,
                    HistoryEntry.NO_CARRIAGE, null, null));
        }
        return 1;
    }

    private static Component header(TrainLookup.TrainInfo info) {
        return Component.literal("[")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(info.displayName()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(info.shortId()).withStyle(ChatFormatting.GRAY))
                .append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component actions(UUID trainId) {
        return button("[Teleport]", ChatFormatting.GREEN, "/loconautics teleport " + trainId)
                .append(Component.literal("  "))
                .append(button("[Retrieve]", ChatFormatting.GOLD, "/loconautics retrieve " + trainId));
    }

    private static MutableComponent button(String label, ChatFormatting color, String command) {
        return Component.literal(label).withStyle(style -> style
                .withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }

    public static int teleport(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        String raw = StringArgumentType.getString(context, "train");
        UUID trainId = TrainArguments.parseTrain(source, raw);
        if (trainId == null) {
            source.sendFailure(Component.literal("no train matches " + raw));
            return 0;
        }
        TrainLookup.TrainInfo info = TrainLookup.getTrain(level, trainId);
        if (info == null || !(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("that train is not in the registry"));
            return 0;
        }
        ServerLevel trainLevel = player.server.getLevel(info.dimension());
        if (trainLevel == null) {
            source.sendFailure(Component.literal(info.dimension().location() + " is not loaded"));
            return 0;
        }
        Vec3 target = findSafeSpot(trainLevel,
                TrainLookup.getDriverPosition(level, trainId));
        player.teleportTo(trainLevel, target.x, target.y, target.z, player.getYRot(), player.getXRot());
        source.sendSuccess(() -> Component.literal("teleported to " + info.displayName())
                .withStyle(ChatFormatting.GREEN), false);

        TrainHistory.get(player.server).record(new HistoryEntry(
                System.currentTimeMillis(), HistoryEvent.TELEPORT, trainId, info.name(),
                player.getUUID(), player.getGameProfile().getName(), info.dimension(), target,
                HistoryEntry.NO_CARRIAGE, null, null));
        return 1;
    }

    private static Vec3 findSafeSpot(ServerLevel level, Vec3 target) {
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        int baseX = floor(target.x);
        int baseZ = floor(target.z);
        int startY = (int) Math.floor(target.y) + START_LIFT;

        for (int offset = 0; offset <= MAX_WALK; offset++) {
            int y = startY + offset;
            if (y + 1 > level.getMaxBuildHeight()) {
                break;
            }
            boolean feet = level.getBlockState(probe.set(baseX, y, baseZ)).isAir();
            boolean head = level.getBlockState(probe.set(baseX, y + 1, baseZ)).isAir();
            if (feet && head) {
                return new Vec3(baseX + 0.5, y, baseZ + 0.5);
            }
        }
        return new Vec3(baseX + 0.5, Math.floor(target.y) + GIVE_UP_LIFT, baseZ + 0.5);
    }

    private static int floor(double coordinate) {
        return (int) Math.floor(coordinate);
    }
}

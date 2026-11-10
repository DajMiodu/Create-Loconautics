package com.lycoris.loconautics.content.trainlookup.commands;

import com.lycoris.loconautics.content.trainlookup.history.HistoryEntry;
import com.lycoris.loconautics.content.trainlookup.history.TrainHistory;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class HistoryCommand {

    private static final int PAGE_SIZE = 20;
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private HistoryCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("history")
                .then(Commands.literal("train")
                        .then(Commands.argument("train", StringArgumentType.string())
                                .suggests(TrainArguments::suggestTrain)
                                .executes(context -> byTrain(context, 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(context -> byTrain(context,
                                                IntegerArgumentType.getInteger(context, "page"))))))
                .then(Commands.literal("player")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(context -> byPlayer(context, 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(context -> byPlayer(context,
                                                IntegerArgumentType.getInteger(context, "page"))))));
    }

    private static int byTrain(CommandContext<CommandSourceStack> context, int page) {
        CommandSourceStack source = context.getSource();
        String raw = StringArgumentType.getString(context, "train");
        UUID trainId = TrainArguments.parseTrain(source, raw);
        if (trainId == null) {
            source.sendFailure(Component.literal("no train matches " + raw));
            return 0;
        }
        return print(source, TrainHistory.get(source.getServer()).forTrain(trainId), page,
                "/loconautics history train " + trainId, "train " + trainId.toString().substring(0, 8));
    }

    private static int byPlayer(CommandContext<CommandSourceStack> context, int page) throws
            com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Collection<com.mojang.authlib.GameProfile> profiles =
                GameProfileArgument.getGameProfiles(context, "player");
        if (profiles.isEmpty()) {
            source.sendFailure(Component.literal("no such player"));
            return 0;
        }
        com.mojang.authlib.GameProfile profile = profiles.iterator().next();
        return print(source, TrainHistory.get(source.getServer()).forPlayer(profile.getId()), page,
                "/loconautics history player " + profile.getName(), "player " + profile.getName());
    }

    private static int print(CommandSourceStack source, List<HistoryEntry> all, int page,
                             String pageCommand, String subject) {
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.literal("no history entries for " + subject)
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        List<HistoryEntry> newestFirst = new ArrayList<>(all);
        java.util.Collections.reverse(newestFirst);

        int pages = (newestFirst.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int clamped = Math.min(Math.max(page, 1), pages);
        int from = (clamped - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, newestFirst.size());

        source.sendSuccess(() -> Component.literal("History for " + subject + "  page " + clamped + "/" + pages)
                .withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(LookupChat::divider, false);
        for (HistoryEntry entry : newestFirst.subList(from, to)) {
            source.sendSuccess(() -> line(entry), false);
        }
        source.sendSuccess(() -> footer(clamped, pages, pageCommand), false);
        return 1;
    }

    private static Component line(HistoryEntry entry) {
        String name = entry.trainName() != null && !entry.trainName().isBlank()
                ? entry.trainName()
                : entry.trainUUID().toString().substring(0, 8);
        String where = LookupChat.dimensionName(entry.dimension()) + " "
                + (int) Math.floor(entry.position().x) + " "
                + (int) Math.floor(entry.position().y) + " "
                + (int) Math.floor(entry.position().z);
        MutableComponent line = Component.literal("[" + STAMP.format(Instant.ofEpochMilli(entry.timestamp())) + "] ")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(entry.event().name()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(name).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(entry.actorName()).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(where).withStyle(ChatFormatting.DARK_AQUA));
        if (entry.carriageLabel() != null) {
            line.append(Component.literal(" (" + entry.carriageLabel() + ")").withStyle(ChatFormatting.YELLOW));
        }
        if (entry.notes() != null) {
            line.append(Component.literal(" " + entry.notes()).withStyle(ChatFormatting.DARK_GRAY));
        }
        return line;
    }

    private static Component footer(int page, int pages, String pageCommand) {
        MutableComponent footer = Component.empty();
        footer.append(page > 1
                ? pageLink("[Prev]", pageCommand + " " + (page - 1))
                : Component.literal("[Prev]").withStyle(ChatFormatting.DARK_GRAY));
        footer.append(Component.literal("  "));
        footer.append(page < pages
                ? pageLink("[Next]", pageCommand + " " + (page + 1))
                : Component.literal("[Next]").withStyle(ChatFormatting.DARK_GRAY));
        return footer;
    }

    private static MutableComponent pageLink(String label, String command) {
        return Component.literal(label).withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }
}

package com.lycoris.loconautics.content.trainlookup.commands;

import com.lycoris.loconautics.content.trainlookup.TrainLookup;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.command.SubLevelArgumentType;
import dev.ryanhcode.sable.command.argument.SubLevelSelectorType;
import dev.ryanhcode.sable.command.argument.SubLevelSuggestionProvider;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class TrainArguments {

    private TrainArguments() {
    }

    private static final List<String[]> MINECRAFT_SELECTORS = List.of(
            new String[]{"@s", "yourself"},
            new String[]{"@p", "nearest player"},
            new String[]{"@a", "all players"});

    public static CompletableFuture<Suggestions> suggestTrain(CommandContext<CommandSourceStack> context,
                                                              SuggestionsBuilder builder) {
        return suggest(context, builder, false);
    }

    // retrieve reads the train as one word, so everything has to be quoted for the  to get through
    public static CompletableFuture<Suggestions> suggestQuotedTrain(CommandContext<CommandSourceStack> context,
                                                                    SuggestionsBuilder builder) {
        return suggest(context, builder, true);
    }

    private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> context,
                                                          SuggestionsBuilder builder, boolean quoteAll) {
        ServerLevel level = context.getSource().getLevel();
        List<TrainLookup.TrainInfo> named = new ArrayList<>();
        List<TrainLookup.TrainInfo> unnamed = new ArrayList<>();
        for (TrainLookup.TrainInfo train : TrainLookup.getAllTrains(level)) {
            if (train.name() != null && !train.name().isBlank()) {
                named.add(train);
            } else {
                unnamed.add(train);
            }
        }
        named.sort(Comparator.comparing(t -> t.name().toLowerCase()));
        unnamed.sort(Comparator.comparing(t -> t.trainId().toString()));

        String remaining = builder.getRemaining().toLowerCase();
        UUID looked = lookedAtTrain(context.getSource());
        for (TrainLookup.TrainInfo train : named) {
            // one argument, never a name and a uuid side by side, brigadier would read the uuid as the next argument
            boolean unique = named.stream().filter(t -> t.name().equalsIgnoreCase(train.name())).count() == 1;
            String text = unique ? train.name() : train.trainId().toString();
            Component tooltip = Component.literal((train.loaded() ? "loaded" : "parked") + ", " + train.name());
            if (train.trainId().equals(looked)) {
                // a leading quote sorts before every letter and digit, so the viewed train is the first tab
                offer(builder, remaining, "\"" + text.replace("\"", "\\\"") + "\"",
                        Component.literal("the train you are looking at"));
            } else {
                offer(builder, remaining, quote(text, quoteAll), tooltip);
            }
        }
        for (TrainLookup.TrainInfo train : unnamed) {
            String id = train.trainId().toString();
            if (train.trainId().equals(looked)) {
                offer(builder, remaining, "\"" + id + "\"", Component.literal("the train you are looking at"));
            } else {
                offer(builder, remaining, quoteAll ? "\"" + id + "\"" : id,
                        Component.literal(train.loaded() ? "loaded" : "parked"));
            }
        }
        for (String[] selector : MINECRAFT_SELECTORS) {
            offer(builder, remaining, quoteAll ? "\"" + selector[0] + "\"" : selector[0],
                    Component.literal(selector[1] + ", the train it is on"));
        }
        for (SubLevelSelectorType type : SubLevelSelectorType.values()) {
            offer(builder, remaining, quoteAll ? "\"@" + type.getChar() + "\"" : "@" + type.getChar(),
                    type.getTooltip());
        }
        return builder.buildFuture();
    }

    @Nullable
    private static UUID lookedAtTrain(CommandSourceStack source) {
        if (!(source instanceof SubLevelSuggestionProvider provider)) {
            return null;
        }
        SubLevel viewed = provider.getSelectedSubLevel();
        SableTrain train = viewed == null ? null : SableTrainRegistry.bySubLevel(viewed.getUniqueId());
        return train == null ? null : train.id();
    }

    @Nullable
    public static UUID parseTrain(CommandSourceStack source, String raw) {
        if (raw == null) {
            return null;
        }
        String input = unquote(raw.trim());
        if (!input.startsWith("@")) {
            return parseTrain(source.getLevel(), raw);
        }
        try {
            if (input.length() > 1 && "spa".indexOf(input.charAt(1)) >= 0) {
                for (Entity entity : EntityArgument.entities().parse(new StringReader(input)).findEntities(source)) {
                    SubLevel on = Sable.HELPER.getTrackingOrVehicleSubLevel(entity);
                    SableTrain train = on == null ? null : SableTrainRegistry.bySubLevel(on.getUniqueId());
                    if (train != null) {
                        return train.id();
                    }
                }
                return null;
            }
            for (ServerSubLevel sub : SubLevelArgumentType.subLevels().parse(new StringReader(input))
                    .getSubLevels(source)) {
                SableTrain train = SableTrainRegistry.bySubLevel(sub.getUniqueId());
                if (train != null) {
                    return train.id();
                }
            }
        } catch (CommandSyntaxException badSelector) {
            return null;
        }
        return null;
    }

    public static CompletableFuture<Suggestions> suggestCarriage(CommandContext<CommandSourceStack> context,
                                                                 SuggestionsBuilder builder, String trainArgument) {
        ServerLevel level = context.getSource().getLevel();
        UUID trainId = parseTrain(context.getSource(), trainArgument);
        if (trainId == null) {
            return builder.buildFuture();
        }
        String remaining = builder.getRemaining().toLowerCase();
        for (TrainLookup.CarriageInfo carriage : TrainLookup.getCarriages(level, trainId)) {
            if (carriage.label().toLowerCase().startsWith(remaining)) {
                builder.suggest(carriage.label(), Component.literal("carriage " + carriage.index()));
            }
        }
        return builder.buildFuture();
    }

    private static void offer(SuggestionsBuilder builder, String remaining, String text, Component tooltip) {
        if (text.toLowerCase().startsWith(remaining)) {
            builder.suggest(text, tooltip);
        }
    }

    private static String quote(String name, boolean force) {
        return force || name.indexOf(' ') >= 0 ? "\"" + name.replace("\"", "\\\"") + "\"" : name;
    }

    @Nullable
    public static UUID parseTrain(ServerLevel level, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String input = unquote(raw.trim());
        int lastSpace = input.lastIndexOf(' ');
        if (lastSpace >= 0) {
            UUID tail = tryUuid(input.substring(lastSpace + 1));
            if (tail != null) {
                return tail;
            }
        }
        UUID direct = tryUuid(input);
        if (direct != null) {
            return direct;
        }
        UUID match = null;
        for (TrainLookup.TrainInfo train : TrainLookup.getAllTrains(level)) {
            if (train.name() != null && train.name().equalsIgnoreCase(input)) {
                if (match != null) {
                    return null;
                }
                match = train.trainId();
            }
        }
        return match;
    }

    @Nullable
    public static TrainLookup.CarriageInfo carriageByLabel(ServerLevel level, UUID trainId, String label) {
        if (label == null) {
            return null;
        }
        List<TrainLookup.CarriageInfo> carriages = TrainLookup.getCarriages(level, trainId);
        for (TrainLookup.CarriageInfo carriage : carriages) {
            if (carriage.label().equalsIgnoreCase(label)) {
                return carriage;
            }
        }
        try {
            int index = Integer.parseInt(label.trim());
            for (TrainLookup.CarriageInfo carriage : carriages) {
                if (carriage.index() == index) {
                    return carriage;
                }
            }
        } catch (NumberFormatException notAnIndex) {
            return null;
        }
        return null;
    }

    private static String unquote(String input) {
        if (input.length() >= 2 && input.charAt(0) == '"' && input.charAt(input.length() - 1) == '"') {
            return input.substring(1, input.length() - 1).replace("\\\"", "\"");
        }
        return input;
    }

    @Nullable
    private static UUID tryUuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }
}

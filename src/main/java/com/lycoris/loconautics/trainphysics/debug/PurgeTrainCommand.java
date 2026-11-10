package com.lycoris.loconautics.trainphysics.debug;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.lycoris.loconautics.trainphysics.state.SableTrainPersistence;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public final class PurgeTrainCommand {

    private PurgeTrainCommand() {
    }

    public static void addBranch(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("purgetrain")
                .then(Commands.argument("id", StringArgumentType.string())
                        .executes(PurgeTrainCommand::purge)));
    }

    private static int purge(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        UUID id;
        try {
            id = UUID.fromString(StringArgumentType.getString(ctx, "id"));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("not a train uuid"));
            return 0;
        }
        SableTrain train = SableTrainRegistry.get(id);
        if (train != null) {
            SableTrainSpawner.remove(train);
            source.sendSuccess(() -> Component.literal("purged live train " + id), true);
            return 1;
        }
        SableTrainPersistence.unpersist(source.getServer(), id);
        source.sendSuccess(() -> Component.literal("dropped saved record " + id + ", no live train held it"), true);
        return 1;
    }
}

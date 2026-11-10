package com.lycoris.loconautics.trainphysics.debug;

import com.lycoris.loconautics.common.sable.SableTeleportation;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.spawn.SubLevelSplitWatcher;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ryanhcode.sable.api.command.SubLevelArgumentType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class IsTrainCommand {

    private IsTrainCommand() {
    }

    public static void addBranch(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("istrain")
                .then(Commands.argument("targets", SubLevelArgumentType.subLevels())
                        .executes(IsTrainCommand::check)));
    }

    private static int check(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        int trains = 0;
        for (ServerSubLevel sub : SubLevelArgumentType.getSubLevels(ctx, "targets")) {
            String id = sub.getUniqueId().toString().substring(0, 8);
            int plotBogeys = SubLevelSplitWatcher.scanBogeyPlotPositions(sub.getPlot()).size();
            SableTrain train = SableTrainRegistry.bySubLevel(sub.getUniqueId());
            if (train == null) {
                String why = GhostWheelBodies.isWheelBody(sub.getUniqueId())
                        ? "it is a ghost wheel body"
                        : plotBogeys == 0 ? "no bogey block in its plot" : "bogey blocks exist but nothing registered it";
                say(source, "body " + id + " is NOT a train, " + why + ", plot bogeys " + plotBogeys);
                continue;
            }
            trains++;
            SableTrain.Car car = train.car();
            say(source, "body " + id + " IS train " + train.id().toString().substring(0, 8)
                    + ", registered bogeys " + car.bogeys().size() + ", plot bogeys " + plotBogeys
                    + ", derailed " + train.isDerailed() + ", off track bogeys " + train.offTrackBogeyCount()
                    + ", station " + train.stationState() + ", ghost " + train.isGhost());
            if (car.bogeys().size() != plotBogeys) {
                say(source, "body " + id + " registered bogeys and plot bogeys disagree, the train object is stale");
            }
        }
        return trains;
    }

    private static void say(CommandSourceStack source, String line) {
        source.sendSuccess(() -> Component.literal("[istrain] " + line), false);
    }
}

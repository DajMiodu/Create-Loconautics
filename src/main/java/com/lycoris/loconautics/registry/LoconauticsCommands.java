package com.lycoris.loconautics.registry;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.config.command.ConfigCommand;
import com.lycoris.loconautics.content.bearingaxle.AxleDebugCommand;
import com.lycoris.loconautics.content.knuckle.KnuckleTuning;
import com.lycoris.loconautics.content.trainlookup.commands.FindCommand;
import com.lycoris.loconautics.content.trainlookup.commands.HistoryCommand;
import com.lycoris.loconautics.content.trainlookup.commands.RemoveCommand;
import com.lycoris.loconautics.content.trainlookup.commands.RetrieveCommand;
import com.lycoris.loconautics.content.trainlookup.commands.TrainArguments;
import com.lycoris.loconautics.trainphysics.debug.RailDebug;
import com.lycoris.loconautics.trainphysics.debug.TeleportDebug;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class LoconauticsCommands {

    private static final int LOOKUP_PERMISSION = 4;
    private static final int DEBUG_PERMISSION = 2;

    private LoconauticsCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> debug = Commands.literal("dbgloco")
                .requires(source -> source.hasPermission(DEBUG_PERMISSION));
        RailDebug.addBranches(debug);
        com.lycoris.loconautics.trainphysics.debug.SpawnCartCommand.addBranch(debug);
        TeleportDebug.addBranch(debug);
        KnuckleTuning.addBranch(debug);
        com.lycoris.loconautics.foundation.debug.SteerTrace.addBranch(debug);
        com.lycoris.loconautics.trainphysics.portal.PortalTrace.addBranch(debug);
        AxleDebugCommand.addBranch(debug);
        com.lycoris.loconautics.content.cerisene.CeriseneDebugCommand.addBranch(debug);
        com.lycoris.loconautics.trainphysics.debug.GraphDump.addBranch(debug);
        com.lycoris.loconautics.trainphysics.debug.PurgeTrainCommand.addBranch(debug);
        com.lycoris.loconautics.trainphysics.debug.IsTrainCommand.addBranch(debug);
        com.lycoris.loconautics.trainphysics.debug.PoseTweakCommand.addBranch(debug);
        com.lycoris.loconautics.trainphysics.debug.LengthTrace.addBranch(debug);
        com.lycoris.loconautics.trainphysics.debug.GhostGapTrace.addBranch(debug);
        com.lycoris.loconautics.trainphysics.debug.GhostConsistTrace.addBranch(debug);
        com.lycoris.loconautics.trainphysics.debug.force.ForceDebugCommand.addBranch(debug);
        com.lycoris.loconautics.content.hose.rope.HoseDebugCommand.addBranch(debug);
        ConfigCommand.addBranch(debug);
        ConfigCommand.addResetBranch(debug);
        event.getDispatcher().register(debug);

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("loconautics")
                .requires(source -> source.hasPermission(DEBUG_PERMISSION));
        root.then(lookup(RetrieveCommand.build()));
        root.then(lookup(FindCommand.build()));
        root.then(lookup(HistoryCommand.build()));
        root.then(lookup(RemoveCommand.build()));
        root.then(lookup(Commands.literal("teleport")
                .then(Commands.argument("train", StringArgumentType.greedyString())
                        .suggests(TrainArguments::suggestTrain)
                        .executes(FindCommand::teleport))));
        event.getDispatcher().register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> lookup(
            LiteralArgumentBuilder<CommandSourceStack> branch) {
        return branch.requires(source -> source.hasPermission(LOOKUP_PERMISSION));
    }
}

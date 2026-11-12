package com.lycoris.loconautics;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.boiler.BoilerContributions;
import com.lycoris.loconautics.content.hose.rope.HoseRopeManager;
import com.lycoris.loconautics.content.hose.spray.HoseSprayLoader;
import com.lycoris.loconautics.content.hose.spray.entity.HoseSprayEntities;
import com.lycoris.loconautics.content.hose.spray.interaction.HoseSprayInteractions;
import com.lycoris.loconautics.content.hose.spray.interaction.VanillaHoseSprayInteractions;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@Mod(LoconauticsConstants.MOD_ID)
public final class Loconautics {

    public Loconautics(IEventBus modEventBus, ModContainer modContainer) {
        com.lycoris.loconautics.foundation.debug.DebugLogFilter.install();
        LoconauticsRegistries.register(modEventBus);
        BoilerBlocks.register(modEventBus);
        com.lycoris.loconautics.content.cerisene.CeriseneBlocks.register(modEventBus);
        HoseSprayEntities.register(modEventBus);
        com.lycoris.loconautics.content.locomotiveready.LocomotiveReadyWorld.register(modEventBus);
        com.lycoris.loconautics.content.industrial.IndustrialBlocks.init();
        com.lycoris.loconautics.content.dashboard.DashboardBlocks.init();
        com.lycoris.loconautics.content.buffer.BufferBlocks.init();
        com.lycoris.loconautics.content.combustion.SmallEngineBlocks.init();
        com.lycoris.loconautics.content.conductorshat.ConductorsHats.init();
        com.lycoris.loconautics.registry.LoconauticsSounds.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(
                com.lycoris.loconautics.trainphysics.state.StationChunkTickets::register);

        modContainer.registerConfig(ModConfig.Type.SERVER, LoconauticsConfig.SERVER_SPEC);

        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.addListener(this::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(this::onDatapackSync);
        NeoForge.EVENT_BUS.register(new com.lycoris.loconautics.content.schedule.map.ChunkColorSamplerHandler());
    }

    private void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                LoconauticsRegistries.HOSE_WINCH_BE.get(),
                (be, side) -> side == null || side == be.fluidInputSide()
                        ? be.getHoseFluidHandler() : null);
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                LoconauticsRegistries.HOSE_CONNECTOR_BE.get(),
                (be, side) -> side == null || side == be.plateSide() ? be.getLinkHandler() : null);
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                com.lycoris.loconautics.content.boiler.BoilerBlocks.BARREL_PIPE_CONNECTOR_BE.get(),
                (be, side) -> be.getFluidHandler());
        for (net.minecraft.world.level.block.Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            event.registerBlock(
                    net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                    (level, pos, state, be, side) -> state.is(com.lycoris.loconautics.content.boiler.BoilerTags.BOILER_BODY)
                            ? new com.lycoris.loconautics.content.boiler.BoilerBodyInlet(level, pos) : null,
                    block);
        }
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                com.lycoris.loconautics.content.combustion.SmallEngineBlocks.ENGINE_BE.get(),
                (be, side) -> be.fluidHandler(side));
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.lycoris.loconautics.content.boiler.BoilerBlocks.FIREBOX_STOCKER_BE.get(),
                (be, side) -> be.isPrimary() ? be.inputHandler() : null);
        if (net.neoforged.fml.ModList.get().isLoaded("hullsteel")) {
            com.lycoris.loconautics.content.hatch.HullsteelSocketBridge.registerCapabilities(event);
        }
        if (net.neoforged.fml.ModList.get().isLoaded("computercraft")) {
            com.lycoris.loconautics.compat.computercraft.LoconauticsCCPlugin.onRegisterCapabilities(event);
        }
    }

    private static void onSubLevelContainerReady(net.minecraft.world.level.Level level,
                                                 dev.ryanhcode.sable.api.sublevel.SubLevelContainer container) {
        if (container instanceof dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer serverContainer) {
            serverContainer.trackingSystem().addTrackingPlugin(
                    new com.lycoris.loconautics.common.strand.StrandTrackingSystem<>(
                            serverContainer.getLevel(),
                            com.lycoris.loconautics.content.steelcable.rope.SteelCableRope::kind,
                            com.lycoris.loconautics.content.steelcable.rope.SteelCableRopeManager::get));
            com.lycoris.loconautics.content.knuckle.KnuckleConnectionManager.watch(serverContainer);
            com.lycoris.loconautics.content.buffer.BufferContactManager.watch(serverContainer);
            com.lycoris.loconautics.common.strand.RemovedSubLevels.watch(serverContainer);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LoconauticsConstants.LOGGER.info("Loconautics common setup");
        com.lycoris.loconautics.content.bearingaxle.BogeyShaftPlacement.init();
        com.lycoris.loconautics.foundation.LoconauticsStress.register();
        if (net.neoforged.fml.ModList.get().isLoaded("hullsteel")) {
            com.lycoris.loconautics.content.hatch.HullsteelSocketBridge.install();
        }
        if (net.neoforged.fml.ModList.get().isLoaded("computercraft")) {
            com.lycoris.loconautics.compat.computercraft.LoconauticsCCPlugin.install();
        }
        com.lycoris.loconautics.trainphysics.debug.force.ForceTrace.register();
        com.lycoris.loconautics.common.strand.StrandRemap.register(
                com.lycoris.loconautics.content.steelcable.SteelCableStrandRemap.INSTANCE);
        com.lycoris.loconautics.common.strand.StrandRemap.register(
                com.lycoris.loconautics.content.hose.HoseStrandRemap.INSTANCE);
        com.lycoris.loconautics.common.strand.StrandRemap.register(
                com.lycoris.loconautics.content.screwlink.ScrewLinkStrandRemap.INSTANCE);
        event.enqueueWork(com.lycoris.loconautics.content.turntablebearing.TurntableCogPlacement::register);
        event.enqueueWork(com.lycoris.loconautics.compat.create.ManeuverEditorInstruction::register);
        SableTrainDriver.register();
        com.lycoris.loconautics.trainphysics.visual.pivot.BogeyPivotManager.register();
        com.lycoris.loconautics.common.strand.RopeManager.register(HoseRopeManager::get);
        com.lycoris.loconautics.common.strand.RopeManager.register(
                com.lycoris.loconautics.content.steelcable.rope.SteelCableRopeManager::get);
        com.lycoris.loconautics.common.strand.RopeManager.register(
                com.lycoris.loconautics.content.screwlink.rope.ScrewLinkRopeManager::get);
        dev.ryanhcode.sable.platform.SableEventPlatform.INSTANCE.onPhysicsTick(
                HoseRopeManager::onPhysicsTick);
        dev.ryanhcode.sable.platform.SableEventPlatform.INSTANCE.onPhysicsTick(
                com.lycoris.loconautics.content.steelcable.rope.SteelCableRopeManager::onPhysicsTick);
        dev.ryanhcode.sable.platform.SableEventPlatform.INSTANCE.onPhysicsTick(
                com.lycoris.loconautics.content.screwlink.rope.ScrewLinkRopeManager::onPhysicsTick);
        dev.ryanhcode.sable.platform.SableEventPlatform.INSTANCE.onPhysicsTick(
                com.lycoris.loconautics.compat.create.patch.RollerWorldActor::onPhysicsTick);
        dev.ryanhcode.sable.platform.SableEventPlatform.INSTANCE.onPhysicsTick(
                com.lycoris.loconautics.compat.create.patch.PloughWorldActor::onPhysicsTick);
        dev.ryanhcode.sable.platform.SableEventPlatform.INSTANCE.onSubLevelContainerReady(
                Loconautics::onSubLevelContainerReady);
        com.lycoris.loconautics.trainphysics.spawn.SubLevelSplitWatcher.registerSplitListener();
        HoseSprayInteractions.setBuiltin(VanillaHoseSprayInteractions::register);
        HoseSprayInteractions.rebuild(() -> {});
        com.lycoris.loconautics.trainphysics.TrainConsist.registerProvider(
                new com.lycoris.loconautics.trainphysics.coupling.RopeCouplingProvider());
        com.lycoris.loconautics.trainphysics.TrainConsist.registerProvider(
                new com.lycoris.loconautics.trainphysics.coupling.BlockEntityCouplingProvider<>(
                        dev.simulated_team.simulated.content.blocks.spring.SpringBlockEntity.class,
                        dev.simulated_team.simulated.content.blocks.spring.SpringBlockEntity::getPartnerSubLevelID,
                        spring -> true)
                        .twist(com.lycoris.loconautics.compat.simulated.SpringTwistLinks::link));
        com.lycoris.loconautics.trainphysics.TrainConsist.registerProvider(
                new com.lycoris.loconautics.trainphysics.coupling.BlockEntityCouplingProvider<>(
                        com.lycoris.loconautics.content.knuckle.KnuckleBlockEntity.class,
                        com.lycoris.loconautics.content.knuckle.KnuckleBlockEntity::getPartnerSubLevelId,
                        com.lycoris.loconautics.content.knuckle.KnuckleBlockEntity::isConnected)
                        .twist((own, knuckle) -> knuckle.twistLink(own)));
        com.lycoris.loconautics.trainphysics.TrainConsist.registerProvider(
                new com.lycoris.loconautics.trainphysics.coupling.BlockEntityCouplingProvider<>(
                        dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity.class,
                        connector -> connector.otherConnectorSubLevelId,
                        dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity::hasOtherConnector)
                        .welded(dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity::isLocked));
        com.lycoris.loconautics.trainphysics.TrainConsist.registerProvider(
                new com.lycoris.loconautics.trainphysics.coupling.BlockEntityCouplingProvider<>(
                        com.simibubi.create.content.contraptions.chassis.StickerBlockEntity.class,
                        sticker -> ((com.lycoris.loconautics.compat.create.CreateStickerExtension) sticker)
                                .loconautics$getPartnerSubLevelId(),
                        sticker -> true)
                        .welded(sticker -> true));
        com.lycoris.loconautics.trainphysics.TrainConsist.registerProvider(
                new com.lycoris.loconautics.trainphysics.coupling.SteelCableCouplingProvider());
        com.lycoris.loconautics.trainphysics.TrainConsist.registerProvider(
                new com.lycoris.loconautics.trainphysics.coupling.ScrewLinkCouplingProvider());
        com.lycoris.loconautics.trainphysics.spawn.ArticulationLinks.registerProviders();
        com.lycoris.loconautics.common.ConnectorDetachers.register(
                new com.lycoris.loconautics.content.knuckle.KnuckleDetacher());

        if (net.neoforged.fml.ModList.get().isLoaded("createbigcannons")) {
            NeoForge.EVENT_BUS.addListener(
                    com.lycoris.loconautics.compat.cbc.CbcRecoilHandler::onEntityJoinLevel);
        }
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        for (var strand : com.lycoris.loconautics.content.steelcable.SteelCableStrandManager
                .get(player.getServer()).all()) {
            sendStrandOnLogin(player, strand.type.kind(), strand.id, strand.points,
                    new com.lycoris.loconautics.common.strand.StrandEnd(strand.subA, strand.localA),
                    new com.lycoris.loconautics.common.strand.StrandEnd(strand.subB, strand.localB));
        }
        for (var strand : com.lycoris.loconautics.content.screwlink.ScrewLinkStrandManager
                .get(player.getServer()).all()) {
            sendStrandOnLogin(player, com.lycoris.loconautics.common.strand.StrandKind.SCREW_LINK,
                    strand.id, strand.points,
                    new com.lycoris.loconautics.common.strand.StrandEnd(strand.subA, strand.localA),
                    strand.localB == null ? null
                            : new com.lycoris.loconautics.common.strand.StrandEnd(strand.subB, strand.localB));
        }
    }

    private static void sendStrandOnLogin(ServerPlayer player,
                                          com.lycoris.loconautics.common.strand.StrandKind kind,
                                          java.util.UUID id,
                                          @org.jetbrains.annotations.Nullable java.util.List<org.joml.Vector3d> points,
                                          com.lycoris.loconautics.common.strand.StrandEnd start,
                                          @org.jetbrains.annotations.Nullable com.lycoris.loconautics.common.strand.StrandEnd end) {
        if (points == null || points.size() < 2) {
            return;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new com.lycoris.loconautics.network.packets.attachment.StrandPointsPacket(
                        kind, id, 0, true, points, start, end));
    }

    private void onServerStopped(ServerStoppedEvent event) {
        runStopHook("PanelDriveBus", com.lycoris.loconautics.content.controls.panel.PanelDriveBus::clear);
        runStopHook("MapColorStorage", com.lycoris.loconautics.content.schedule.map.MapColorStorage::closeAll);
        runStopHook("ForceTrace", com.lycoris.loconautics.trainphysics.debug.force.ForceTrace::reset);
        runStopHook("RailDebug", com.lycoris.loconautics.trainphysics.debug.RailDebug::clearSelections);
        runStopHook("RerailApproach", com.lycoris.loconautics.trainphysics.spawn.RerailApproach::clearAll);
        runStopHook("TurntableRegistry", com.lycoris.loconautics.common.turntable.TurntableRegistry::clear);
        runStopHook("SignalOverrides", com.lycoris.loconautics.common.railway.SignalOverrides::clear);
        runStopHook("PointOverrides", com.lycoris.loconautics.common.railway.PointOverrides::clearAll);
        // every one of these holds a constraint handle or a body from a rapier scene that is about to go away
        runStopHook("GhostWheelBodies", com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies::clear);
        runStopHook("KnuckleJoint", com.lycoris.loconautics.content.knuckle.KnuckleJoint::clear);
        runStopHook("SubLevelContacts",com.lycoris.loconautics.trainphysics.sublevel.SubLevelContacts::clear);
        runStopHook("BogeyPivotManager", com.lycoris.loconautics.trainphysics.visual.pivot.BogeyPivotManager::clear);
        runStopHook("CarriageCollisions", com.lycoris.loconautics.trainphysics.drive.CarriageCollisions::clear);
        runStopHook("CreateTrainShadow", com.lycoris.loconautics.trainphysics.state.CreateTrainShadow::clear);
        runStopHook("SableTrainDriver", com.lycoris.loconautics.trainphysics.drive.SableTrainDriver::clearAll);
        runStopHook("TrainResidency", com.lycoris.loconautics.trainphysics.state.TrainResidency::clear);
    }

    private void runStopHook(String name, Runnable hook) {
        try {
            hook.run();
        } catch (Throwable t) {
            LoconauticsConstants.LOGGER.error("[shutdown] {} failed to clear, its static state may leak into the next server", name, t);
        }
    }

    private void onAddReloadListeners(net.neoforged.neoforge.event.AddReloadListenerEvent event) {
        event.addListener(new HoseSprayLoader());
        event.addListener(new BoilerContributions());
        event.addListener(new com.lycoris.loconautics.content.variantpicker.data.VariantFamilyLoader());
    }

    private void onDatapackSync(net.neoforged.neoforge.event.OnDatapackSyncEvent event) {
        var sprayColors = new com.lycoris.loconautics.network.packets.attachment.HoseSprayColorSyncPacket(
                HoseSprayLoader.loadedColors());
        var families = new com.lycoris.loconautics.network.packets.variantpicker.VariantFamilySyncPacket(
                com.lycoris.loconautics.content.variantpicker.data.VariantFamilyLoader.loaded());
        if (event.getPlayer() != null) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(event.getPlayer(), sprayColors);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(event.getPlayer(), families);
        } else {
            net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(sprayColors);
            net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(families);
        }
    }
}
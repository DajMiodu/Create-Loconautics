package com.lycoris.loconautics;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.client.LoconauticsSpriteShifts;
import com.lycoris.loconautics.client.StationParkingFlagClient;
import com.lycoris.loconautics.client.ponder.LoconauticsPonderPlugin;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerClientHandler;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerHUD;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerRenderer;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerScreen;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleRenderer;
import com.lycoris.loconautics.client.gui.selector.PanelPartScreens;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeBlock;
import com.lycoris.loconautics.content.relocator.SableTrainRelocator;
import com.lycoris.loconautics.content.transmission.TransmissionScreen;
import com.lycoris.loconautics.network.packets.analogcontroller.AnalogControllerScrollPacket;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.lycoris.loconautics.trainphysics.fx.SableTrainParticles;
import com.lycoris.loconautics.trainphysics.fx.SableTrainSounds;
import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import com.simibubi.create.content.kinetics.transmission.SplitShaftRenderer;
import com.simibubi.create.content.kinetics.transmission.SplitShaftVisual;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.createmod.catnip.platform.CatnipServices;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = LoconauticsConstants.MOD_ID, dist = Dist.CLIENT)
public final class LoconauticsClient {

    public LoconauticsClient(ModContainer container, IEventBus modBus) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, LoconauticsConfig.CLIENT_SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onClientSetup);
        modBus.addListener(this::onRegisterKeyMappings);
        modBus.addListener(this::onRegisterRenderers);
        modBus.addListener(this::onRegisterScreens);
        modBus.addListener(this::onRegisterGuiLayers);
        modBus.addListener(this::onRegisterParticleProviders);
        modBus.addListener(this::onRegisterGeometryLoaders);
        modBus.addListener(this::onRegisterClientExtensions);
        modBus.addListener(this::onRegisterBlockColors);
        modBus.addListener(this::onAddPackFinders);

        NeoForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onKeyInput);
        NeoForge.EVENT_BUS.addListener(this::onMouseScroll);
        NeoForge.EVENT_BUS.addListener(this::onClientDisconnect);
        NeoForge.EVENT_BUS.addListener(this::onClientLevelUnload);
        NeoForge.EVENT_BUS.addListener(this::onUseItem);
        NeoForge.EVENT_BUS.addListener(this::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(this::onRenderGuiLayer);        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, this::onCalculatePlayerTurn);
        NeoForge.EVENT_BUS.addListener(com.lycoris.loconautics.client.gui.CreateConfigRedirect::onScreenInit);
    }

    // railways already writes the casing summary, a second one would fight it over pack order
    private void onAddPackFinders(net.neoforged.neoforge.event.AddPackFindersEvent event) {
        if (event.getPackType() != net.minecraft.server.packs.PackType.CLIENT_RESOURCES
                || net.neoforged.fml.ModList.get().isLoaded("railways")) {
            return;
        }
        event.addPackFinders(LoconauticsConstants.id("resourcepacks/casing_tooltip"),
                net.minecraft.server.packs.PackType.CLIENT_RESOURCES,
                net.minecraft.network.chat.Component.literal("Loconautics casing tooltip"),
                net.minecraft.server.packs.repository.PackSource.BUILT_IN, true,
                net.minecraft.server.packs.repository.Pack.Position.TOP);
    }

    private void onRegisterParticleProviders(
            net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(
                com.lycoris.loconautics.registry.LoconauticsRegistries.HOSE_WATER_SPRAY.get(),
                com.lycoris.loconautics.content.hose.spray.entity.HoseSprayParticle.Provider::new);
        event.registerSpriteSet(
                LoconauticsRegistries.SMOKE.get(),
                com.lycoris.loconautics.foundation.smoke.SmokeParticle.Provider::new);
    }

    private void onRegisterBlockColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> com.lycoris.loconautics.content.cerisene.CeriseneTint.color(state),
                com.lycoris.loconautics.content.cerisene.CeriseneBlocks.CERISENE_BLOCK.get());
    }

    private void onRegisterClientExtensions(
            net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        event.registerFluidType(com.lycoris.loconautics.content.cerisene.CeriseneFluidType.clientExtensions(),
                com.lycoris.loconautics.content.cerisene.CeriseneBlocks.CERISENE_TYPE.get());
        event.registerBlock(
                com.lycoris.loconautics.content.boiler.barrel.BarrelConnectorBlock.clientExtensions(),
                com.lycoris.loconautics.content.boiler.BoilerBlocks.BARREL_PIPE_CONNECTOR.get());
        event.registerBlock(
                GaugeBlock.clientExtensions(),
                com.lycoris.loconautics.content.boiler.BoilerBlocks.LOCOMOTIVE_GAUGE.get());
        event.registerBlock(
                com.lycoris.loconautics.content.ghostwheel.GhostWheelBlock.clientExtensions(),
                LoconauticsRegistries.GHOST_WHEEL.get());
        event.registerItem(
                com.lycoris.loconautics.content.relocator.CreativeTrainRelocatorItem.clientExtensions(),
                LoconauticsRegistries.CREATIVE_TRAIN_RELOCATOR.get());
        event.registerItem(
                com.lycoris.loconautics.content.casingpicker.BogeyItem.clientExtensions(),
                LoconauticsRegistries.BOGEY.get());
    }

    private void onRegisterGeometryLoaders(
            net.neoforged.neoforge.client.event.ModelEvent.RegisterGeometryLoaders event) {
        event.register(LoconauticsConstants.id("connected"),
                new com.lycoris.loconautics.client.connected.ConnectedModelLoader());
        event.register(LoconauticsConstants.id("connected_variants"),
                new com.lycoris.loconautics.client.connected.ConnectedVariantModelLoader());
        event.register(LoconauticsConstants.id("copycat_bed"),
                new com.lycoris.loconautics.client.copycat.CopycatBedLoader());
        event.register(LoconauticsConstants.id("copycat_boxes"),
                new com.lycoris.loconautics.client.copycat.CopycatBoxesLoader());
    }

    private void onCommonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        com.lycoris.loconautics.content.boiler.barrel.BoilerCTType.register();
        registerBarrelCT(com.lycoris.loconautics.content.boiler.BoilerBlocks.BOILER_BODY.get(), "boiler_body");
        registerCeriseneCT();
        com.lycoris.loconautics.content.industrial.IndustrialClientSetup.register();
        for (net.minecraft.world.level.block.Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            if (block instanceof com.simibubi.create.content.trains.bogey.AbstractBogeyBlock<?>) {
                registerDrivenBogey(block);
            }
        }
    }

    private static void registerDrivenBogey(net.minecraft.world.level.block.Block bogey) {
        net.minecraft.resources.ResourceLocation id =
                net.createmod.catnip.registry.RegisteredObjectsHelper.getKeyOrThrow(bogey);
        boolean standard = com.lycoris.loconautics.content.bearingaxle.DrivenBogeyModel.isStandardShape(id);
        com.simibubi.create.CreateClient.MODEL_SWAPPER.getCustomBlockModels().register(id,
                original -> new com.lycoris.loconautics.content.bearingaxle.DrivenBogeyModel(original, standard));
    }

    private static void registerCeriseneCT() {
        var shift = com.simibubi.create.foundation.block.connected.CTSpriteShifter.getCT(
                com.simibubi.create.foundation.block.connected.AllCTTypes.OMNIDIRECTIONAL,
                LoconauticsConstants.id("block/cerisene/block"),
                LoconauticsConstants.id("block/cerisene/block_connected"));
        com.simibubi.create.CreateClient.MODEL_SWAPPER.getCustomBlockModels().register(
                net.createmod.catnip.registry.RegisteredObjectsHelper.getKeyOrThrow(
                        com.lycoris.loconautics.content.cerisene.CeriseneBlocks.CERISENE_BLOCK.get()),
                model -> new com.simibubi.create.foundation.block.connected.CTModel(
                        model,
                        new com.simibubi.create.foundation.block.connected.SimpleCTBehaviour(shift)));
    }

    private static void registerBarrelCT(net.minecraft.world.level.block.Block block, String folder) {
        String base = "block/boiler/barrel/" + folder + "/barrel_side";
        var shift = com.lycoris.loconautics.content.boiler.barrel.BoilerCTShiftEntry.get(
                LoconauticsConstants.id(base), LoconauticsConstants.id(base + "_ct"));
        com.simibubi.create.CreateClient.MODEL_SWAPPER.getCustomBlockModels().register(
                net.createmod.catnip.registry.RegisteredObjectsHelper.getKeyOrThrow(block),
                model -> new com.simibubi.create.foundation.block.connected.CTModel(
                        model,
                        new com.lycoris.loconautics.content.boiler.barrel.BarrelCTBehaviour(shift)));
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        com.lycoris.loconautics.content.controls.panellever.LeverHoldInteraction.registered();
        com.lycoris.loconautics.content.brakes.pull.PullBrakeHold.registered();
        LoconauticsConstants.LOGGER.info("Loconautics client setup");
        LoconauticsPartialModels.init();
        LoconauticsSpriteShifts.init();

        com.simibubi.create.foundation.item.render.CustomRenderedItems.register(
                LoconauticsRegistries.CREATIVE_TRAIN_RELOCATOR.get());
        com.simibubi.create.foundation.item.render.CustomRenderedItems.register(LoconauticsRegistries.BOGEY.get());

        event.enqueueWork(() -> net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                com.lycoris.loconautics.content.cerisene.CeriseneBlocks.CERISENE.get(), net.minecraft.client.renderer.RenderType.translucent()));
        event.enqueueWork(() -> net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                com.lycoris.loconautics.content.cerisene.CeriseneBlocks.CERISENE_FLOWING.get(), net.minecraft.client.renderer.RenderType.translucent()));

        PonderIndex.addPlugin(new LoconauticsPonderPlugin());
        PonderIndex.addPlugin(new com.lycoris.loconautics.client.ponder.BoilerPonderPlugin());
        PonderIndex.addPlugin(new com.lycoris.loconautics.client.ponder.HosePonderPlugin());
        PonderIndex.addPlugin(new com.lycoris.loconautics.client.ponder.SmallEnginePonderPlugin());

        SimpleBlockEntityVisualizer
                .builder(com.lycoris.loconautics.content.boiler.BoilerBlocks.FIREBOX_BE.get())
                .factory(com.lycoris.loconautics.content.boiler.firebox.FireboxVisual::new)
                .apply();

        SimpleBlockEntityVisualizer
                .builder(com.lycoris.loconautics.content.boiler.BoilerBlocks.LOCOMOTIVE_GAUGE_BE.get())
                .factory(com.lycoris.loconautics.content.controls.panel.visual.PanelVisual::new)
                .apply();

        SimpleBlockEntityVisualizer
                .builder(com.lycoris.loconautics.content.dashboard.DashboardBlocks.DASHBOARD_BE.get())
                .factory(com.lycoris.loconautics.content.controls.panel.visual.PanelVisual::new)
                .apply();

        SimpleBlockEntityVisualizer
                .builder(com.lycoris.loconautics.content.combustion.SmallEngineBlocks.ENGINE_BE.get())
                .factory(com.lycoris.loconautics.content.combustion.SmallEngineVisual::new)
                .apply();

        SimpleBlockEntityVisualizer
                .builder(LoconauticsRegistries.PULL_BRAKE_BE.get())
                .factory(com.lycoris.loconautics.content.brakes.pull.PullBrakeVisual::new)
                .apply();

        SimpleBlockEntityVisualizer
                .builder(LoconauticsRegistries.TRANSMISSION_BE.get())
                .factory(SplitShaftVisual::new)
                .apply();

        SimpleBlockEntityVisualizer
                .builder(LoconauticsRegistries.BEARING_AXLE_BE.get())
                .factory((context, be, partialTick) -> new com.simibubi.create.content.kinetics.base
                        .OrientedRotatingVisual<>(context, be, partialTick, net.minecraft.core.Direction.SOUTH,
                        com.lycoris.loconautics.content.bearingaxle.BearingAxleBlock.shaftFace(be.getBlockState()),
                        dev.engine_room.flywheel.lib.model.Models.partial(
                                com.simibubi.create.AllPartialModels.SHAFT_HALF)))
                .apply();

        SimpleBlockEntityVisualizer
                .builder(LoconauticsRegistries.TURNTABLE_BEARING_BE.get())
                .factory(com.lycoris.loconautics.content.turntablebearing.TurntableBearingVisual::new)
                .apply();

        ItemDescription.useKey(LoconauticsRegistries.STEEL_CABLE.get(), "item.loconautics.steel_cable");
        TooltipModifier.REGISTRY.register(LoconauticsRegistries.STEEL_CABLE.get(),
                new ItemDescription.Modifier(LoconauticsRegistries.STEEL_CABLE.get(),
                        net.createmod.catnip.lang.FontHelper.Palette.STANDARD_CREATE));

        ItemDescription.useKey(LoconauticsRegistries.KNUCKLE_ITEM.get(), "block.loconautics.knuckle");
        TooltipModifier.REGISTRY.register(LoconauticsRegistries.KNUCKLE_ITEM.get(),
                new ItemDescription.Modifier(LoconauticsRegistries.KNUCKLE_ITEM.get(),
                        net.createmod.catnip.lang.FontHelper.Palette.STANDARD_CREATE));

        com.lycoris.loconautics.common.strand.StrandKnotPicker.registerSource(
                new com.lycoris.loconautics.content.hose.winch.HoseWinchKnotSource());

        registerTooltip(com.lycoris.loconautics.content.cerisene.CeriseneBlocks.RAW_CERISENE.get(), "item.loconautics.raw_cerisene");
        registerTooltip(com.lycoris.loconautics.content.cerisene.CeriseneBlocks.CERISENE_BLOCK_ITEM.get(), "block.loconautics.cerisene_block");
        registerTooltip(LoconauticsRegistries.HOSE.get(), "item.loconautics.hose");
        com.lycoris.loconautics.content.combustion.SmallEngineBlocks.ENGINE_ITEMS
                .forEach((color, item) -> registerTooltip(item.get(), "block.loconautics.small_combustion_engine"));

        com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlocks.CONTROLLER_ITEMS
                .forEach((color, item) -> registerTooltip(item.get(), "block.loconautics.analog_controller"));
        registerTooltip(LoconauticsRegistries.BEARING_AXLE_ITEM.get(), "block.loconautics.bearing_axle");
        registerTooltip(LoconauticsRegistries.TRANSMISSION_ITEM.get(), "block.loconautics.transmission");
        registerTooltip(com.lycoris.loconautics.content.boiler.BoilerBlocks.LOCOMOTIVE_GAUGE_ITEM.get(),
                "block.loconautics.locomotive_gauge");
        registerTooltip(com.lycoris.loconautics.content.boiler.BoilerBlocks.SMART_SAFETY_VALVE_ITEM.get(),
                "block.loconautics.smart_safety_valve");
        com.lycoris.loconautics.content.dashboard.DashboardBlocks.DASHBOARD_ITEMS
                .forEach((color, item) -> registerTooltip(item.get(), "block.loconautics.dashboard"));
        registerTooltip(LoconauticsRegistries.SMOKESTACK_ITEM.get(), "block.loconautics.smokestack");
        registerTooltip(com.lycoris.loconautics.content.boiler.BoilerBlocks.VALVE_ITEM.get(),
                "item.loconautics.locomotive_valve");
        registerTooltip(com.lycoris.loconautics.content.boiler.BoilerBlocks.GAUGE_BUTTON_ITEM.get(),
                "item.loconautics.gauge_button");
        registerTooltip(com.lycoris.loconautics.content.boiler.BoilerBlocks.GAUGE_LEVER_ITEM.get(),
                "item.loconautics.gauge_lever");
        registerTooltip(com.lycoris.loconautics.content.boiler.BoilerBlocks.GAUGE_SWITCH_ITEM.get(),
                "item.loconautics.gauge_switch");
        registerTooltip(com.lycoris.loconautics.content.industrial.IndustrialBlocks.TANK_ITEM.get(),
                "block.loconautics.industrial_fluid_tank");
        registerTooltip(com.lycoris.loconautics.content.industrial.IndustrialBlocks.PUMP_ITEM.get(),
                "block.loconautics.industrial_pump");
    }

    private static void registerTooltip(net.minecraft.world.item.Item item, String key) {
        ItemDescription.useKey(item, key);
        TooltipModifier.REGISTRY.register(item,
                new ItemDescription.Modifier(item,
                        net.createmod.catnip.lang.FontHelper.Palette.STANDARD_CREATE));
    }

    private void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                LoconauticsRegistries.ANALOG_CONTROLLER_BE.get(),
                AnalogControllerRenderer::new
        );
        event.registerBlockEntityRenderer(
                com.lycoris.loconautics.content.combustion.SmallEngineBlocks.ENGINE_BE.get(),
                com.lycoris.loconautics.content.combustion.SmallEngineRenderer::new
        );
        event.registerBlockEntityRenderer(
                LoconauticsRegistries.PULL_BRAKE_BE.get(),
                com.lycoris.loconautics.content.brakes.pull.PullBrakeRenderer::new
        );
        event.registerBlockEntityRenderer(
                LoconauticsRegistries.TURNTABLE_BEARING_BE.get(),
                com.lycoris.loconautics.content.turntablebearing.TurntableBearingRenderer::new
        );
        event.registerBlockEntityRenderer(
                LoconauticsRegistries.BEARING_AXLE_BE.get(),
                BearingAxleRenderer::new
        );
        event.registerBlockEntityRenderer(
                com.lycoris.loconautics.content.boiler.BoilerBlocks.STEAM_TAKEOFF_BE.get(),
                com.lycoris.loconautics.content.boiler.steamtakeoff.SteamTakeoffRenderer::new
        );
        event.registerBlockEntityRenderer(
                com.lycoris.loconautics.content.boiler.BoilerBlocks.BARREL_PIPE_CONNECTOR_BE.get(),
                com.lycoris.loconautics.content.boiler.barrel.BarrelConnectorRenderer::new
        );
        event.registerBlockEntityRenderer(
                com.lycoris.loconautics.content.boiler.BoilerBlocks.LOCOMOTIVE_GAUGE_BE.get(),
                com.lycoris.loconautics.content.controls.panel.PanelRenderer::new
        );
        event.registerBlockEntityRenderer(
                com.lycoris.loconautics.content.dashboard.DashboardBlocks.DASHBOARD_BE.get(),
                com.lycoris.loconautics.content.dashboard.DashboardRenderer::new
        );
        event.registerBlockEntityRenderer(
                LoconauticsRegistries.TRANSMISSION_BE.get(),
                SplitShaftRenderer::new
        );
        event.registerBlockEntityRenderer(
                com.lycoris.loconautics.content.industrial.IndustrialBlocks.TANK_BE.get(),
                com.lycoris.loconautics.content.industrial.IndustrialTankRenderer::new
        );
        event.registerBlockEntityRenderer(
                LoconauticsRegistries.KNUCKLE_BE.get(),
                com.lycoris.loconautics.content.knuckle.KnuckleRenderer::new
        );
        event.registerBlockEntityRenderer(
                com.lycoris.loconautics.content.buffer.BufferBlocks.BUFFER_BE.get(),
                com.lycoris.loconautics.content.buffer.BufferRenderer::new
        );
        event.registerBlockEntityRenderer(
                LoconauticsRegistries.HOSE_CONNECTOR_BE.get(),
                com.lycoris.loconautics.content.hose.link.HoseConnectorRenderer::new
        );
        event.registerBlockEntityRenderer(
                LoconauticsRegistries.HOSE_WINCH_BE.get(),
                com.lycoris.loconautics.content.hose.winch.HoseWinchRenderer::new
        );
        event.registerBlockEntityRenderer(
                LoconauticsRegistries.FLUID_HATCH_BE.get(),
                com.lycoris.loconautics.content.hatch.HatchRenderer::new
        );
        event.registerBlockEntityRenderer(
                com.lycoris.loconautics.content.boiler.BoilerBlocks.FIREBOX_BE.get(),
                com.lycoris.loconautics.content.boiler.firebox.FireboxRenderer::new
        );
        event.registerEntityRenderer(
                com.lycoris.loconautics.content.hose.spray.entity.HoseSprayEntities.HOSE_SPRAY.get(),
                com.lycoris.loconautics.content.hose.spray.entity.HoseSprayEntityRenderer::new
        );
    }

    private void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(LoconauticsRegistries.ANALOG_CONTROLLER_MENU.get(),
                AnalogControllerScreen::new);
        event.register(LoconauticsRegistries.TRANSMISSION_MENU.get(),
                TransmissionScreen::new);
        event.register(LoconauticsRegistries.BEARING_AXLE_MENU.get(),
                com.lycoris.loconautics.content.bearingaxle.BearingAxleScreen::new);
        event.register(LoconauticsRegistries.PANEL_FREQUENCY_MENU.get(), PanelPartScreens::create);
    }

    private void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_BAR,
                LoconauticsConstants.id("analog_controller_hud"),
                AnalogControllerHUD.OVERLAY);
        event.registerAbove(VanillaGuiLayers.HOTBAR, LoconauticsConstants.id("scroll_picker"),
                com.lycoris.loconautics.client.scrollmenu.ScrollMenuRenderer.OVERLAY);
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, LoconauticsConstants.id("grab_crosshair"),
                com.lycoris.loconautics.content.grab.GrabCrosshair.OVERLAY);
        event.registerAboveAll(LoconauticsConstants.id("tick_meter"),
                com.lycoris.loconautics.foundation.debug.TickMeterHud.OVERLAY);
    }

    private void onRegisterClientCommands(
            net.neoforged.neoforge.client.event.RegisterClientCommandsEvent event) {
        com.lycoris.loconautics.trainphysics.visual.BogeyTurnTweakCommand.register(event.getDispatcher());
    }

    private void onClientTick(ClientTickEvent.Pre event) {
        if (net.minecraft.client.Minecraft.getInstance().level != null) {
            com.lycoris.loconautics.trainphysics.rail.RailSeams.trackClientLevel(
                    net.minecraft.client.Minecraft.getInstance().level);
        }
        com.lycoris.loconautics.trainphysics.portal.PortalCutsClient.tickReport();
        AnalogControllerClientHandler.tick();
        AnalogControllerHUD.tick();
        SableTrainRelocator.clientTick();
        SableTrainSounds.tickAll();
        com.lycoris.loconautics.content.analogcontroller.WhistleHonks.tick();
        SableTrainParticles.tickAll();
        com.lycoris.loconautics.trainphysics.visual.BogeyWheelAnimator.tickAll();
        com.lycoris.loconautics.compat.create.patch.RollerAnimator.tickAll();
        StationParkingFlagClient.tick();
        com.lycoris.loconautics.content.hose.HoseInteractionHandler.tickPreview();
        com.lycoris.loconautics.content.steelcable.SteelCablePreview.tickPreview();
        com.lycoris.loconautics.content.steelcable.SteelCableHover.tick();
        com.lycoris.loconautics.content.grab.GrabClientHandler.tick();
        com.lycoris.loconautics.content.grab.GrabHandRenderer.tick();
        com.lycoris.loconautics.foundation.debug.DebugLinesClient.tick();
        com.lycoris.loconautics.client.scrollmenu.ScrollMenuHandler.tick();
    }

    private void onUseItem(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isUseItem() && com.lycoris.loconautics.content.steelcable.SteelCableHover.onUse()) {
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }
        SableTrainRelocator.onInteract(event);
        com.lycoris.loconautics.content.hose.HoseInteractionHandler.onInteract(event);
        com.lycoris.loconautics.content.grab.GrabClientHandler.onInteract(event);
    }

    private void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(VanillaGuiLayers.TAB_LIST)
                && com.lycoris.loconautics.client.scrollmenu.ScrollMenuHandler.isOpen()) {
            event.setCanceled(true);
        }
        if (event.getName().equals(VanillaGuiLayers.CROSSHAIR)
                && com.lycoris.loconautics.content.grab.GrabCrosshair.replacesCrosshair()) {
            event.setCanceled(true);
        }
    }

    private void onCalculatePlayerTurn(net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent event) {
        com.lycoris.loconautics.content.grab.GrabClientHandler.onCalculateTurn(event);
    }

    private void onRegisterKeyMappings(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
        event.register(com.lycoris.loconautics.content.grab.GrabClientHandler.ROTATE_MODE);
        event.register(com.lycoris.loconautics.content.variantpicker.ScrollPickerHandler.OPEN_KEY);
        event.register(com.lycoris.loconautics.content.casingpicker.CasingPickerClient.OPEN_KEY);
    }

    private void onKeyInput(InputEvent.Key event) {
        if (com.lycoris.loconautics.content.variantpicker.ScrollPickerHandler.OPEN_KEY
                .matches(event.getKey(), event.getScanCode()) && event.getAction() == 1
                && Minecraft.getInstance().screen == null) {
            com.lycoris.loconautics.content.variantpicker.ScrollPickerHandler.tryOpen();
        }
        if (com.lycoris.loconautics.content.casingpicker.CasingPickerClient.OPEN_KEY
                .matches(event.getKey(), event.getScanCode()) && event.getAction() == 1
                && Minecraft.getInstance().screen == null) {
            com.lycoris.loconautics.content.casingpicker.CasingPickerClient.tryOpen();
        }
        if (!AnalogControllerClientHandler.isControlling()) return;
        if (event.getKey() == 256 && event.getAction() == 1) {
            AnalogControllerClientHandler.stopControllingClient();
            Minecraft.getInstance().setScreen(null);
        }
    }

    private void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && com.lycoris.loconautics.client.scrollmenu.ScrollMenuHandler.isOpen()) {
            if (com.lycoris.loconautics.client.scrollmenu.ScrollMenuHandler.onScroll(
                    event.getScrollDeltaY() > 0 ? -1 : 1)) {
                event.setCanceled(true);
                return;
            }
        }
        if (mc.screen == null
                && com.lycoris.loconautics.content.grab.GrabClientHandler.onScroll(event.getScrollDeltaY())) {
            event.setCanceled(true);
            return;
        }

        if (!AnalogControllerClientHandler.isControlling()) return;
        if (Minecraft.getInstance().screen != null) return;
        int delta = event.getScrollDeltaY() > 0 ? 1 : -1;
        CatnipServices.NETWORK.sendToServer(
                new AnalogControllerScrollPacket(delta, AnalogControllerClientHandler.getMountedPos()));
        event.setCanceled(true);
    }

    private void onRenderLevelStage(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            var lineBuffers = net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource();
            com.lycoris.loconautics.foundation.debug.DebugLinesClient.renderAll(
                    event.getPoseStack(), lineBuffers, event.getCamera().getPosition());
            lineBuffers.endBatch();
            com.lycoris.loconautics.foundation.particle.LateParticles.renderAll(
                    event.getCamera(), event.getModelViewMatrix(),
                    event.getPartialTick().getGameTimeDeltaPartialTick(false));
            return;
        }
        if (event.getStage() != net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        var buffers = net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource();
        com.lycoris.loconautics.content.steelcable.SteelCableStrandRenderer.renderAll(
                event.getPoseStack(), buffers, event.getCamera().getPosition(),
                event.getPartialTick().getGameTimeDeltaPartialTick(false));
        com.lycoris.loconautics.content.screwlink.ScrewLinkStrandRenderer.renderAll(
                event.getPoseStack(), buffers, event.getCamera().getPosition(),
                event.getPartialTick().getGameTimeDeltaPartialTick(false));
        buffers.endBatch();
    }

    // the portal cut removes flywheel visuals, and they have to go back before the level holding them is dropped
    private void onClientLevelUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel level) {
            com.lycoris.loconautics.trainphysics.portal.PortalCutsClient.onLevelUnload(level);
            com.lycoris.loconautics.foundation.particle.LateParticles.clear();
            com.lycoris.loconautics.content.analogcontroller.AnalogControllerClientHandler.clear();
        }
    }

    private void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        com.lycoris.loconautics.common.strand.ClientStrands.clearAll();
        SableTrainClientRegistry.clear();
        com.lycoris.loconautics.compat.crn.CrnCartDisplays.clear();
        com.lycoris.loconautics.trainphysics.actors.TrainActorControls.clearClient();
        SableTrainSounds.stopAll();
        com.lycoris.loconautics.content.analogcontroller.WhistleHonks.clear();
        StationParkingFlagClient.clear();
        com.lycoris.loconautics.trainphysics.portal.PortalCutsClient.clear();
        com.lycoris.loconautics.foundation.debug.DebugLinesClient.clear();
        com.lycoris.loconautics.client.scrollmenu.ScrollMenuHandler.forceClose();
        com.lycoris.loconautics.content.analogcontroller.AnalogControllerClientHandler.clear();
    }
}
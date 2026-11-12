package com.lycoris.loconautics.network;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.network.packets.analogcontroller.AnalogControllerDismountPacket;
import com.lycoris.loconautics.network.packets.analogcontroller.AnalogControllerInputPacket;
import com.lycoris.loconautics.network.packets.analogcontroller.AnalogControllerMountPacket;
import com.lycoris.loconautics.network.packets.analogcontroller.AnalogControllerScrollPacket;
import com.lycoris.loconautics.network.packets.analogcontroller.AnalogControllerStationPromptPacket;
import com.lycoris.loconautics.network.packets.attachment.GrabDragPacket;
import com.lycoris.loconautics.network.packets.attachment.GrabStopPacket;
import com.lycoris.loconautics.network.packets.attachment.GrabUndockPacket;
import com.lycoris.loconautics.network.packets.attachment.HoseAttachPacket;
import com.lycoris.loconautics.network.packets.attachment.HoseSprayColorSyncPacket;
import com.lycoris.loconautics.network.packets.attachment.SteelCableBreakPacket;
import com.lycoris.loconautics.network.packets.attachment.StrandHookGrabPacket;
import com.lycoris.loconautics.network.packets.attachment.StrandPointsPacket;
import com.lycoris.loconautics.network.packets.schedule.ConductorHatPacket;
import com.lycoris.loconautics.network.packets.schedule.MapColorRequestPacket;
import com.lycoris.loconautics.network.packets.schedule.MapColorResponsePacket;
import com.lycoris.loconautics.network.packets.train.AssembleSableTrainPacket;
import com.lycoris.loconautics.network.packets.train.DisassembleSableTrainPacket;
import com.lycoris.loconautics.network.packets.train.PlaceStoredTrainPacket;
import com.lycoris.loconautics.network.packets.train.PortalCutPacket;
import com.lycoris.loconautics.network.packets.train.SableTrainRelocatePacket;
import com.lycoris.loconautics.network.packets.train.SableTrainSyncPacket;
import com.lycoris.loconautics.network.packets.train.StationParkedSyncPacket;
import com.lycoris.loconautics.network.packets.train.StationSetTrainColorPacket;
import com.lycoris.loconautics.network.packets.train.StationSetTrainNamePacket;
import com.lycoris.loconautics.network.packets.train.TrainHornPacket;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class LoconauticsNetwork {

    private LoconauticsNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(String.valueOf(LoconauticsConstants.NETWORK_VERSION));

        r.playToServer(com.lycoris.loconautics.network.packets.variantpicker.BlockVariantApplyPacket.TYPE,
                com.lycoris.loconautics.network.packets.variantpicker.BlockVariantApplyPacket.STREAM_CODEC,
                com.lycoris.loconautics.network.packets.variantpicker.BlockVariantApplyPacket::handle);
        r.playToServer(com.lycoris.loconautics.network.packets.variantpicker.HatColorApplyPacket.TYPE,
                com.lycoris.loconautics.network.packets.variantpicker.HatColorApplyPacket.STREAM_CODEC,
                com.lycoris.loconautics.network.packets.variantpicker.HatColorApplyPacket::handle);
        r.playToServer(com.lycoris.loconautics.network.packets.casingpicker.CasingSwapPacket.TYPE,
                com.lycoris.loconautics.network.packets.casingpicker.CasingSwapPacket.STREAM_CODEC,
                com.lycoris.loconautics.network.packets.casingpicker.CasingSwapPacket::handle);
        r.playToClient(com.lycoris.loconautics.network.packets.variantpicker.VariantFamilySyncPacket.TYPE,
                com.lycoris.loconautics.network.packets.variantpicker.VariantFamilySyncPacket.STREAM_CODEC,
                com.lycoris.loconautics.network.packets.variantpicker.VariantFamilySyncPacket::handle);

        r.playToServer(com.lycoris.loconautics.network.packets.controls.LocomotiveGaugeSelectPacket.TYPE,
                com.lycoris.loconautics.network.packets.controls.LocomotiveGaugeSelectPacket.STREAM_CODEC,
                com.lycoris.loconautics.network.packets.controls.LocomotiveGaugeSelectPacket::handle);
        r.playToServer(com.lycoris.loconautics.network.packets.controls.ValveTurnPacket.TYPE,
                com.lycoris.loconautics.network.packets.controls.ValveTurnPacket.STREAM_CODEC,
                com.lycoris.loconautics.network.packets.controls.ValveTurnPacket::handle);
        r.playToClient(com.lycoris.loconautics.network.packets.controls.ValveOpenPacket.TYPE,
                com.lycoris.loconautics.network.packets.controls.ValveOpenPacket.STREAM_CODEC,
                com.lycoris.loconautics.network.packets.controls.ValveOpenPacket::handle);

        r.playToServer(com.lycoris.loconautics.network.packets.controls.selector.ValveSelectorApplyPacket.TYPE,
                com.lycoris.loconautics.network.packets.controls.selector.ValveSelectorApplyPacket.STREAM_CODEC,
                com.lycoris.loconautics.network.packets.controls.selector.ValveSelectorApplyPacket::handle);
        r.playToServer(com.lycoris.loconautics.network.packets.controls.selector.ButtonSelectorApplyPacket.TYPE,
                com.lycoris.loconautics.network.packets.controls.selector.ButtonSelectorApplyPacket.STREAM_CODEC,
                com.lycoris.loconautics.network.packets.controls.selector.ButtonSelectorApplyPacket::handle);
        r.playToServer(com.lycoris.loconautics.network.packets.controls.selector.LeverSelectorApplyPacket.TYPE,
                com.lycoris.loconautics.network.packets.controls.selector.LeverSelectorApplyPacket.STREAM_CODEC,
                com.lycoris.loconautics.network.packets.controls.selector.LeverSelectorApplyPacket::handle);

        r.playToServer(com.lycoris.loconautics.network.packets.controls.selector.SwitchSelectorApplyPacket.TYPE,
                com.lycoris.loconautics.network.packets.controls.selector.SwitchSelectorApplyPacket.STREAM_CODEC,
                com.lycoris.loconautics.network.packets.controls.selector.SwitchSelectorApplyPacket::handle);

        r.playToServer(com.lycoris.loconautics.network.packets.controls.selector.PanelInvertPacket.TYPE,
                com.lycoris.loconautics.network.packets.controls.selector.PanelInvertPacket.STREAM_CODEC,
                com.lycoris.loconautics.network.packets.controls.selector.PanelInvertPacket::handle);

        r.playToServer(com.lycoris.loconautics.network.packets.controls.selector.PanelLabelPacket.TYPE,
                com.lycoris.loconautics.network.packets.controls.selector.PanelLabelPacket.STREAM_CODEC,
                com.lycoris.loconautics.network.packets.controls.selector.PanelLabelPacket::handle);

        r.playToServer(com.lycoris.loconautics.network.packets.turntablebearing.TurntableConfigurePacket.TYPE,
                com.lycoris.loconautics.network.packets.turntablebearing.TurntableConfigurePacket.STREAM_CODEC,
                com.lycoris.loconautics.network.packets.turntablebearing.TurntableConfigurePacket::handle);

        r.playToServer(com.lycoris.loconautics.network.packets.controls.LeverSetPacket.TYPE,
                com.lycoris.loconautics.network.packets.controls.LeverSetPacket.STREAM_CODEC,
                com.lycoris.loconautics.network.packets.controls.LeverSetPacket::handle);
        r.playToServer(com.lycoris.loconautics.network.packets.pullbrake.PullBrakeSetPacket.TYPE,
                com.lycoris.loconautics.network.packets.pullbrake.PullBrakeSetPacket.STREAM_CODEC,
                com.lycoris.loconautics.network.packets.pullbrake.PullBrakeSetPacket::handle);
        r.playToServer(AnalogControllerInputPacket.TYPE, AnalogControllerInputPacket.STREAM_CODEC,
                AnalogControllerInputPacket::handle);
        r.playToServer(AnalogControllerScrollPacket.TYPE, AnalogControllerScrollPacket.STREAM_CODEC,
                AnalogControllerScrollPacket::handle);
        r.playToServer(AnalogControllerDismountPacket.TYPE, AnalogControllerDismountPacket.STREAM_CODEC,
                AnalogControllerDismountPacket::handle);
        r.playToClient(AnalogControllerMountPacket.TYPE, AnalogControllerMountPacket.STREAM_CODEC,
                AnalogControllerMountPacket::handle);
        r.playToClient(AnalogControllerStationPromptPacket.TYPE, AnalogControllerStationPromptPacket.STREAM_CODEC,
                AnalogControllerStationPromptPacket::handle);

        r.playToServer(AssembleSableTrainPacket.TYPE, AssembleSableTrainPacket.STREAM_CODEC,
                AssembleSableTrainPacket::handle);
        r.playToServer(DisassembleSableTrainPacket.TYPE, DisassembleSableTrainPacket.STREAM_CODEC,
                DisassembleSableTrainPacket::handle);
        r.playToServer(SableTrainRelocatePacket.TYPE, SableTrainRelocatePacket.STREAM_CODEC,
                SableTrainRelocatePacket::handle);
        r.playToServer(PlaceStoredTrainPacket.TYPE, PlaceStoredTrainPacket.STREAM_CODEC,
                PlaceStoredTrainPacket::handle);
        r.playToServer(com.lycoris.loconautics.network.packets.train.RelocatePreviewPacket.TYPE,
                com.lycoris.loconautics.network.packets.train.RelocatePreviewPacket.STREAM_CODEC,
                com.lycoris.loconautics.network.packets.train.RelocatePreviewPacket::handle);
        r.playToClient(com.lycoris.loconautics.network.packets.train.RelocatePreviewResultPacket.TYPE,
                com.lycoris.loconautics.network.packets.train.RelocatePreviewResultPacket.STREAM_CODEC,
                com.lycoris.loconautics.network.packets.train.RelocatePreviewResultPacket::handle);
        r.playToServer(StationSetTrainNamePacket.TYPE, StationSetTrainNamePacket.STREAM_CODEC,
                StationSetTrainNamePacket::handle);
        r.playToServer(StationSetTrainColorPacket.TYPE, StationSetTrainColorPacket.STREAM_CODEC,
                StationSetTrainColorPacket::handle);
        r.playToClient(SableTrainSyncPacket.TYPE, SableTrainSyncPacket.STREAM_CODEC,
                SableTrainSyncPacket::handle);
        r.playToClient(com.lycoris.loconautics.network.packets.train.SuspensionLeanPacket.TYPE,
                com.lycoris.loconautics.network.packets.train.SuspensionLeanPacket.STREAM_CODEC,
                com.lycoris.loconautics.network.packets.train.SuspensionLeanPacket::handle);
        r.playToClient(StationParkedSyncPacket.TYPE, StationParkedSyncPacket.STREAM_CODEC,
                StationParkedSyncPacket::handle);
        r.playToClient(PortalCutPacket.TYPE, PortalCutPacket.STREAM_CODEC, PortalCutPacket::handle);
        r.playToClient(TrainHornPacket.TYPE, TrainHornPacket.STREAM_CODEC, TrainHornPacket::handle);
        r.playToClient(com.lycoris.loconautics.network.packets.debug.DebugLinePacket.TYPE,
                com.lycoris.loconautics.network.packets.debug.DebugLinePacket.STREAM_CODEC,
                com.lycoris.loconautics.network.packets.debug.DebugLinePacket::handle);

        r.playToServer(HoseAttachPacket.TYPE, HoseAttachPacket.STREAM_CODEC, HoseAttachPacket::handle);
        r.playToServer(com.lycoris.loconautics.network.packets.attachment.HoseLinkPacket.TYPE,
                com.lycoris.loconautics.network.packets.attachment.HoseLinkPacket.STREAM_CODEC,
                com.lycoris.loconautics.network.packets.attachment.HoseLinkPacket::handle);
        r.playToServer(GrabDragPacket.TYPE, GrabDragPacket.STREAM_CODEC, GrabDragPacket::handle);
        r.playToServer(GrabStopPacket.TYPE, GrabStopPacket.STREAM_CODEC, GrabStopPacket::handle);
        r.playToServer(GrabUndockPacket.TYPE, GrabUndockPacket.STREAM_CODEC, GrabUndockPacket::handle);
        r.playToClient(StrandPointsPacket.TYPE, StrandPointsPacket.STREAM_CODEC,
                StrandPointsPacket::handle);
        r.playToClient(com.lycoris.loconautics.network.packets.train.SubLevelFrameSyncPacket.TYPE,
                com.lycoris.loconautics.network.packets.train.SubLevelFrameSyncPacket.STREAM_CODEC,
                com.lycoris.loconautics.network.packets.train.SubLevelFrameSyncPacket::handle);
        r.playToClient(com.lycoris.loconautics.network.packets.attachment.HoseFlowPacket.TYPE,
                com.lycoris.loconautics.network.packets.attachment.HoseFlowPacket.STREAM_CODEC,
                com.lycoris.loconautics.network.packets.attachment.HoseFlowPacket::handle);
        r.playToServer(StrandHookGrabPacket.TYPE, StrandHookGrabPacket.STREAM_CODEC,
                StrandHookGrabPacket::handle);
        r.playToServer(com.lycoris.loconautics.network.packets.attachment.StrandKnotGrabPacket.TYPE,
                com.lycoris.loconautics.network.packets.attachment.StrandKnotGrabPacket.STREAM_CODEC,
                com.lycoris.loconautics.network.packets.attachment.StrandKnotGrabPacket::handle);
        r.playToServer(SteelCableBreakPacket.TYPE, SteelCableBreakPacket.STREAM_CODEC,
                SteelCableBreakPacket::handle);
        r.playToClient(HoseSprayColorSyncPacket.TYPE, HoseSprayColorSyncPacket.STREAM_CODEC,
                HoseSprayColorSyncPacket::handle);

        r.playToServer(MapColorRequestPacket.TYPE, MapColorRequestPacket.STREAM_CODEC,
                MapColorRequestPacket::handle);
        r.playToClient(MapColorResponsePacket.TYPE, MapColorResponsePacket.STREAM_CODEC,
                MapColorResponsePacket::handle);
        r.playToClient(ConductorHatPacket.TYPE, ConductorHatPacket.STREAM_CODEC, ConductorHatPacket::handle);
        r.playToClient(com.lycoris.loconautics.network.packets.schedule.BlazeConductorHatPacket.TYPE,
                com.lycoris.loconautics.network.packets.schedule.BlazeConductorHatPacket.STREAM_CODEC,
                com.lycoris.loconautics.network.packets.schedule.BlazeConductorHatPacket::handle);
    }

    public static void sendMount(ServerPlayer player, boolean mounted, BlockPos pos) {
        PacketDistributor.sendToPlayer(player, new AnalogControllerMountPacket(mounted, pos));
    }

    public static void sendStationPrompt(ServerPlayer player, Component text, boolean shadow) {
        PacketDistributor.sendToPlayer(player, new AnalogControllerStationPromptPacket(text, shadow));
    }

    public static void sendDismount(BlockPos pos) {
        CatnipServices.NETWORK.sendToServer(new AnalogControllerDismountPacket(pos));
    }
}
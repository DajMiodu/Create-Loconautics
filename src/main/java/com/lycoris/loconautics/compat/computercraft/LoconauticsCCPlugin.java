package com.lycoris.loconautics.compat.computercraft;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class LoconauticsCCPlugin {
    private LoconauticsCCPlugin() {}

    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                PeripheralCapability.get(),
                LoconauticsRegistries.BEARING_AXLE_BE.get(),
                (axle, side) -> LoconauticsTrainPeripheral.of(axle));
        event.registerBlockEntity(
                PeripheralCapability.get(),
                LoconauticsRegistries.TRANSMISSION_BE.get(),
                (transmission, side) -> TransmissionPeripheral.of(transmission));
        event.registerBlockEntity(
                PeripheralCapability.get(),
                LoconauticsRegistries.TURNTABLE_BEARING_BE.get(),
                (bearing, side) -> TurntableBearingPeripheral.of(bearing));
    }

    public static void install() {
        ComputerCraftAPI.registerAPIFactory(LoconauticsLuaApi::new);
    }
}

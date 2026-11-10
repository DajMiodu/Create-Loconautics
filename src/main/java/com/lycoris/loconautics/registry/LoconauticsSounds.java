package com.lycoris.loconautics.registry;

import com.lycoris.loconautics.LoconauticsConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class LoconauticsSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, LoconauticsConstants.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SWITCH_TOGGLE = SOUND_EVENTS.register(
            "switch_toggle", () -> SoundEvent.createVariableRangeEvent(LoconauticsConstants.id("switch_toggle")));

    private LoconauticsSounds() {
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}

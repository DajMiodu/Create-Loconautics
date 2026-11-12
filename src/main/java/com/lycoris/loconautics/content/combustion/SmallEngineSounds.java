package com.lycoris.loconautics.content.combustion;

import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.WeakHashMap;

@OnlyIn(Dist.CLIENT)
final class SmallEngineSounds {

    private static final Map<SmallEngineBlockEntity, SmallEngineSound> ACTIVE = new WeakHashMap<>();

    private SmallEngineSounds() {
    }

    static void tick(SmallEngineBlockEntity engine, boolean running) {
        SmallEngineSound sound = ACTIVE.get(engine);
        if (running && !(engine.getLevel() instanceof PonderLevel) && (sound == null || sound.isStopped())) {
            sound = new SmallEngineSound(engine);
            ACTIVE.put(engine, sound);
            Minecraft.getInstance().getSoundManager().play(sound);
        } else if (!running && sound != null && sound.isStopped()) {
            ACTIVE.remove(engine);
        }
    }
}

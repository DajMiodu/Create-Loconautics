package com.lycoris.loconautics.mixin.compat.railways;

import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.createmod.catnip.data.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.UUID;

@Mixin(targets = "com.railwayteam.railways.content.bogey_menu.handler.BogeyMenuHandlerServer", remap = false)
public interface BogeyMenuHandlerServerAccessor {

    @Invoker("getStyle")
    static Pair<BogeyStyle, BogeySizes.BogeySize> loconautics$getStyle(UUID player) {
        throw new AssertionError();
    }

    @Invoker("addStyle")
    static void loconautics$addStyle(UUID player, Pair<BogeyStyle, BogeySizes.BogeySize> style) {
        throw new AssertionError();
    }
}

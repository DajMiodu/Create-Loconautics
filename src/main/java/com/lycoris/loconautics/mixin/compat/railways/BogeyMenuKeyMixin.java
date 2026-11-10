package com.lycoris.loconautics.mixin.compat.railways;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lycoris.loconautics.content.casingpicker.BogeyItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

@Mixin(targets = "com.railwayteam.railways.content.bogey_menu.handler.BogeyMenuEventsHandler", remap = false)
public abstract class BogeyMenuKeyMixin {

    @WrapOperation(method = "onKeyInput", at = @At(value = "INVOKE",
            target = "Lcom/railwayteam/railways/util/EntityUtils;isHolding"
                    + "(Lnet/minecraft/world/entity/player/Player;Ljava/util/function/Predicate;)Z"))
    private static boolean loconautics$alsoOpenForBogey(Player player, Predicate<ItemStack> holding,
                                                         Operation<Boolean> original) {
        return original.call(player, holding)
                || original.call(player, (Predicate<ItemStack>) stack -> stack.getItem() instanceof BogeyItem);
    }
}

package com.lycoris.loconautics.content.casingpicker;

import com.lycoris.loconautics.client.scrollmenu.ScrollMenu;
import com.lycoris.loconautics.client.scrollmenu.ScrollMenuHandler;
import com.lycoris.loconautics.network.packets.casingpicker.CasingSwapPacket;
import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.AllBlocks;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class CasingPickerClient {

    public static final KeyMapping OPEN_KEY = new KeyMapping(
            "key.loconautics.casing_picker", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            InputConstants.KEY_C, "key.categories.loconautics");

    private static final int CASING = 0;
    private static final int BOGEY = 1;

    private CasingPickerClient() {
    }

    public static boolean tryOpen() {
        if (ScrollMenuHandler.isOpen()) return false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        ItemStack held = player.getMainHandItem();
        boolean holdingBogey = held.getItem() instanceof BogeyItem;
        if (!holdingBogey && !AllBlocks.RAILWAY_CASING.isIn(held)) return false;

        BogeyChoice bogey = holdingBogey ? BogeyItem.choiceOf(held) : BogeyChoice.of(player.getUUID());
        ItemStack bogeyStack = BogeyItem.of(bogey, 1);
        ItemStack casingStack = AllBlocks.RAILWAY_CASING.asStack();
        List<ScrollMenu.Entry> entries = List.of(
                new ScrollMenu.Entry(casingStack, casingStack.getHoverName()),
                new ScrollMenu.Entry(bogeyStack, bogeyStack.getHoverName()));

        ScrollMenu menu = new ScrollMenu(entries, holdingBogey ? BOGEY : CASING,
                idx -> CatnipServices.NETWORK.sendToServer(new CasingSwapPacket(idx == BOGEY)),
                false, OPEN_KEY.getKey().getValue());
        return ScrollMenuHandler.open(menu);
    }
}

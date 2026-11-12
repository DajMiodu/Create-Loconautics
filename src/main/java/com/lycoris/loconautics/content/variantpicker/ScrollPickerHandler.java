package com.lycoris.loconautics.content.variantpicker;

import com.lycoris.loconautics.client.scrollmenu.ScrollMenu;
import com.lycoris.loconautics.client.scrollmenu.ScrollMenuHandler;
import com.lycoris.loconautics.content.conductorshat.ConductorsGoggles;
import com.lycoris.loconautics.content.conductorshat.ConductorsGogglesItem;
import com.lycoris.loconautics.content.conductorshat.ConductorsHatItem;
import com.lycoris.loconautics.content.conductorshat.ConductorsHats;
import com.lycoris.loconautics.network.packets.variantpicker.BlockVariantApplyPacket;
import com.lycoris.loconautics.network.packets.variantpicker.HatColorApplyPacket;
import com.mojang.blaze3d.platform.InputConstants;

import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@OnlyIn(Dist.CLIENT)
public final class ScrollPickerHandler {

    public static final KeyMapping OPEN_KEY = new KeyMapping(
            "key.loconautics.variant_picker", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            InputConstants.KEY_LALT, "key.categories.loconautics");

    private ScrollPickerHandler() {
    }

    public static boolean tryOpen() {
        if (ScrollMenuHandler.isOpen()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (mc.player.getMainHandItem().getItem() instanceof ConductorsHatItem hat) {
            return openColors(hat.getColor(), color -> ConductorsHats.HATS.get(color).get());
        }
        if (mc.player.getMainHandItem().getItem() instanceof ConductorsGogglesItem goggles) {
            return openColors(goggles.getColor(), color -> ConductorsGoggles.GOGGLES.get(color).get());
        }
        Block held = Block.byItem(mc.player.getMainHandItem().getItem());
        BlockVariantRegistry.Location loc = BlockVariantRegistry.locate(held);
        if (loc == null) return false;
        List<BlockVariantRegistry.VariantType> types = BlockVariantRegistry.family(loc.family());
        if (types.size() < 2) return false;

        List<Block> blocks = new ArrayList<>();
        List<ScrollMenu.Entry> entries = new ArrayList<>();
        for (BlockVariantRegistry.VariantType type : types) {
            Block b = blockAt(type, loc);
            blocks.add(b);
            Component name = b != null ? b.getName() : Component.translatable(type.translationKey());
            entries.add(new ScrollMenu.Entry(b != null ? new ItemStack(b) : ItemStack.EMPTY, name));
        }

        ScrollMenu menu = new ScrollMenu(entries, loc.type(), idx -> {
            Block chosen = blocks.get(idx);
            if (chosen != null) {
                CatnipServices.NETWORK.sendToServer(new BlockVariantApplyPacket(
                        InteractionHand.MAIN_HAND, BuiltInRegistries.BLOCK.getKey(chosen)));
            }
        }, false, OPEN_KEY.getKey().getValue());
        return ScrollMenuHandler.open(menu);
    }

    private static boolean openColors(DyeColor current, Function<DyeColor, Item> itemFor) {
        List<ScrollMenu.Entry> entries = new ArrayList<>();
        for (DyeColor color : DyeColor.values()) {
            Item item = itemFor.apply(color);
            entries.add(new ScrollMenu.Entry(new ItemStack(item), item.getDescription()));
        }
        ScrollMenu menu = new ScrollMenu(entries, current.getId(), idx ->
                CatnipServices.NETWORK.sendToServer(new HatColorApplyPacket(InteractionHand.MAIN_HAND, idx)),
                false, OPEN_KEY.getKey().getValue());
        return ScrollMenuHandler.open(menu);
    }

    private static Block blockAt(BlockVariantRegistry.VariantType type, BlockVariantRegistry.Location loc) {
        if (loc.material() >= 0) {
            Block wrapped = type.wrappedAt(loc.color(), loc.material());
            if (wrapped != null) return wrapped;
        }
        return type.at(loc.color());
    }
}

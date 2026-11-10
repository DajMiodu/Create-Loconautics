package com.lycoris.loconautics.content.trainlookup.commands;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class LookupChat {

    private LookupChat() {
    }

    public static Component divider() {
        return Component.literal("─".repeat(34)).withStyle(ChatFormatting.DARK_GRAY);
    }

    public static String dimensionName(ResourceKey<Level> dimension) {
        if (Level.OVERWORLD.equals(dimension)) {
            return "Overworld";
        }
        if (Level.NETHER.equals(dimension)) {
            return "Nether";
        }
        if (Level.END.equals(dimension)) {
            return "The End";
        }
        String path = dimension.location().getPath();
        return Character.toUpperCase(path.charAt(0)) + path.substring(1).replace('_', ' ');
    }
}

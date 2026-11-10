package com.lycoris.loconautics.content.controls.panel;

import com.lycoris.loconautics.content.controls.panel.style.PanelStyleSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

public final class PanelPartNbt {

    private PanelPartNbt() {}

    @Nullable
    public static BlockPos readLink(CompoundTag tag) {
        return tag.contains("Link") ? BlockPos.of(tag.getLong("Link")) : null;
    }

    public static int readBody(CompoundTag tag, int fallback) {
        return Mth.clamp(tag.contains("Body") ? tag.getInt("Body") : fallback, 0, PanelPartType.BODY_VARIANTS - 1);
    }

    public static int readColor(CompoundTag tag, String key, int fallback) {
        return Mth.clamp(tag.contains(key) ? tag.getInt(key) : fallback, 0, PanelPartType.PALETTE_COLORS - 1);
    }

    public static int readStyle(CompoundTag tag, PanelStyleSet set, int fallback) {
        return set.clamp(tag.contains("Style") ? tag.getInt("Style") : fallback);
    }

    public static int readOptions(CompoundTag tag, PanelStyleSet set, int fallback) {
        return set.clampOptions(tag.contains("Options") ? tag.getInt("Options") : fallback);
    }

    public static void writeAppearance(CompoundTag tag, @Nullable BlockPos link, int body, String colorKey,
                                       int color, int style, int options) {
        if (link != null) tag.putLong("Link", link.asLong());
        tag.putInt("Body", body);
        tag.putInt(colorKey, color);
        tag.putInt("Style", style);
        tag.putInt("Options", options);
    }
}

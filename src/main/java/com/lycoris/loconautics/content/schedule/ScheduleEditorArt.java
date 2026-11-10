package com.lycoris.loconautics.content.schedule;

import com.lycoris.loconautics.mixin.client.gui.SlotYAccessor;
import com.simibubi.create.content.trains.schedule.ScheduleMenu;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.Map;
import java.util.WeakHashMap;

public final class ScheduleEditorArt {

    public static final int EXTRA = 20;

    private static final int EDITOR_HEIGHT = 89;
    private static final int SPLIT = 66;
    private static final int STRIPE = 2;

    private static final Map<ScheduleMenu, Boolean> TALL = new WeakHashMap<>();

    private ScheduleEditorArt() {
    }

    public static void renderEditor(GuiGraphics graphics, int x, int y) {
        ResourceLocation texture = AllGuiTextures.SCHEDULE_EDITOR.getLocation();
        graphics.blit(texture, x, y, 0, 0, 256, SPLIT);
        for (int i = 0; i < EXTRA / STRIPE; i++) {
            graphics.blit(texture, x, y + SPLIT + i * STRIPE, 0, SPLIT, 256, STRIPE);
        }
        graphics.blit(texture, x, y + SPLIT + EXTRA, 0, SPLIT, 256, EDITOR_HEIGHT - SPLIT);
    }

    public static void setTall(ScheduleMenu menu, boolean tall) {
        if (TALL.getOrDefault(menu, false) == tall) {
            return;
        }
        TALL.put(menu, tall);
        int shift = tall ? EXTRA : -EXTRA;
        for (Slot slot : ((AbstractContainerMenu) menu).slots) {
            if (slot.container instanceof Inventory) {
                ((SlotYAccessor) slot).loconautics$setY(slot.y + shift);
            }
        }
    }
}

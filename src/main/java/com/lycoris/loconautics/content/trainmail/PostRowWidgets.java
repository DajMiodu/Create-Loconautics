package com.lycoris.loconautics.content.trainmail;

import com.lycoris.loconautics.content.schedule.ScheduleEditorArt;
import com.lycoris.loconautics.mixin.client.gui.ModularGuiLineBuilderAccessor;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class PostRowWidgets {

    private static final int ROW_WIDTH = 121;
    private static final int MAX_WILDCARDS = 3;
    private static final int ICON_COLUMN = 53;
    private static final int ROW_TOP = 87;
    private static final int ROW_BOX_HEIGHT = 18;
    private static final int ROW_BOX_WIDTH = 120;

    @Nullable
    private static EditBox stationBox;

    private PostRowWidgets() {
    }

    public static void addRows(ModularGuiLineBuilder builder, boolean delivers) {
        builder.addTextInput(0, ROW_WIDTH, (box, area) -> {
            limitWildcards(box);
            area.withTooltip(PostRows.stationTooltip(delivers));
            stationBox = box;
        }, PostRows.STATION_KEY);

        ModularGuiLineBuilderAccessor access = (ModularGuiLineBuilderAccessor) builder;
        new ModularGuiLineBuilder(access.loconautics$getFont(), access.loconautics$getTarget(),
                access.loconautics$getX(), access.loconautics$getY() + ScheduleEditorArt.EXTRA)
                .addTextInput(0, ROW_WIDTH, (box, area) -> {
                    limitWildcards(box);
                    area.withTooltip(PostRows.addressTooltip(delivers));
                }, PostRows.NAME_KEY);
    }

    @Nullable
    public static EditBox takeStationBox() {
        EditBox box = stationBox;
        stationBox = null;
        return box;
    }

    public static boolean renderEditorIcons(GuiGraphics graphics, int x, int y) {
        if (y < 60) {
            return false;
        }
        GuiGameElement.of(AllBlocks.PACKAGE_POSTBOXES.get(DyeColor.WHITE).asStack()).at(x, y).render(graphics);
        AllGuiTextures.SCHEDULE_EDITOR_INACTIVE_SLOT.render(graphics, x - 1, y + ScheduleEditorArt.EXTRA - 1);
        ItemStack box = PackageStyles.getDefaultBox();
        GuiGameElement.of(box).at(x, y + ScheduleEditorArt.EXTRA).render(graphics);
        return true;
    }

    @Nullable
    public static List<Component> secondRowTooltip(boolean delivers, int leftPos, int topPos, int mouseX,
                                                   int mouseY) {
        int left = leftPos + ICON_COLUMN;
        int top = topPos + ROW_TOP + ScheduleEditorArt.EXTRA;
        boolean inside = mouseX >= left && mouseX < left + ROW_BOX_WIDTH
                && mouseY >= top && mouseY < top + ROW_BOX_HEIGHT;
        return inside ? PostRows.addressTooltip(delivers) : null;
    }

    private static void limitWildcards(EditBox box) {
        box.setFilter(s -> StringUtils.countMatches(s, '*') <= MAX_WILDCARDS);
    }
}

package com.lycoris.loconautics.mixin.client.schedule;

import com.lycoris.loconautics.content.schedule.ScheduleEditorArt;
import com.lycoris.loconautics.content.trainmail.PostRowWidgets;
import com.lycoris.loconautics.content.trainmail.PostalEntry;
import com.lycoris.loconautics.content.trainmail.PostalOrder;
import com.simibubi.create.content.trains.schedule.DestinationSuggestions;
import com.simibubi.create.content.trains.schedule.IScheduleInput;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleMenu;
import com.simibubi.create.content.trains.schedule.ScheduleScreen;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import net.createmod.catnip.data.IntAttached;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ScheduleScreen.class)
public abstract class SchedulePostalScreenMixin extends AbstractSimiContainerScreen<ScheduleMenu> {

    @Shadow
    private ScheduleInstruction editingDestination;

    @Shadow
    private ScheduleWaitCondition editingCondition;

    @Shadow
    private DestinationSuggestions destinationSuggestions;

    @Shadow
    protected abstract List<IntAttached<String>> getViableStations(IScheduleInput field);

    @Shadow
    protected abstract void onDestinationEdited(String text);

    @Shadow
    protected abstract void renderActionTooltip(GuiGraphics graphics, List<Component> tooltip, int mx, int my);

    private SchedulePostalScreenMixin(ScheduleMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Unique
    private boolean loconautics$postal() {
        return editingCondition == null && editingDestination instanceof PostalEntry;
    }

    @Inject(method = "startEditing", at = @At("HEAD"))
    private void loconautics$postalLast(CallbackInfo ci) {
        PostalOrder.moveLast(Schedule.INSTRUCTION_TYPES);
    }

    @Inject(method = "updateEditorSubwidgets", at = @At("RETURN"))
    private void loconautics$postalRows(IScheduleInput field, CallbackInfo ci) {
        boolean postal = field instanceof PostalEntry;
        ScheduleEditorArt.setTall(menu, postal);
        EditBox stationBox = PostRowWidgets.takeStationBox();
        if (!postal || stationBox == null) {
            return;
        }
        destinationSuggestions = new DestinationSuggestions(this.minecraft, this, stationBox, this.font,
                getViableStations(field), false, topPos + 33);
        destinationSuggestions.setAllowSuggestions(true);
        destinationSuggestions.updateCommandInfo();
        stationBox.setResponder(this::onDestinationEdited);
    }

    @Inject(method = "stopEditing", at = @At("HEAD"))
    private void loconautics$flatAgain(CallbackInfo ci) {
        ScheduleEditorArt.setTall(menu, false);
    }

    @Redirect(method = "renderBg", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/foundation/gui/AllGuiTextures;render(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            ordinal = 1))
    private void loconautics$editorArt(AllGuiTextures texture, GuiGraphics graphics, int x, int y) {
        if (loconautics$postal()) {
            ScheduleEditorArt.renderEditor(graphics, x, y);
        } else {
            texture.render(graphics, x, y);
        }
    }

    @Redirect(method = "renderBg", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/foundation/gui/AllGuiTextures;render(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            ordinal = 2))
    private void loconautics$inventoryArt(AllGuiTextures texture, GuiGraphics graphics, int x, int y) {
        texture.render(graphics, x, loconautics$postal() ? y + ScheduleEditorArt.EXTRA : y);
    }

    @ModifyArg(method = "renderBg", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"),
            index = 3)
    private int loconautics$inventoryTitle(int y) {
        return loconautics$postal() ? y + ScheduleEditorArt.EXTRA : y;
    }

    @Inject(method = "renderForeground", at = @At("RETURN"))
    private void loconautics$addressTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks,
                                            CallbackInfo ci) {
        if (!loconautics$postal()) {
            return;
        }
        List<Component> tooltip = PostRowWidgets.secondRowTooltip(
                ((PostalEntry) editingDestination).loconautics$delivers(), leftPos, topPos, mouseX, mouseY);
        if (tooltip != null) {
            renderActionTooltip(graphics, tooltip, mouseX, mouseY);
        }
    }
}

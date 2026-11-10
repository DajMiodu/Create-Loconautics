package com.lycoris.loconautics.mixin.create.schedule;

import com.lycoris.loconautics.content.trainmail.CreateTrainMail;
import com.lycoris.loconautics.content.trainmail.PostRowWidgets;
import com.lycoris.loconautics.content.trainmail.PostRows;
import com.lycoris.loconautics.content.trainmail.PostalEntry;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.destination.FetchPackagesInstruction;
import com.simibubi.create.content.trains.schedule.destination.TextScheduleInstruction;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(FetchPackagesInstruction.class)
public abstract class FetchPackagesInstructionMixin extends TextScheduleInstruction implements PostalEntry {

    @Inject(method = "getTitleAs", at = @At("HEAD"), cancellable = true)
    private void loconautics$title(String type, CallbackInfoReturnable<List<Component>> cir) {
        cir.setReturnValue(PostRows.fetchTitle(textData(PostRows.STATION_KEY), getLabelText()));
    }

    @Inject(method = "getSecondLineTooltip", at = @At("HEAD"), cancellable = true)
    private void loconautics$iconTooltip(int slot, CallbackInfoReturnable<List<Component>> cir) {
        cir.setReturnValue(PostRows.stationTooltip(false));
    }

    @Inject(method = "start", at = @At("HEAD"), cancellable = true)
    private void loconautics$start(ScheduleRuntime runtime, Level level, CallbackInfoReturnable<DiscoveredPath> cir) {
        cir.setReturnValue(CreateTrainMail.fetch(runtime, getLabelText(), textData(PostRows.STATION_KEY)));
    }

    @Override
    public String loconautics$address() {
        return getLabelText();
    }

    @Override
    public String loconautics$post() {
        return textData(PostRows.STATION_KEY);
    }

    @Override
    public boolean loconautics$delivers() {
        return false;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initConfigurationWidgets(ModularGuiLineBuilder builder) {
        PostRowWidgets.addRows(builder, false);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean renderSpecialIcon(GuiGraphics graphics, int x, int y) {
        return PostRowWidgets.renderEditorIcons(graphics, x, y);
    }
}

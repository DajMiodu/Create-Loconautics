package com.lycoris.loconautics.mixin.client.station;

import com.lycoris.loconautics.network.packets.train.DisassembleSableTrainPacket;
import com.lycoris.loconautics.network.packets.train.StationSetTrainColorPacket;
import com.lycoris.loconautics.network.packets.train.StationSetTrainNamePacket;
import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TrainIconType;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.content.trains.station.StationScreen;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StationScreen.class, remap = false)
public abstract class StationScreenMixin extends Screen {

    @Shadow
    private IconButton disassembleTrainButton;

    @Shadow
    private IconButton newTrainButton;

    @Shadow
    private IconButton dropScheduleButton;

    @Shadow
    private EditBox trainNameBox;

    @Shadow
    private ScrollInput colorTypeScroll;

    @Shadow
    private int messedWithColors;

    @Unique
    private boolean loconautics$forced;

    @Unique
    private ScrollInput loconautics$iconScroll;

    @Unique
    private java.util.List<net.minecraft.resources.ResourceLocation> loconautics$iconTypes;

    @Unique
    private LerpedFloat loconautics$trainPosition;

    @Unique
    private int loconautics$leaving;

    @Unique
    private boolean loconautics$hadTrain;

    @Unique
    private boolean loconautics$seeded;

    @Unique
    private boolean loconautics$sawEmpty;

    @Unique
    private int loconautics$absent;

    @Unique
    private float loconautics$lastApproach = Float.MAX_VALUE;

    @Unique
    private int loconautics$disassembling;

    @Unique
    private static final int loconautics$LOST_GRACE = 5;

    @Unique
    private static final float loconautics$APPROACH_RANGE = 30.0f;

    @Unique
    private int[] loconautics$spans = new int[0];

    private StationScreenMixin() {
        super(Component.empty());
    }

    @Unique
    private StationBlockEntity loconautics$be() {
        return ((AbstractStationScreenAccessor) this).loconautics$getBlockEntity();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void loconautics$routeColorScroll(CallbackInfo ci) {
        int bgWidth = AllGuiTextures.STATION.getWidth();
        int x = (this.width - bgWidth) / 2;
        int y = (this.height - AllGuiTextures.STATION.getHeight()) / 2;

        loconautics$trainPosition = LerpedFloat.linear().startWithValue(0);
        loconautics$leaving = 0;
        loconautics$seeded = false;
        loconautics$sawEmpty = false;
        loconautics$absent = 0;
        loconautics$lastApproach = Float.MAX_VALUE;

        loconautics$iconTypes = TrainIconType.REGISTRY.keySet().stream().toList();
        loconautics$iconScroll = new ScrollInput(x + 4, y + 17, 162, 14)
                .titled(CreateLang.translateDirect("station.icon_type"));
        loconautics$iconScroll.withRange(0, loconautics$iconTypes.size());
        loconautics$iconScroll.withStepFunction(ctx -> -loconautics$iconScroll.standardStep().apply(ctx));
        loconautics$iconScroll.calling(index -> loconautics$sendStyle());
        loconautics$iconScroll.active = false;
        loconautics$iconScroll.visible = false;
        this.addRenderableWidget(loconautics$iconScroll);

        this.colorTypeScroll.calling(index -> {
            StationBlockEntity be = loconautics$be();
            if (SableTrainClientRegistry.isStationParked(be.getBlockPos())) {
                loconautics$sendStyle();
                return;
            }
            Train train = ((AbstractStationScreenAccessor) this).loconautics$getDisplayedTrain().get();
            if (train != null) {
                train.mapColorIndex = index;
                this.messedWithColors = 10;
            }
        });
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void loconautics$showSableDisassemble(CallbackInfo ci) {
        StationBlockEntity be = loconautics$be();
        BlockPos pos = be.getBlockPos();
        boolean hasTrain = SableTrainClientRegistry.stationHasTrain(pos);
        if (loconautics$disassembling > 0) {
            loconautics$disassembling--;
        }
        loconautics$driveArrival(pos, hasTrain);
        if (!hasTrain) {
            if (loconautics$showingTrain()) {
                this.newTrainButton.visible = false;
                this.newTrainButton.active = false;
                if (loconautics$leaving > 0) {
                    loconautics$hideTrainControls();
                }
            } else if (loconautics$forced) {
                loconautics$hideTrainControls();
                loconautics$forced = false;
            }
            return;
        }
        loconautics$forced = true;

        this.newTrainButton.visible = false;
        this.newTrainButton.active = false;
        this.disassembleTrainButton.visible = true;
        this.disassembleTrainButton.active = SableTrainClientRegistry.isStationParked(be.getBlockPos());
        this.disassembleTrainButton.withCallback(() -> {
            loconautics$disassembling = 60;
            CatnipServices.NETWORK.sendToServer(new DisassembleSableTrainPacket(be.getBlockPos()));
        });

        this.dropScheduleButton.visible = true;
        this.dropScheduleButton.active = false;

        this.colorTypeScroll.visible = true;
        this.colorTypeScroll.active = true;
        this.loconautics$iconScroll.visible = true;
        this.loconautics$iconScroll.active = true;

        // while the cursor is on the widget the player owns the value, otherwise the server echo does
        if (!this.colorTypeScroll.isHovered()) {
            this.colorTypeScroll.setState(SableTrainClientRegistry.stationMapColorIndex(be.getBlockPos()));
        }
        if (!this.loconautics$iconScroll.isHovered()) {
            this.loconautics$iconScroll.setState(
                    loconautics$iconTypes.indexOf(loconautics$currentIcon().getId()));
        }

        if (!this.trainNameBox.active) {
            this.trainNameBox.active = true;
            this.addRenderableWidget(this.trainNameBox);
        }
        if (!this.trainNameBox.isFocused()) {
            this.trainNameBox.setValue(SableTrainClientRegistry.stationTrainName(be.getBlockPos()));
        }
    }

    @Unique
    private void loconautics$hideTrainControls() {
        this.disassembleTrainButton.visible = false;
        this.disassembleTrainButton.active = false;
        this.dropScheduleButton.visible = false;
        this.dropScheduleButton.active = false;
        this.colorTypeScroll.visible = false;
        this.colorTypeScroll.active = false;
        this.loconautics$iconScroll.visible = false;
        this.loconautics$iconScroll.active = false;
    }

    @Unique
    private void loconautics$driveArrival(BlockPos pos, boolean hasTrain) {
        if (hasTrain) {
            if (!loconautics$hadTrain) {
                loconautics$lastApproach = Float.MAX_VALUE;
            }
            loconautics$spans = SableTrainClientRegistry.stationCarriages(pos);
            loconautics$leaving = 0;
            loconautics$absent = 0;
            loconautics$hadTrain = true;
        } else if (loconautics$hadTrain) {
            loconautics$absent++;
            if (loconautics$absent < loconautics$LOST_GRACE) {
                return;
            }
            loconautics$hadTrain = false;
            loconautics$leaving = loconautics$lastApproach <= 0.0f && loconautics$disassembling <= 0 ? 80 : 0;
        } else if (loconautics$leaving > 0) {
            loconautics$leaving--;
        }

        if (!hasTrain && loconautics$leaving <= 0) {
            loconautics$seeded = false;
            loconautics$sawEmpty = true;
            loconautics$lastApproach = Float.MAX_VALUE;
            return;
        }

        int bgWidth = AllGuiTextures.STATION.getWidth();
        int target = loconautics$targetPos();
        if (!hasTrain) {
            float f = 1.0f - loconautics$leaving / 80.0f;
            loconautics$trainPosition.setValue(target + f * f * f * (bgWidth - target + 5));
            return;
        }

        float approach = SableTrainClientRegistry.stationApproach(pos);
        if (approach >= Float.MAX_VALUE) {
            approach = 0.0f;
        }
        approach = Math.min(approach, loconautics$lastApproach);
        loconautics$lastApproach = approach;

        float f = Mth.clamp(approach / loconautics$APPROACH_RANGE, 0.0f, 1.0f);
        float slide = target - (target + loconautics$rowWidth()) * f;
        if (loconautics$seeded) {
            float shown = loconautics$trainPosition.getValue();
            loconautics$trainPosition.setValue(shown + (slide - shown) * 0.25f);
            return;
        }
        float start = loconautics$sawEmpty ? target - (target + loconautics$rowWidth()) : slide;
        loconautics$trainPosition.startWithValue(start);
        loconautics$seeded = true;
    }

    @Unique
    private int loconautics$rowWidth() {
        TrainIconType icon = loconautics$scrolledIcon();
        int w = icon.getIconWidth(TrainIconType.ENGINE);
        for (int i = loconautics$spans.length - 1; i >= 1; i--) {
            w += icon.getIconWidth(loconautics$spans[i]) + 1;
        }
        return w;
    }

    @Unique
    private int loconautics$targetPos() {
        int width = loconautics$rowWidth();
        int target = AllGuiTextures.STATION.getWidth() / 2 - width / 2;
        return width > 130 ? target - (width - 130) : target;
    }

    @Unique
    private float loconautics$edgeFade(float position, int offset, int bgWidth) {
        return Mth.clamp(Math.min((position + offset - 10) / 30.0f,
                (bgWidth - 40 - position - offset) / 30.0f), 0.0f, 1.0f);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"))
    private void loconautics$commitNameOnEnter(int key, int scan, int mods, CallbackInfoReturnable<Boolean> cir) {
        if ((key != GLFW.GLFW_KEY_ENTER && key != GLFW.GLFW_KEY_KP_ENTER) || !this.trainNameBox.isFocused()) {
            return;
        }
        StationBlockEntity be = loconautics$be();
        if (SableTrainClientRegistry.isStationParked(be.getBlockPos())) {
            loconautics$sendName(be);
        }
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void loconautics$commitNameOnClose(CallbackInfo ci) {
        if (this.trainNameBox == null) {
            return;
        }
        StationBlockEntity be = loconautics$be();
        if (SableTrainClientRegistry.isStationParked(be.getBlockPos())) {
            loconautics$sendName(be);
        }
    }

    @Unique
    private TrainIconType loconautics$currentIcon() {
        String id = SableTrainClientRegistry.stationIconType(loconautics$be().getBlockPos());
        if (id.isEmpty()) {
            return TrainIconType.getDefault();
        }
        net.minecraft.resources.ResourceLocation parsed = net.minecraft.resources.ResourceLocation.tryParse(id);
        return parsed == null ? TrainIconType.getDefault() : TrainIconType.byId(parsed);
    }

    @Unique
    private TrainIconType loconautics$scrolledIcon() {
        int index = this.loconautics$iconScroll.getState();
        if (index < 0 || index >= loconautics$iconTypes.size()) {
            return loconautics$currentIcon();
        }
        return TrainIconType.byId(loconautics$iconTypes.get(index));
    }

    @Unique
    private void loconautics$sendStyle() {
        int index = this.loconautics$iconScroll.getState();
        String iconId = index < 0 || index >= loconautics$iconTypes.size()
                ? "" : loconautics$iconTypes.get(index).toString();
        CatnipServices.NETWORK.sendToServer(new StationSetTrainColorPacket(
                loconautics$be().getBlockPos(), this.colorTypeScroll.getState(), iconId));
    }

    @Unique
    private void loconautics$sendName(StationBlockEntity be) {
        CatnipServices.NETWORK.sendToServer(
                new StationSetTrainNamePacket(be.getBlockPos(), this.trainNameBox.getValue()));
    }

    @Redirect(method = "renderWindow",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawString("
                            + "Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"),
            remap = false)
    private int loconautics$suppressIdleHeader(GuiGraphics graphics, Font font, Component text,
                                               int sx, int sy, int color, boolean shadow) {
        if (loconautics$showingTrain()) {
            return 0;
        }
        return graphics.drawString(font, text, sx, sy, color, shadow);
    }

    // return, not tail create bails out early when displayedtrain is null, which it always is for a sable train
    @Inject(method = "renderWindow", at = @At("RETURN"))
    private void loconautics$renderSableTrainDisplay(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks,
                                                     CallbackInfo ci) {
        if (!loconautics$showingTrain()) {
            return;
        }
        int bgWidth = AllGuiTextures.STATION.getWidth();
        int x = (this.width - bgWidth) / 2;
        int y = (this.height - AllGuiTextures.STATION.getHeight()) / 2;

        int[] spans = loconautics$spans;
        TrainIconType icon = loconautics$scrolledIcon();
        float position = loconautics$trainPosition.getValue(partialTicks);

        PoseStack ms = graphics.pose();
        ms.pushPose();
        ms.translate(position, 0, 0);
        RenderSystem.enableBlend();
        int offset = 0;
        for (int i = spans.length - 1; i >= 1; i--) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, loconautics$edgeFade(position, offset, bgWidth));
            offset += icon.render(spans[i], graphics, x + offset, y + 20) + 1;
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, loconautics$edgeFade(position, offset, bgWidth));
        offset += icon.render(TrainIconType.ENGINE, graphics, x + offset, y + 20);
        RenderSystem.disableBlend();
        ms.popPose();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        AllGuiTextures.STATION_TEXTBOX_TOP.render(graphics, x + 21, y + 42);
        UIRenderHelper.drawStretched(graphics, x + 21, y + 60, 150, 26, 0,
                AllGuiTextures.STATION_TEXTBOX_MIDDLE);
        AllGuiTextures.STATION_TEXTBOX_BOTTOM.render(graphics, x + 21, y + 86);
        int speechX = x + (int) Mth.clamp(position + offset - 13, 25.0f, 159.0f);
        AllGuiTextures.STATION_TEXTBOX_SPEECH.render(graphics, speechX, y + 38);

        if (!this.trainNameBox.isFocused()) {
            String name = this.trainNameBox.getValue();
            String shown = name;
            if (name.isEmpty()) {
                Component placeholder = CreateLang.translateDirect("train.unnamed");
                shown = placeholder.getString();
                graphics.drawString(this.font, placeholder, loconautics$nameBoxX(shown), y + 47, 0x808080, false);
            }
            int buttonX = loconautics$nameBoxX(shown) + this.font.width(shown) + 5;
            AllGuiTextures.STATION_EDIT_TRAIN_NAME.render(graphics, Math.min(buttonX, x + 156), y + 44);
        }

        loconautics$renderMapColorPreview(graphics);
    }

    @Unique
    private boolean loconautics$showingTrain() {
        return loconautics$hadTrain || loconautics$leaving > 0;
    }

    @Unique
    private void loconautics$renderMapColorPreview(GuiGraphics graphics) {
        AllGuiTextures sprite = AllGuiTextures.TRAINMAP_SPRITES;
        sprite.bind();
        int index = this.colorTypeScroll.getState();
        int colorRow = index / 4;
        int colorCol = index % 4;
        int rotation = (AnimationTickHolder.getTicks() / 5) % 8;

        for (int slice = 0; slice < 3; slice++) {
            int row = slice == 0 ? 1 : slice == 2 ? 2 : 3;
            graphics.blit(sprite.location, this.colorTypeScroll.getX() + 4, this.colorTypeScroll.getY() - 1,
                    rotation * 16 + colorCol * 128, row * 16 + colorRow * 64, 16, 16,
                    sprite.getWidth(), sprite.getHeight());
        }
    }

    @Unique
    private int loconautics$nameBoxX(String s) {
        int bgWidth = AllGuiTextures.STATION.getWidth();
        int x = (this.width - bgWidth) / 2;
        return x + bgWidth / 2 - (Math.min(this.font.width(s), this.trainNameBox.getWidth()) + 10) / 2;
    }
}

package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.client.scrollmenu.ScrollMenu;
import com.lycoris.loconautics.client.scrollmenu.ScrollMenuRenderer;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.AnimatedOverlayElementBase;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class ScrollMenuPonder extends AnimatedOverlayElementBase implements DeferredGui {

    private final ScrollMenu menu;

    private ScrollMenuPonder(ScrollMenu menu) {
        this.menu = menu;
    }

    public static ScrollMenuPonder open(ItemStack first, ItemStack second) {
        List<ScrollMenu.Entry> entries = List.of(
                new ScrollMenu.Entry(first, first.getHoverName()),
                new ScrollMenu.Entry(second, second.getHoverName()));
        return new ScrollMenuPonder(new ScrollMenu(entries, 0, index -> {
        }, false));
    }

    public void show(CreateSceneBuilder scene) {
        scene.addInstruction(ponder -> {
            menu.selected = 0;
            ponder.addElement(this);
            setVisible(true);
            fade.startWithValue(0);
            fade.chase(1, 0.4, LerpedFloat.Chaser.EXP);
        });
    }

    public void select(CreateSceneBuilder scene, int index) {
        scene.addInstruction(ponder -> menu.selected = index);
    }

    public void hide(CreateSceneBuilder scene) {
        scene.addInstruction(ponder -> fade.chase(0, 0.4, LerpedFloat.Chaser.EXP));
    }

    @Override
    public void tick(PonderScene scene) {
        fade.tickChaser();
    }

    @Override
    public void reset(PonderScene scene) {
        menu.selected = 0;
        fade.startWithValue(0);
        fade.chase(0, 0.4, LerpedFloat.Chaser.EXP);
    }

    @Override
    public void render(PonderScene scene, PonderUI screen, GuiGraphics graphics, float partialTicks, float fade) {
    }

    @Override
    public void drawDeferred(PonderScene scene, PonderUI screen, GuiGraphics graphics, float partialTicks, float fade) {
        if (fade < 0.0625f || screen.getActiveScene() != scene) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 50);
        ScrollMenuRenderer.draw(graphics, menu, fade, 1f, screen.width / 2, screen.height / 6);
        graphics.pose().popPose();
    }
}

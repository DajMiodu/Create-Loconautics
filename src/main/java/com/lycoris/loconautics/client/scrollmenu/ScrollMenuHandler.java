package com.lycoris.loconautics.client.scrollmenu;

import com.mojang.blaze3d.platform.InputConstants;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ScrollMenuHandler {

    private static final int IDLE_TIMEOUT = 100;

    private static ScrollMenu current;

    private ScrollMenuHandler() {
    }

    public static ScrollMenu current() {
        return current;
    }

    public static boolean isOpen() {
        return current != null && current.open;
    }

    public static boolean open(ScrollMenu menu) {
        if (current != null) return false;
        current = menu;
        menu.alpha.setValue(0);
        menu.nameAlpha.setValue(0);
        return true;
    }

    public static boolean onScroll(int dir) {
        if (current == null) return false;
        current.selected = current.wrapped(dir);
        current.idleTicks = 0;
        return true;
    }

    public static void confirm() {
        ScrollMenu s = current;
        if (s == null || !s.open) return;
        if (s.alwaysConfirm || s.selected != s.original) s.onConfirm.accept(s.selected);
        beginClose(0.4);
    }

    public static void cancel() {
        if (current != null) beginClose(0.7);
    }

    public static void forceClose() {
        current = null;
    }

    private static void beginClose(double speed) {
        if (current != null) {
            current.open = false;
            current.closeSpeed = speed;
        }
    }

    public static void tick() {
        ScrollMenu s = current;
        if (s == null) return;
        if (s.open) {
            if (!holdKeyDown(s)) confirm();
            else if (++s.idleTicks >= IDLE_TIMEOUT) confirm();
        }
        animate(s);
        if (!s.open && s.alpha.getValue() < 0.02f) current = null;
    }

    private static boolean holdKeyDown(ScrollMenu s) {
        long win = Minecraft.getInstance().getWindow().getWindow();
        for (int key : s.holdKeys) {
            if (InputConstants.isKeyDown(win, key)) return true;
        }
        return false;
    }

    private static void animate(ScrollMenu s) {
        s.alpha.chase(s.open ? 1.0 : 0.0, s.open ? 0.5 : s.closeSpeed, Chaser.EXP);
        s.scale.chase(s.open ? 1.0 : 0.9, 0.5, Chaser.EXP);
        s.nameAlpha.chase(1.0, 0.5, Chaser.EXP);
        s.alpha.tickChaser();
        s.scale.tickChaser();
        s.nameAlpha.tickChaser();
    }
}

package com.lycoris.loconautics.foundation.ui;

import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PoseScissor {

    private PoseScissor() {}

    // vanilla scissors ignore the pose stack, so a scaled screen has to move the rect itself
    public static void enable(GuiGraphics g, int minX, int minY, int maxX, int maxY) {
        Matrix4f pose = g.pose().last().pose();
        Vector3f low = pose.transformPosition(minX, minY, 0f, new Vector3f());
        Vector3f high = pose.transformPosition(maxX, maxY, 0f, new Vector3f());
        g.enableScissor((int) Math.floor(low.x), (int) Math.floor(low.y),
                (int) Math.ceil(high.x), (int) Math.ceil(high.y));
    }
}

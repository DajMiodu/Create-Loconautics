package com.lycoris.loconautics.client.gui.selector;

import com.lycoris.loconautics.foundation.ui.PoseScissor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;

public abstract class PartModelViewer {

    protected static final float PIVOT_X = 4f / 16f;
    protected static final float PIVOT_Y = 1f / 16f;
    protected static final float PIVOT_Z = 4f / 16f;

    protected static final float DEFAULT_YAW = 136.66673f;
    protected static final float DEFAULT_PITCH = 43.666695f;
    protected static final float DEFAULT_SCALE = 3.0759742f;
    protected static final float DEFAULT_PAN_X = 0f;
    protected static final float DEFAULT_PAN_Y = 2f;

    private static final float ZOOM_STEP = 1.1f;
    private static final int ZOOM_LIMIT = 16;

    private static float modelYaw = DEFAULT_YAW, modelPitch = DEFAULT_PITCH;
    private static float modelPanX = DEFAULT_PAN_X, modelPanY = DEFAULT_PAN_Y;
    private static int zoomLevel;

    protected BlockState state;

    private int modelDrag;
    private int vx, vy, vw, vh;

    public void setState(BlockState state) {
        this.state = state;
    }

    protected abstract void draw(PoseStack pose, MultiBufferSource buffers);

    protected void drawGlass(PoseStack pose, MultiBufferSource buffers) {
    }

    protected float iconDrop() {
        return 0f;
    }

    protected float iconScale() {
        return 1f;
    }

    public void render(GuiGraphics g, int x, int y, int w, int h) {
        vx = x;
        vy = y;
        vw = w;
        vh = h;
        if (state == null || w <= 0 || h <= 0) return;

        Minecraft mc = Minecraft.getInstance();
        mc.getTextureManager().getTexture(InventoryMenu.BLOCK_ATLAS).setFilter(false, false);

        float size = Math.min(w, h) * 0.5f * scale();
        PoseScissor.enable(g, x, y, x + w, y + h);
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x + w / 2f + modelPanX, y + h / 2f + modelPanY, 250);
        pose.scale(size, -size, size);
        pose.mulPose(Axis.XP.rotationDegrees(modelPitch));
        pose.mulPose(Axis.YP.rotationDegrees(modelYaw));
        pose.translate(-PIVOT_X, -PIVOT_Y, -PIVOT_Z);

        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        draw(pose, buf);
        drawGlass(pose, buf);
        buf.endBatch();
        pose.popPose();
        g.disableScissor();
    }

    public void renderIcon(GuiGraphics g, int cx, int cy, int box) {
        if (state == null || box <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        mc.getTextureManager().getTexture(InventoryMenu.BLOCK_ATLAS).setFilter(false, false);

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy + box * iconDrop(), 250);
        float size = box * 0.5f * DEFAULT_SCALE * iconScale();
        pose.scale(size, -size, size);
        pose.mulPose(Axis.XP.rotationDegrees(DEFAULT_PITCH));
        pose.mulPose(Axis.YP.rotationDegrees(DEFAULT_YAW));
        pose.translate(-PIVOT_X, -PIVOT_Y, -PIVOT_Z);

        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        draw(pose, buf);
        buf.endBatch();
        pose.popPose();
    }

    public void resetView() {
        modelYaw = DEFAULT_YAW;
        modelPitch = DEFAULT_PITCH;
        modelPanX = DEFAULT_PAN_X;
        modelPanY = DEFAULT_PAN_Y;
        zoomLevel = 0;
    }

    private static float scale() {
        return DEFAULT_SCALE * (float) Math.pow(ZOOM_STEP, zoomLevel);
    }

    public int zoom() {
        return zoomLevel;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (!inside(mx, my)) return false;
        modelDrag = button == 0 ? 1 : 2;
        return true;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (modelDrag == 1) {
            modelYaw += (float) dx;
            modelPitch = Mth.clamp((float) (modelPitch + dy), -90f, 90f);
            return true;
        }
        if (modelDrag == 2) {
            modelPanX += (float) dx;
            modelPanY += (float) dy;
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        boolean wasDragging = modelDrag != 0;
        modelDrag = 0;
        return wasDragging;
    }

    public boolean mouseScrolled(double mx, double my, double sy) {
        if (!inside(mx, my) || sy == 0) return false;
        zoomLevel = Mth.clamp(zoomLevel + (sy > 0 ? 1 : -1), -ZOOM_LIMIT, ZOOM_LIMIT);
        return true;
    }

    private boolean inside(double mx, double my) {
        return mx >= vx && mx < vx + vw && my >= vy && my < vy + vh;
    }
}

package com.lycoris.loconautics.content.controls.panel;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.client.LoconauticsSpriteShifts;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SpriteShiftEntry;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.state.BlockState;

public final class PanelBodyRenderer {

    public static final int BODY_STRIDE = 14;

    private static final int ATLAS_SIZE = 64;

    private PanelBodyRenderer() {}

    public static void renderPlate(PoseStack ms, VertexConsumer buffer, int light, BlockState state) {
        CachedBuffers.partial(LoconauticsPartialModels.PLATE, state).light(light).renderInto(ms, buffer);
    }

    public static void renderSlimPlate(PoseStack ms, VertexConsumer buffer, int light, BlockState state) {
        CachedBuffers.partial(LoconauticsPartialModels.SLIM_PLATE, state).light(light).renderInto(ms, buffer);
    }

    public static void renderBody(PoseStack ms, VertexConsumer buffer, int light, BlockState state, int body) {
        renderShell(LoconauticsPartialModels.BODY, LoconauticsSpriteShifts.BODY, ms, buffer, light, state, body);
    }

    public static void renderSlimBody(PoseStack ms, VertexConsumer buffer, int light, BlockState state, int body) {
        renderShell(LoconauticsPartialModels.SLIM_BODY, LoconauticsSpriteShifts.SLIM_BODY, ms, buffer, light, state,
                body);
    }

    public static void renderBigBody(PoseStack ms, VertexConsumer buffer, int light, BlockState state, int body) {
        renderShell(LoconauticsPartialModels.BIG_BODY, LoconauticsSpriteShifts.BIG_BODY, ms, buffer, light, state,
                body);
    }

    private static void renderShell(PartialModel model, SpriteShiftEntry shift, PoseStack ms, VertexConsumer buffer,
                                    int light, BlockState state, int body) {
        TextureAtlasSprite sheet = shift.getTarget();
        float pixelU = (sheet.getU1() - sheet.getU0()) / ATLAS_SIZE;
        CachedBuffers.partial(model, state)
                .light(light)
                .shiftUVScrolling(shift, pixelU * BODY_STRIDE * ((body + 1) % 4), 0)
                .renderInto(ms, buffer);
    }
}

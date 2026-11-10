package com.lycoris.loconautics.content.screwlink;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.common.strand.StrandKind;
import com.lycoris.loconautics.common.strand.StrandRenderer;
import com.lycoris.loconautics.content.grab.GrabClientHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

public final class ScrewLinkStrandRenderer {

    private ScrewLinkStrandRenderer() {
    }

    public static void renderAll(PoseStack ps, MultiBufferSource buffer, Vec3 camera, float partialTick) {
        StrandRenderer.renderAll(ps, buffer, camera, partialTick, StrandKind.SCREW_LINK.clientStrands(),
                LoconauticsPartialModels.SCREW_LINK_LINK, LoconauticsPartialModels.SCREW_LINK_KNOT,
                0.0, GrabClientHandler.grabbedStrand());
    }
}

package com.lycoris.loconautics.content.steelcable;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.common.strand.StrandKind;
import com.lycoris.loconautics.common.strand.StrandRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

public final class SteelCableStrandRenderer {

    private SteelCableStrandRenderer() {
    }

    public static void renderAll(PoseStack ps, MultiBufferSource buffer, Vec3 camera, float partialTick) {
        StrandRenderer.renderAll(ps, buffer, camera, partialTick, StrandKind.STEEL_CABLE.clientStrands(),
                LoconauticsPartialModels.STEEL_CABLE_ROPE, LoconauticsPartialModels.STEEL_CABLE_KNOT,
                0.0, SteelCableHover.hovered);
        StrandRenderer.renderAll(ps, buffer, camera, partialTick, StrandKind.HOSE_LINK.clientStrands(),
                LoconauticsPartialModels.HOSE_STRAND, LoconauticsPartialModels.HOSE_KNOT,
                0.0, SteelCableHover.hovered,
                com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlockEntity::clientNozzleAt,
                com.lycoris.loconautics.content.hose.flow.ClientHoseFlow::swell);
    }
}

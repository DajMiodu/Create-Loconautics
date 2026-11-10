package com.lycoris.loconautics.mixin.client.track;


import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import com.simibubi.create.content.trains.track.TrackVisual;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = TrackVisual.class, remap = false)
public abstract class TrackVisualCullingMixin implements SimpleDynamicVisual {

    @Shadow @Final private List<?> visuals;
    @Shadow @Final protected TrackBlockEntity blockEntity;
    @Shadow @Final protected BlockPos pos;
    @Shadow @Final protected BlockPos visualPos;

    @Shadow public abstract void _delete();

    @Shadow private void collectConnections() {
        throw new AssertionError();
    }

    @Unique
    private boolean loconautics$culled;

    @Unique
    private AABB loconautics$bounds;

    @Unique
    private boolean loconautics$boundsDirty = true;

    @Inject(method = "collectConnections", at = @At("TAIL"), remap = false)
    private void loconautics$markBoundsDirty(CallbackInfo ci) {
        loconautics$boundsDirty = true;
    }

    // update skips collectconnections entirely once blockentity.connections is empty, so that path never hit the inject above
    @Inject(method = "update", at = @At("HEAD"), remap = false)
    private void loconautics$markBoundsDirtyOnUpdate(float pt, CallbackInfo ci) {
        loconautics$boundsDirty = true;
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        if (loconautics$boundsDirty) {
            loconautics$boundsDirty = false;
            loconautics$bounds = null;
            for (BezierConnection bc : blockEntity.getConnections().values()) {
                if (!bc.isPrimary()) {
                    continue;
                }
                AABB b = bc.getBounds();
                loconautics$bounds = loconautics$bounds == null ? b : loconautics$bounds.minmax(b);
            }
        }
        AABB bounds = loconautics$bounds;
        if (bounds == null) {
            return;
        }

        int roX = pos.getX() - visualPos.getX();
        int roY = pos.getY() - visualPos.getY();
        int roZ = pos.getZ() - visualPos.getZ();
        boolean visible = ctx.frustum().testAab(
                (float) (bounds.minX - roX), (float) (bounds.minY - roY), (float) (bounds.minZ - roZ),
                (float) (bounds.maxX - roX), (float) (bounds.maxY - roY), (float) (bounds.maxZ - roZ));

        if (visible) {
            if (loconautics$culled || visuals.isEmpty()) {
                collectConnections();
                loconautics$culled = false;
            }
        } else if (!loconautics$culled || !visuals.isEmpty()) {
            _delete();
            loconautics$culled = true;
        }
    }
}

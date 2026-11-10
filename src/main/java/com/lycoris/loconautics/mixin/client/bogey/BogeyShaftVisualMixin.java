package com.lycoris.loconautics.mixin.client.bogey;

import com.lycoris.loconautics.content.bearingaxle.BogeyShaft;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import com.simibubi.create.content.trains.bogey.BogeyBlockEntityVisual;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.FlatLit;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(value = BogeyBlockEntityVisual.class, remap = false)
public abstract class BogeyShaftVisualMixin extends AbstractBlockEntityVisual<AbstractBogeyBlockEntity> {

    @Unique
    private RotatingInstance loconautics$shaft;
    @Unique
    private Direction.Axis loconautics$shaftAxis;

    protected BogeyShaftVisualMixin(VisualizationContext ctx, AbstractBogeyBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
    }

    @Inject(method = "beginFrame", at = @At("HEAD"))
    private void loconautics$syncShaft(CallbackInfo ci) {
        BlockState state = blockEntity.getBlockState();
        if (!BogeyShaft.isShafted(state)) {
            loconautics$dropShaft();
            return;
        }
        Direction.Axis axis = state.getValue(BlockStateProperties.HORIZONTAL_AXIS);
        if (loconautics$shaft != null && loconautics$shaftAxis != axis) {
            loconautics$dropShaft();
        }
        if (loconautics$shaft == null) {
            loconautics$shaft = instancerProvider()
                    .instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT))
                    .createInstance();
            loconautics$shaftAxis = axis;
            loconautics$shaft.rotateToFace(axis).setPosition(getVisualPosition()).setChanged();
            relight(loconautics$shaft);
        }
        KineticBlockEntity end = BogeyShaft.endpoint(blockEntity.getLevel(), pos, axis);
        if (end == null) {
            loconautics$shaft.setRotationAxis(axis).setRotationalSpeed(0.0f).setRotationOffset(0.0f).setChanged();
            return;
        }
        KineticBlockEntityVisual.applyOverstressEffect(end, loconautics$shaft.setup(end, axis));
    }

    @Inject(method = "updateLight", at = @At("TAIL"))
    private void loconautics$relightShaft(float partialTick, CallbackInfo ci) {
        if (loconautics$shaft != null) {
            relight(new FlatLit[]{loconautics$shaft});
        }
    }

    @Inject(method = "collectCrumblingInstances", at = @At("TAIL"))
    private void loconautics$crumbleShaft(Consumer<Instance> consumer, CallbackInfo ci) {
        if (loconautics$shaft != null) {
            consumer.accept(loconautics$shaft);
        }
    }

    @Inject(method = "_delete", at = @At("HEAD"))
    private void loconautics$deleteShaft(CallbackInfo ci) {
        loconautics$dropShaft();
    }

    @Unique
    private void loconautics$dropShaft() {
        if (loconautics$shaft != null) {
            loconautics$shaft.delete();
            loconautics$shaft = null;
        }
    }
}

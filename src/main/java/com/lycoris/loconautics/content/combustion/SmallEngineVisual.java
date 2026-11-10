package com.lycoris.loconautics.content.combustion;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.function.Consumer;

public class SmallEngineVisual extends KineticBlockEntityVisual<SmallEngineBlockEntity>
        implements SimpleDynamicVisual {

    private final TransformedInstance leftPiston;
    private final TransformedInstance rightPiston;
    private @Nullable RotatingInstance shaft;

    public SmallEngineVisual(VisualizationContext context, SmallEngineBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);
        leftPiston = piston();
        rightPiston = piston();
        syncShaft();
        animate(partialTick);
        relight(leftPiston, rightPiston);
    }

    private TransformedInstance piston() {
        return instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, Models.partial(EnginePistons.model(blockState)))
                .createInstance();
    }

    private void syncShaft() {
        if (!blockEntity.isHead()) {
            if (shaft != null) {
                shaft.delete();
                shaft = null;
            }
            return;
        }
        if (shaft == null) {
            shaft = instancerProvider()
                    .instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT_HALF))
                    .createInstance();
            shaft.setup(blockEntity)
                    .setPosition(getVisualPosition())
                    .rotateToFace(Direction.SOUTH, SmallEngineBlock.output(blockState))
                    .setChanged();
            relight(shaft);
            return;
        }
        shaft.setup(blockEntity).setChanged();
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        animate(ctx.partialTick());
    }

    // right piston is the left one turned half a turn
    private void animate(float partialTick) {
        float yaw = EnginePistons.yaw(blockState);
        pose(leftPiston, yaw, blockEntity.pistonShake(partialTick, 0f));
        pose(rightPiston, yaw + 180f, blockEntity.pistonShake(partialTick, Mth.PI));
    }

    private void pose(TransformedInstance piston, float yaw, Vector3f shake) {
        Vec3i at = getVisualPosition();
        piston.setIdentityTransform()
                .translate(at.getX(), at.getY(), at.getZ())
                .translate(0.5f, 0.5f, 0.5f)
                .rotateYDegrees(yaw)
                .translate(-0.5f, -0.5f, -0.5f)
                .translate(shake.x, shake.y, shake.z)
                .setChanged();
    }

    @Override
    public void update(float partialTick) {
        syncShaft();
    }

    @Override
    public void updateLight(float partialTick) {
        if (shaft != null) relight(shaft);
        relight(leftPiston, rightPiston);
    }

    @Override
    protected void _delete() {
        if (shaft != null) shaft.delete();
        leftPiston.delete();
        rightPiston.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        consumer.accept(shaft);
        consumer.accept(leftPiston);
        consumer.accept(rightPiston);
    }
}

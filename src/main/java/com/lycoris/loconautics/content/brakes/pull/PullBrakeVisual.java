package com.lycoris.loconautics.content.brakes.pull;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

import java.util.function.Consumer;

public class PullBrakeVisual extends AbstractBlockEntityVisual<PullBrakeBlockEntity>
        implements SimpleDynamicVisual {

    private static final int LINK_COUNT = PullBrakeMotion.LAST_LINK - PullBrakeMotion.FIRST_LINK + 1;

    private final TransformedInstance wheel;
    private final TransformedInstance[] links = new TransformedInstance[LINK_COUNT];

    public PullBrakeVisual(VisualizationContext ctx, PullBrakeBlockEntity be, float partialTick) {
        super(ctx, be, partialTick);
        String color = ((PullBrakeBlock) blockState.getBlock()).colorName();
        wheel = instance(LoconauticsPartialModels.PULL_BRAKE_WHEELS.get(color));
        for (int i = 0; i < LINK_COUNT; i++) {
            links[i] = instance(LoconauticsPartialModels.PULL_BRAKE_CHAINS.get(color));
        }
        animate(partialTick);
    }

    private TransformedInstance instance(PartialModel model) {
        return instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(model)).createInstance();
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        if (blockEntity.isRemoved() || level.getBlockEntity(pos) != blockEntity) {
            delete();
            return;
        }
        animate(ctx.partialTick());
    }

    private void animate(float partialTick) {
        float pull = blockEntity.pull.getValue(partialTick);
        float yaw = 180.0f - blockState.getValue(HorizontalDirectionalBlock.FACING).toYRot();
        int light = LevelRenderer.getLightColor(level, pos);

        place(wheel, yaw, light)
                .translate(PullBrakeMotion.WHEEL_PIVOT_X, PullBrakeMotion.WHEEL_PIVOT_Y, PullBrakeMotion.WHEEL_PIVOT_Z)
                .rotateZDegrees(PullBrakeMotion.wheelDegrees(pull))
                .translate(-PullBrakeMotion.WHEEL_PIVOT_X, -PullBrakeMotion.WHEEL_PIVOT_Y,
                        -PullBrakeMotion.WHEEL_PIVOT_Z);
        wheel.setChanged();

        for (int i = 0; i < LINK_COUNT; i++) {
            int link = PullBrakeMotion.FIRST_LINK + i;
            TransformedInstance instance = links[i];
            if (!PullBrakeMotion.linkSpawned(link, pull)) {
                instance.setZeroTransform();
            } else {
                place(instance, yaw, light).translate(0, PullBrakeMotion.linkOffsetPixels(link, pull) / 16f, 0);
            }
            instance.setChanged();
        }
    }

    private TransformedInstance place(TransformedInstance instance, float yaw, int light) {
        instance.setIdentityTransform()
                .translate(visualPos.getX(), visualPos.getY(), visualPos.getZ())
                .translate(0.5f, 0.5f, 0.5f)
                .rotateYDegrees(yaw)
                .translate(-0.5f, -0.5f, -0.5f);
        instance.light(light);
        return instance;
    }

    @Override
    public void updateLight(float partialTick) {
        animate(partialTick);
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(wheel);
        for (TransformedInstance link : links) consumer.accept(link);
    }

    @Override
    protected void _delete() {
        wheel.delete();
        for (TransformedInstance link : links) link.delete();
    }
}

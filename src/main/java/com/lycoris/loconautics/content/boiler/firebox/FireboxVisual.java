package com.lycoris.loconautics.content.boiler.firebox;

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
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Vector3f;

import java.util.function.Consumer;

public class FireboxVisual extends AbstractBlockEntityVisual<FireboxBlockEntity>
        implements SimpleDynamicVisual {

    private static final float LEVER_DEGREES = -42.5f;

    private static final float DOOR_DEGREES = 35.0f;

    private static final Vector3f LEVER_PIVOT = pivot(7.5f, 12.5f, -1.025f);
    private static final Vector3f DOOR_LEFT_PIVOT = pivot(8.0f, 13.0f, -0.5f);
    private static final Vector3f DOOR_RIGHT_PIVOT = pivot(8.0f, 13.0f, -0.5f);

    private final TransformedInstance leverArm;
    private final TransformedInstance doorLeft;
    private final TransformedInstance doorRight;

    private Direction facing;

    private float yaw;

    private BlockPos openAirLightPos;

    private boolean hidden;

    public FireboxVisual(VisualizationContext ctx, FireboxBlockEntity be, float partialTick) {
        super(ctx, be, partialTick);
        leverArm = instance(LoconauticsPartialModels.FIREBOX_LEVER_ARM);
        doorLeft = instance(LoconauticsPartialModels.FIREBOX_DOOR_LEFT);
        doorRight = instance(LoconauticsPartialModels.FIREBOX_DOOR_RIGHT);

        applyFacing(blockState.getValue(BlockStateProperties.HORIZONTAL_FACING));
        animate(partialTick);
    }

    private void applyFacing(Direction next) {
        facing = next;
        yaw = 180.0f - next.toYRot();
        openAirLightPos = pos.relative(next);
        if (lightSections != null) collectSections();
    }

    private void collectSections() {
        long here = SectionPos.asLong(pos);
        long front = SectionPos.asLong(openAirLightPos);
        lightSections.sections(here == front ? LongSet.of(here) : LongSet.of(here, front));
    }

    private static Vector3f pivot(float x, float y, float z) {
        return new Vector3f(x / 16.0f, y / 16.0f, z / 16.0f);
    }

    private TransformedInstance instance(PartialModel model) {
        return instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, Models.partial(model))
                .createInstance();
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        if (stale()) {
            delete();
            return;
        }
        animate(ctx.partialTick());
    }

    private boolean stale() {
        return blockEntity.isRemoved() || level.getBlockEntity(pos) != blockEntity;
    }

    private void animate(float partialTick) {
        BlockState state = blockEntity.getBlockState();
        if (state.getValue(FireboxBlock.EXTENSION)) {
            hide();
            return;
        }
        hidden = false;

        Direction current = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        if (current != facing) applyFacing(current);

        float swing = blockEntity.getDoorOpenTime(partialTick)
                / FireboxBlockEntity.DOOR_SWING_TICKS;
        float progress = Mth.sin(swing * Mth.HALF_PI);
        int light = brightest(LevelRenderer.getLightColor(level, pos),
                LevelRenderer.getLightColor(level, openAirLightPos));

        swingPart(leverArm, LEVER_PIVOT, LEVER_DEGREES * progress, light);
        swingPart(doorLeft, DOOR_LEFT_PIVOT, -DOOR_DEGREES * progress, light);
        swingPart(doorRight, DOOR_RIGHT_PIVOT, DOOR_DEGREES * progress, light);
    }

    private static int brightest(int a, int b) {
        return LightTexture.pack(
                Math.max(LightTexture.block(a), LightTexture.block(b)),
                Math.max(LightTexture.sky(a), LightTexture.sky(b)));
    }

    private void hide() {
        if (hidden) return;
        hidden = true;
        for (TransformedInstance instance : new TransformedInstance[]{leverArm, doorLeft, doorRight}) {
            instance.setZeroTransform();
            instance.setChanged();
        }
    }

    private void swingPart(TransformedInstance instance, Vector3f hinge, float degrees, int light) {
        instance.setIdentityTransform()
                .translate(visualPos.getX(), visualPos.getY(), visualPos.getZ())
                .translate(0.5f, 0.5f, 0.5f)
                .rotateYDegrees(yaw)
                .translate(-0.5f, -0.5f, -0.5f)
                .translate(hinge.x, hinge.y, hinge.z)
                .rotateZDegrees(degrees)
                .translate(-hinge.x, -hinge.y, -hinge.z);
        instance.light(light);
        instance.setChanged();
    }

    @Override
    public void setSectionCollector(SectionCollector collector) {
        super.setSectionCollector(collector);
        collectSections();
    }

    @Override
    public void updateLight(float partialTick) {
        animate(partialTick);
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(leverArm);
        consumer.accept(doorLeft);
        consumer.accept(doorRight);
    }

    @Override
    protected void _delete() {
        leverArm.delete();
        doorLeft.delete();
        doorRight.delete();
    }
}

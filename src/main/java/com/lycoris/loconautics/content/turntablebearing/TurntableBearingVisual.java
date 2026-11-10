package com.lycoris.loconautics.content.turntablebearing;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.FlatLit;
import dev.engine_room.flywheel.lib.model.Models;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.function.Consumer;

public class TurntableBearingVisual extends KineticBlockEntityVisual<TurntableBearingBlockEntity> {

    private final RotatingInstance cogInstance;

    public TurntableBearingVisual(VisualizationContext context, TurntableBearingBlockEntity blockEntity,
                                    float partialTick) {
        super(context, blockEntity, partialTick);

        Direction facing = blockEntity.getBlockState().getValue(BlockStateProperties.FACING);
        cogInstance = instancerProvider()
                .instancer(AllInstanceTypes.ROTATING, Models.partial(LoconauticsPartialModels.TURNTABLE_COG))
                .createInstance()
                .rotateToFace(Direction.UP, facing.getOpposite())
                .setup(blockEntity.getExtraKinetics())
                .setPosition(getVisualPosition());

        cogInstance.setChanged();
    }

    @Override
    public void update(float partialTick) {
        cogInstance.setup(blockEntity.getExtraKinetics()).setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
        relight(new FlatLit[]{cogInstance});
    }

    @Override
    protected void _delete() {
        cogInstance.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(cogInstance);
    }
}

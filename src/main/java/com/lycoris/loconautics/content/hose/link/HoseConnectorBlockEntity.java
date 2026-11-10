package com.lycoris.loconautics.content.hose.link;

import com.lycoris.loconautics.content.hose.flow.HoseFlowSink;
import com.lycoris.loconautics.content.hose.flow.HosePressure;
import com.lycoris.loconautics.content.hose.flow.HoseSourceProbe;
import com.lycoris.loconautics.content.hose.flow.PressureProbe;
import com.lycoris.loconautics.content.steelcable.SteelCableHolderBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class HoseConnectorBlockEntity extends SmartBlockEntity implements HoseLinkEndpoint {

    private final IFluidHandler linkHandler = new LinkHandler();
    private final PressureProbe pressureProbe = new PressureProbe();
    private final HoseSourceProbe sourceProbe = new HoseSourceProbe();

    public HoseConnectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new SteelCableHolderBehaviour(this, strandCapacity()));
    }

    public Direction plateSide() {
        return getBlockState().getValue(HoseConnectorBlock.FACING);
    }

    public IFluidHandler getLinkHandler() {
        return linkHandler;
    }

    @Nullable
    @Override
    public IFluidHandler linkSink() {
        if (level == null) {
            return null;
        }
        Direction plate = plateSide();
        return level.getCapability(Capabilities.FluidHandler.BLOCK, getBlockPos().relative(plate),
                plate.getOpposite());
    }

    @Override
    public Vec3 getAttachmentPoint() {
        Direction open = plateSide().getOpposite();
        return Vec3.atCenterOf(getBlockPos()).add(open.getStepX() * 0.1875, open.getStepY() * 0.1875,
                open.getStepZ() * 0.1875);
    }

    private HosePressure.Pressure platePressure() {
        return pressureProbe.read(level, getBlockPos(), plateSide());
    }

    private boolean pulls(@Nullable IFluidHandler sink) {
        float pull = Math.abs(platePressure().pull());
        return pull <= 0.0f || !(sink instanceof HoseFlowSink flow) || flow.pull(pull);
    }

    @Nullable
    private IFluidHandler farSink() {
        IFluidHandler sink = HoseLinks.farSink(this);
        if (sink == null || HoseFlowSink.unwrap(sink) == linkHandler
                || sourceProbe.feeds(level, getBlockPos(), plateSide(), sink)) {
            return null;
        }
        return sink;
    }

    private final class LinkHandler implements IFluidHandler {

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            IFluidHandler sink = farSink();
            return sink == null || sink.getTanks() == 0 ? FluidStack.EMPTY : sink.getFluidInTank(0);
        }

        @Override
        public int getTankCapacity(int tank) {
            IFluidHandler sink = farSink();
            return sink == null || sink.getTanks() == 0 ? 0 : sink.getTankCapacity(0);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            IFluidHandler sink = farSink();
            return sink != null && sink.getTanks() > 0 && sink.isFluidValid(0, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            IFluidHandler sink = farSink();
            if (sink == null) {
                return 0;
            }
            float rpm = platePressure().push();
            return rpm > 0.0f && sink instanceof HoseFlowSink flow
                    ? flow.fillPushed(resource, rpm, action)
                    : sink.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            IFluidHandler sink = farSink();
            return sink == null || !pulls(sink) ? FluidStack.EMPTY : sink.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            IFluidHandler sink = farSink();
            return sink == null || !pulls(sink) ? FluidStack.EMPTY : sink.drain(maxDrain, action);
        }
    }
}

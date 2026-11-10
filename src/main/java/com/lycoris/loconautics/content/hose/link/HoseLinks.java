package com.lycoris.loconautics.content.hose.link;

import com.lycoris.loconautics.common.strand.StrandEndpoints;
import com.lycoris.loconautics.content.hose.flow.HoseFlowSink;
import com.lycoris.loconautics.content.hose.flow.HosePressure;
import com.lycoris.loconautics.content.hose.HoseStrandManager;
import com.lycoris.loconautics.content.steelcable.SteelCableHolderBehaviour;
import com.lycoris.loconautics.content.steelcable.SteelCableStrand;
import com.lycoris.loconautics.content.steelcable.SteelCableStrandDriver;
import com.lycoris.loconautics.content.steelcable.SteelCableStrandManager;
import com.lycoris.loconautics.content.steelcable.rope.SteelCableRopeManager;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class HoseLinks {

    private static final int MAX_HOPS = 16;
    private static final int MAX_LINKS_PER_BLOCK = 2;

    private HoseLinks() {
    }

    private record Route(HoseLinkEndpoint end, List<HoseFlowSink.Hop> hops) {
    }

    @Nullable
    public static IFluidHandler farSink(BlockEntity from) {
        Route route = route(from);
        if (route == null) {
            return null;
        }
        IFluidHandler sink = route.end().linkSink();
        return sink == null ? null : new HoseFlowSink(sink, route.hops(), from.getLevel());
    }

    public static HosePressure.Pressure pressureBehind(BlockEntity from, int linkDepth) {
        Route route = route(from);
        if (route == null || !(route.end() instanceof HoseConnectorBlockEntity connector)
                || connector.getLevel() == null) {
            return HosePressure.Pressure.NONE;
        }
        return HosePressure.read(connector.getLevel(), connector.getBlockPos(), connector.plateSide(), linkDepth);
    }

    @Nullable
    private static Route route(BlockEntity from) {
        if (!(from.getLevel() instanceof ServerLevel level)) {
            return null;
        }
        SteelCableStrandManager strands = SteelCableStrandManager.get(level);
        if (strands == null) {
            return null;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        BlockEntity current = from;
        UUID incoming = null;
        List<HoseFlowSink.Hop> hops = new ArrayList<>();
        for (int hop = 0; hop < MAX_HOPS; hop++) {
            SteelCableStrand next = null;
            for (SteelCableStrand strand : fluidStrands(current, strands)) {
                if (!strand.id.equals(incoming)) {
                    next = strand;
                    break;
                }
            }
            if (next == null) {
                return null;
            }
            BlockEntity other = otherEnd(level, container, next, current);
            if (other == null) {
                return null;
            }
            hops.add(new HoseFlowSink.Hop(next.id, isEndA(level, next, current)));
            if (other instanceof HoseLinkEndpoint end) {
                return new Route(end, hops);
            }
            current = other;
            incoming = next.id;
        }
        return null;
    }

    public static List<SteelCableStrand> fluidStrands(BlockEntity be, SteelCableStrandManager strands) {
        List<SteelCableStrand> out = new ArrayList<>();
        if (!(be instanceof SmartBlockEntity sbe)) {
            return out;
        }
        SteelCableHolderBehaviour holder = sbe.getBehaviour(SteelCableHolderBehaviour.TYPE);
        if (holder == null) {
            return out;
        }
        for (UUID id : List.copyOf(holder.attachedStrandIds())) {
            SteelCableStrand strand = strands.get(id);
            if (strand != null && strand.type.carriesFluid()) {
                out.add(strand);
            }
        }
        return out;
    }

    public static boolean atLinkLimit(BlockEntity be) {
        if (!(be.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        SteelCableStrandManager strands = SteelCableStrandManager.get(level);
        HoseStrandManager winchHoses = HoseStrandManager.get(level);
        if (strands == null || winchHoses == null) {
            return false;
        }
        SubLevel sub = Sable.HELPER.getContaining(level, be.getBlockPos());
        UUID subId = sub == null ? null : sub.getUniqueId();
        long winchCount = winchHoses.all().stream()
                .filter(s -> Objects.equals(s.nozzleSubLevelId, subId) && be.getBlockPos().equals(s.nozzleLocalPos))
                .count();
        return fluidStrands(be, strands).size() + winchCount >= MAX_LINKS_PER_BLOCK;
    }

    @Nullable
    public static IFluidHandler sinkFromWinchEnd(BlockEntity end) {
        if (end.getLevel() instanceof ServerLevel level) {
            SteelCableStrandManager strands = SteelCableStrandManager.get(level);
            if (strands != null && !fluidStrands(end, strands).isEmpty()) {
                return farSink(end);
            }
        }
        return end instanceof HoseLinkEndpoint endpoint ? endpoint.linkSink() : null;
    }

    public static boolean hasAnyStrand(BlockEntity be) {
        if (!(be instanceof SmartBlockEntity sbe)) {
            return false;
        }
        SteelCableHolderBehaviour holder = sbe.getBehaviour(SteelCableHolderBehaviour.TYPE);
        return holder != null && !holder.attachedStrandIds().isEmpty();
    }

    private static boolean isEndA(ServerLevel level, SteelCableStrand strand, BlockEntity current) {
        SubLevel sub = Sable.HELPER.getContaining(level, current.getBlockPos());
        UUID subId = sub == null ? null : sub.getUniqueId();
        return Objects.equals(strand.subA, subId) && strand.localA.equals(current.getBlockPos());
    }

    @Nullable
    private static BlockEntity otherEnd(ServerLevel level, @Nullable SubLevelContainer container,
                                        SteelCableStrand strand, BlockEntity current) {
        return isEndA(level, strand, current)
                ? StrandEndpoints.blockEntity(level, container, strand.subB, strand.localB)
                : StrandEndpoints.blockEntity(level, container, strand.subA, strand.localA);
    }

    public static boolean detachNewest(ServerLevel level, BlockEntity be, @Nullable ServerPlayer player) {
        SteelCableStrandManager strands = SteelCableStrandManager.get(level);
        SteelCableRopeManager ropes = SteelCableRopeManager.get(level);
        if (strands == null || ropes == null) {
            return false;
        }
        List<SteelCableStrand> fluid = fluidStrands(be, strands);
        if (fluid.isEmpty()) {
            return false;
        }
        SteelCableStrandDriver.detach(level, strands, ropes, fluid.get(fluid.size() - 1), player);
        return true;
    }
}

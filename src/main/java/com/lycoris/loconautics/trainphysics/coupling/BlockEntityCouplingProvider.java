package com.lycoris.loconautics.trainphysics.coupling;

import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.common.sable.SubLevelData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class BlockEntityCouplingProvider<T extends BlockEntity> implements CouplingProvider {

    private final Class<T> type;
    private final Function<T, UUID> partnerGetter;
    private final Predicate<T> connectedCheck;
    private Predicate<T> weldedCheck;
    private BiFunction<ServerSubLevel, T, TwistLink> twistBuilder;

    public BlockEntityCouplingProvider(Class<T> type, Function<T, UUID> partnerGetter, Predicate<T> connectedCheck) {
        this.type = type;
        this.partnerGetter = partnerGetter;
        this.connectedCheck = connectedCheck;
    }

    public BlockEntityCouplingProvider<T> welded(Predicate<T> check) {
        this.weldedCheck = check;
        return this;
    }

    public BlockEntityCouplingProvider<T> twist(BiFunction<ServerSubLevel, T, TwistLink> builder) {
        this.twistBuilder = builder;
        return this;
    }

    @Override
    public void collectTwistLinks(ServerLevel level, UUID subLevelId, Consumer<TwistLink> links) {
        ServerSubLevel sub = twistBuilder == null ? null : SubLevelData.resolve(level, subLevelId);
        if (sub == null) {
            return;
        }
        for (T be : SubLevelBlocks.findAll(sub, type)) {
            if (!connectedCheck.test(be)) {
                continue;
            }
            TwistLink link = twistBuilder.apply(sub, be);
            if (link != null) {
                links.accept(link);
            }
        }
    }

    @Override
    public void collectCoupledSubLevels(ServerLevel level, UUID subLevelId, Consumer<UUID> neighbours) {
        collect(level, subLevelId, neighbours, connectedCheck);
    }

    @Override
    public void collectWeldedSubLevels(ServerLevel level, UUID subLevelId, Consumer<UUID> neighbours) {
        if (weldedCheck != null) {
            collect(level, subLevelId, neighbours, weldedCheck);
        }
    }

    private void collect(ServerLevel level, UUID subLevelId, Consumer<UUID> neighbours, Predicate<T> gate) {
        ServerSubLevel sub = SubLevelData.resolve(level, subLevelId);
        if (sub == null) {
            return;
        }
        for (T be : SubLevelBlocks.findAll(sub, type)) {
            if (!gate.test(be)) {
                continue;
            }
            UUID partner = partnerGetter.apply(be);
            if (partner != null && !subLevelId.equals(partner)) {
                neighbours.accept(partner);
            }
        }
    }
}

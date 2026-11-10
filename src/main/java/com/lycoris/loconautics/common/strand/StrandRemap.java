package com.lycoris.loconautics.common.strand;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class StrandRemap {

    public interface Target<S> {

        String name();

        Collection<S> strands(ServerLevel level);

        UUID idOf(S strand);

        boolean remapEnds(S strand, StrandEnd from, StrandEnd to);

        List<StrandEnd> endsOf(S strand);

        @Nullable
        RopeManager<?> ropes(ServerLevel level);

        void touch(ServerLevel level);

        // old block entity lets go or its onremove snaps the strand, the new one grabs it
        void handOver(@Nullable BlockEntity from, @Nullable BlockEntity to, UUID strandId);
    }

    private static final List<Target<?>> TARGETS = new CopyOnWriteArrayList<>();

    private StrandRemap() {
    }

    public static void register(Target<?> target) {
        TARGETS.add(target);
    }

    public static void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockPos oldPos, BlockPos newPos) {
        StrandEnd from = new StrandEnd(subLevelIdAt(originLevel, oldPos), oldPos);
        StrandEnd to = new StrandEnd(subLevelIdAt(resultingLevel, newPos), newPos);
        BlockEntity oldBe = originLevel.getBlockEntity(oldPos);
        BlockEntity newBe = resultingLevel.getBlockEntity(newPos);
        for (Target<?> target : TARGETS) {
            int remapped = remap(originLevel, target, from, to, oldBe, newBe);
            if (remapped > 0) {
            }
        }
    }

    private static <S> int remap(ServerLevel level, Target<S> target, StrandEnd from, StrandEnd to,
                                 @Nullable BlockEntity oldBe, @Nullable BlockEntity newBe) {
        RopeManager<?> ropes = target.ropes(level);
        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.get(level);
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        int remapped = 0;
        for (S strand : new ArrayList<>(target.strands(level))) {
            if (!target.remapEnds(strand, from, to)) {
                continue;
            }
            remapped++;
            UUID id = target.idOf(strand);
            // sable never rebinds the old attachments, the driver rebuilds the rope next tick
            if (ropes != null && system != null) {
                ropes.remove(system, id);
            }
            target.handOver(oldBe, newBe, id);
            for (StrandEnd end : target.endsOf(strand)) {
                if (!end.matches(to.subLevelId(), to.local())
                        && StrandEndpoints.blockEntity(level, container, end.subLevelId(), end.local())
                        instanceof SmartBlockEntity far) {
                    far.sendData();
                }
            }
        }
        if (remapped > 0) {
            target.touch(level);
        }
        return remapped;
    }

    @Nullable
    private static UUID subLevelIdAt(ServerLevel level, BlockPos pos) {
        SubLevel sub = Sable.HELPER.getContaining(level, pos);
        return sub == null ? null : sub.getUniqueId();
    }
}

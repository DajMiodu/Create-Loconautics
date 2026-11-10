package com.lycoris.loconautics.common.strand;

import com.simibubi.create.foundation.utility.RaycastHelper;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;

public final class StrandKnotPicker {

    private static final double KNOT_HALF_EXTENT = 0.2;

    private static final List<Source> SOURCES = new ArrayList<>();

    public record Knot(UUID strand, int index, double distance) {}

    // for ropes that are not a strand kind and keep their points somewhere else, like the winch hose
    public interface Source {
        void collect(BiConsumer<UUID, List<Vector3d>> out);
    }

    private StrandKnotPicker() {
    }

    public static void registerSource(Source source) {
        SOURCES.add(source);
    }

    @Nullable
    public static Knot pick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return null;
        }
        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        Vec3 eye = player.getEyePosition();
        Vec3 target = RaycastHelper.getTraceTarget(player, reach, eye);
        HitResult hit = mc.hitResult;
        double[] bestSqr = {hit == null ? reach * reach
                : Sable.HELPER.projectOutOfSubLevel(mc.level, hit.getLocation()).distanceToSqr(eye)};

        Knot[] best = new Knot[1];
        for (StrandKind kind : StrandKind.values()) {
            for (Map.Entry<UUID, ClientStrands.Strand> entry : kind.clientStrands().entries()) {
                List<ClientStrands.Point> points = entry.getValue().points();
                consider(entry.getKey(), points.size(), i -> points.get(i).position, eye, target, bestSqr, best);
            }
        }
        for (Source source : SOURCES) {
            source.collect((id, points) -> consider(id, points.size(), points::get, eye, target, bestSqr, best));
        }
        return best[0];
    }

    private static void consider(UUID strand, int size, IntFunction<Vector3d> position, Vec3 eye, Vec3 target,
                                 double[] bestSqr, Knot[] best) {
        for (int i = 1; i < size - 1; i++) {
            Vector3d at = position.apply(i);
            AABB box = new AABB(at.x - KNOT_HALF_EXTENT, at.y - KNOT_HALF_EXTENT, at.z - KNOT_HALF_EXTENT,
                    at.x + KNOT_HALF_EXTENT, at.y + KNOT_HALF_EXTENT, at.z + KNOT_HALF_EXTENT);
            Optional<Vec3> clip = box.clip(eye, target);
            if (clip.isEmpty()) {
                continue;
            }
            double distSqr = clip.get().distanceToSqr(eye);
            if (distSqr > bestSqr[0]) {
                continue;
            }
            bestSqr[0] = distSqr;
            best[0] = new Knot(strand, i, Math.sqrt(distSqr));
        }
    }

    @Nullable
    public static Vec3 knotPosition(UUID strand, int index, float partialTick) {
        for (StrandKind kind : StrandKind.values()) {
            ClientStrands.Strand found = kind.clientStrands().get(strand);
            if (found != null) {
                if (index >= found.size()) {
                    return null;
                }
                Vector3d at = found.points().get(index).renderPos(partialTick, new Vector3d());
                return new Vec3(at.x, at.y, at.z);
            }
        }
        Vec3[] result = new Vec3[1];
        for (Source source : SOURCES) {
            source.collect((id, points) -> {
                if (id.equals(strand) && index < points.size()) {
                    Vector3d at = points.get(index);
                    result[0] = new Vec3(at.x, at.y, at.z);
                }
            });
        }
        return result[0];
    }

    public static int knotCount(UUID strand) {
        for (StrandKind kind : StrandKind.values()) {
            ClientStrands.Strand found = kind.clientStrands().get(strand);
            if (found != null) {
                return found.size();
            }
        }
        int[] count = new int[1];
        for (Source source : SOURCES) {
            source.collect((id, points) -> {
                if (id.equals(strand)) {
                    count[0] = points.size();
                }
            });
        }
        return count[0];
    }
}

package com.lycoris.loconautics.common.sable;

import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.entities.honey_glue.HoneyGlueEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class SubLevelGlue {

    public record Strip(AABB local, boolean honey) {}

    private SubLevelGlue() {
    }

    public static List<Strip> take(ServerSubLevel sub) {
        ServerLevel level = sub.getLevel();
        Vec3 shift = corner(sub).scale(-1.0);
        AABB plot = plotArea(sub);
        List<Strip> out = new ArrayList<>();
        for (SuperGlueEntity g : level.getEntitiesOfClass(SuperGlueEntity.class, plot)) {
            out.add(new Strip(g.getBoundingBox().move(shift), false));
            g.remove(Entity.RemovalReason.KILLED);
        }
        try {
            takeHoney(level, plot, shift, out);
        } catch (NoClassDefFoundError ignored) {
        }
        return out;
    }

    public static void put(ServerSubLevel sub, List<Strip> strips) {
        if (strips.isEmpty()) {
            return;
        }
        ServerLevel level = sub.getLevel();
        Vec3 shift = corner(sub);
        for (Strip strip : strips) {
            AABB box = strip.local().move(shift);
            if (!strip.honey()) {
                level.addFreshEntity(new SuperGlueEntity(level, box));
                continue;
            }
            try {
                putHoney(level, box);
            } catch (NoClassDefFoundError ignored) {
            }
        }
    }

    private static void takeHoney(ServerLevel level, AABB plot, Vec3 shift, List<Strip> out) {
        for (HoneyGlueEntity g : level.getEntitiesOfClass(HoneyGlueEntity.class, plot)) {
            out.add(new Strip(g.getBoundingBox().move(shift), true));
            g.remove(Entity.RemovalReason.KILLED);
        }
    }

    private static void putHoney(ServerLevel level, AABB box) {
        HoneyGlueEntity glue = new HoneyGlueEntity(level, box);
        level.addFreshEntity(glue);
        glue.setBoundsAndSync(box);
    }

    private static Vec3 corner(ServerSubLevel sub) {
        BlockPos center = sub.getPlot().getCenterBlock();
        return new Vec3(center.getX(), center.getY(), center.getZ());
    }

    private static AABB plotArea(ServerSubLevel sub) {
        var box = sub.getPlot().getBoundingBox();
        return new AABB(box.minX(), box.minY(), box.minZ(),
                box.maxX() + 1.0, box.maxY() + 1.0, box.maxZ() + 1.0);
    }
}

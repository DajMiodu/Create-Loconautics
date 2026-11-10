package com.lycoris.loconautics.client;

import dev.ryanhcode.sable.Sable;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class StrandPreviewOutline {

    private static final int SUCCESS_LIME = new java.awt.Color(158, 222, 115).getRGB();
    private static final int PERCHANCE_ORANGE = new java.awt.Color(255, 201, 102).getRGB();
    private static final int NUH_UH_RED = new java.awt.Color(255, 113, 113).getRGB();

    private StrandPreviewOutline() {
    }

    public static void draw(ClientLevel level, String keyPrefix, Vec3 firstPoint,
                            Vec3 hitLocation, BlockPos hitPos, double maxRange, boolean targetOk) {
        Vec3 hitWorld = Sable.HELPER.projectOutOfSubLevel(level, hitLocation);
        boolean inRange = firstPoint.distanceToSqr(hitWorld) < maxRange * maxRange;
        boolean valid = inRange && targetOk;
        Vec3 target = valid ? Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(hitPos)) : hitWorld;
        Color color = new Color(valid ? SUCCESS_LIME : (inRange ? PERCHANCE_ORANGE : NUH_UH_RED));

        Outliner.getInstance().chaseAABB(keyPrefix + "FirstPoint", new AABB(firstPoint, firstPoint))
                .colored(color).lineWidth(0.33333334f).disableLineNormals();

        if (valid) {
            Outliner.getInstance().chaseAABB(keyPrefix + "SecondPoint", new AABB(target, target))
                    .colored(color).lineWidth(0.33333334f).disableLineNormals();
            double points = Math.floor(firstPoint.distanceTo(target));
            Vec3 backwards = firstPoint.subtract(target).normalize();
            for (int i = 0; i < points; i++) {
                Vec3 p = target.add(backwards.scale(i));
                Outliner.getInstance().chaseAABB(keyPrefix + "Point" + i, new AABB(p, p))
                        .colored(color).lineWidth(0.125f).disableLineNormals();
            }
        } else if (!inRange) {
            target = target.subtract(firstPoint).normalize().scale(maxRange - 0.5).add(firstPoint);
            Outliner.getInstance().chaseAABB(keyPrefix + "SecondPoint", new AABB(target, target))
                    .colored(color).lineWidth(0.33333334f).disableLineNormals();
        }

        DustParticleOptions dust = new DustParticleOptions(color.asVectorF(), 1.0f);
        for (int i = 0; i < 4; i++) {
            Vec3 p = firstPoint.lerp(target, level.random.nextFloat());
            level.addParticle(dust, p.x, p.y, p.z, 0.0, 0.0, 0.0);
        }
    }
}

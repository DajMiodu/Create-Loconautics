package com.lycoris.loconautics.trainphysics.fx;

import com.lycoris.loconautics.trainphysics.portal.PortalCutsClient;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SubLevelParticleRedirect {

    private SubLevelParticleRedirect() {
    }

    public static void addParticle(Level level, ParticleOptions options, double x, double y, double z,
            double vx, double vy, double vz) {
        ClientSubLevel sub = containing(x, y, z);
        if (sub != null && PortalCutsClient.hiddenAtPlot(x, y, z)) {
            return;
        }
        if (sub == null) {
            level.addParticle(options, x, y, z, vx, vy, vz);
            return;
        }
        Vec3 w = sub.renderPose().transformPosition(new Vec3(x, y, z));
        Minecraft.getInstance().level.addParticle(options, w.x, w.y, w.z, vx, vy, vz);
    }

    public static void addAlwaysVisibleParticle(Level level, ParticleOptions options, boolean force,
            double x, double y, double z, double vx, double vy, double vz) {
        ClientSubLevel sub = containing(x, y, z);
        if (sub != null && PortalCutsClient.hiddenAtPlot(x, y, z)) {
            return;
        }
        if (sub == null) {
            level.addAlwaysVisibleParticle(options, force, x, y, z, vx, vy, vz);
            return;
        }
        Vec3 w = sub.renderPose().transformPosition(new Vec3(x, y, z));
        Minecraft.getInstance().level.addAlwaysVisibleParticle(options, force, w.x, w.y, w.z, vx, vy, vz);
    }

    private static ClientSubLevel containing(double x, double y, double z) {
        return Sable.HELPER.getContainingClient(BlockPos.containing(x, y, z));
    }
}

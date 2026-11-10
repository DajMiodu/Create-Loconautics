package com.lycoris.loconautics.compat.create.patch;

import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import com.lycoris.loconautics.trainphysics.visual.BogeyWheelAnimator;
import com.simibubi.create.content.contraptions.actors.roller.RollerBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3d;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

@OnlyIn(Dist.CLIENT)
public final class RollerAnimator {

    private static final Map<RollerBlockEntity, double[]> SPIN =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final double SMOOTHING = 0.3;

    private static final double DRUM_DROP = 2.0;

    private static final double DEG_PER_SEC = -1000.0;

    private RollerAnimator() {
    }

    public static void tickAll() {
        synchronized (SPIN) {
            SPIN.entrySet().removeIf(e -> {
                RollerBlockEntity roller = e.getKey();
                ClientSubLevel sub = Sable.HELPER.getContainingClient(roller);
                if (sub == null) {
                    roller.setAnimatedSpeed(0);
                    return true;
                }
                tick(roller, sub, e.getValue());
                return false;
            });
        }
    }

    public static void frame(RollerBlockEntity roller) {
        ClientSubLevel sub = Sable.HELPER.getContainingClient(roller);
        if (sub == null) return;
        SPIN.putIfAbsent(roller, new double[] {0.0, 0.0});
    }

    public static Float angle(RollerBlockEntity roller, float partialTicks) {
        double[] spin = SPIN.get(roller);
        if (spin == null) return null;
        return (float) ((spin[0] + spin[1] * partialTicks) % 360.0);
    }

    private static void tick(RollerBlockEntity roller, ClientSubLevel sub, double[] spin) {
        BlockState state = roller.getBlockState();
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            roller.setAnimatedSpeed(0);
            return;
        }

        BlockPos bp = roller.getBlockPos();
        Vector3d localCenter = new Vector3d(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
        Vector3d cur = sub.logicalPose().transformPosition(localCenter, new Vector3d());
        Vector3d prev = sub.lastPose().transformPosition(localCenter, new Vector3d());

        net.minecraft.core.Direction localFacing = state.getValue(HorizontalDirectionalBlock.FACING);
        Vector3d facing = new Vector3d(localFacing.getStepX(), 0, localFacing.getStepZ());
        sub.logicalPose().transformNormal(facing, facing);

        boolean grounded = BogeyWheelAnimator.isGroundedAt(sub.getLevel(), cur.x, cur.y - DRUM_DROP, cur.z);
        double along = cur.sub(prev, new Vector3d()).dot(facing);

        SableTrainClientRegistry.TrainMarker marker = SableTrainClientRegistry.get(sub.getUniqueId());
        double perTick = marker != null && !marker.derailed()
                ? Math.copySign(Math.abs(marker.speed()), along == 0.0 ? spin[1] : along)
                : along;

        double target = grounded ? perTick * DEG_PER_SEC / 20.0 : 0.0;
        spin[1] += (target - spin[1]) * SMOOTHING;
        spin[0] = (spin[0] + spin[1]) % 360.0;

        roller.setAnimatedSpeed((float) (spin[1] * 20.0));
    }
}

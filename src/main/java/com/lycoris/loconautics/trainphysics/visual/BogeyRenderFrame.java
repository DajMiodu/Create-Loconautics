package com.lycoris.loconautics.trainphysics.visual;

import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

@OnlyIn(Dist.CLIENT)
public final class BogeyRenderFrame {

    public record Frame(Quaternionf local, Vector3f sway, Vector3f railOffset, Vector3f bodyCounter,
                        boolean railed) { }

    private static final Set<AbstractBogeyBlockEntity> RAILED_BACKWARDS =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private BogeyRenderFrame() {
    }

    static boolean facesBackwards(AbstractBogeyBlockEntity be) {
        return RAILED_BACKWARDS.contains(be);
    }

    public static Frame of(AbstractBogeyBlockEntity be, ClientSubLevel sub, float partialTick) {
        Vector3f offset = BogeyYawVisual.getRailLocalOffset(be, new Vector3f());
        Quaternionf local = new Quaternionf();
        Vector3f sway = new Vector3f(offset);
        // the server publishes a swing for exactly the bogeys that are off the rail, one loose bogey on an otherwise
        Quaternionf swing = BogeyYawVisual.getWheelSwing(be, new Quaternionf());
        BogeyRailPoints.Chord walked = sub == null || derailed(sub) || swing != null
                ? null : BogeyRailPoints.chordAt(be, sub);
        boolean railed = walked != null;

        boolean axisX = be.getBlockState().getValue(AbstractBogeyBlock.AXIS) == Direction.Axis.X;
        if (walked == null) {
            double yaw = (axisX ? 90.0 : 0.0) + (RAILED_BACKWARDS.contains(be) ? 180.0 : 0.0);
            local.rotationY((float) Math.toRadians(yaw));
            if (swing != null) {
                local.premul(BogeySwingSmoother.interp(be, swing, partialTick, new Quaternionf())).normalize();
            }
            sway.zero();
        } else {
            Quaterniondc bodyQ = sub.renderPose().orientation();
            Quaternionf body = new Quaternionf((float) bodyQ.x(), (float) bodyQ.y(),
                    (float) bodyQ.z(), (float) bodyQ.w());
            local.set(new Quaternionf(body).conjugate()
                    .mul(BogeyRailPoints.worldFrame(walked, new Quaternionf())));
            dropTwist(local);
            Vector3f faces = local.transform(new Vector3f(0.0f, 0.0f, 1.0f), new Vector3f());
            if ((axisX ? faces.x : faces.z) < 0.0f) {
                RAILED_BACKWARDS.add(be);
            } else {
                RAILED_BACKWARDS.remove(be);
            }
        }

        Vector3f counter = railed ? BogeyYawVisual.getBodyCounter(be, new Vector3f()) : new Vector3f();
        return new Frame(local, sway, offset, counter, railed);
    }

    private static boolean derailed(ClientSubLevel sub) {
        SableTrainClientRegistry.TrainMarker marker = SableTrainClientRegistry.get(sub.getUniqueId());
        return marker != null && marker.derailed();
    }

    // the rail frame has no roll, so the bogey keeps the roll of the car instead of staying level with the rail
    private static void dropTwist(Quaternionf localFrame) {
        Quaternionf twist = new Quaternionf(0.0f, 0.0f, localFrame.z, localFrame.w);
        if (twist.lengthSquared() < 1.0e-12f) {
            return;
        }
        localFrame.mul(twist.normalize().invert()).normalize();
    }
}

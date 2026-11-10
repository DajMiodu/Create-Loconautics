package com.lycoris.loconautics.trainphysics.glue;

import com.lycoris.loconautics.network.packets.train.SuspensionLeanPacket;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class SuspensionLean {

    private static final double SEND_STEP = Math.toRadians(0.05);
    private static final double REST = 1.0e-4;
    private static final long HEARTBEAT_TICKS = 20L;

    private SuspensionLean() {
    }

    // the client keeps the bogeys level against this lean, it forgets a lean that stops coming
    static void publish(ServerSubLevel sub, CarriageRoll.Roll roll, Vector3d forward0, long now) {
        if (now == roll.suspensionSentTick) {
            return;
        }
        boolean moved = Math.abs(roll.suspension - roll.suspensionSent) > SEND_STEP;
        boolean level = Math.abs(roll.suspension) < REST && Math.abs(roll.suspensionSent) < REST;
        if (!moved && (level || now - roll.suspensionSentTick < HEARTBEAT_TICKS)) {
            return;
        }
        roll.suspensionSent = roll.suspension;
        roll.suspensionSentTick = now;
        Quaterniond turn = new Quaterniond().rotateAxis(roll.suspension, forward0.x, forward0.y, forward0.z);
        PacketDistributor.sendToAllPlayers(new SuspensionLeanPacket(sub.getUniqueId(), (float) turn.x,
                (float) turn.y, (float) turn.z, (float) turn.w));
    }
}

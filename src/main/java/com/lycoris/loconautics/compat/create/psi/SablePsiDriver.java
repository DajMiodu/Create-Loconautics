package com.lycoris.loconautics.compat.create.psi;

import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceBlock;
import com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

public final class SablePsiDriver {

    private static final double MAX_LINK_SPEED = 5.0;
    private static final int MAX_REACH = 2;

    private static final double STRICT_LATERAL = 0.35;
    private static final double CARRIAGE_LATERAL = 0.75;
    private static final double STRICT_FACING_DOT = 0.98;

    private SablePsiDriver() {
    }

    public static void tick(PortableStorageInterfaceBlockEntity be) {
        if (!(be.getLevel() instanceof ServerLevel level)) {
            return;
        }
        SablePsiHost host = (SablePsiHost) be;
        if (be.isPowered()) {
            return;
        }
        ServerSubLevel own = Sable.HELPER.getContaining(be) instanceof ServerSubLevel s ? s : null;
        if (host.loconautics$linked()) {
            PortableStorageInterfaceBlockEntity far = revalidate(level, be, host, own);
            if (far != null) {
                be.keepAlive = 2;
                far.keepAlive = 2;
            }
            return;
        }
        if (be.keepAlive > 0) {
            return;
        }
        connect(level, be, host, own);
    }

    public static void unlink(PortableStorageInterfaceBlockEntity be) {
        SablePsiHost host = (SablePsiHost) be;
        if (!host.loconautics$linked()) {
            return;
        }
        BlockPos partnerPos = host.loconautics$partnerPos();
        host.loconautics$unlink();
        if (partnerPos == null || !(be.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (level.getBlockEntity(partnerPos) instanceof PortableStorageInterfaceBlockEntity far) {
            setCapability(far, null, null);
            ((SablePsiHost) far).loconautics$unlink();
        }
    }

    @Nullable
    private static PortableStorageInterfaceBlockEntity revalidate(ServerLevel level,
                                                                  PortableStorageInterfaceBlockEntity be,
                                                                  SablePsiHost host,
                                                                  @Nullable ServerSubLevel own) {
        UUID partnerSub = host.loconautics$partnerSubLevel();
        BlockPos partnerPos = host.loconautics$partnerPos();
        if (partnerPos == null || !nearlyStopped(own)) {
            return null;
        }
        ServerSubLevel sub = null;
        if (partnerSub != null) {
            sub = resolve(level, partnerSub);
            if (sub == null || !nearlyStopped(sub)) {
                return null;
            }
        }
        boolean carriage = isCarriage(own) || isCarriage(sub);
        Vec3 axis = SablePsiFrames.dirToWorld(own, be.getBlockState().getValue(PortableStorageInterfaceBlock.FACING), false);
        PortableStorageInterfaceBlockEntity far =
                partnerAt(level, sub, partnerPos, be.getBlockState(), axis, !carriage);
        if (far == null) {
            return null;
        }
        Vec3 delta = gapVector(own, be.getBlockPos(), sub, partnerPos);
        if (!stillInReach(delta, axis, carriage)) {
            return null;
        }
        retune(be, host, far, SablePsiFrames.reachOf(delta.dot(axis)));
        return far;
    }

    private static void retune(PortableStorageInterfaceBlockEntity be, SablePsiHost host,
                               PortableStorageInterfaceBlockEntity far, float reach) {
        if (Math.abs(host.loconautics$distance() - reach) < 0.03f) {
            return;
        }
        host.loconautics$setDistance(reach);
        ((SablePsiHost) far).loconautics$setDistance(reach);
        be.notifyUpdate();
        far.notifyUpdate();
    }

    private static boolean stillInReach(Vec3 delta, Vec3 axis, boolean carriage) {
        double along = delta.dot(axis);
        if (along < 0.5 || along > MAX_REACH + 0.5) {
            return false;
        }
        double lateral = carriage ? CARRIAGE_LATERAL : STRICT_LATERAL;
        return delta.subtract(axis.scale(along)).length() <= lateral;
    }

    private static void connect(ServerLevel level, PortableStorageInterfaceBlockEntity be, SablePsiHost host,
                                @Nullable ServerSubLevel own) {
        BlockState state = be.getBlockState();
        Vec3 axis = SablePsiFrames.dirToWorld(own, state.getValue(PortableStorageInterfaceBlock.FACING), false);
        Vec3 origin = SablePsiFrames.toWorld(own, be.getBlockPos(), false);

        for (int step = 1; step <= MAX_REACH; step++) {
            Vec3 center = origin.add(axis.scale(step));
            BoundingBox3d bounds = new BoundingBox3d(new AABB(BlockPos.containing(center)).inflate(0.5));
            for (SubLevel candidate : Sable.HELPER.getAllIntersecting(level, bounds)) {
                if (!(candidate instanceof ServerSubLevel sub) || sub == own || sub.isRemoved()
                        || !nearlyStopped(sub)) {
                    continue;
                }
                Vector3d local = sub.logicalPose()
                        .transformPositionInverse(new Vector3d(center.x, center.y, center.z), new Vector3d());
                if (tryLink(level, be, host, own, sub, BlockPos.containing(local.x, local.y, local.z), axis)) {
                    return;
                }
            }
            if (own != null && nearlyStopped(own)
                    && tryLink(level, be, host, own, null, BlockPos.containing(center), axis)) {
                return;
            }
        }
    }

    private static boolean tryLink(ServerLevel level, PortableStorageInterfaceBlockEntity be, SablePsiHost host,
                                   @Nullable ServerSubLevel own, @Nullable ServerSubLevel farSub,
                                   BlockPos farPos, Vec3 axis) {
        boolean carriage = isCarriage(own) || isCarriage(farSub);
        PortableStorageInterfaceBlockEntity far =
                partnerAt(level, farSub, farPos, be.getBlockState(), axis, !carriage);
        if (far == null) {
            return false;
        }
        Vec3 delta = gapVector(own, be.getBlockPos(), farSub, farPos);
        if (!carriage && !squarelyAligned(delta, axis)) {
            return false;
        }
        link(level, be, host, own, farSub, far, farPos, delta.dot(axis));
        return true;
    }

    private static Vec3 gapVector(@Nullable ServerSubLevel own, BlockPos ownPos,
                                  @Nullable ServerSubLevel far, BlockPos farPos) {
        return SablePsiFrames.toWorld(far, farPos, false).subtract(SablePsiFrames.toWorld(own, ownPos, false));
    }

    private static boolean squarelyAligned(Vec3 delta, Vec3 axis) {
        double along = delta.dot(axis);
        if (along < 0.5 || along > MAX_REACH + 0.5) {
            return false;
        }
        return delta.subtract(axis.scale(along)).length() <= STRICT_LATERAL;
    }

    private static void link(ServerLevel level, PortableStorageInterfaceBlockEntity be, SablePsiHost host,
                             @Nullable ServerSubLevel own, @Nullable ServerSubLevel farSub,
                             PortableStorageInterfaceBlockEntity far, BlockPos farPos, double centerGap) {
        BlockPos ownPos = be.getBlockPos();
        Runnable notify = be::onContentTransferred;

        if (be instanceof SablePsiItemHost && far instanceof SablePsiItemHost) {
            setCapability(be, itemsOf(level, farSub, farPos, notify), null);
            setCapability(far, itemsOf(level, own, ownPos, notify), null);
        } else if (be instanceof SablePsiFluidHost && far instanceof SablePsiFluidHost) {
            setCapability(be, null, fluidsOf(level, farSub, farPos, notify));
            setCapability(far, null, fluidsOf(level, own, ownPos, notify));
        } else {
            return;
        }

        host.loconautics$link(farSub == null ? null : farSub.getUniqueId(), farPos);
        ((SablePsiHost) far).loconautics$link(own == null ? null : own.getUniqueId(), ownPos);
        float reach = SablePsiFrames.reachOf(centerGap);
        extend(be, host, reach);
        extend(far, (SablePsiHost) far, reach);
    }

    private static IItemHandlerModifiable itemsOf(ServerLevel level, @Nullable ServerSubLevel sub,
                                                  BlockPos pos, Runnable notify) {
        return isCarriage(sub)
                ? SablePsiHandlers.carriageItems(sub, notify)
                : SablePsiHandlers.passThroughItems(level, pos, notify);
    }

    private static IFluidHandler fluidsOf(ServerLevel level, @Nullable ServerSubLevel sub,
                                          BlockPos pos, Runnable notify) {
        return sub != null
                ? SablePsiHandlers.carriageFluids(sub, notify)
                : SablePsiHandlers.passThroughFluids(level, pos, notify);
    }

    private static void extend(PortableStorageInterfaceBlockEntity be, SablePsiHost host, float reach) {
        host.loconautics$setDistance(reach);
        be.startConnecting();
        be.keepAlive = 2;
        be.notifyUpdate();
    }

    @Nullable
    private static PortableStorageInterfaceBlockEntity partnerAt(ServerLevel level, @Nullable ServerSubLevel sub,
                                                                 BlockPos localPos, BlockState ownState,
                                                                 Vec3 ownAxis, boolean strict) {
        BlockState state = level.getBlockState(localPos);
        if (state.getBlock() != ownState.getBlock()) {
            return null;
        }
        Vec3 facing = SablePsiFrames.dirToWorld(sub, state.getValue(PortableStorageInterfaceBlock.FACING), false);
        Vec3 wanted = ownAxis.reverse();
        if (Direction.getNearest(facing.x, facing.y, facing.z) != Direction.getNearest(wanted.x, wanted.y, wanted.z)) {
            return null;
        }
        if (strict ? facing.dot(wanted) < STRICT_FACING_DOT : facing.distanceTo(wanted) > 0.5) {
            return null;
        }
        if (!(level.getBlockEntity(localPos) instanceof PortableStorageInterfaceBlockEntity far)
                || far.isPowered()) {
            return null;
        }
        return far;
    }

    private static void setCapability(PortableStorageInterfaceBlockEntity be,
                                      @Nullable IItemHandlerModifiable items,
                                      @Nullable IFluidHandler fluids) {
        if (be instanceof SablePsiItemHost itemHost) {
            itemHost.loconautics$setItemCapability(items);
        } else if (be instanceof SablePsiFluidHost fluidHost) {
            fluidHost.loconautics$setFluidCapability(fluids);
        }
    }

    private static boolean isCarriage(@Nullable ServerSubLevel sub) {
        return sub != null && SableTrainRegistry.bySubLevel(sub.getUniqueId()) != null;
    }

    private static boolean nearlyStopped(@Nullable ServerSubLevel sub) {
        if (sub == null) {
            return true;
        }
        RigidBodyHandle handle = RigidBodyHandle.of(sub);
        if (handle == null || !handle.isValid()) {
            return true;
        }
        return handle.getLinearVelocity(new Vector3d()).length() <= MAX_LINK_SPEED;
    }

    @Nullable
    private static ServerSubLevel resolve(ServerLevel level, UUID id) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        return container.getSubLevel(id) instanceof ServerSubLevel sub && !sub.isRemoved() ? sub : null;
    }
}

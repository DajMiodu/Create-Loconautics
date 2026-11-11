package com.lycoris.loconautics.content.hose.winch;

import com.lycoris.loconautics.trainphysics.portal.PortalTwins;
import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.strand.RopeGeometry;
import com.lycoris.loconautics.common.strand.RemovedSubLevels;
import com.lycoris.loconautics.common.strand.StrandEndpoints;
import com.lycoris.loconautics.common.strand.StrandSnap;
import com.lycoris.loconautics.common.strand.StrandHolder;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.hose.HoseFluidCarrier;
import com.lycoris.loconautics.content.hose.HoseHolderBehaviour;
import com.lycoris.loconautics.content.hose.HoseStrand;
import com.lycoris.loconautics.content.hose.HoseStrandHolder;
import com.lycoris.loconautics.content.hose.HoseStrandManager;
import com.lycoris.loconautics.content.hose.flow.HosePressure;
import com.lycoris.loconautics.content.hose.flow.HoseSourceProbe;
import com.lycoris.loconautics.content.hose.flow.PressureProbe;
import com.lycoris.loconautics.content.hose.link.HoseLinks;
import com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlockEntity;
import com.lycoris.loconautics.content.hose.rope.HoseRope;
import com.lycoris.loconautics.content.hose.rope.HoseRopeManager;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.simibubi.create.content.kinetics.base.KineticEffectHandler;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

public class HoseWinchBlockEntity extends GeneratingKineticBlockEntity implements HoseStrandHolder, HoseFluidCarrier {

    private static final float COIL_DEGREES_PER_RPM_TICK = 0.3f;
    private static final double TAUT_STRETCH = 1.02;
    private static final int FLOW_LINGER_TICKS = 3;
    public static final float FREE_SPIN_CAPACITY = 256.0f;
    private static final double FREE_SPIN_SLACK = 0.001;
    private static final double FREE_SPIN_GAIN = 30.0;
    private static final double FREE_SPIN_GRAB_GAIN = 0.5;
    private static final double FREE_SPIN_MAX_PAYOUT = 1.0;
    private static final double FREE_SPIN_PAYOUT_SMOOTH = 0.5;
    private static final int FREE_SPIN_APPLY_TICKS = 1;
    private static final float FREE_SPIN_APPLY_STEP = 0.5f;
    private static final float FREE_SPIN_LERP = 0.2f;
    private static final int FLOW_LOG_TICKS = 20;

    private HoseHolderBehaviour holder;

    private final IFluidHandler passThrough = new PassThrough();

    private FluidStack flowFluid = FluidStack.EMPTY;
    private int flowTicks;
    private int flowRate = 1;
    private boolean pressureReaches;
    private boolean suctionReaches;
    private final PressureProbe pressureProbe = new PressureProbe();
    private final HoseSourceProbe sourceProbe = new HoseSourceProbe();
    private float sprayStrength;

    private float throwStrength;
    private float reach;
    private float lastSyncedThrow = -1.0f;
    private float lastSyncedReach = -1.0f;
    private FluidStack lastSyncedFluid = FluidStack.EMPTY;

    private float clientThrow;
    private float clientReach;
    private FluidStack clientFluid = FluidStack.EMPTY;
    private final net.createmod.catnip.animation.LerpedFloat clientFill =
            net.createmod.catnip.animation.LerpedFloat.linear().startWithValue(0);
    private float wavePhase;
    private float wavePhaseRate;

    private boolean clientHasStrand;
    private float clientLength;
    @Nullable private UUID clientStrandId;
    @Nullable private UUID clientNozzleSub;
    @Nullable private BlockPos clientNozzleLocal;

    @Nullable private float[] ropePtsIncoming;
    @Nullable private float[] ropePtsPrev;
    @Nullable private float[] ropePtsCurr;

    @Nullable private float[] pendingRopePoints;


    private float coilAngle;
    private float coilAnglePrev;
    private double smoothedPayout;
    private float freeSpinTarget;
    private float freeSpinRpm;
    private float appliedRpm;
    private long lastRpmApply;

    public HoseWinchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        effects = new KineticEffectHandler(this) {
            @Override
            public void queueRotationIndicators() {
            }

            @Override
            public void spawnRotationIndicators() {
            }
        };
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        holder = new HoseHolderBehaviour(this, true);
        behaviours.add(holder);
    }

    public void onWinchBroken() {
        if (level == null || level.isClientSide || isPortalTwin()) {
            return;
        }
        HoseStrand strand = getAttachedStrand();
        if (strand != null) {
            snapAt(strand, false);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            HoseWinchKnotSource.track(this);
            coilAnglePrev = coilAngle;
            coilAngle += getSpeed() * COIL_DEGREES_PER_RPM_TICK;
            float wrap = (int) (coilAngle / 360.0f) * 360.0f;
            coilAngle -= wrap;
            coilAnglePrev -= wrap;
            ropePtsPrev = ropePtsCurr;
            ropePtsCurr = ropePtsIncoming;
            if (clientReach <= 0.001f) {
                clientFill.startWithValue(0);
            } else {
                clientFill.chase(clientReach, 0.2, net.createmod.catnip.animation.LerpedFloat.Chaser.LINEAR);
                clientFill.tickChaser();
            }
            float period = 40.0f - 26.0f * Mth.clamp(clientThrow, 0.0f, 1.0f);
            wavePhaseRate = (float) (Math.PI * 2.0) / period;
            wavePhase = (float) ((wavePhase + wavePhaseRate) % (Math.PI * 2.0));
            return;
        }
        if (isPortalTwin()) {
            return;
        }
        HoseStrand strand = getAttachedStrand();
        if (strand == null) {
            clearFlow();
            freeSpinTarget = 0.0f;
            syncFreeSpin();
            return;
        }
        if (isNozzleGone(strand)) {
            snapAt(strand, true);
            return;
        }

        driveRope(strand);
        HoseRopeManager ropeMgr = HoseRopeManager.get(level);
        HoseRope driven = ropeMgr == null ? null : ropeMgr.get(strand.id);
        if (driven != null && driven.isActive() && driven.tickSnap()) {
            snapAt(strand, false);
            return;
        }
        syncFreeSpin();
        updateFluidState(strand);
        tryTensionUndock(strand);

        HoseStrandManager mgr = HoseStrandManager.get(level);
        if (mgr != null) {
            mgr.touch();
        }
    }

    private boolean isPortalTwin() {
        return level instanceof ServerLevel server && PortalTwins.masterBlock(server, getBlockPos()) != null;
    }

    private void updateFluidState(HoseStrand strand) {
        float len = strand.currentLength;
        HosePressure.Pressure pressure = pressureProbe.read(level, getBlockPos(), fluidInputSide());
        float push = Math.abs(pressure.push());
        float pull = Math.abs(pressure.pull());
        float pump = Math.max(push, pull);
        reach = HosePressure.reach(pump);
        pressureReaches = HosePressure.reaches(push, len);
        suctionReaches = pull > 0.0f && HosePressure.reaches(pull, len);
        flowRate = Math.max(1, (int) (pump / 2.0f));

        HoseNozzleBlockEntity nozzle = resolveNozzle(strand);
        IFluidHandler linkSink = nozzle == null ? farLinkSink(strand) : null;
        boolean docked = nozzle != null ? nozzle.isDocked() : linkSink != null;

        if (flowTicks > 0) {
            flowTicks--;
        } else {
            flowFluid = FluidStack.EMPTY;
        }

        sprayStrength = 0.0f;
        if (docked) {
            reach = pressureReaches || suctionReaches ? len : 0.0f;
        } else if (pressureReaches) {
            sprayStrength = HosePressure.strength(pump, len);
        }
        throwStrength = flowFluid.isEmpty() ? 0.0f : sprayStrength;
        if (nozzle != null) {
            nozzle.setSuckPower(suctionReaches ? HosePressure.strength(pull, len) : 0.0f);
        }
        syncFluidState();
    }

    private void syncFluidState() {
        if (Math.abs(throwStrength - lastSyncedThrow) > 0.02f
                || Math.abs(reach - lastSyncedReach) > 0.1f
                || !FluidStack.isSameFluidSameComponents(flowFluid, lastSyncedFluid)) {
            lastSyncedThrow = throwStrength;
            lastSyncedReach = reach;
            lastSyncedFluid = flowFluid.copy();
            sendData();
        }
    }

    private void clearFlow() {
        pressureReaches = false;
        suctionReaches = false;
        sprayStrength = 0.0f;
        flowTicks = 0;
        flowFluid = FluidStack.EMPTY;
        reach = 0.0f;
        throwStrength = 0.0f;
        syncFluidState();
    }

    private void noteFlow(FluidStack moved) {
        flowFluid = moved.copyWithAmount(1);
        flowTicks = FLOW_LINGER_TICKS;
    }

    @Nullable
    private IFluidHandler heldSink() {
        HoseStrand strand = getAttachedStrand();
        if (strand == null) {
            return null;
        }
        HoseNozzleBlockEntity nozzle = resolveNozzle(strand);
        if (nozzle == null) {
            return farLinkSink(strand);
        }
        return nozzle.isDocked() ? nozzle.dockedFluidHandler() : nozzle.suctionHandler();
    }

    private boolean loopsBack(@Nullable IFluidHandler sink) {
        return level != null && sourceProbe.feeds(level, getBlockPos(), fluidInputSide(), sink);
    }

    private class PassThrough implements IFluidHandler {

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return flowFluid;
        }

        @Override
        public int getTankCapacity(int tank) {
            IFluidHandler sink = heldSink();
            return sink != null ? sink.getTankCapacity(0) : flowRate;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            IFluidHandler sink = heldSink();
            return sink == null || sink.isFluidValid(0, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!pressureReaches || resource.isEmpty()) {
                logFillRefused("pressure", resource);
                return 0;
            }
            HoseStrand strand = getAttachedStrand();
            if (strand == null) {
                logFillRefused("no strand", resource);
                return 0;
            }
            HoseNozzleBlockEntity nozzle = resolveNozzle(strand);
            IFluidHandler sink = nozzle == null ? farLinkSink(strand) : null;
            if (loopsBack(nozzle != null ? nozzle.dockedFluidHandler() : sink)) {
                return 0;
            }
            int moved = nozzle != null
                    ? nozzle.receive(resource, sprayStrength, action)
                    : sink == null ? 0 : sink.fill(resource, action);
            if (moved <= 0) {
                logFillRefused(nozzle != null ? "nozzle took 0, spray=" + sprayStrength : "no sink", resource);
            }
            if (moved > 0 && action.execute()) {
                noteFlow(resource);
            }
            return moved;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            IFluidHandler sink = suctionReaches ? heldSink() : null;
            if (sink == null || loopsBack(sink)) {
                return FluidStack.EMPTY;
            }
            FluidStack out = sink.drain(resource, action);
            if (!out.isEmpty() && action.execute()) {
                noteFlow(out);
            }
            return out;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            IFluidHandler sink = suctionReaches ? heldSink() : null;
            if (sink == null || loopsBack(sink)) {
                return FluidStack.EMPTY;
            }
            FluidStack out = sink.drain(Math.min(maxDrain, flowRate), action);
            if (!out.isEmpty() && action.execute()) {
                noteFlow(out);
            }
            return out;
        }
    }

    private void logFillRefused(String why, FluidStack resource) {
        if (level != null && !level.isClientSide && level.getGameTime() % FLOW_LOG_TICKS == 0) {
        }
    }

    private static final double UNDOCK_STRETCH = 1.03;
    private static final double FREE_SPIN_UNDOCK_PAYOUT = 0.5;

    private void tryTensionUndock(HoseStrand strand) {
        HoseNozzleBlockEntity nozzle = resolveNozzle(strand);
        if (nozzle != null && nozzle.isDocked() && level.getGameTime() % 5 == 0) {
            HoseRopeManager dbg = HoseRopeManager.get(level);
            HoseRope dbgRope = dbg == null ? null : dbg.get(strand.id);
            com.lycoris.loconautics.LoconauticsConstants.LOGGER.info(
                    "[undockdbg] winch tension canUndock={} stretch={} freeSpin={} payout={} ext={}",
                    nozzle.canUndock(), dbgRope == null ? "norope" : dbgRope.getStretchRatio(), isFreeSpin(),
                    smoothedPayout, dbgRope == null ? "norope" : dbgRope.getCurrentExtension());
        }
        if (nozzle == null || !nozzle.canUndock()) {
            return;
        }
        HoseRopeManager mgr = HoseRopeManager.get(level);
        HoseRope rope = mgr == null ? null : mgr.get(strand.id);
        if (rope == null) {
            return;
        }
        double stretch = rope.getStretchRatio();
        boolean freeSpinPull = isFreeSpin() && smoothedPayout >= FREE_SPIN_UNDOCK_PAYOUT && stretch >= 1.0;
        if (stretch >= UNDOCK_STRETCH || freeSpinPull) {
            nozzle.undock(level.getGameTime());
        }
    }

    @Nullable
    private BlockEntity farEnd(HoseStrand strand) {
        if (strand.nozzleLocalPos == null || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return StrandEndpoints.blockEntity(serverLevel, SubLevelContainer.getContainer(serverLevel),
                strand.nozzleSubLevelId, strand.nozzleLocalPos);
    }

    @Nullable
    private IFluidHandler farLinkSink(HoseStrand strand) {
        BlockEntity far = farEnd(strand);
        return far == null ? null : HoseLinks.sinkFromWinchEnd(far);
    }

    @Nullable
    private HoseNozzleBlockEntity resolveNozzle(HoseStrand strand) {
        if (strand.nozzleSubLevelId == null || strand.nozzleLocalPos == null) {
            return null;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null
                && container.getSubLevel(strand.nozzleSubLevelId) instanceof ServerSubLevel sub
                && sub.getLevel().getBlockEntity(strand.nozzleLocalPos) instanceof HoseNozzleBlockEntity nozzle) {
            return nozzle;
        }
        return null;
    }

    public net.minecraft.core.Direction fluidInputSide() {
        net.minecraft.world.level.block.state.BlockState st = getBlockState();
        return st.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)
                ? st.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING).getOpposite()
                : net.minecraft.core.Direction.UP;
    }

    @Override
    public IFluidHandler getHoseFluidHandler() {
        return passThrough;
    }

    private boolean isFreeSpin() {
        return getBlockState().hasProperty(HoseWinchBlock.POWERED) && getBlockState().getValue(HoseWinchBlock.POWERED);
    }

    @Override
    public float getGeneratedSpeed() {
        return appliedRpm;
    }

    @Override
    public float calculateAddedStressCapacity() {
        float capacity = appliedRpm == 0.0f ? 0.0f : FREE_SPIN_CAPACITY / Math.abs(appliedRpm);
        lastCapacityProvided = capacity;
        return capacity;
    }

    private double freeSpinPayout(HoseRope rope) {
        double desired = rope.getExtension() + (rope.getPoints().size() - 2);
        double excess = rope.getCurrentExtension() - desired;
        return excess > FREE_SPIN_SLACK ? Math.min(FREE_SPIN_MAX_PAYOUT, (excess - FREE_SPIN_SLACK) * FREE_SPIN_GAIN) : 0.0;
    }

    private double grabPayout(HoseRope rope, @Nullable HoseNozzleBlockEntity nozzle) {
        if (nozzle == null || !(level instanceof ServerLevel serverLevel)) {
            return 0.0;
        }
        Vector3d anchor = winchWorld();
        double gap = 0.0;
        for (var grab : nozzle.grabbers().entrySet()) {
            Player player = serverLevel.getPlayerByUUID(grab.getKey());
            if (player != null) {
                gap = Math.max(gap, anchor.distance(grab.getValue().goalFor(player)) - rope.getCurrentExtension());
            }
        }
        return gap > FREE_SPIN_SLACK ? Math.min(FREE_SPIN_MAX_PAYOUT, gap * FREE_SPIN_GRAB_GAIN) : 0.0;
    }

    private void syncFreeSpin() {
        freeSpinRpm = Mth.lerp(FREE_SPIN_LERP, freeSpinRpm, freeSpinTarget);
        if (freeSpinRpm < 1.0f) {
            freeSpinRpm = 0.0f;
        }
        boolean zeroFlip = (freeSpinRpm == 0.0f) != (appliedRpm == 0.0f);
        if ((zeroFlip || Math.abs(freeSpinRpm - appliedRpm) >= FREE_SPIN_APPLY_STEP)
                && level.getGameTime() - lastRpmApply >= FREE_SPIN_APPLY_TICKS) {
            appliedRpm = freeSpinRpm;
            lastRpmApply = level.getGameTime();
            updateGeneratedRotation();
        }
    }

    private double getMovementSpeed(HoseStrand strand) {
        double linear = convertToLinear(getSpeed()) * strand.type.reelTierSpeedMultiplier();
        return Math.max(-0.49, Math.min(0.49, linear));
    }

    private void driveRope(HoseStrand strand) {
        HoseRopeManager mgr = HoseRopeManager.get(level);
        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.get(level);
        if (mgr == null || system == null) {
            return;
        }
        HoseRope rope = mgr.get(strand.id);
        if (rope == null) {
            java.util.UUID winchSub = winchSubLevelId();
            if (strand.points != null && strand.points.size() >= 2) {
                rope = HoseRope.restore(strand.id, strand.type, winchSub, getBlockPos(),
                        strand.nozzleSubLevelId, strand.nozzleLocalPos, strand.points, strand.extensionGoal);
            } else {
                rope = HoseRope.build(strand.id, strand.type, winchSub, getBlockPos(), winchWorld(),
                        strand.nozzleSubLevelId, strand.nozzleLocalPos, nozzleWorld(strand),
                        Math.max(0.5, strand.currentLength));
            }
            mgr.add(rope);
        }
        // pose first, reel changes the point count and the physics pose is still the old one
        if (rope.isActive()) {
            rope.updatePose();
            com.lycoris.loconautics.common.strand.RopePullTrace.ends(rope, (ServerLevel) level);
        }
        boolean externalDrive = isFreeSpin() && appliedRpm == 0.0f && getSpeed() != 0.0f;
        boolean freeSpinning = isFreeSpin() && !externalDrive;
        double pull = freeSpinning ? Math.max(freeSpinPayout(rope), grabPayout(rope, resolveNozzle(strand))) : 0.0;
        smoothedPayout = Mth.lerp(FREE_SPIN_PAYOUT_SMOOTH, smoothedPayout, pull);
        if (smoothedPayout < 0.002) {
            smoothedPayout = 0.0;
        }
        int maxRpm = com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.maxRotationSpeed.get();
        freeSpinTarget = Mth.clamp((float) (smoothedPayout / strand.type.reelTierSpeedMultiplier() * 512.0), 0.0f, maxRpm);
        double reelSpeed = freeSpinning ? smoothedPayout : getMovementSpeed(strand);
        // ends held by one consist cannot come closer, reeling past a taut rope only squeezes the cars together
        if (reelSpeed < 0.0 && rope.getStretchRatio() > TAUT_STRETCH && sharesBody(winchSubLevelId(), strand.nozzleSubLevelId)) {
            reelSpeed = 0.0;
        }
        int pointsBefore = rope.getPoints().size();
        rope.reel(reelSpeed, winchWorld());
        if (!freeSpinning && getSpeed() != 0.0f) {
            logReel(rope, strand, reelSpeed, pointsBefore);
        }

        if (!rope.isActive() && rope.areAttachmentsLoaded((net.minecraft.server.level.ServerLevel) level)
                && system.getTicketManager().wouldBeLoaded(level, rope)) {
            system.addObject(rope);
        }
        if (rope.isActive()) {
            strand.currentLength = (float) Math.min(rope.getCurrentExtension(), strand.type.maxLength() + 1.0);
            strand.points = new java.util.ArrayList<>(rope.getPoints());
            strand.extensionGoal = rope.getExtension();
            if (rope.needsSync()) {
                syncRopePoints(rope);
                rope.justSynced();
            }
        }
    }

    private boolean sharesBody(@Nullable UUID winchSub, @Nullable UUID nozzleSub) {
        if (winchSub == null || nozzleSub == null || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        return winchSub.equals(nozzleSub) || TrainConsist.connectedSubLevels(serverLevel, winchSub).contains(nozzleSub);
    }

    @Nullable private Vec3 lastNozzleLog;

    private void logReel(HoseRope rope, HoseStrand strand, double reelSpeed, int pointsBefore) {
        var pts = rope.getPoints();
        double maxSegment = 0.0;
        for (int i = 0; i < pts.size() - 1; i++) {
            maxSegment = Math.max(maxSegment, pts.get(i).distance(pts.get(i + 1)));
        }
        double firstSegment = pts.size() > 1 ? pts.get(0).distance(pts.get(1)) : 0.0;
        Vec3 nozzle = resolveNozzlePoint(strand);
        double nozzleStep = nozzle == null || lastNozzleLog == null ? 0.0 : nozzle.distanceTo(lastNozzleLog);
        lastNozzleLog = nozzle;
        LoconauticsConstants.LOGGER.info("[winchreel] t={} speed={} reel={} pts={}->{} goal={} desired={} cur={} stretch={} first={} maxSeg={} nozzleStep={} joint={} active={}",
                level.getGameTime(), getSpeed(), reelSpeed, pointsBefore, pts.size(), rope.getExtension(),
                rope.getDesiredExtension(), rope.getCurrentExtension(), rope.getStretchRatio(), firstSegment,
                maxSegment, nozzleStep, rope.hasJoint(), rope.isActive());
    }

    private Vector3d projectOut(double x, double y, double z) {
        Vec3 w = dev.ryanhcode.sable.Sable.HELPER.projectOutOfSubLevel(level, new Vec3(x, y, z));
        return new Vector3d(w.x, w.y, w.z);
    }

    private Vector3d winchWorld() {
        Vec3 a = hoseAnchor();
        return projectOut(a.x, a.y, a.z);
    }

    @Nullable
    private java.util.UUID winchSubLevelId() {
        Vec3 a = hoseAnchor();
        dev.ryanhcode.sable.sublevel.SubLevel s = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, a);
        return s != null ? s.getUniqueId() : null;
    }

    private Vec3 hoseAnchor() {
        return Vec3.atCenterOf(getBlockPos());
    }

    public double spanTo(Vec3 farWorld) {
        Vec3 a = hoseAnchor();
        Vector3d world = projectOut(a.x, a.y, a.z);
        return world.distance(farWorld.x, farWorld.y, farWorld.z);
    }

    private Vector3d nozzleWorld(HoseStrand strand) {
        Vec3 p = resolveNozzlePoint(strand);
        return p != null ? new Vector3d(p.x, p.y, p.z) : winchWorld();
    }

    private void syncRopePoints(HoseRope rope) {
        var pts = rope.getPoints();
        int n = pts.size();
        float[] rel = new float[n * 3];
        BlockPos base = getBlockPos();
        dev.ryanhcode.sable.sublevel.SubLevel own = dev.ryanhcode.sable.Sable.HELPER.getContaining(this);
        Vector3d scratch = new Vector3d();
        for (int i = 0; i < n; i++) {
            scratch.set(pts.get(i));
            if (own != null) {
                own.logicalPose().transformPositionInverse(scratch);
            }
            rel[i * 3] = (float) (scratch.x - base.getX());
            rel[i * 3 + 1] = (float) (scratch.y - base.getY());
            rel[i * 3 + 2] = (float) (scratch.z - base.getZ());
        }
        pendingRopePoints = rel;
        sendData();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) {
            tag.putFloat("Throw", throwStrength);
            tag.putFloat("Reach", reach);
            tag.put("FlowFluid", flowFluid.saveOptional(registries));
            HoseStrand strand = getAttachedStrand();
            tag.putBoolean("HasStrand", strand != null);
            if (strand != null) {
                tag.putFloat("Length", strand.currentLength);
                tag.putUUID("StrandId", strand.id);
                if (resolveNozzle(strand) != null) {
                    tag.putUUID("NozzleSub", strand.nozzleSubLevelId);
                    tag.putLong("NozzleLocal", strand.nozzleLocalPos.asLong());
                }
            }
            if (pendingRopePoints != null) {
                int[] packed = new int[pendingRopePoints.length];
                for (int i = 0; i < packed.length; i++) {
                    packed[i] = Float.floatToIntBits(pendingRopePoints[i]);
                }
                tag.putIntArray("RopePts", packed);
            }
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) {
            clientThrow = tag.getFloat("Throw");
            clientReach = tag.getFloat("Reach");
            clientFluid = tag.contains("FlowFluid")
                    ? FluidStack.parseOptional(registries, tag.getCompound("FlowFluid")) : FluidStack.EMPTY;
            clientHasStrand = tag.getBoolean("HasStrand");
            clientLength = tag.getFloat("Length");
            clientStrandId = tag.hasUUID("StrandId") ? tag.getUUID("StrandId") : null;
            clientNozzleSub = tag.hasUUID("NozzleSub") ? tag.getUUID("NozzleSub") : null;
            clientNozzleLocal = tag.contains("NozzleLocal") ? BlockPos.of(tag.getLong("NozzleLocal")) : null;
            if (tag.contains("RopePts")) {
                int[] packed = tag.getIntArray("RopePts");
                float[] pts = new float[packed.length];
                for (int i = 0; i < packed.length; i++) {
                    pts[i] = Float.intBitsToFloat(packed[i]);
                }
                ropePtsIncoming = pts;
            }
            if (!clientHasStrand) {
                ropePtsIncoming = ropePtsCurr = ropePtsPrev = null;
            }
        }
    }

    public float coilAngleRadians(float partialTicks) {
        return Mth.lerp(partialTicks, coilAnglePrev, coilAngle) * Mth.DEG_TO_RAD;
    }

    public boolean clientHasStrand() {
        return clientHasStrand;
    }

    public float clientThrow() {
        return clientThrow;
    }

    public boolean clientHasFluid() {
        return !clientFluid.isEmpty();
    }

    public float clientReach() {
        return clientReach;
    }

    public float wavePhase(float partialTicks) {
        return wavePhase + wavePhaseRate * partialTicks;
    }

    public float clientFill(float partialTicks) {
        return clientFill.getValue(partialTicks);
    }

    public float clientLength() {
        return clientLength;
    }

    @Nullable
    public UUID clientStrandId() {
        return clientStrandId;
    }

    @Nullable
    public UUID clientNozzleSub() {
        return clientNozzleSub;
    }

    @Nullable
    public BlockPos clientNozzleLocal() {
        return clientNozzleLocal;
    }

    @Nullable
    public List<Vec3> clientRopePoints(float partialTicks) {
        return RopeGeometry.interpolateRopePoints(ropePtsCurr, ropePtsPrev, getBlockPos(), partialTicks);
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return RopeGeometry.renderBounds(ropePtsCurr, getBlockPos(), 1.0);
    }

    private boolean isNozzleGone(HoseStrand strand) {
        if (strand.nozzleLocalPos == null || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (RemovedSubLevels.contains(strand.nozzleSubLevelId)) {
            return true;
        }
        if (!StrandEndpoints.isLoaded(serverLevel, container, strand.nozzleSubLevelId, strand.nozzleLocalPos)) {
            return false;
        }
        return !(farEnd(strand) instanceof StrandHolder);
    }

    private void snapAt(HoseStrand strand, boolean farEnd) {
        if (!(level instanceof ServerLevel slevel)) {
            return;
        }
        HoseRopeManager ropes = HoseRopeManager.get(level);
        HoseRope rope = ropes == null ? null : ropes.get(strand.id);
        java.util.List<Vector3d> pts = rope != null ? java.util.List.copyOf(rope.getPoints()) : strand.points;
        Vector3d at = StrandSnap.endOf(pts, !farEnd);
        if (at == null) {
            Vec3 fallback = farEnd ? resolveNozzlePoint(strand) : null;
            fallback = fallback != null ? fallback : Vec3.atCenterOf(getBlockPos());
            at = new Vector3d(fallback.x, fallback.y, fallback.z);
        }
        StrandSnap.snap(slevel, new net.minecraft.world.item.ItemStack(
                com.lycoris.loconautics.registry.LoconauticsRegistries.HOSE.get()), pts, at);
        HoseNozzleBlockEntity nozzle = resolveNozzle(strand);
        if (nozzle != null) {
            nozzle.onStrandDetached();
        }
        HoseStrandManager mgr = HoseStrandManager.get(level);
        if (mgr != null) {
            mgr.remove(strand.id);
        }
        removeRope(strand.id);
        onStrandDetached();
    }

    private void removeRope(UUID id) {
        if (level == null || level.isClientSide) {
            return;
        }
        HoseRopeManager ropes = HoseRopeManager.get(level);
        if (ropes != null) {
            ropes.remove(SubLevelPhysicsSystem.get(level), id);
        }
    }

    @Nullable
    private Vec3 resolveNozzlePoint(HoseStrand strand) {
        if (strand.nozzleLocalPos == null || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        Vector3d point = StrandEndpoints.worldPoint(serverLevel, SubLevelContainer.getContainer(serverLevel),
                strand.nozzleSubLevelId, strand.nozzleLocalPos);
        return point == null ? null : new Vec3(point.x, point.y, point.z);
    }

    @Override
    public Vec3 getAttachmentPoint() {
        return hoseAnchor();
    }

    @Override
    public boolean canAttachStrand() {
        return holder != null && holder.isFree();
    }

    @Override
    public void onStrandAttached(HoseStrand strand) {
        holder.setAttachedStrandId(strand.id);
        setHasCoil(true);
        setChanged();
        sendData();
    }

    @Override
    public void onStrandDetached() {
        if (holder == null || holder.getAttachedStrandId() == null) {
            return;
        }
        holder.setAttachedStrandId(null);
        setHasCoil(false);
        setChanged();
        sendData();
    }

    private void setHasCoil(boolean hasCoil) {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState st = level.getBlockState(getBlockPos());
        if (st.getBlock() instanceof HoseWinchBlock && st.getValue(HoseWinchBlock.HAS_COIL) != hasCoil) {
            level.setBlock(getBlockPos(), st.setValue(HoseWinchBlock.HAS_COIL, hasCoil), 3);
        }
    }

    @Nullable
    @Override
    public HoseStrand getAttachedStrand() {
        return holder == null ? null : holder.getAttachedStrand();
    }
}

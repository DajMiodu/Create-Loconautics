package com.lycoris.loconautics.content.hose.nozzle;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.dock.IDockable;
import com.lycoris.loconautics.common.strand.StrandAnchors;
import com.lycoris.loconautics.common.strand.StrandAttachments;
import com.lycoris.loconautics.common.grab.GrabConstraint;
import com.lycoris.loconautics.common.grab.GrabbableActor;
import com.lycoris.loconautics.content.hatch.NozzleDockService;
import com.lycoris.loconautics.content.hose.HoseHolderBehaviour;
import com.lycoris.loconautics.content.hose.HoseStrand;
import com.lycoris.loconautics.content.hose.HoseStrandHolder;
import com.lycoris.loconautics.content.hose.flow.HosePressure;
import com.lycoris.loconautics.content.hose.flow.PressuredSink;
import com.lycoris.loconautics.content.hose.link.HoseLinkEndpoint;
import com.lycoris.loconautics.content.hose.spray.entity.HoseSprayEntity;
import com.lycoris.loconautics.content.hose.spray.interaction.HoseSprayInteractions;
import com.lycoris.loconautics.content.hose.rope.HoseRope;
import com.lycoris.loconautics.content.hose.rope.HoseRopeManager;
import com.lycoris.loconautics.content.steelcable.SteelCableHolderBehaviour;
import com.lycoris.loconautics.content.steelcable.rope.SteelCableRope;
import com.lycoris.loconautics.content.steelcable.rope.SteelCableRopeManager;
import com.simibubi.create.content.fluids.hosePulley.HosePulleyFluidHandler;
import com.simibubi.create.content.fluids.transfer.FluidDrainingBehaviour;
import com.simibubi.create.content.fluids.transfer.FluidFillingBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HoseNozzleBlockEntity extends SmartBlockEntity
        implements HoseStrandHolder, HoseLinkEndpoint, BlockEntitySubLevelActor, GrabbableActor {

    private static final int UNDOCK_SAFETY_TICKS = 10;
    private static final int REDOCK_COOLDOWN_TICKS = 10;
    private static final java.util.Set<HoseNozzleBlockEntity> CLIENT_NOZZLES = new java.util.HashSet<>();
    private static final int LINK_SPRAY_FLOW = 16;
    private static final int LINK_FLOW_LINGER_TICKS = 3;
    private static final float LINK_SPRAY_STRENGTH = 0.5f;

    private static final int SUCTION_TANK_CAPACITY = 1500;
    private static final double SUCTION_ROOT_SLACK_SQ = 4.0;
    private static final double SUCTION_BOTTOM_INSET = 1.0 / 16.0;
    private static final double[] TRACE_PROBE_INSETS = {1.0 / 16.0, -0.1, -0.25, -0.5, -1.0, -1.5};

    private HoseHolderBehaviour holder;
    private FluidDrainingBehaviour drainer;
    private FluidFillingBehaviour filler;
    private final SmartFluidTank suctionTank = new SmartFluidTank(SUCTION_TANK_CAPACITY, stack -> setChanged());
    private final IFluidHandler suctionHandler;
    @Nullable private BlockPos suctionRoot;

    private final Map<UUID, GrabConstraint> grabbers = new HashMap<>();

    @Nullable private IDockable dockedTarget;
    @Nullable private java.util.UUID hatchSubLevelId;
    private final Vector3d dockPoint = new Vector3d();
    private final Quaterniond dockOrientation = new Quaterniond();
    private final Vector3d dockInitialRelPos = new Vector3d();
    private final Quaterniond dockInitialRelOrient = new Quaterniond();
    private final Quaterniond dockTargetOrient = new Quaterniond();
    private boolean dockCaptured;
    private double dockLerp;
    private static final double DOCK_LERP_STEP = 0.04;
    @Nullable private PhysicsConstraintHandle dockConstraint;
    private int undockSafety;
    private long redockReadyTick;

    private final IFluidHandler linkSinkHandler = new LinkSink();
    private FluidStack linkFluid = FluidStack.EMPTY;
    private long linkFlowTick;
    private float linkStrength;
    private boolean linkSprayActive;

    private float sprayReaction;
    private long sprayReactionTick;

    private float sprayThrow;
    private FluidStack sprayFluid = FluidStack.EMPTY;
    private float lastSyncedSprayThrow = -1.0f;
    private FluidStack lastSyncedSprayFluid = FluidStack.EMPTY;
    private float clientSprayThrow;
    private FluidStack clientSprayFluid = FluidStack.EMPTY;

    private final java.util.List<UUID> sprayEntityIds = new java.util.ArrayList<>();

    private static final int SUCK_LINGER_TICKS = 3;
    private static final double SUCK_MIN_RANGE = 0.5;
    private static final double SUCK_MAX_RANGE = 3.0;
    private static final double SUCK_TIP_RADIUS = 0.12;
    private FluidStack suckFluid = FluidStack.EMPTY;
    private long suckTick;
    private float suckPower;
    private FluidStack lastSyncedSuckFluid = FluidStack.EMPTY;
    private float lastSyncedSuckStrength;
    private FluidStack clientSuckFluid = FluidStack.EMPTY;
    private float clientSuckStrength;

    public HoseNozzleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        suctionHandler = new HosePulleyFluidHandler(suctionTank, filler, drainer, this::suctionRoot, () -> !isDocked());
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        holder = new HoseHolderBehaviour(this, false);
        behaviours.add(holder);
        behaviours.add(new SteelCableHolderBehaviour(this, strandCapacity()));
        drainer = new FluidDrainingBehaviour(this);
        filler = new FluidFillingBehaviour(this);
        behaviours.add(drainer);
        behaviours.add(filler);
    }

    @Nullable
    public IFluidHandler suctionHandler() {
        if (isDocked() || level == null || level.isClientSide) {
            return null;
        }
        return fluidHit() == null ? null : suctionHandler;
    }

    private record FluidHit(@Nullable SubLevel sub, BlockPos pos) {
    }

    // pos is a plot position when sub is set, a world position when it is null
    @Nullable
    private FluidHit fluidHit() {
        return fluidHit(SUCTION_BOTTOM_INSET);
    }

    @Nullable
    private FluidHit fluidHit(double inset) {
        BlockPos lp = getBlockPos();
        SubLevel own = Sable.HELPER.getContaining(level, lp);
        Vec3 probe = new Vec3(lp.getX() + 0.5, lp.getY() + inset, lp.getZ() + 0.5);
        return Sable.HELPER.runIncludingSubLevels(level, probe, own == null, own,
                (sub, pos) -> level.getFluidState(pos).isEmpty() ? null : new FluidHit(sub, pos));
    }

    // create restarts its search on every root move
    private BlockPos suctionRoot() {
        FluidHit hit = fluidHit();
        if (hit == null) {
            return suctionRoot != null ? suctionRoot : getBlockPos();
        }
        if (suctionRoot != null && !level.getFluidState(suctionRoot).isEmpty()
                && suctionRoot.distSqr(hit.pos()) <= SUCTION_ROOT_SLACK_SQ) {
            return suctionRoot;
        }
        suctionRoot = hit.pos();
        return suctionRoot;
    }

    @Override
    public void tick() {
        super.tick();
        if (level instanceof ServerLevel) {
            tickLinkSpray(level.getGameTime());
            tickSuction(level.getGameTime());
        } else if (level != null && level.isClientSide) {
            var containing = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, getBlockPos());
            if (containing instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel sub) {
                emitSprayClient(sub);
            }
            emitSuctionClient(containing instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel sub ? sub : null);
        }
    }

    public void setSuckPower(float power) {
        this.suckPower = power;
    }

    private void tickSuction(long gameTime) {
        boolean sucking = !isDocked() && !suckFluid.isEmpty() && gameTime - suckTick <= SUCK_LINGER_TICKS;
        FluidStack shown = sucking ? suckFluid : FluidStack.EMPTY;
        float strength = sucking ? Math.max(suckPower, HosePressure.MIN_SPRAY_STRENGTH) : 0.0f;
        if (!FluidStack.isSameFluidSameComponents(shown, lastSyncedSuckFluid)
                || Math.abs(strength - lastSyncedSuckStrength) > 0.05f) {
            lastSyncedSuckFluid = shown.copy();
            lastSyncedSuckStrength = strength;
            if (!sucking) {
                suckFluid = FluidStack.EMPTY;
            }
            sendData();
        }
    }

    private void tickLinkSpray(long gameTime) {
        boolean flowing = !isDocked() && !linkFluid.isEmpty() && gameTime - linkFlowTick <= LINK_FLOW_LINGER_TICKS;
        if (flowing) {
            linkSprayActive = true;
            driveSprayServer(linkFluid, linkStrength, true);
        } else if (linkSprayActive) {
            linkSprayActive = false;
            linkFluid = FluidStack.EMPTY;
            driveSprayServer(FluidStack.EMPTY, 0.0f, false);
        }
    }

    @Override
    public IFluidHandler linkSink() {
        return linkSinkHandler;
    }

    public int receive(FluidStack resource, float strength, IFluidHandler.FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }
        if (isDocked()) {
            IFluidHandler docked = dockedFluidHandler();
            return docked == null ? 0 : docked.fill(resource, action);
        }
        if (strength <= 0.0f) {
            return 0;
        }
        int moved = Math.min(resource.getAmount(), LINK_SPRAY_FLOW);
        if (moved > 0 && action.execute() && level != null) {
            linkFluid = resource.copyWithAmount(1);
            linkStrength = strength;
            linkFlowTick = level.getGameTime();
        }
        return moved;
    }

    private final class LinkSink implements PressuredSink {

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            IFluidHandler docked = dockedFluidHandler();
            return docked == null ? FluidStack.EMPTY : docked.getFluidInTank(0);
        }

        @Override
        public int getTankCapacity(int tank) {
            IFluidHandler docked = dockedFluidHandler();
            return docked == null ? LINK_SPRAY_FLOW : docked.getTankCapacity(0);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            IFluidHandler docked = dockedFluidHandler();
            return docked == null || docked.isFluidValid(0, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return fill(resource, LINK_SPRAY_STRENGTH, action);
        }

        @Override
        public int fill(FluidStack resource, float strength, FluidAction action) {
            return receive(resource, strength, action);
        }

        @Override
        public void pull(float strength) {
            setSuckPower(strength);
        }

        @Nullable
        @Override
        public IFluidHandler endTank() {
            return dockedFluidHandler();
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            IFluidHandler source = drainSource();
            return source == null ? FluidStack.EMPTY : noteSuction(source.drain(resource, action), action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            IFluidHandler source = drainSource();
            return source == null ? FluidStack.EMPTY : noteSuction(source.drain(maxDrain, action), action);
        }

        private FluidStack noteSuction(FluidStack drained, FluidAction action) {
            if (!drained.isEmpty() && action.execute() && !isDocked() && level != null) {
                suckFluid = drained.copyWithAmount(1);
                suckTick = level.getGameTime();
            }
            return drained;
        }

        @Nullable
        private IFluidHandler drainSource() {
            IFluidHandler docked = dockedFluidHandler();
            return docked != null ? docked : suctionHandler();
        }
    }

    public void updateGrab(UUID player, float range, boolean rotating, Quaterniondc orientation) {
        if (isDocked()) {
            return;
        }
        GrabConstraint c = grabbers.get(player);
        if (c == null) {
            c = new GrabConstraint(player, range);
            grabbers.put(player, c);
        } else {
            c.setScrollDistance(range);
        }
        c.setRotation(rotating, orientation);
    }

    @Override
    public boolean canGrab() {
        return !isDocked();
    }

    @Override
    public Map<UUID, GrabConstraint> grabbers() {
        return grabbers;
    }

    public boolean isDocked() {
        return dockedTarget != null;
    }

    @Nullable
    public net.neoforged.neoforge.fluids.capability.IFluidHandler dockedFluidHandler() {
        return dockedTarget == null ? null : dockedTarget.getDockedFluidHandler();
    }

    @Nullable
    public java.util.UUID dockHatchSubLevelId() {
        return hatchSubLevelId;
    }

    public boolean canUndock() {
        return isDocked() && undockSafety <= 0;
    }

    public boolean canRedock(long gameTime) {
        return gameTime >= redockReadyTick;
    }

    public void dock(IDockable hatch, @Nullable SubLevel hatchSub, Vec3 anchor,
                     Quaterniondc orientation) {
        if (isDocked()) {
            return;
        }
        clearGrabs();
        dockedTarget = hatch;
        hatchSubLevelId = hatchSub == null ? null : hatchSub.getUniqueId();
        dockPoint.set(anchor.x, anchor.y, anchor.z);
        dockOrientation.set(orientation);
        dockCaptured = false;
        undockSafety = UNDOCK_SAFETY_TICKS;
        if (level != null) {
            NozzleDockSounds.docked(level, worldPosition);
        }
        setChanged();
    }

    public void forceUndock() {
        undockSafety = 0;
        undock(level == null ? 0L : level.getGameTime());
    }

    public void undock(long gameTime) {
        if (!canUndock()) {
            return;
        }
        releaseDockConstraint();
        if (dockedTarget != null) {
            dockedTarget.onUndocked();
            dockedTarget = null;
        }
        hatchSubLevelId = null;
        if (level != null) {
            NozzleDockSounds.undocked(level, worldPosition);
        }
        redockReadyTick = gameTime + REDOCK_COOLDOWN_TICKS;
        setChanged();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        NozzleDockService.unregister(this);
        CLIENT_NOZZLES.remove(this);
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level != null && level.isClientSide) {
            CLIENT_NOZZLES.add(this);
        }
    }

    public static boolean clientNozzleAt(org.joml.Vector3d world) {
        for (HoseNozzleBlockEntity nozzle : CLIENT_NOZZLES) {
            if (nozzle.level == null) {
                continue;
            }
            Vec3 anchor = nozzle.getAttachmentPoint();
            Vec3 at = dev.ryanhcode.sable.Sable.HELPER.projectOutOfSubLevel(nozzle.level, anchor);
            if (at.distanceToSqr(world.x, world.y, world.z) < 0.09) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (!(subLevel.getLevel() instanceof ServerLevel level)) {
            return;
        }
        NozzleDockService.register(this);
        if (dockedTarget != null && !dockedTarget.isDockPresent()) {
            forceUndock();
        }
        setPowered(subLevel, isDocked());
        traceFloat(subLevel, handle);
        if (isDocked()) {
            if (undockSafety > 0) {
                undockSafety--;
            }
            maintainDockConstraint(subLevel);
            return;
        }
        applySprayRecoil(subLevel, handle);
        driveGrabs(subLevel, level);
    }

    private void traceFloat(ServerSubLevel subLevel, RigidBodyHandle handle) {
        if (subLevel.getLevel().getGameTime() % 20 != 0 || handle == null || !handle.isValid()) {
            return;
        }
        FluidHit hit = null;
        double probeInset = 0.0;
        for (double inset : TRACE_PROBE_INSETS) {
            hit = fluidHit(inset);
            if (hit != null) {
                probeInset = inset;
                break;
            }
        }
        dev.ryanhcode.sable.api.physics.mass.MassData mass = subLevel.getMassTracker();
        Vector3d velocity = handle.getLinearVelocity(new Vector3d());
        String column = "none";
        if (hit != null) {
            BlockPos top = hit.pos();
            while (!level.getFluidState(top.above()).isEmpty()) {
                top = top.above();
            }
            BlockPos bottom = hit.pos();
            while (!level.getFluidState(bottom.below()).isEmpty()) {
                bottom = bottom.below();
            }
            double surfaceLocal = top.getY() + level.getFluidState(top).getHeight(level, top);
            Vector3d surface = new Vector3d(top.getX() + 0.5, surfaceLocal, top.getZ() + 0.5);
            if (hit.sub() != null) {
                hit.sub().logicalPose().transformPosition(surface);
            }
            BlockPos lp = getBlockPos();
            double bottomWorldY = subLevel.logicalPose()
                    .transformPosition(new Vector3d(lp.getX() + 0.5, lp.getY(), lp.getZ() + 0.5)).y;
            column = "probe=" + probeInset + " depth=" + (top.getY() - bottom.getY() + 1)
                    + " floor=" + level.getBlockState(bottom.below()) + " surfaceWorldY=" + surface.y
                    + " bottomWorldY=" + bottomWorldY + " gap=" + (bottomWorldY - surface.y);
        }
        LoconauticsConstants.LOGGER.info(
                "[hosefloat] vy={} speed={} mass={} volume={} hitSub={} hitPos={} column=[{}] ropes=[{}] docked={}",
                velocity.y, velocity.length(), mass == null ? null : mass.getMass(),
                dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper.getVolume(getBlockState()),
                hit == null ? null : hit.sub() == null ? "world" : hit.sub().getUniqueId(),
                hit == null ? null : hit.pos(), column, ropeTrace(), isDocked());
    }

    private String ropeTrace() {
        StringBuilder out = new StringBuilder();
        HoseRopeManager hoses = HoseRopeManager.get(level);
        if (holder != null && holder.getAttachedStrandId() != null && hoses != null) {
            HoseRope rope = hoses.get(holder.getAttachedStrandId());
            out.append("hose stretch=").append(rope == null ? "none" : rope.getStretchRatio()).append(' ');
        }
        SteelCableRopeManager cables = SteelCableRopeManager.get(level);
        SteelCableHolderBehaviour cableHolder = getBehaviour(SteelCableHolderBehaviour.TYPE);
        if (cableHolder != null && cables != null) {
            for (UUID id : cableHolder.attachedStrandIds()) {
                SteelCableRope rope = cables.get(id);
                out.append("link taut=").append(rope == null ? "none" : rope.isTaut())
                        .append(" ext=").append(rope == null ? "none" : rope.getCurrentExtension())
                        .append(" goal=").append(rope == null ? "none" : rope.getExtension()).append(' ');
            }
        }
        return out.toString().trim();
    }

    public void setSprayReaction(float reaction) {
        this.sprayReaction = reaction;
        if (level != null) {
            this.sprayReactionTick = level.getGameTime();
        }
    }

    private static final double RECOIL_DELTA_V = 0.5;

    private void applySprayRecoil(ServerSubLevel subLevel, RigidBodyHandle handle) {
        if (sprayReaction <= 0.0f || handle == null || !handle.isValid()) {
            return;
        }
        if (subLevel.getLevel().getGameTime() - sprayReactionTick > 2) {
            sprayReaction = 0.0f;
            return;
        }
        dev.ryanhcode.sable.api.physics.mass.MassData mass = subLevel.getMassTracker();
        if (mass == null || mass.isInvalid()) {
            return;
        }
        org.joml.Vector3dc com = mass.getCenterOfMass();
        if (com == null) {
            return;
        }
        double phase = subLevel.getLevel().getGameTime() * 0.9;
        double pulse = 0.75 + 0.25 * Math.sin(phase);
        double impulse = mass.getMass() * RECOIL_DELTA_V * sprayReaction * pulse;
        handle.applyImpulseAtPoint(new Vector3d(com), new Vector3d(0.0, impulse, 0.0));
    }

    private void releaseDockConstraint() {
        if (dockConstraint != null) {
            if (dockConstraint.isValid()) {
                dockConstraint.remove();
            }
            dockConstraint = null;
        }
    }

    private void maintainDockConstraint(ServerSubLevel subLevel) {
        if (!(SubLevelContainer.getContainer(subLevel.getLevel()) instanceof ServerSubLevelContainer container)) {
            return;
        }
        PhysicsPipelineBody hatchBody = null;
        if (hatchSubLevelId != null) {
            // a removed hatch still looks alive to getsublevel for a tick, and rapier hates what follows
            if (container.getSubLevel(hatchSubLevelId) instanceof PhysicsPipelineBody body && !body.isRemoved()) {
                hatchBody = body;
            } else {
                releaseDockConstraint();
                return;
            }
        }
        if (!dockCaptured) {
            captureDockPose(subLevel, hatchBody);
            dockCaptured = true;
        }
        if (dockLerp >= 1.0 && dockConstraint != null && dockConstraint.isValid()) {
            return;
        }
        dockLerp = Math.min(1.0, dockLerp + DOCK_LERP_STEP);
        releaseDockConstraint();
        Vector3d anchorB = getGrabCenter().fma(1.0 - dockLerp, dockInitialRelPos);
        double rotLerp = Math.min(1.0, dockLerp * 2.0);
        Quaterniond orient = new Quaterniond(dockInitialRelOrient).slerp(dockTargetOrient, rotLerp);
        dockConstraint = container.physicsSystem().getPipeline().addConstraint(
                hatchBody, subLevel,
                new FixedConstraintConfiguration(new Vector3d(dockPoint), anchorB, orient));
    }

    private void captureDockPose(ServerSubLevel nozzleSub, @Nullable PhysicsPipelineBody hatchBody) {
        Vector3d worldA = new Vector3d(dockPoint);
        Quaterniond orientA = new Quaterniond();
        if (hatchBody instanceof SubLevel hatchSub) {
            hatchSub.logicalPose().transformPosition(new Vector3d(dockPoint), worldA);
            orientA.set(hatchSub.logicalPose().orientation());
        }
        Vector3d worldB = nozzleSub.logicalPose().transformPosition(getGrabCenter(), new Vector3d());
        Quaterniond orientB = new Quaterniond(nozzleSub.logicalPose().orientation());
        orientB.transformInverse(worldA.sub(worldB, new Vector3d()), dockInitialRelPos);
        dockInitialRelOrient.set(new Quaterniond(orientA).conjugate().mul(orientB));
        dockTargetOrient.set(dockOrientation);
        dockLerp = 0.0;
    }

    private void setPowered(ServerSubLevel subLevel, boolean powered) {
        BlockState st = subLevel.getLevel().getBlockState(getBlockPos());
        if (st.hasProperty(HoseNozzleBlock.POWERED) && st.getValue(HoseNozzleBlock.POWERED) != powered) {
            subLevel.getLevel().setBlock(getBlockPos(), st.setValue(HoseNozzleBlock.POWERED, powered), 3);
        }
    }

    @Override
    public Iterable<SubLevel> sable$getConnectionDependencies() {
        return List.of();
    }

    public void driveSprayServer(FluidStack fluid, float throwStrength, boolean leaking) {
        this.sprayFluid = fluid == null ? FluidStack.EMPTY : fluid;
        this.sprayThrow = leaking ? throwStrength : 0.0f;
        setSprayReaction(leaking ? throwStrength : 0.0f);
        if (leaking && throwStrength > 0.0f) {
            manageSpray(throwStrength);
        } else {
            killSpray();
        }
        if (Math.abs(sprayThrow - lastSyncedSprayThrow) > 0.02f
                || Math.abs(sprayFluid.getAmount() - lastSyncedSprayFluid.getAmount()) > 50
                || !FluidStack.isSameFluidSameComponents(sprayFluid, lastSyncedSprayFluid)) {
            lastSyncedSprayThrow = sprayThrow;
            lastSyncedSprayFluid = sprayFluid.copy();
            setChanged();
            sendData();
        }
    }

    private static final int SPRAY_INTERVAL_TICKS = 5;

    private void manageSpray(float power) {
        if (!(level instanceof ServerLevel slevel)) {
            return;
        }
        if (slevel.getGameTime() % SPRAY_INTERVAL_TICKS == 0
                && HoseSprayInteractions.get(sprayFluid) != null) {
            spawnSprayDroplet(slevel, power);
        }
    }

    private void spawnSprayDroplet(ServerLevel slevel, float power) {
        BlockPos lp = getBlockPos();
        double cx = lp.getX() + 0.5, cz = lp.getZ() + 0.5;
        Vector3d o = projectOut(cx, lp.getY() - 1.0 / 16.0, cz);
        Vector3d b = projectOut(cx, lp.getY() - 0.5, cz);
        Vec3 origin = new Vec3(o.x, o.y, o.z);
        Vec3 dir = new Vec3(b.x - o.x, b.y - o.y, b.z - o.z);
        pruneDeadDroplets(slevel);
        UUID attacker = grabbers.keySet().stream().findFirst().orElse(null);
        sprayEntityIds.add(HoseSprayEntity.launch(slevel, origin, dir, sprayFluid, power, attacker).getUUID());
    }

    private void killSpray() {
        if (sprayEntityIds.isEmpty()) {
            return;
        }
        if (level instanceof ServerLevel slevel) {
            for (UUID id : sprayEntityIds) {
                if (slevel.getEntity(id) instanceof HoseSprayEntity e) {
                    e.discard();
                }
            }
        }
        sprayEntityIds.clear();
    }

    private void pruneDeadDroplets(ServerLevel slevel) {
        sprayEntityIds.removeIf(id -> !(slevel.getEntity(id) instanceof HoseSprayEntity e) || !e.isAlive());
    }

    private Vector3d projectOut(double x, double y, double z) {
        Vec3 w = dev.ryanhcode.sable.Sable.HELPER.projectOutOfSubLevel(level, new Vec3(x, y, z));
        return new Vector3d(w.x, w.y, w.z);
    }

    public void emitSprayClient(dev.ryanhcode.sable.sublevel.ClientSubLevel sub) {
        if (level == null || !level.isClientSide || sub == null) {
            return;
        }
        if (clientSprayThrow <= 0.0f || clientSprayFluid.isEmpty()) {
            return;
        }
        BlockState st = getBlockState();
        if (st.hasProperty(HoseNozzleBlock.POWERED) && st.getValue(HoseNozzleBlock.POWERED)) {
            return;
        }
        BlockPos lp = getBlockPos();
        double cx = lp.getX() + 0.5, cz = lp.getZ() + 0.5, yBase = lp.getY();
        Vector3d spawn = sub.renderPose().transformPosition(new Vector3d(cx, yBase - 1.0 / 16.0, cz), new Vector3d());
        Vector3d belowMore = sub.renderPose().transformPosition(new Vector3d(cx, yBase - 0.5, cz), new Vector3d());
        Vector3d down = belowMore.sub(spawn, new Vector3d());
        if (down.lengthSquared() > 1.0e-9) {
            down.normalize();
        }
        emitFluidJet(spawn, down, jetColors());
    }

    private void emitSuctionClient(@Nullable dev.ryanhcode.sable.sublevel.ClientSubLevel sub) {
        if (level == null || clientSuckFluid.isEmpty() || isDocked()) {
            return;
        }
        BlockPos lp = getBlockPos();
        Vector3d tip = new Vector3d(lp.getX() + 0.5, lp.getY() - 1.0 / 16.0, lp.getZ() + 0.5);
        Vector3d below = new Vector3d(lp.getX() + 0.5, lp.getY() - 0.5, lp.getZ() + 0.5);
        if (sub != null) {
            sub.renderPose().transformPosition(tip);
            sub.renderPose().transformPosition(below);
        }
        Vector3d axis = below.sub(tip, new Vector3d());
        if (axis.lengthSquared() < 1.0e-9) {
            return;
        }
        axis.normalize();
        Vector3d side = new Vector3d(axis.z, axis.x, axis.y).cross(axis);
        if (side.lengthSquared() < 1.0e-6) {
            side.set(1.0, 0.0, 0.0);
        }
        side.normalize();
        Vector3d up = new Vector3d(axis).cross(side).normalize();

        int[] colors = HoseJetColors.of(clientSuckFluid);
        double range = SUCK_MIN_RANGE + (SUCK_MAX_RANGE - SUCK_MIN_RANGE) * clientSuckStrength;
        int count = 2 + (int) (clientSuckStrength * 4.0f);
        for (int i = 0; i < count; i++) {
            double distance = 0.35 + level.random.nextDouble() * (range - 0.35);
            double spawnAngle = level.random.nextDouble() * Math.PI * 2.0;
            double spawnRadius = Math.sqrt(level.random.nextDouble()) * (0.15 + 0.3 * distance);
            Vector3d start = new Vector3d(tip).fma(distance, axis)
                    .fma(Math.cos(spawnAngle) * spawnRadius, side).fma(Math.sin(spawnAngle) * spawnRadius, up);
            double endAngle = level.random.nextDouble() * Math.PI * 2.0;
            double endRadius = Math.sqrt(level.random.nextDouble()) * SUCK_TIP_RADIUS;
            Vector3d end = new Vector3d(tip)
                    .fma(Math.cos(endAngle) * endRadius, side).fma(Math.sin(endAngle) * endRadius, up);
            // the particle reads its velocity as the offset to the end point
            Vector3d toEnd = end.sub(start, new Vector3d());
            level.addParticle(new com.lycoris.loconautics.content.hose.spray.HoseSprayOptions(
                    colors[level.random.nextInt(colors.length)], true), start.x, start.y, start.z,
                    toEnd.x, toEnd.y, toEnd.z);
        }
    }

    private void emitFluidJet(Vector3d spawn, Vector3d down, int[] colors) {
        Vector3d axis = new Vector3d(down);
        Vector3d side = new Vector3d(axis.z, axis.x, axis.y).cross(axis);
        if (side.lengthSquared() < 1.0e-6) {
            side.set(1.0, 0.0, 0.0);
        }
        side.normalize();
        Vector3d up = new Vector3d(axis).cross(side).normalize();

        float t = net.minecraft.util.Mth.clamp(clientSprayThrow, 0.0f, 1.0f);
        double coreSpeed = 0.30 + 0.30 * t;
        int coreCount = 1 + (int) (t * 4.0f);
        int mistCount = (int) (t * 5.0f);

        for (int i = 0; i < coreCount; i++) {
            double spread = 0.04;
            Vector3d v = new Vector3d(axis).mul(coreSpeed * (0.85 + level.random.nextFloat() * 0.3))
                    .fma((level.random.nextFloat() - 0.5) * spread, side)
                    .fma((level.random.nextFloat() - 0.5) * spread, up);
            com.lycoris.loconautics.trainphysics.fx.ParticleWorldLock.addParticle(level, sprayOption(colors), spawn.x, spawn.y, spawn.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < mistCount; i++) {
            double spread = 0.14;
            double mistSpeed = 0.06 + level.random.nextFloat() * 0.08;
            Vector3d v = new Vector3d(axis).mul(mistSpeed)
                    .fma((level.random.nextFloat() - 0.5) * spread, side)
                    .fma((level.random.nextFloat() - 0.5) * spread, up);
            com.lycoris.loconautics.trainphysics.fx.ParticleWorldLock.addParticle(level, sprayOption(colors), spawn.x, spawn.y, spawn.z, v.x, v.y, v.z);
        }
    }

    private com.lycoris.loconautics.content.hose.spray.HoseSprayOptions sprayOption(int[] colors) {
        return new com.lycoris.loconautics.content.hose.spray.HoseSprayOptions(colors[level.random.nextInt(colors.length)]);
    }

    private int[] jetColors() {
        return HoseJetColors.of(clientSprayFluid);
    }

    @Override
    protected void write(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries,
                         boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("SuctionTank", suctionTank.writeToNBT(registries, new net.minecraft.nbt.CompoundTag()));
        if (clientPacket) {
            tag.putFloat("SprayThrow", sprayThrow);
            tag.put("SprayFluid", sprayFluid.saveOptional(registries));
            tag.put("SuckFluid", lastSyncedSuckFluid.saveOptional(registries));
            tag.putFloat("SuckStrength", lastSyncedSuckStrength);
        }
    }

    @Override
    protected void read(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries,
                        boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        suctionTank.readFromNBT(registries, tag.getCompound("SuctionTank"));
        if (clientPacket) {
            clientSprayThrow = tag.getFloat("SprayThrow");
            clientSprayFluid = tag.contains("SprayFluid")
                    ? FluidStack.parseOptional(registries, tag.getCompound("SprayFluid")) : FluidStack.EMPTY;
            clientSuckFluid = tag.contains("SuckFluid")
                    ? FluidStack.parseOptional(registries, tag.getCompound("SuckFluid")) : FluidStack.EMPTY;
            clientSuckStrength = tag.getFloat("SuckStrength");
        }
    }

    @Override
    public Vec3 getAttachmentPoint() {
        return StrandAnchors.hoseNozzle(getBlockPos());
    }

    @Override
    public boolean canAttachStrand() {
        return holder != null && holder.isFree()
                && StrandAttachments.isFree(this, SteelCableHolderBehaviour.TYPE);
    }

    @Override
    public void onStrandAttached(HoseStrand strand) {
        holder.setAttachedStrandId(strand.id);
        setChanged();
    }

    @Override
    public void onStrandDetached() {
        if (holder == null || holder.getAttachedStrandId() == null) {
            return;
        }
        holder.setAttachedStrandId(null);
        setChanged();
    }

    @Nullable
    @Override
    public HoseStrand getAttachedStrand() {
        return holder == null ? null : holder.getAttachedStrand();
    }
}

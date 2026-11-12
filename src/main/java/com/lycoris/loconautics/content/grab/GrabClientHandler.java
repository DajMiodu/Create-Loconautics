package com.lycoris.loconautics.content.grab;

import com.lycoris.loconautics.common.grab.GrabTarget;
import com.lycoris.loconautics.common.grab.GrabbableActor;
import com.lycoris.loconautics.common.strand.StrandKnotPicker;
import com.lycoris.loconautics.network.packets.attachment.GrabDragPacket;
import com.lycoris.loconautics.network.packets.attachment.GrabStopPacket;
import com.lycoris.loconautics.network.packets.attachment.StrandHookGrabPacket;
import com.lycoris.loconautics.network.packets.attachment.StrandKnotGrabPacket;
import com.mojang.blaze3d.platform.InputConstants;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

public final class GrabClientHandler {

    private static final float MIN_GRAB_DISTANCE = GrabTarget.MIN_GRAB_DISTANCE;
    private static final float MAX_GRAB_DISTANCE = GrabTarget.MAX_GRAB_DISTANCE;

    public static final KeyMapping ROTATE_MODE = new KeyMapping(
            "key.loconautics.grab_rotate", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_TAB, "key.categories.loconautics");

    private static final double ROTATE_SENSITIVITY = 0.35;

    private static final double SENSITIVITY_THAT_ZEROES_THE_TURN = -1.0 / 3.0;

    @Nullable private static UUID grabSub;
    @Nullable private static UUID grabStrand;
    @Nullable private static UUID grabKnotStrand;
    private static int grabKnotIndex;
    @Nullable private static BlockPos grabLocalPos;
    private static float range;
    private static boolean rotating;
    private static boolean sawFree;
    private static final Quaterniond grabOrientation = new Quaterniond();

    private GrabClientHandler() {
    }

    public static boolean grabbing() {
        return grabSub != null || grabStrand != null || grabKnotStrand != null;
    }

    @Nullable
    public static UUID grabbedStrand() {
        return grabStrand;
    }

    @Nullable
    public static Vec3 grabAnchorWorld(float partialTick) {
        if (grabKnotStrand != null) {
            return StrandKnotPicker.knotPosition(grabKnotStrand, grabKnotIndex, partialTick);
        }
        if (grabStrand != null) {
            return com.lycoris.loconautics.content.screwlink.ScrewLinkGrabClient.hookPosition(grabStrand, partialTick);
        }
        Minecraft mc = Minecraft.getInstance();
        if (grabSub == null || grabLocalPos == null || mc.level == null) {
            return null;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(mc.level);
        if (container == null || !(container.getSubLevel(grabSub) instanceof ClientSubLevel sub)) {
            return null;
        }
        Vector3d w = sub.renderPose(partialTick).transformPosition(
                new Vector3d(grabLocalPos.getX() + 0.5, grabLocalPos.getY() + 0.5, grabLocalPos.getZ() + 0.5));
        return new Vec3(w.x, w.y, w.z);
    }

    public static void onInteract(net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.isSpectator()) {
            return;
        }
        if (grabbing()) {
            stopGrabbing();
            event.setCanceled(true);
            return;
        }
        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
            return;
        }
        // damn you maxwell
        if (player.isSecondaryUseActive()) {
            StrandKnotPicker.Knot knot = StrandKnotPicker.pick();
            if (knot != null) {
                startKnotGrab(knot);
                event.setCanceled(true);
                return;
            }
        }
        UUID hookStrand = com.lycoris.loconautics.content.screwlink.ScrewLinkGrabClient.pickHook();
        if (hookStrand != null) {
            startHookGrab(player, hookStrand);
            event.setCanceled(true);
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos hitPos = hit.getBlockPos();
        SubLevel sub = Sable.HELPER.getContaining(mc.level, hitPos);
        if (sub == null || !(sub.getLevel().getBlockEntity(hitPos) instanceof GrabbableActor)) {
            return;
        }
        Vector3d grabCenterWorld = sub.logicalPose().transformPosition(
                new Vector3d(hitPos.getX() + 0.5, hitPos.getY() + 0.5, hitPos.getZ() + 0.5), new Vector3d());
        double reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).getValue();
        double dist = player.getEyePosition()
                .distanceTo(new Vec3(grabCenterWorld.x, grabCenterWorld.y, grabCenterWorld.z));
        grabSub = sub.getUniqueId();
        grabLocalPos = hitPos;
        range = clampRange((float) Math.min(dist, Math.min(MAX_GRAB_DISTANCE, reach)));
        rotating = false;
        sawFree = false;
        grabOrientation.set(sub.logicalPose().orientation());
        com.lycoris.loconautics.LoconauticsConstants.LOGGER.info("[undockdbg] client grab pos={} powered={}",
                hitPos, isPowered(sub, hitPos));
        if (isPowered(sub, hitPos)) {
            PacketDistributor.sendToServer(new com.lycoris.loconautics.network.packets.attachment.GrabUndockPacket(
                    grabSub, grabLocalPos, range));
        } else {
            sendUpdate();
        }
        event.setCanceled(true);
    }

    private static boolean isPowered(SubLevel sub, BlockPos pos) {
        var state = sub.getLevel().getBlockState(pos);
        return state.hasProperty(com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlock.POWERED)
                && state.getValue(com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlock.POWERED);
    }

    public static void tick() {
        if (!grabbing()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            reset();
            return;
        }
        if (grabKnotStrand != null) {
            if (StrandKnotPicker.knotCount(grabKnotStrand) <= grabKnotIndex + 1) {
                stopGrabbing();
            }
            return;
        }
        if (grabStrand != null) {
            if (!com.lycoris.loconautics.content.screwlink.ScrewLinkGrabClient.stillLoaded(grabStrand)) {
                reset();
            }
            return;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(mc.level);
        if (container == null || container.getSubLevel(grabSub) == null) {
            stopGrabbing();
            return;
        }
        SubLevel sub = container.getSubLevel(grabSub);
        boolean captured = grabLocalPos != null && isPowered(sub, grabLocalPos);
        if (!captured) {
            sawFree = true;
        } else if (sawFree) {
            reset();
        }
    }

    public static boolean onScroll(double deltaY) {
        if (!grabbing()) {
            return false;
        }
        range = clampRange((float) (range + deltaY));
        sendUpdate();
        return true;
    }

    public static void onCalculateTurn(CalculatePlayerTurnEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        double sensitivity = event.getMouseSensitivity() * 0.6 + 0.2;
        double cubed = sensitivity * sensitivity * sensitivity;
        boolean scoped = !event.getCinematicCameraEnabled()
                && mc.options.getCameraType().isFirstPerson() && player.isScoping();
        double scale = scoped ? cubed : cubed * 8.0;
        int invertY = mc.options.invertYMouse().get() ? -1 : 1;
        double yaw = mc.mouseHandler.getXVelocity() * scale;
        double pitch = mc.mouseHandler.getYVelocity() * scale * invertY;
        if (!onMouseTurn(yaw, pitch)) {
            return;
        }
        event.setMouseSensitivity(SENSITIVITY_THAT_ZEROES_THE_TURN);
        event.setCinematicCameraEnabled(false);
    }

    public static boolean onMouseTurn(double yaw, double pitch) {
        if (!grabbing() || grabKnotStrand != null) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        if (!ROTATE_MODE.isDown()) {
            if (rotating) {
                rotating = false;
                sendUpdate();
            }
            return false;
        }
        Vec3 axis = player.calculateViewVector(0.0f, player.getYRot() - 90.0f);
        grabOrientation.rotateLocalY(Math.toRadians(yaw) * ROTATE_SENSITIVITY);
        grabOrientation.premul(new Quaterniond(
                new AxisAngle4d(Math.toRadians(-pitch) * ROTATE_SENSITIVITY, axis.x, axis.y, axis.z)));
        rotating = true;
        sendUpdate();
        return true;
    }

    private static void startKnotGrab(StrandKnotPicker.Knot knot) {
        grabKnotStrand = knot.strand();
        grabKnotIndex = knot.index();
        grabStrand = null;
        grabSub = null;
        grabLocalPos = null;
        rotating = false;
        sawFree = false;
        range = clampRange((float) knot.distance());
        sendUpdate();
    }

    private static void startHookGrab(LocalPlayer player, UUID strandId) {
        grabStrand = strandId;
        grabSub = null;
        grabLocalPos = null;
        rotating = false;
        sawFree = false;
        range = clampRange((float) Math.min(
                player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).getValue(), MAX_GRAB_DISTANCE));
        grabOrientation.identity();
        sendUpdate();
    }

    private static void sendUpdate() {
        if (grabKnotStrand != null) {
            PacketDistributor.sendToServer(new StrandKnotGrabPacket(grabKnotStrand, grabKnotIndex, false, range));
            return;
        }
        if (grabStrand != null) {
            PacketDistributor.sendToServer(new StrandHookGrabPacket(
                    grabStrand, false, range, rotating, new Quaterniond(grabOrientation)));
            return;
        }
        if (grabSub != null && grabLocalPos != null) {
            PacketDistributor.sendToServer(
                    new GrabDragPacket(grabSub, grabLocalPos, range, rotating, new Quaterniond(grabOrientation)));
        }
    }

    private static void stopGrabbing() {
        if (grabKnotStrand != null) {
            PacketDistributor.sendToServer(StrandKnotGrabPacket.stopping(grabKnotStrand));
        }
        if (grabStrand != null) {
            PacketDistributor.sendToServer(StrandHookGrabPacket.stopping(grabStrand));
        }
        if (grabSub != null && grabLocalPos != null) {
            PacketDistributor.sendToServer(new GrabStopPacket(grabSub, grabLocalPos));
        }
        reset();
    }

    private static void reset() {
        grabSub = null;
        grabStrand = null;
        grabKnotStrand = null;
        grabLocalPos = null;
        rotating = false;
        sawFree = false;
    }

    private static float clampRange(float r) {
        return Mth.clamp(r, MIN_GRAB_DISTANCE, MAX_GRAB_DISTANCE);
    }
}

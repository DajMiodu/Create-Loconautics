package com.lycoris.loconautics.content.analogcontroller;

import com.lycoris.loconautics.network.packets.analogcontroller.AnalogControllerInputPacket;
import com.simibubi.create.foundation.utility.ControlsUtil;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Collection;
import java.util.HashSet;

@OnlyIn(Dist.CLIENT)
public class AnalogControllerClientHandler {

    private static final int PACKET_RATE = 5;

    private static BlockPos mountedPos;
    private static Collection<Integer> currentlyPressed = new HashSet<>();
    private static int packetCooldown;



    public static void startControlling(BlockPos pos) {
        mountedPos = pos;
        currentlyPressed.clear();
        packetCooldown = 0;
        Minecraft.getInstance().player.displayClientMessage(
                Component.translatable("block.loconautics.analog_controller.start"),
                true
        );
    }

    public static void stopControllingClient() {
        if (mountedPos == null) return;
        CatnipServices.NETWORK.sendToServer(
                new com.lycoris.loconautics.network.packets.analogcontroller.AnalogControllerDismountPacket(mountedPos)
        );
        stopControlling();
    }

    public static void stopControlling() {
        if (mountedPos == null) return;

        ControlsUtil.getControls().forEach(kb -> kb.setDown(ControlsUtil.isActuallyPressed(kb)));

        if (!currentlyPressed.isEmpty()) {
            CatnipServices.NETWORK.sendToServer(
                    new AnalogControllerInputPacket(currentlyPressed, false, mountedPos)
            );
        }

        Minecraft.getInstance().player.displayClientMessage(
                Component.translatable("block.loconautics.analog_controller.stop"),
                true
        );

        mountedPos = null;
        currentlyPressed.clear();
        packetCooldown = 0;
    }

    // no packets here, the server already dropped the driver when the world went away
    public static void clear() {
        mountedPos = null;
        currentlyPressed.clear();
        packetCooldown = 0;
    }

    public static boolean isControlling() {
        return mountedPos != null;
    }

    public static BlockPos getMountedPos() {
        return mountedPos;
    }

    public static AnalogControllerBlockEntity resolveMountedBlockEntity() {
        if (mountedPos == null) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        if (mc.level.getBlockEntity(mountedPos) instanceof AnalogControllerBlockEntity hostBe) {
            return hostBe;
        }
        dev.ryanhcode.sable.sublevel.ClientSubLevel sub =
                dev.ryanhcode.sable.Sable.HELPER.getContainingClient(mountedPos);
        if (sub != null && sub.getLevel().getBlockEntity(mountedPos) instanceof AnalogControllerBlockEntity subBe) {
            return subBe;
        }
        return null;
    }

    public static double speedBarRatio(AnalogControllerBlockEntity be) {
        dev.ryanhcode.sable.sublevel.ClientSubLevel sub =
                dev.ryanhcode.sable.Sable.HELPER.getContainingClient(be.getBlockPos());
        if (sub == null) return 0.0;
        var marker = com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry.get(sub.getUniqueId());
        return marker == null ? 0.0 : (int) (speedFraction(marker.speed(), marker) * 18) / 18.0;
    }

    public static double speedFraction(double speed,
                                       com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry.TrainMarker marker) {
        double top = marker != null && marker.topSpeed() > 0.0
                ? marker.topSpeed() : com.lycoris.loconautics.trainphysics.drive.TrainPropulsion.bearingAxleCapBpt();
        return top <= 0.0 ? 0.0 : Math.min(1.0, Math.abs(speed) / top);
    }

    private static net.minecraft.client.KeyMapping[] controlKeys() {
        net.minecraft.client.Options o = Minecraft.getInstance().options;
        return new net.minecraft.client.KeyMapping[] {
                o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keyShift, o.keySprint
        };
    }

    private static boolean isBoundKeyDown(long window, net.minecraft.client.KeyMapping kb) {
        com.mojang.blaze3d.platform.InputConstants.Key key = kb.getKey();
        if (key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE) {
            return org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, key.getValue())
                    == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        }
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, key.getValue());
    }

    public static void tick() {
        if (mountedPos == null) return;

        Minecraft mc = Minecraft.getInstance();

        if (mc.level != null && resolveMountedBlockEntity() == null
                && !(mc.level.getBlockState(mountedPos).getBlock()
                        instanceof com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlock)) {
            stopControlling();
            return;
        }

        long window = mc.getWindow().getWindow();

        net.minecraft.client.KeyMapping[] keys = controlKeys();
        HashSet<Integer> pressedKeys = new HashSet<>();
        for (int i = 0; i < keys.length; i++) {
            if (isBoundKeyDown(window, keys[i])) {
                pressedKeys.add(i);
            }
        }

        HashSet<Integer> newKeys = new HashSet<>(pressedKeys);
        Collection<Integer> releasedKeys = new HashSet<>(currentlyPressed);
        newKeys.removeAll(currentlyPressed);
        releasedKeys.removeAll(pressedKeys);

        if (!releasedKeys.isEmpty()) {
            CatnipServices.NETWORK.sendToServer(
                    new AnalogControllerInputPacket(releasedKeys, false, mountedPos)
            );
        }

        if (!newKeys.isEmpty()) {
            CatnipServices.NETWORK.sendToServer(
                    new AnalogControllerInputPacket(newKeys, true, mountedPos)
            );
            packetCooldown = PACKET_RATE;
        }

        if (packetCooldown <= 0 && !pressedKeys.isEmpty()) {
            CatnipServices.NETWORK.sendToServer(
                    new AnalogControllerInputPacket(pressedKeys, true, mountedPos)
            );
            packetCooldown = PACKET_RATE;
        }

        if (packetCooldown > 0) packetCooldown--;

        currentlyPressed = pressedKeys;

        ControlsUtil.getControls().forEach(kb -> kb.setDown(false));
    }
}
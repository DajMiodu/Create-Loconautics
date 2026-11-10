package com.lycoris.loconautics.content.controls.panelvalve;

import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.network.PacketTargets;
import com.lycoris.loconautics.network.packets.controls.ValveOpenPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ValveMenu {

    private ValveMenu() {}

    public static void open(Player player, BlockPos panelPos, PanelSlot slot) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        PanelBlockEntity panel = PacketTargets.blockEntity(
                serverPlayer.level(), panelPos, PanelBlockEntity.class);
        if (panel == null) return;
        PanelPart part = panel.getPart(slot);
        if (!(part instanceof ValvePart valve)) return;
        PacketDistributor.sendToPlayer(serverPlayer,
                new ValveOpenPacket(panelPos, slot.ordinal(), valve.degrees()));
    }
}

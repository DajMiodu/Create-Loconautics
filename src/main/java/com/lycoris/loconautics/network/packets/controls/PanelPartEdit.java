package com.lycoris.loconautics.network.packets.controls;

import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.network.PacketTargets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class PanelPartEdit {

    @FunctionalInterface
    public interface Action<T extends PanelPart> {
        void apply(ServerPlayer player, PanelBlockEntity panel, PanelSlot slot, T part);
    }

    private PanelPartEdit() {
    }

    public static <T extends PanelPart> void run(IPayloadContext context, BlockPos panelPos, int slotIndex,
                                                 Class<T> partType, Action<T> action) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (slotIndex < 0 || slotIndex >= PanelSlot.values().length) {
                return;
            }
            PanelBlockEntity panel = PacketTargets.blockEntity(player.level(), panelPos, PanelBlockEntity.class);
            if (panel == null || !PacketTargets.withinReach(player, panel)) {
                return;
            }
            PanelSlot slot = PanelSlot.values()[slotIndex];
            PanelPart part = panel.getPart(slot);
            if (!partType.isInstance(part)) {
                return;
            }
            action.apply(player, panel, slot, partType.cast(part));
        });
    }
}

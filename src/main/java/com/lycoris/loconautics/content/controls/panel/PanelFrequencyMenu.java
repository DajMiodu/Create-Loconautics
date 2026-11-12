package com.lycoris.loconautics.content.controls.panel;

import com.lycoris.loconautics.content.frequency.BindFrequencyMenu;
import com.lycoris.loconautics.network.PacketTargets;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.lycoris.loconautics.client.ClientAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public class PanelFrequencyMenu extends BindFrequencyMenu<PanelSlotTarget> {

    public static final String SIGNAL_KEY = "gui.loconautics.bind_frequency.signal";

    private BlockPos clientPos;
    private PanelSlot clientSlot;
    private PanelPart clientPart;

    public PanelFrequencyMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData, Component.translatable(SIGNAL_KEY));
    }

    public PanelFrequencyMenu(MenuType<?> type, int id, Inventory inv, PanelSlotTarget target) {
        super(type, id, inv, target, Component.translatable(SIGNAL_KEY));
    }

    public static void open(Player player, BlockPos panelPos, PanelSlot slot) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        PanelBlockEntity panel = PacketTargets.blockEntity(serverPlayer.level(), panelPos, PanelBlockEntity.class);
        if (panel == null || !panel.hasPart(slot)) return;
        open(player, panel, slot);
    }

    public static void open(Player player, PanelBlockEntity panel, PanelSlot slot) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        PanelSlotTarget target = new PanelSlotTarget(panel, slot);
        serverPlayer.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new PanelFrequencyMenu(
                        LoconauticsRegistries.PANEL_FREQUENCY_MENU.get(), id, inv, target),
                panel.stackForSlot(slot).getHoverName()), buf -> {
            PanelPart part = panel.getPart(slot);
            CompoundTag partTag = new CompoundTag();
            part.write(partTag);
            buf.writeBlockPos(panel.getBlockPos());
            buf.writeVarInt(slot.ordinal());
            buf.writeVarInt(part.type().ordinal());
            buf.writeNbt(partTag);
        });
    }

    public BlockPos panelPos() {
        return contentHolder != null ? contentHolder.panel().getBlockPos() : clientPos;
    }

    public PanelSlot slot() {
        return contentHolder != null ? contentHolder.slot() : clientSlot;
    }

    public PanelPart part() {
        if (clientPart != null) return clientPart;
        return contentHolder == null ? null : contentHolder.panel().getPart(contentHolder.slot());
    }

    @Override
    protected int rowCount() {
        return 1;
    }

    public boolean hasFrequency() {
        return part() instanceof RedstoneLinkedPart;
    }

    @Override
    protected void addSlots() {
        addPlayerSlots(PanelPartLayout.INV_X + 8, PanelPartLayout.INV_Y + 18);
        if (hasFrequency()) {
            addGhostPair(0, PanelPartLayout.GHOST_ONE_X, PanelPartLayout.GHOST_TWO_X, PanelPartLayout.GHOST_Y);
        }
    }

    @Override
    protected PanelSlotTarget createOnClient(RegistryFriendlyByteBuf extraData) {
        BlockPos pos = extraData.readBlockPos();
        int ordinal = extraData.readVarInt();
        PanelPartType[] types = PanelPartType.values();
        int typeOrdinal = extraData.readVarInt();
        CompoundTag partTag = extraData.readNbt();
        PanelSlot[] slots = PanelSlot.values();
        if (ordinal < 0 || ordinal >= slots.length) return null;
        clientPos = pos;
        clientSlot = slots[ordinal];
        if (typeOrdinal >= 0 && typeOrdinal < types.length && partTag != null) {
            clientPart = types[typeOrdinal].read(partTag);
        }
        PanelBlockEntity panel = PacketTargets.blockEntity(
                ClientAccess.level(), pos, PanelBlockEntity.class);
        return panel == null ? null : new PanelSlotTarget(panel, clientSlot);
    }

    @Override
    protected ItemStackHandler createGhostInventory() {
        ItemStackHandler handler = new ItemStackHandler(2);
        PanelLinkTransmitter transmitter =
                contentHolder == null ? null : contentHolder.panel().transmitter(contentHolder.slot());
        if (transmitter != null) {
            handler.setStackInSlot(0, transmitter.first().copy());
            handler.setStackInSlot(1, transmitter.second().copy());
        }
        return handler;
    }

    @Override
    protected void saveData(PanelSlotTarget target) {
        if (target == null || !target.canPlayerUse(player) || !target.hasFrequency()) return;
        ItemStack first = ghostInventory.getStackInSlot(0);
        ItemStack second = ghostInventory.getStackInSlot(1);
        target.panel().setLinkFrequency(target.slot(), first, second);
    }
}

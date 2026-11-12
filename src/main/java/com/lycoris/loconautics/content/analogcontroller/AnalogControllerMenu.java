package com.lycoris.loconautics.content.analogcontroller;

import com.lycoris.loconautics.content.frequency.BindFrequencyMenu;
import com.lycoris.loconautics.client.ClientAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;

public class AnalogControllerMenu extends BindFrequencyMenu<AnalogControllerBlockEntity> {

    public static final String FORWARD_KEY  = "gui.loconautics.bind_frequency.speed";
    public static final String BACKWARD_KEY = "gui.loconautics.bind_frequency.direction";
    public static final String LEFT_KEY     = "gui.loconautics.bind_frequency.left";
    public static final String RIGHT_KEY    = "gui.loconautics.bind_frequency.right";

    public AnalogControllerMenu(MenuType<?> type, int id, Inventory inv,
                                RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData,
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(extraData),
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(extraData),
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(extraData),
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(extraData));
    }

    public AnalogControllerMenu(MenuType<?> type, int id, Inventory inv,
                                AnalogControllerBlockEntity be) {
        super(type, id, inv, be,
                Component.translatable(FORWARD_KEY),
                Component.translatable(BACKWARD_KEY),
                Component.translatable(LEFT_KEY),
                Component.translatable(RIGHT_KEY));
    }

    @Override
    protected int rowCount() {
        return 4;
    }

    @Override
    protected AnalogControllerBlockEntity createOnClient(RegistryFriendlyByteBuf extraData) {
        BlockEntity be = ClientAccess.level().getBlockEntity(extraData.readBlockPos());
        if (be instanceof AnalogControllerBlockEntity ace) {
            ace.readClient(extraData.readNbt(), extraData.registryAccess());
            return ace;
        }
        return null;
    }

    @Override
    protected ItemStackHandler createGhostInventory() {
        ItemStackHandler handler = new ItemStackHandler(8);
        if (contentHolder != null) {
            handler.setStackInSlot(0, contentHolder.getFrequencyFirst().copy());
            handler.setStackInSlot(1, contentHolder.getFrequencySecond().copy());
            handler.setStackInSlot(2, contentHolder.getFrequencyBackFirst().copy());
            handler.setStackInSlot(3, contentHolder.getFrequencyBackSecond().copy());
            handler.setStackInSlot(4, contentHolder.getFrequencyLeftFirst().copy());
            handler.setStackInSlot(5, contentHolder.getFrequencyLeftSecond().copy());
            handler.setStackInSlot(6, contentHolder.getFrequencyRightFirst().copy());
            handler.setStackInSlot(7, contentHolder.getFrequencyRightSecond().copy());
        }
        return handler;
    }

    @Override
    protected void saveData(AnalogControllerBlockEntity be) {
        if (be == null) return;
        be.setFrequency(
                ghostInventory.getStackInSlot(0),
                ghostInventory.getStackInSlot(1));
        be.setBackwardFrequency(
                ghostInventory.getStackInSlot(2),
                ghostInventory.getStackInSlot(3));
        be.setLeftFrequency(
                ghostInventory.getStackInSlot(4),
                ghostInventory.getStackInSlot(5));
        be.setRightFrequency(
                ghostInventory.getStackInSlot(6),
                ghostInventory.getStackInSlot(7));
    }
}

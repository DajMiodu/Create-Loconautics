package com.lycoris.loconautics.content.bearingaxle;

import com.lycoris.loconautics.content.frequency.BindFrequencyMenu;
import com.lycoris.loconautics.client.ClientAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;

public class BearingAxleMenu extends BindFrequencyMenu<BearingAxleBlockEntity> {

    public static final String LEFT_KEY  = "gui.loconautics.bind_frequency.left";
    public static final String RIGHT_KEY = "gui.loconautics.bind_frequency.right";

    public BearingAxleMenu(MenuType<?> type, int id, Inventory inv,
                           RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData,
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(extraData),
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(extraData));
    }

    public BearingAxleMenu(MenuType<?> type, int id, Inventory inv,
                           BearingAxleBlockEntity be) {
        super(type, id, inv, be,
                Component.translatable(LEFT_KEY),
                Component.translatable(RIGHT_KEY));
    }

    @Override
    protected int rowCount() {
        return 2;
    }

    @Override
    protected BearingAxleBlockEntity createOnClient(RegistryFriendlyByteBuf extraData) {
        BlockEntity be = ClientAccess.level().getBlockEntity(extraData.readBlockPos());
        if (be instanceof BearingAxleBlockEntity axle) {
            axle.readClient(extraData.readNbt(), extraData.registryAccess());
            return axle;
        }
        return null;
    }

    @Override
    protected ItemStackHandler createGhostInventory() {
        ItemStackHandler handler = new ItemStackHandler(4);
        if (contentHolder != null) {
            handler.setStackInSlot(0, contentHolder.getLeftFreqFirst().copy());
            handler.setStackInSlot(1, contentHolder.getLeftFreqSecond().copy());
            handler.setStackInSlot(2, contentHolder.getRightFreqFirst().copy());
            handler.setStackInSlot(3, contentHolder.getRightFreqSecond().copy());
        }
        return handler;
    }

    @Override
    protected void saveData(BearingAxleBlockEntity be) {
        if (be == null) return;
        be.setLeftFrequency(
                ghostInventory.getStackInSlot(0),
                ghostInventory.getStackInSlot(1)
        );
        be.setRightFrequency(
                ghostInventory.getStackInSlot(2),
                ghostInventory.getStackInSlot(3)
        );
    }
}

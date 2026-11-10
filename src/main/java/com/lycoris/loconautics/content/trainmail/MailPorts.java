package com.lycoris.loconautics.content.trainmail;

import com.lycoris.loconautics.LoconauticsConstants;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import com.simibubi.create.content.trains.station.GlobalPackagePort;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.regex.PatternSyntaxException;

public final class MailPorts {

    private MailPorts() {
    }

    public record Open(IItemHandlerModifiable items, @Nullable PostboxBlockEntity box) {
    }

    public static boolean holdsWaitingPackage(MinecraftServer server, GlobalStation station, String regex) {
        for (Map.Entry<BlockPos, GlobalPackagePort> entry : station.connectedPorts.entrySet()) {
            GlobalPackagePort port = entry.getValue();
            IItemHandlerModifiable items = open(server, station, entry.getKey(), port).items();
            for (int slot = 0; slot < items.getSlots(); slot++) {
                ItemStack stack = items.getStackInSlot(slot);
                if (!PackageItem.isPackage(stack)) {
                    continue;
                }
                try {
                    if (PackageItem.getAddress(stack).matches(regex)) {
                        return true;
                    }
                } catch (PatternSyntaxException bad) {
                    LoconauticsConstants.LOGGER.warn("[schedule] bad package address filter {}", regex, bad);
                    return false;
                }
            }
        }
        return false;
    }

    public static Open open(MinecraftServer server, GlobalStation station, BlockPos pos, GlobalPackagePort port) {
        ServerLevel level = server.getLevel(station.getBlockEntityDimension());
        if (level != null && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof PostboxBlockEntity box) {
            return new Open(box.inventory, box);
        }
        return new Open(port.offlineBuffer, null);
    }
}

package com.lycoris.loconautics.content.trainmail;

import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.trainphysics.storage.ConsistAccess;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.simibubi.create.Create;
import com.simibubi.create.compat.computercraft.events.PackageEvent;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.trains.station.GlobalPackagePort;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public final class TrainMailTransfer {

    private static final int INTERVAL_TICKS = 20;

    // unloads is false for a retrieve away from the post office, a fetched package stays aboard even at a postbox with its address
    public record Rules(boolean loads, @Nullable String loadRegex, boolean deposit, boolean unloads) {

        public static final Rules ALL = new Rules(true, null, false, true);

        public static Rules of(@Nullable Object instruction, GlobalStation station, boolean depositFlag) {
            if (!(instruction instanceof PostalEntry entry)) {
                return ALL;
            }
            String post = PostRows.stationRegex(entry.loconautics$post());
            boolean atOffice = post != null && station.name.matches(post);
            boolean delivers = entry.loconautics$delivers();
            String regex = PostRows.nameRegex(entry.loconautics$address());
            return new Rules(delivers == atOffice, regex, depositFlag && !delivers && atOffice, delivers || atOffice);
        }

        boolean fetches() {
            return !unloads;
        }

        boolean accepts(ItemStack stack) {
            return loadRegex == null || PackageItem.getAddress(stack).matches(loadRegex);
        }
    }

    private TrainMailTransfer() {
    }

    public static void tick(SableTrain train) {
        GlobalStation station = train.currentStation() != null ? train.currentStation() : train.atStation();
        if (station == null || train.stationState() != SableTrain.StationState.STOPPED) {
            return;
        }
        if (train.dwellTicks() % INTERVAL_TICKS == 1) {
            run(train, station);
        }
    }

    public static void run(SableTrain train, GlobalStation station) {
        SableScheduleRuntime runtime = SableScheduleRuntime.get(train.id());
        Rules rules = runtime == null ? Rules.ALL
                : Rules.of(runtime.currentInstruction(), station, runtime.loconautics$deposit());
        if (run(train, station, rules) && rules.deposit()) {
            runtime.loconautics$setDeposit(false);
        }
    }

    private static boolean run(SableTrain train, GlobalStation station, Rules rules) {
        ServerLevel level = train.level();
        if (station.connectedPorts.isEmpty() || train.isGhost() || level == null || train.car() == null
                || train.car().subLevelId() == null) {
            return false;
        }
        List<IItemHandler> cargo = ConsistAccess.itemsIn(level, train.car().subLevelId(),
                ConsistAccess.WHOLE_CONSIST);
        if (cargo.isEmpty()) {
            return false;
        }
        transfer(level.getServer(), station, cargo, rules);
        return true;
    }

    public static void transfer(MinecraftServer server, GlobalStation station, List<IItemHandler> cargo,
                                Rules rules) {
        if (rules.loads() && !rules.deposit()) {
            load(server, station, cargo, rules);
        }
        if (rules.unloads()) {
            unload(server, station, cargo, rules);
        }
    }

    private static void load(MinecraftServer server, GlobalStation station, List<IItemHandler> cargo, Rules rules) {
        for (Map.Entry<BlockPos, GlobalPackagePort> entry : station.connectedPorts.entrySet()) {
            GlobalPackagePort port = entry.getValue();
            MailPorts.Open open = MailPorts.open(server, station, entry.getKey(), port);
            for (int slot = 0; slot < open.items().getSlots(); slot++) {
                ItemStack stack = open.items().getStackInSlot(slot);
                if (!PackageItem.isPackage(stack) || !rules.accepts(stack)
                        || (PackageItem.matchAddress(stack, port.address) && !rules.fetches())) {
                    continue;
                }
                if (!insert(cargo, stack)) {
                    continue;
                }
                notifyBox(open, stack, "package_sent");
                open.items().setStackInSlot(slot, ItemStack.EMPTY);
                settle(open, port);
            }
        }
    }

    private static void unload(MinecraftServer server, GlobalStation station, List<IItemHandler> cargo,
                               Rules rules) {
        for (IItemHandler handler : cargo) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!PackageItem.isPackage(stack)) {
                    continue;
                }
                ItemStack held = stack.copy();
                if (handler.extractItem(slot, held.getCount(), true).getCount() < held.getCount()) {
                    continue;
                }
                deliver(server, station, handler, slot, held, rules.deposit() && rules.accepts(held));
            }
        }
    }

    private static void deliver(MinecraftServer server, GlobalStation station, IItemHandler from, int slot,
                                ItemStack held, boolean any) {
        for (Map.Entry<BlockPos, GlobalPackagePort> entry : station.connectedPorts.entrySet()) {
            GlobalPackagePort port = entry.getValue();
            if (!any && !PackageItem.matchAddress(held, port.address)) {
                continue;
            }
            MailPorts.Open open = MailPorts.open(server, station, entry.getKey(), port);
            if (!ItemHandlerHelper.insertItemStacked(open.items(), held, true).isEmpty()) {
                continue;
            }
            from.extractItem(slot, held.getCount(), false);
            ItemHandlerHelper.insertItemStacked(open.items(), held, false);
            notifyBox(open, held, "package_received");
            settle(open, port);
            return;
        }
    }

    private static boolean insert(List<IItemHandler> cargo, ItemStack stack) {
        for (IItemHandler handler : cargo) {
            if (!ItemHandlerHelper.insertItemStacked(handler, stack, true).isEmpty()) {
                continue;
            }
            ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false);
            return true;
        }
        return false;
    }

    private static void notifyBox(MailPorts.Open open, ItemStack stack, String status) {
        if (open.box() != null) {
            open.box().computerBehaviour.prepareComputerEvent(new PackageEvent(stack, status));
        }
    }

    private static void settle(MailPorts.Open open, GlobalPackagePort port) {
        if (open.box() == null) {
            port.primed = true;
        } else {
            open.box().spawnParticles();
        }
        Create.RAILWAYS.markTracksDirty();
    }
}

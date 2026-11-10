package com.lycoris.loconautics.content.hatch;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.dock.DockPolling;
import com.lycoris.loconautics.common.dock.IDockable;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.boiler.BoilerWaterSource;
import com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class HullsteelSocketBridge {

    private static final String[] COLORS = {
            "", "_black", "_blue", "_brown", "_cyan", "_green", "_light_blue", "_light_gray",
            "_lime", "_magenta", "_orange", "_pink", "_purple", "_red", "_white", "_yellow",
    };

    private static final Map<BlockEntity, Dock> DOCKS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static boolean installed;

    @Nullable
    public static BoilerWaterSource waterSource(BlockEntity be) {
        return installed && be.getBlockState().is(
                com.lycoris.loconautics.content.boiler.BoilerTags.HATCHES) ? dockOf(be) : null;
    }

    public static void install() {
        try {
            Class<?> api = Class.forName("com.lycoris.hullsteel.api.HullsteelSocketApi");
            Class<?> iface = Class.forName("com.lycoris.hullsteel.api.SocketIntegration");
            Object impl = Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "serverTick" -> {
                            serverTick((BlockEntity) args[0], (Direction) args[1],
                                    (Boolean) args[2], (Boolean) args[3]);
                            yield null;
                        }
                        case "holdsLidOpen" -> dockOf((BlockEntity) args[0]).docked;
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "LoconauticsSocketIntegration";
                        default -> null;
                    });
            api.getMethod("install", iface).invoke(null, impl);
            installed = true;
            LoconauticsConstants.LOGGER.info("Hullsteel sockets wired to the Loconautics nozzle and boiler plumbing");
        } catch (ReflectiveOperationException e) {
            LoconauticsConstants.LOGGER.warn("Hullsteel is present but its socket api did not match, sockets stay decorative", e);
        }
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (String suffix : COLORS) {
            Block block = BuiltInRegistries.BLOCK.get(
                    ResourceLocation.fromNamespaceAndPath("hullsteel", "fluid_hatch" + suffix));
            if (block == net.minecraft.world.level.block.Blocks.AIR) {
                continue;
            }
            event.registerBlock(Capabilities.FluidHandler.BLOCK,
                    (level, pos, state, be, side) -> be == null ? null : dockOf(be).getDockedFluidHandler(),
                    block);
        }
    }

    private static Dock dockOf(BlockEntity socket) {
        return DOCKS.computeIfAbsent(socket, Dock::new);
    }

    private static void serverTick(BlockEntity socket, Direction facing, boolean open, boolean raised) {
        Dock dock = dockOf(socket);
        dock.facing = facing;
        dock.open = open;
        dock.raised = raised;
        if (socket.getLevel() == null) {
            return;
        }
        DockPolling.maybeUndock(dock.docked, dock.nozzle, n -> !n.isRemoved() && n.isDocked(), dock::onUndocked);
        if (dock.docked && dock.nozzle != null && socket.getLevel().hasNeighborSignal(socket.getBlockPos())) {
            dock.nozzle.forceUndock();
        }
        if (!open) {
            return;
        }
        SubLevel socketSub = Sable.HELPER.getContaining(socket);
        Vec3 anchor = dock.anchorLocal();
        long time = socket.getLevel().getGameTime();
        DockPolling.poll(dock, NozzleDockService.all(),
                nozzle -> !nozzle.isRemoved() && !nozzle.isDocked() && nozzle.canRedock(time),
                nozzle -> Sable.HELPER.getContaining(nozzle),
                HoseNozzleBlockEntity::worldCenter,
                (nozzle, sub) -> {
                    nozzle.dock(dock, socketSub, anchor, dock.getDockOrientation());
                    dock.nozzle = nozzle;
                    dock.onDocked();
                });
    }

    private HullsteelSocketBridge() {
    }

    private static final class Dock implements IDockable, BoilerWaterSource {

        private final BlockEntity socket;
        private final FluidTank tank;

        private Direction facing = Direction.DOWN;
        private boolean open;
        private boolean raised;
        private boolean docked;
        @Nullable private HoseNozzleBlockEntity nozzle;

        private final HatchWaterSocket water;

        Dock(BlockEntity socket) {
            this.socket = socket;
            this.tank = new FluidTank(LoconauticsConfig.INJECTOR_TANK_CAPACITY.get(),
                    stack -> stack.getFluid() == Fluids.WATER);
            this.water = new HatchWaterSocket(socket, () -> facing, () -> raised, this::dockSeatOffset, tank);
        }

        Vec3 anchorLocal() {
            return water.dockAnchorLocal();
        }

        @Override
        public Vec3 getDockPoint() {
            return water.getDockPoint();
        }

        @Override
        public Quaterniond getDockOrientation() {
            return water.getDockOrientation();
        }

        @Override
        public double dockRadius() {
            return 0.5;
        }

        @Override
        public double dockSeatOffset() {
            return 3.0 / 16.0;
        }

        @Override
        public boolean canDock() {
            return open && (socket.getLevel() == null || !socket.getLevel().hasNeighborSignal(socket.getBlockPos()));
        }

        @Override
        public boolean isDockPresent() {
            return !socket.isRemoved();
        }

        @Override
        public void onDocked() {
            docked = true;
        }

        @Override
        public void onUndocked() {
            docked = false;
            nozzle = null;
        }

        @Override
        @Nullable
        public IFluidHandler getDockedFluidHandler() {
            return water.getDockedFluidHandler();
        }

        @Override
        public int getWaterAmount() {
            return water.getWaterAmount();
        }

        @Override
        public int getWaterCapacity() {
            return water.getWaterCapacity();
        }

        @Override
        public int drawWater(int millibuckets) {
            return water.drawWater(millibuckets);
        }

        @Override
        public void returnWater(int millibuckets) {
            water.returnWater(millibuckets);
        }
    }
}

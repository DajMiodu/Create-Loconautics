package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.content.hose.winch.HoseWinchBlock;
import com.lycoris.loconautics.content.hose.winch.HoseWinchBlockEntity;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public class HoseScenes {

    private static final BlockPos CASING_LOW = new BlockPos(2, 1, 4);
    private static final BlockPos CASING_HIGH = new BlockPos(2, 2, 4);
    private static final BlockPos LEVER = new BlockPos(2, 3, 4);

    private static final BlockPos WINCH = new BlockPos(2, 2, 3);
    private static final BlockPos REEL_COG = new BlockPos(1, 2, 3);
    private static final BlockPos REEL_SHAFT = new BlockPos(0, 2, 3);

    private static final BlockPos PUMP = new BlockPos(2, 2, 2);
    private static final BlockPos PUMP_COG = new BlockPos(3, 2, 2);
    private static final BlockPos PUMP_SHAFT_NEAR = new BlockPos(3, 2, 3);
    private static final BlockPos PUMP_SHAFT_FAR = new BlockPos(3, 2, 5);
    private static final BlockPos TANK_LOW = new BlockPos(2, 1, 1);
    private static final BlockPos TANK_HIGH = new BlockPos(2, 3, 1);

    private static final BlockPos HATCH = new BlockPos(4, 1, 4);
    private static final BlockPos NOZZLE = new BlockPos(4, 2, 4);

    private static final BlockPos CAMPFIRE = new BlockPos(4, 1, 1);

    private static final BlockPos SOCKET_MOUNT = new BlockPos(2, 1, 2);
    private static final BlockPos SOCKET_ALONE = new BlockPos(2, 2, 2);
    private static final BlockPos NOZZLE_ALONE = new BlockPos(2, 3, 2);
    private static final BlockPos SCAFFOLD = new BlockPos(1, 1, 2);
    private static final BlockPos SOCKET_LEVER = new BlockPos(1, 2, 2);

    private static final Vec3 DOCK_DROP = new Vec3(0.0, -0.8125, 0.0);

    private static final BlockPos F_WINCH_MOUNT = new BlockPos(2, 1, 4);
    private static final BlockPos F_WINCH = new BlockPos(2, 2, 4);
    private static final BlockPos F_PUMP = new BlockPos(2, 3, 4);
    private static final BlockPos F_SOURCE = new BlockPos(2, 4, 4);
    private static final BlockPos F_COG = new BlockPos(3, 3, 4);
    private static final BlockPos F_SHAFT_NEAR = new BlockPos(3, 2, 4);
    private static final BlockPos F_SHAFT_FAR = new BlockPos(3, 1, 4);
    private static final BlockPos F_SHAFT_BASE = new BlockPos(3, 0, 4);
    private static final BlockPos F_TANK = new BlockPos(2, 1, 1);
    private static final BlockPos F_SOCKET = new BlockPos(2, 2, 1);
    private static final BlockPos F_DOCKED = new BlockPos(2, 3, 1);
    private static final BlockPos F_LOOSE = new BlockPos(4, 1, 1);
    private static final Vec3 END_F_DOCKED = new Vec3(2.5, 2.6875, 1.5);
    private static final Vec3 END_F_LOOSE = new Vec3(4.5, 1.5, 1.5);
    private static final Vec3 PULL_OUT_F = new Vec3(2.0, -1.1875, 0.0);
    private static final int HOSE_LEAD_TICKS = 1;

    public static void winch(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "hose_winch", "Reeling Hoses using Hose Winches");

        scene.world().showSection(util.select().fromTo(CASING_LOW, CASING_HIGH), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(WINCH), Direction.SOUTH);
        scene.overlay().showText(60)
                .text("Hose Winches attach flat to any block face")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(WINCH, Direction.NORTH));
        scene.idle(70);

        Selection reel = util.select().fromTo(REEL_SHAFT, WINCH);
        scene.world().showSection(util.select().fromTo(REEL_SHAFT, REEL_COG), Direction.EAST);
        scene.world().setKineticSpeed(reel, 32.0f);
        scene.effects().rotationSpeedIndicator(REEL_COG);
        scene.overlay().showText(60)
                .text("Rotational Force will reel the hose in or out")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(REEL_COG));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("One direction pays hose out, the other hauls it back")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(WINCH, Direction.NORTH));
        scene.idle(70);

        scene.world().showSection(util.select().position(LEVER), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showControls(util.vector().blockSurface(LEVER, Direction.UP), Pointing.DOWN, 30)
                .rightClick();
        scene.idle(7);
        scene.world().toggleRedstonePower(util.select().fromTo(LEVER, WINCH));
        scene.effects().indicateRedstone(LEVER);
        scene.world().setKineticSpeed(reel, 0.0f);
        scene.overlay().showText(60)
                .text("A Redstone signal locks the reel like a ratchet")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(WINCH, Direction.NORTH));
        scene.idle(70);

        scene.overlay().showOutline(PonderPalette.RED, "winch", util.select().position(WINCH), 60);
        scene.overlay().showText(70)
                .text("Breaking the winch snaps the hose and drops the Hose item")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(WINCH, Direction.NORTH));
        scene.idle(80);
    }

    public static void connect(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "hose_connect", "Running a Hose");
        station(scene, util);
        scene.idle(15);

        scene.world().showSection(util.select().position(NOZZLE), Direction.DOWN);
        scene.overlay().showText(60)
                .text("Hose Nozzles are loose bodies, they fall and swing freely")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(NOZZLE));
        scene.idle(70);

        scene.overlay().showControls(util.vector().blockSurface(WINCH, Direction.NORTH), Pointing.RIGHT, 40)
                .rightClick()
                .withItem(new ItemStack(LoconauticsRegistries.HOSE.get()));
        scene.idle(25);
        scene.overlay().showControls(util.vector().topOf(NOZZLE), Pointing.DOWN, 40)
                .rightClick()
                .withItem(new ItemStack(LoconauticsRegistries.HOSE.get()));
        scene.idle(25);
        scene.overlay().showLine(PonderPalette.GREEN,
                util.vector().blockSurface(WINCH, Direction.NORTH), util.vector().topOf(NOZZLE), 90);
        scene.overlay().showText(60)
                .text("Right click the winch with a Hose, then the nozzle")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(NOZZLE));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("A dotted line previews the run while one end is selected")
                .placeNearTarget()
                .pointAt(util.vector().topOf(NOZZLE));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("The preview turns orange off target and red past 28 blocks")
                .attachKeyFrame()
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .pointAt(util.vector().topOf(NOZZLE));
        scene.idle(70);

        scene.overlay().showControls(util.vector().topOf(NOZZLE), Pointing.DOWN, 40)
                .rightClick()
                .whileSneaking()
                .withItem(new ItemStack(LoconauticsRegistries.HOSE.get()));
        scene.idle(25);
        scene.overlay().showText(60)
                .text("Sneak and right click to drop the selection")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(NOZZLE));
        scene.idle(70);

        scene.overlay().showText(70)
                .text("Once taut a hose cannot stretch, but the winch can pay out more")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(WINCH, Direction.NORTH));
        scene.idle(80);
    }

    public static void nozzle(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "hose_nozzle", "Handling Hose Nozzles");
        station(scene, util);
        scene.world().showSection(util.select().position(NOZZLE), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showControls(util.vector().topOf(NOZZLE), Pointing.DOWN, 40)
                .rightClick();
        scene.idle(25);
        scene.overlay().showText(60)
                .text("Right click a nozzle with an empty hand to hold it")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(NOZZLE));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("Scrolling pushes it out or pulls it closer")
                .placeNearTarget()
                .pointAt(util.vector().topOf(NOZZLE));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("Holding Tab while moving the mouse turns it")
                .placeNearTarget()
                .pointAt(util.vector().topOf(NOZZLE));
        scene.idle(70);

        scene.world().showSection(util.select().position(HATCH), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showOutline(PonderPalette.GREEN, "hatch", util.select().position(HATCH), 70);
        scene.overlay().showText(60)
                .text("Near a Fluid Hatch, a nozzle docks itself upright")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(HATCH));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("Grabbing a docked nozzle pops it out again")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(NOZZLE));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("A taut hose pops it out as well")
                .placeNearTarget()
                .pointAt(util.vector().topOf(NOZZLE));
        scene.idle(70);
    }

    public static void fluid(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "hose_fluid", "Pumping Fluids through Hoses", F_SHAFT_BASE);
        Selection pump = util.select().position(F_PUMP);
        Selection drive = fDrive(util);

        scene.world().showSection(util.select().position(F_WINCH_MOUNT), Direction.DOWN);
        scene.idle(5);
        hosed(scene, F_WINCH);
        scene.world().showSection(util.select().position(F_WINCH), Direction.DOWN);
        scene.idle(5);
        scene.world().modifyBlock(F_SOCKET, state -> state.setValue(BlockStateProperties.OPEN, true), false);
        scene.world().showSection(util.select().fromTo(F_TANK, F_SOCKET), Direction.DOWN);
        scene.idle(8);
        ElementLink<WorldSectionElement> nozzle =
                scene.world().showIndependentSection(util.select().position(F_DOCKED), Direction.DOWN);
        scene.world().moveSection(nozzle, DOCK_DROP, 0);
        scene.idle(8);
        hose(scene, F_WINCH, END_F_DOCKED, 0.0f);
        scene.world().showSection(pump, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(F_SOURCE), Direction.DOWN);
        scene.idle(5);
        scene.world().modifyBlockEntity(F_SOURCE, FluidTankBlockEntity.class, be -> be.getTankInventory()
                .fill(new FluidStack(Fluids.WATER, 24000), FluidAction.EXECUTE));
        scene.idle(10);
        scene.overlay().showText(60)
                .text("Hoses need a Mechanical Pump on the face opposite the mount")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(F_PUMP));
        scene.idle(70);

        scene.world().showSection(util.select().position(F_COG), Direction.DOWN);
        scene.idle(5);
        scene.world().restoreBlocks(util.select().position(F_SHAFT_BASE));
        scene.world().showSection(util.select().fromTo(F_SHAFT_NEAR, F_SHAFT_FAR), Direction.DOWN);
        scene.idle(15);
        pumpSpeed(scene, pump, drive, 64.0f);
        scene.world().propagatePipeChange(F_PUMP);
        scene.effects().rotationSpeedIndicator(F_COG);
        scene.overlay().showText(60)
                .text("Pump speed sets how far the pressure reaches...")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().centerOf(F_COG));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("...a hose only works once the pressure covers its whole length")
                .placeNearTarget()
                .pointAt(util.vector().topOf(F_WINCH));
        scene.idle(70);

        scene.overlay().showOutline(PonderPalette.GREEN, "socket", util.select().position(F_SOCKET), 70);
        scene.world().modifyBlockEntity(F_TANK, FluidTankBlockEntity.class, be -> be.getTankInventory()
                .fill(new FluidStack(Fluids.WATER, 8000), FluidAction.EXECUTE));
        scene.overlay().showText(60)
                .text("Docked in a Nozzle Socket, everything pumped goes into it")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(F_SOCKET));
        scene.idle(70);

        pumpSpeed(scene, pump, drive, -64.0f);
        scene.world().propagatePipeChange(F_PUMP);
        scene.effects().rotationDirectionIndicator(F_COG);
        scene.overlay().showText(60)
                .text("A reversed pump pulls Fluid back out through the hose")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(F_PUMP));
        scene.idle(70);

        pumpSpeed(scene, pump, drive, 64.0f);
        scene.world().propagatePipeChange(F_PUMP);
        scene.world().modifyBlock(F_DOCKED, state -> state.setValue(BlockStateProperties.POWERED, false), false);
        drag(scene, nozzle, PULL_OUT_F, F_WINCH, END_F_DOCKED, END_F_LOOSE, 0.0f, 20);
        scene.effects().emitParticles(new Vec3(4.5, 1.2, 1.5),
                scene.effects().simpleParticleEmitter(ParticleTypes.FALLING_WATER, new Vec3(0.0, -0.15, 0.0)),
                3.0f, 40);
        scene.overlay().showText(60)
                .text("A loose nozzle sprays what is pumped, and the jet pushes it back")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(F_LOOSE));
        scene.idle(70);
    }

    public static void spray(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "hose_spray", "Spraying with Hose Nozzles");
        station(scene, util);
        scene.world().showSection(util.select().position(NOZZLE), Direction.DOWN);
        scene.world().modifyBlock(NOZZLE, state -> state.setValue(BlockStateProperties.POWERED, false), false);
        scene.world().showSection(util.select().position(CAMPFIRE), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showLine(PonderPalette.BLUE, util.vector().topOf(NOZZLE), util.vector().topOf(CAMPFIRE), 80);
        scene.idle(20);
        scene.world().modifyBlock(CAMPFIRE, state -> state.setValue(BlockStateProperties.LIT, false), true);
        scene.effects().emitParticles(util.vector().topOf(CAMPFIRE),
                scene.effects().simpleParticleEmitter(ParticleTypes.CLOUD,
                        util.vector().of(0.0, 0.1, 0.0)), 2.0f, 20);
        scene.overlay().showText(60)
                .text("Water puts out fires and soaks farmland")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(CAMPFIRE));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("Lava lights surfaces and sets mobs alight")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(NOZZLE));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("Potions apply their effects to anyone sprayed")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(NOZZLE));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("Honey slows down whatever it covers")
                .placeNearTarget()
                .pointAt(util.vector().topOf(NOZZLE));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("Other fluids can be given an effect by a datapack")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(NOZZLE));
        scene.idle(70);
    }

    public static void socket(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "nozzle_socket", "Docking Nozzles in Nozzle Sockets");

        scene.world().showSection(util.select().position(SOCKET_MOUNT), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(SOCKET_ALONE), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(60)
                .text("Nozzle Sockets hold a nozzle upright and take its Fluid")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(SOCKET_ALONE));
        scene.idle(70);

        scene.overlay().showControls(util.vector().topOf(SOCKET_ALONE), Pointing.DOWN, 30)
                .rightClick();
        scene.idle(10);
        scene.world().modifyBlock(SOCKET_ALONE, state -> state.setValue(BlockStateProperties.OPEN, true), false);
        scene.overlay().showText(50)
                .text("Right click the lid to open it")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(SOCKET_ALONE));
        scene.idle(55);

        ElementLink<WorldSectionElement> nozzle =
                scene.world().showIndependentSection(util.select().position(NOZZLE_ALONE), Direction.DOWN);
        scene.idle(15);
        scene.world().moveSection(nozzle, DOCK_DROP, 12);
        scene.overlay().showText(60)
                .text("A nozzle that comes near an open socket docks itself")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(SOCKET_ALONE));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("Alone, a socket stores Water in its own tank")
                .placeNearTarget()
                .pointAt(util.vector().topOf(SOCKET_ALONE));
        scene.idle(70);

        scene.world().setBlock(SOCKET_MOUNT, AllBlocks.FLUID_TANK.getDefaultState(), true);
        scene.idle(5);
        scene.world().modifyBlockEntity(SOCKET_MOUNT, FluidTankBlockEntity.class, be -> be.getTankInventory()
                .fill(new FluidStack(Fluids.WATER, 8000), FluidAction.EXECUTE));
        scene.overlay().showText(60)
                .text("With a Fluid Tank behind it, the Fluid goes there instead")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(SOCKET_MOUNT));
        scene.idle(70);

        scene.world().showSection(util.select().position(SCAFFOLD), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(SOCKET_LEVER), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showControls(util.vector().topOf(SOCKET_LEVER), Pointing.DOWN, 30)
                .rightClick();
        scene.idle(10);
        scene.world().toggleRedstonePower(util.select().position(SOCKET_LEVER));
        scene.effects().indicateRedstone(SOCKET_LEVER);
        scene.world().moveSection(nozzle, new Vec3(0.0, 1.0, 0.0), 8);
        scene.overlay().showText(60)
                .text("A Redstone signal shuts docking and pops the nozzle out")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(SOCKET_ALONE));
        scene.idle(70);
    }

    private static void station(CreateSceneBuilder scene, SceneBuildingUtil util) {
        scene.world().showSection(util.select().fromTo(CASING_LOW, CASING_HIGH), Direction.DOWN);
        scene.world().showSection(util.select().position(WINCH), Direction.SOUTH);
    }

    private static Selection pumpLine(SceneBuildingUtil util) {
        return util.select().position(PUMP)
                .add(util.select().position(PUMP_COG))
                .add(util.select().fromTo(PUMP_SHAFT_NEAR, PUMP_SHAFT_FAR));
    }

    private static void fill(CreateSceneBuilder scene) {
        scene.world().modifyBlockEntity(TANK_LOW, FluidTankBlockEntity.class, be -> be.getTankInventory()
                .fill(new FluidStack(Fluids.WATER, 24000), FluidAction.EXECUTE));
    }

    private static CreateSceneBuilder open(SceneBuilder builder, String id, String title) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(id, title);
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);
        return scene;
    }

    private static Selection fDrive(SceneBuildingUtil util) {
        return util.select().position(F_COG).add(util.select().fromTo(F_SHAFT_BASE, F_SHAFT_NEAR));
    }

    // a cogwheel meshing with the pump turns the other way
    private static void pumpSpeed(CreateSceneBuilder scene, Selection pump, Selection drive, float speed) {
        scene.world().setKineticSpeed(pump, speed);
        scene.world().setKineticSpeed(drive, -speed);
    }

    // an empty winch has its own model, it only swaps to the coiled one once a hose is attached
    private static void hosed(CreateSceneBuilder scene, BlockPos winch) {
        scene.world().modifyBlock(winch, state -> state.setValue(HoseWinchBlock.HAS_COIL, true), false);
        coil(scene, winch);
    }

    // the coil only draws while the winch has a strand, so it gets one with no rope points yet
    private static void coil(CreateSceneBuilder scene, BlockPos winch) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("HasStrand", true);
        scene.world().modifyBlockEntity(winch, HoseWinchBlockEntity.class,
                be -> be.readClient(tag, be.getLevel().registryAccess()));
    }

    // the winch shows the points it got one tick ago, so the hose end is sent one tick ahead of the nozzle
    private static void drag(CreateSceneBuilder scene, ElementLink<WorldSectionElement> nozzle, Vec3 delta,
                             BlockPos winch, Vec3 from, Vec3 to, float speed, int ticks) {
        scene.world().moveSection(nozzle, delta, ticks);
        for (int i = 1; i <= ticks; i++) {
            hose(scene, winch, from.lerp(to, Math.min(1.0, (double) (i + HOSE_LEAD_TICKS) / ticks)), speed);
            scene.idle(1);
        }
    }

    // draws the real hose the winch renders from its synced rope points, a gentle sag between the two ends
    private static void hose(CreateSceneBuilder scene, BlockPos winch, Vec3 end, float speed) {
        Vec3 start = new Vec3(0.5, 0.5, 0.5);
        Vec3 span = end.subtract(winch.getX(), winch.getY(), winch.getZ()).subtract(start);
        int segments = Math.max(2, (int) Math.ceil(span.length()));
        int[] packed = new int[(segments + 1) * 3];
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            double sag = -0.4 * Math.sin(Math.PI * t) * Math.min(1.0, span.length() / 3.0);
            double y = Math.max(start.y + span.y * t + sag, 1.1 - winch.getY());
            packed[i * 3] = Float.floatToIntBits((float) (start.x + span.x * t));
            packed[i * 3 + 1] = Float.floatToIntBits((float) y);
            packed[i * 3 + 2] = Float.floatToIntBits((float) (start.z + span.z * t));
        }
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("HasStrand", true);
        tag.putFloat("Length", (float) span.length());
        tag.putFloat("Speed", speed);
        tag.putIntArray("RopePts", packed);
        scene.world().modifyBlockEntity(winch, HoseWinchBlockEntity.class,
                be -> be.readClient(tag, be.getLevel().registryAccess()));
    }

    // a casing sunk into the plate stays out of sight until the parts that turn it are shown
    private static CreateSceneBuilder open(SceneBuilder builder, String id, String title, BlockPos sunk) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(id, title);
        scene.configureBasePlate(0, 0, 5);
        scene.world().setBlock(sunk, Blocks.SNOW_BLOCK.defaultBlockState(), false);
        scene.showBasePlate();
        scene.idle(5);
        return scene;
    }
}

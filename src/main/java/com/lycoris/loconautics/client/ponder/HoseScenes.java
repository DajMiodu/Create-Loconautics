package com.lycoris.loconautics.client.ponder;

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
import net.minecraft.world.item.ItemStack;
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
        CreateSceneBuilder scene = open(builder, "hose_fluid", "Pumping through Hoses");
        station(scene, util);
        scene.world().showSection(util.select().position(NOZZLE), Direction.DOWN);
        scene.world().showSection(util.select().position(HATCH), Direction.DOWN);
        scene.idle(15);

        scene.world().showSection(util.select().fromTo(TANK_LOW, TANK_HIGH), Direction.DOWN);
        scene.idle(5);
        fill(scene);
        scene.world().showSection(util.select().position(PUMP), Direction.SOUTH);
        scene.idle(15);
        scene.overlay().showText(60)
                .text("Hoses need a Mechanical Pump on the face opposite the mount")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(PUMP));
        scene.idle(70);

        scene.world().showSection(util.select().position(PUMP_COG), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().fromTo(PUMP_SHAFT_NEAR, PUMP_SHAFT_FAR), Direction.NORTH);
        scene.idle(15);
        scene.world().setKineticSpeed(pumpLine(util), 64.0f);
        scene.world().propagatePipeChange(PUMP);
        scene.effects().rotationSpeedIndicator(PUMP_COG);
        scene.overlay().showLine(PonderPalette.BLUE, util.vector().topOf(PUMP), util.vector().topOf(NOZZLE), 90);
        scene.overlay().showText(60)
                .text("Pump speed sets how far the pressure reaches...")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(WINCH, Direction.NORTH));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("...up to 28 blocks at the top speed")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(WINCH, Direction.NORTH));
        scene.idle(70);

        scene.overlay().showOutline(PonderPalette.GREEN, "hatch", util.select().position(HATCH), 70);
        scene.overlay().showText(60)
                .text("Docked, everything pumped goes into the hatch's tank")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(HATCH));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("It can pull fluid back out the other way too")
                .placeNearTarget()
                .pointAt(util.vector().topOf(HATCH));
        scene.idle(70);

        scene.world().modifyBlock(NOZZLE, state -> state.setValue(BlockStateProperties.POWERED, false), false);
        scene.effects().emitParticles(util.vector().topOf(NOZZLE),
                scene.effects().simpleParticleEmitter(ParticleTypes.FALLING_WATER,
                        util.vector().of(0.0, -0.15, 0.2)), 3.0f, 40);
        scene.overlay().showText(60)
                .text("A loose nozzle sprays, and the jet pushes it back")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(NOZZLE));
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
}

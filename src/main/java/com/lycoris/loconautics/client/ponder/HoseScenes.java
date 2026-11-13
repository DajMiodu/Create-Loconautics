package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public class HoseScenes {

    private static final BlockPos CASING_LOW = new BlockPos(2, 1, 4);
    private static final BlockPos CASING_HIGH = new BlockPos(2, 2, 4);
    private static final BlockPos WINCH = new BlockPos(2, 2, 3);
    private static final BlockPos REEL_COG = new BlockPos(1, 2, 3);
    private static final BlockPos REEL_SHAFT = new BlockPos(0, 2, 3);

    private static final BlockPos PUMP = new BlockPos(2, 2, 2);
    private static final BlockPos PUMP_COG = new BlockPos(3, 2, 2);
    private static final BlockPos PUMP_SHAFT_NEAR = new BlockPos(3, 2, 3);
    private static final BlockPos PUMP_SHAFT_FAR = new BlockPos(3, 2, 5);
    private static final BlockPos SOURCE_LOW = new BlockPos(2, 1, 1);
    private static final BlockPos SOURCE_HIGH = new BlockPos(2, 3, 1);

    private static final BlockPos FAR_TANK = new BlockPos(4, 1, 4);
    private static final BlockPos SOCKET = new BlockPos(4, 2, 4);
    private static final BlockPos NOZZLE = new BlockPos(4, 3, 4);
    private static final BlockPos CONNECTOR = new BlockPos(4, 2, 4);

    private static final BlockPos RELAY = new BlockPos(4, 1, 3);
    private static final BlockPos RELAY_TANK = new BlockPos(4, 1, 0);
    private static final BlockPos RELAY_CONNECTOR = new BlockPos(4, 2, 0);

    private static final BlockPos MOUNT_A = new BlockPos(1, 1, 2);
    private static final BlockPos SOCKET_A = new BlockPos(1, 2, 2);
    private static final BlockPos NOZZLE_A = new BlockPos(1, 3, 2);
    private static final BlockPos TANK_B = new BlockPos(3, 1, 2);
    private static final BlockPos SOCKET_B = new BlockPos(3, 2, 2);
    private static final BlockPos NOZZLE_B = new BlockPos(3, 3, 2);

    public static void winch(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "hose_winch", "Reeling Hoses using Hose Winches");

        scene.world().showSection(util.select().fromTo(CASING_LOW, CASING_HIGH), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(WINCH), Direction.NORTH);
        scene.idle(5);
        scene.overlay().showText(60)
                .text("Hose Winches attach flat to any block face")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(WINCH));
        scene.idle(70);

        scene.world().showSection(util.select().fromTo(FAR_TANK, NOZZLE), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showLine(PonderPalette.GREEN, util.vector().topOf(WINCH), util.vector().topOf(NOZZLE), 200);
        Selection reel = util.select().fromTo(REEL_SHAFT, WINCH);
        scene.world().showSection(util.select().fromTo(REEL_SHAFT, REEL_COG), Direction.EAST);
        scene.idle(10);
        scene.world().setKineticSpeed(reel, 32.0f);
        scene.effects().rotationSpeedIndicator(REEL_COG);
        scene.overlay().showText(60)
                .text("Rotational Force will reel the hose in or out")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(REEL_COG));
        scene.idle(70);

        scene.world().multiplyKineticSpeed(reel, -1.0f);
        scene.effects().rotationDirectionIndicator(REEL_COG);
        scene.overlay().showText(70)
                .text("The direction of rotation decides if the hose is paid out or hauled in")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(REEL_COG));
        scene.idle(80);

        scene.world().setKineticSpeed(reel, -64.0f);
        scene.effects().rotationSpeedIndicator(REEL_COG);
        scene.overlay().showText(60)
                .text("A faster rotation reels the hose faster")
                .placeNearTarget()
                .pointAt(util.vector().topOf(REEL_COG));
        scene.idle(70);

        scene.world().setKineticSpeed(reel, 0.0f);
        scene.overlay().showOutline(PonderPalette.RED, "winch", util.select().position(WINCH), 70);
        scene.overlay().showText(70)
                .text("Breaking the winch snaps the hose and drops the Hose item")
                .attachKeyFrame()
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .pointAt(util.vector().topOf(WINCH));
        scene.idle(80);
    }

    public static void connect(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "hose_connect", "Running a Hose");
        station(scene, util);
        scene.world().modifyBlock(NOZZLE, state -> state.setValue(BlockStateProperties.POWERED, false), false);
        scene.world().showSection(util.select().fromTo(FAR_TANK, NOZZLE), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(60)
                .text("A Hose joins a Hose Winch to a Hose Nozzle or a Hose Connector")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(WINCH));
        scene.idle(70);

        scene.overlay().showControls(util.vector().topOf(WINCH), Pointing.DOWN, 40)
                .rightClick()
                .withItem(hose());
        scene.idle(25);
        scene.overlay().showControls(util.vector().topOf(NOZZLE), Pointing.DOWN, 40)
                .rightClick()
                .withItem(hose());
        scene.idle(25);
        scene.overlay().showLine(PonderPalette.GREEN, util.vector().topOf(WINCH), util.vector().topOf(NOZZLE), 160);
        scene.overlay().showText(60)
                .text("Right click the winch with a Hose, then the nozzle")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(NOZZLE));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("A line previews the run while one end is selected")
                .placeNearTarget()
                .pointAt(util.vector().topOf(NOZZLE));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("The preview turns red when the hose is too long")
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .pointAt(util.vector().topOf(NOZZLE));
        scene.idle(70);

        scene.overlay().showControls(util.vector().topOf(NOZZLE), Pointing.DOWN, 40)
                .rightClick()
                .whileSneaking()
                .withItem(hose());
        scene.idle(25);
        scene.overlay().showText(60)
                .text("Sneak and Right click to drop the selection")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(NOZZLE));
        scene.idle(70);

        scene.overlay().showText(70)
                .text("A hose can only be paid out up to its maximum length")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(WINCH));
        scene.idle(80);
    }

    public static void nozzle(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "hose_nozzle", "Handling Hose Nozzles");
        station(scene, util);
        scene.world().modifyBlock(NOZZLE, state -> state.setValue(BlockStateProperties.POWERED, false), false);
        scene.world().showSection(util.select().fromTo(FAR_TANK, NOZZLE), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showControls(util.vector().topOf(NOZZLE), Pointing.DOWN, 40)
                .rightClick();
        scene.idle(25);
        scene.overlay().showText(60)
                .text("Right click a nozzle with empty hands to hold it")
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
                .text("Holding the rotate key while moving the mouse turns it")
                .placeNearTarget()
                .pointAt(util.vector().topOf(NOZZLE));
        scene.idle(70);

        scene.overlay().showOutline(PonderPalette.GREEN, "socket", util.select().position(SOCKET), 70);
        scene.world().modifyBlock(NOZZLE, state -> state.setValue(BlockStateProperties.POWERED, true), false);
        scene.overlay().showText(60)
                .text("Near an open Nozzle Socket, a nozzle docks itself upright")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(SOCKET));
        scene.idle(70);

        scene.overlay().showLine(PonderPalette.GREEN, util.vector().topOf(WINCH), util.vector().topOf(NOZZLE), 80);
        scene.world().modifyBlock(NOZZLE, state -> state.setValue(BlockStateProperties.POWERED, false), false);
        scene.overlay().showText(60)
                .text("A taut hose pulls a docked nozzle out of its socket")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(NOZZLE));
        scene.idle(70);
    }

    public static void fluid(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "hose_fluid", "Pumping Fluids through Hoses");
        station(scene, util);
        scene.world().showSection(util.select().fromTo(FAR_TANK, NOZZLE), Direction.DOWN);
        scene.idle(15);

        scene.world().showSection(util.select().fromTo(SOURCE_LOW, SOURCE_HIGH), Direction.DOWN);
        scene.idle(5);
        scene.world().modifyBlockEntity(SOURCE_LOW, FluidTankBlockEntity.class, be -> be.getTankInventory()
                .fill(new FluidStack(Fluids.WATER, 24000), FluidAction.EXECUTE));
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
        scene.overlay().showLine(PonderPalette.BLUE, util.vector().topOf(WINCH), util.vector().topOf(NOZZLE), 200);
        scene.overlay().showText(60)
                .text("Pump speed sets how far the pressure reaches...")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(PUMP));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("...a hose only works once the pressure covers its whole length")
                .placeNearTarget()
                .pointAt(util.vector().topOf(WINCH));
        scene.idle(70);

        scene.overlay().showOutline(PonderPalette.GREEN, "socket", util.select().position(SOCKET), 70);
        scene.world().modifyBlockEntity(FAR_TANK, FluidTankBlockEntity.class, be -> be.getTankInventory()
                .fill(new FluidStack(Fluids.WATER, 8000), FluidAction.EXECUTE));
        scene.overlay().showText(60)
                .text("Docked in a Nozzle Socket, everything pumped goes into it")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(SOCKET));
        scene.idle(70);

        scene.world().multiplyKineticSpeed(pumpLine(util), -1.0f);
        scene.world().propagatePipeChange(PUMP);
        scene.effects().rotationDirectionIndicator(PUMP_COG);
        scene.overlay().showText(60)
                .text("A reversed pump pulls Fluid back out through the hose")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(PUMP));
        scene.idle(70);

        scene.world().multiplyKineticSpeed(pumpLine(util), -1.0f);
        scene.world().propagatePipeChange(PUMP);
        scene.world().modifyBlock(NOZZLE, state -> state.setValue(BlockStateProperties.POWERED, false), false);
        scene.effects().emitParticles(util.vector().topOf(NOZZLE),
                scene.effects().simpleParticleEmitter(ParticleTypes.FALLING_WATER,
                        util.vector().of(0.0, -0.15, 0.2)), 3.0f, 40);
        scene.overlay().showText(60)
                .text("A loose nozzle sprays what is pumped, and the jet pushes it back")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(NOZZLE));
        scene.idle(70);
    }

    public static void connector(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "hose_connector", "Linking Fluids using Hose Connectors");

        scene.world().showSection(util.select().position(FAR_TANK), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(CONNECTOR), Direction.DOWN);
        scene.idle(5);
        scene.overlay().showText(60)
                .text("Hose Connectors attach to the face of any block that holds Fluids")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(CONNECTOR));
        scene.idle(70);

        station(scene, util);
        scene.world().showSection(util.select().fromTo(SOURCE_LOW, SOURCE_HIGH), Direction.DOWN);
        scene.world().modifyBlockEntity(SOURCE_LOW, FluidTankBlockEntity.class, be -> be.getTankInventory()
                .fill(new FluidStack(Fluids.WATER, 24000), FluidAction.EXECUTE));
        scene.idle(10);
        scene.world().showSection(util.select().position(PUMP), Direction.SOUTH);
        scene.idle(10);
        scene.overlay().showControls(util.vector().topOf(WINCH), Pointing.DOWN, 40)
                .rightClick()
                .withItem(hose());
        scene.idle(25);
        scene.overlay().showControls(util.vector().topOf(CONNECTOR), Pointing.DOWN, 40)
                .rightClick()
                .withItem(hose());
        scene.idle(25);
        scene.overlay().showLine(PonderPalette.GREEN, util.vector().topOf(WINCH), util.vector().topOf(CONNECTOR), 120);
        scene.overlay().showText(60)
                .text("Right click a Hose Winch with a Hose, then the connector")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(CONNECTOR));
        scene.idle(70);

        scene.world().showSection(util.select().position(PUMP_COG), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(PUMP_SHAFT_NEAR, PUMP_SHAFT_FAR), Direction.NORTH);
        scene.idle(15);
        scene.world().setKineticSpeed(pumpLine(util), 64.0f);
        scene.world().propagatePipeChange(PUMP);
        scene.effects().rotationSpeedIndicator(PUMP_COG);
        scene.overlay().showLine(PonderPalette.BLUE, util.vector().topOf(WINCH), util.vector().topOf(CONNECTOR), 160);
        scene.world().modifyBlockEntity(FAR_TANK, FluidTankBlockEntity.class, be -> be.getTankInventory()
                .fill(new FluidStack(Fluids.WATER, 8000), FluidAction.EXECUTE));
        scene.overlay().showText(60)
                .text("Fluid pumped through the hose enters the block behind the plate...")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(CONNECTOR));
        scene.idle(70);

        scene.world().multiplyKineticSpeed(pumpLine(util), -1.0f);
        scene.world().propagatePipeChange(PUMP);
        scene.effects().rotationDirectionIndicator(PUMP_COG);
        scene.overlay().showText(60)
                .text("...and a reversed pump pulls it back out the same way")
                .placeNearTarget()
                .pointAt(util.vector().topOf(CONNECTOR));
        scene.idle(70);

        scene.world().setKineticSpeed(pumpLine(util), 0.0f);
        scene.overlay().showControls(util.vector().topOf(CONNECTOR), Pointing.DOWN, 40)
                .rightClick()
                .withItem(new ItemStack(AllItems.WRENCH.get()));
        scene.idle(25);
        scene.overlay().showOutline(PonderPalette.RED, "connector", util.select().position(CONNECTOR), 60);
        scene.overlay().showText(70)
                .text("Using a Wrench on it removes the newest hose, then the connector itself")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(CONNECTOR));
        scene.idle(80);
    }

    public static void ropeConnector(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "hose_rope_connector", "Extending Hoses using Rope Connectors");

        scene.world().showSection(util.select().position(RELAY), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(60)
                .text("Rope Connectors can hold a Hose as well as a Rope")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(RELAY));
        scene.idle(70);

        station(scene, util);
        scene.world().showSection(util.select().fromTo(SOURCE_LOW, SOURCE_HIGH), Direction.DOWN);
        scene.world().modifyBlockEntity(SOURCE_LOW, FluidTankBlockEntity.class, be -> be.getTankInventory()
                .fill(new FluidStack(Fluids.WATER, 24000), FluidAction.EXECUTE));
        scene.idle(10);
        scene.world().showSection(util.select().position(PUMP), Direction.SOUTH);
        scene.idle(10);
        scene.overlay().showControls(util.vector().topOf(WINCH), Pointing.DOWN, 40)
                .rightClick()
                .withItem(hose());
        scene.idle(25);
        scene.overlay().showControls(util.vector().topOf(RELAY), Pointing.DOWN, 40)
                .rightClick()
                .withItem(hose());
        scene.idle(25);
        scene.overlay().showLine(PonderPalette.GREEN, util.vector().topOf(WINCH), util.vector().topOf(RELAY), 220);
        scene.overlay().showText(60)
                .text("A winch can reel its Hose in to a Rope Connector...")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(RELAY));
        scene.idle(70);

        scene.world().showSection(util.select().position(RELAY_TANK), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(RELAY_CONNECTOR), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showControls(util.vector().topOf(RELAY), Pointing.DOWN, 40)
                .rightClick()
                .withItem(hose());
        scene.idle(25);
        scene.overlay().showControls(util.vector().topOf(RELAY_CONNECTOR), Pointing.DOWN, 40)
                .rightClick()
                .withItem(hose());
        scene.idle(25);
        scene.overlay().showLine(PonderPalette.GREEN, util.vector().topOf(RELAY), util.vector().topOf(RELAY_CONNECTOR), 200);
        scene.overlay().showText(60)
                .text("...and a second Hose carries on to a Hose Connector")
                .placeNearTarget()
                .pointAt(util.vector().topOf(RELAY_CONNECTOR));
        scene.idle(70);

        scene.world().showSection(util.select().position(PUMP_COG), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(PUMP_SHAFT_NEAR, PUMP_SHAFT_FAR), Direction.NORTH);
        scene.idle(15);
        scene.world().setKineticSpeed(pumpLine(util), 64.0f);
        scene.world().propagatePipeChange(PUMP);
        scene.effects().rotationSpeedIndicator(PUMP_COG);
        scene.overlay().showLine(PonderPalette.BLUE, util.vector().topOf(WINCH), util.vector().topOf(RELAY), 160);
        scene.overlay().showLine(PonderPalette.BLUE, util.vector().topOf(RELAY), util.vector().topOf(RELAY_CONNECTOR), 160);
        scene.world().modifyBlockEntity(RELAY_TANK, FluidTankBlockEntity.class, be -> be.getTankInventory()
                .fill(new FluidStack(Fluids.WATER, 8000), FluidAction.EXECUTE));
        scene.overlay().showText(60)
                .text("Fluid follows the chain of hoses to the last connector or nozzle")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(RELAY_CONNECTOR));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("Each connector holds at most 2 hoses, one in and one out")
                .placeNearTarget()
                .pointAt(util.vector().topOf(RELAY));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("Plain Steel Cables never carry Fluids")
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .pointAt(util.vector().topOf(RELAY));
        scene.idle(70);
    }

    public static void socket(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "nozzle_socket", "Docking Nozzles in Nozzle Sockets");

        scene.world().showSection(util.select().position(MOUNT_A), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(SOCKET_A), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(60)
                .text("Nozzle Sockets hold a Hose Nozzle upright and take its Fluid")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(SOCKET_A));
        scene.idle(70);

        scene.overlay().showControls(util.vector().topOf(SOCKET_A), Pointing.DOWN, 30)
                .rightClick();
        scene.idle(10);
        scene.world().modifyBlock(SOCKET_A, state -> state.setValue(BlockStateProperties.OPEN, true), false);
        scene.overlay().showText(50)
                .text("Right click the lid to open it")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(SOCKET_A));
        scene.idle(55);

        scene.world().showSection(util.select().position(NOZZLE_A), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(60)
                .text("A nozzle that comes near an open socket docks itself")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(NOZZLE_A));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("Alone, a socket stores Water in its own tank")
                .placeNearTarget()
                .pointAt(util.vector().topOf(SOCKET_A));
        scene.idle(70);

        scene.world().showSection(util.select().position(TANK_B), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(SOCKET_B), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(NOZZLE_B), Direction.DOWN);
        scene.idle(10);
        scene.world().modifyBlockEntity(TANK_B, FluidTankBlockEntity.class, be -> be.getTankInventory()
                .fill(new FluidStack(Fluids.WATER, 8000), FluidAction.EXECUTE));
        scene.overlay().showText(60)
                .text("With a Fluid Tank behind it, the Fluid goes there instead")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(SOCKET_B));
        scene.idle(70);

        scene.effects().indicateRedstone(SOCKET_B);
        scene.world().modifyBlock(NOZZLE_B, state -> state.setValue(BlockStateProperties.POWERED, false), false);
        scene.overlay().showText(60)
                .text("A Redstone signal shuts docking and pops the nozzle out")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(SOCKET_B));
        scene.idle(70);
    }

    private static void station(CreateSceneBuilder scene, SceneBuildingUtil util) {
        scene.world().showSection(util.select().fromTo(CASING_LOW, CASING_HIGH), Direction.DOWN);
        scene.world().showSection(util.select().position(WINCH), Direction.NORTH);
    }

    private static Selection pumpLine(SceneBuildingUtil util) {
        return util.select().position(PUMP)
                .add(util.select().position(PUMP_COG))
                .add(util.select().fromTo(PUMP_SHAFT_NEAR, PUMP_SHAFT_FAR));
    }

    private static ItemStack hose() {
        return new ItemStack(LoconauticsRegistries.HOSE.get());
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

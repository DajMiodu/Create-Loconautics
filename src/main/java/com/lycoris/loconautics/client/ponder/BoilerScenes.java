package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeBlockEntity;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeMetric;
import com.simibubi.create.AllBlocks;
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
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public class BoilerScenes {

    private static final BlockPos LONE_FIREBOX = new BlockPos(2, 2, 2);
    private static final BlockPos LONE_LEG = new BlockPos(2, 1, 2);

    private static final BlockPos FIREBOX = new BlockPos(1, 2, 2);
    private static final BlockPos BODY_NEAR = new BlockPos(2, 2, 2);
    private static final BlockPos BODY_MID = new BlockPos(3, 2, 2);
    private static final BlockPos BODY_FAR = new BlockPos(4, 2, 2);
    private static final BlockPos LEGS_FROM = new BlockPos(1, 1, 2);
    private static final BlockPos LEGS_TO = new BlockPos(4, 1, 2);
    private static final BlockPos HATCH = new BlockPos(2, 3, 2);
    private static final BlockPos SAFETY_VALVE = new BlockPos(3, 3, 2);
    private static final BlockPos SMOKESTACK = new BlockPos(4, 3, 2);
    private static final BlockPos TAKEOFF = new BlockPos(3, 2, 1);
    private static final BlockPos SHAFT = new BlockPos(3, 2, 0);
    private static final BlockPos PANEL = new BlockPos(2, 2, 1);

    private static final BlockPos WET_BODY = new BlockPos(3, 1, 2);
    private static final BlockPos WET_HATCH = new BlockPos(3, 2, 2);
    private static final BlockPos CONNECTOR = new BlockPos(2, 1, 2);
    private static final BlockPos PUMP = new BlockPos(1, 1, 2);
    private static final BlockPos COG = new BlockPos(1, 1, 3);
    private static final BlockPos SHAFT_NEAR = new BlockPos(2, 1, 3);
    private static final BlockPos SHAFT_FAR = new BlockPos(5, 1, 3);
    private static final BlockPos TANK_LOW = new BlockPos(0, 1, 2);
    private static final BlockPos TANK_HIGH = new BlockPos(0, 3, 2);

    public static void firebox(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "boiler_firebox", "Firing a Boiler");

        scene.world().showSection(util.select().fromTo(LONE_LEG, LONE_FIREBOX), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(60)
                .text("The Firebox burns fuel to heat a Boiler")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(LONE_FIREBOX));
        scene.idle(70);

        scene.overlay().showControls(util.vector().blockSurface(LONE_FIREBOX, Direction.WEST), Pointing.RIGHT, 40)
                .rightClick()
                .withItem(new ItemStack(Items.COAL));
        scene.idle(20);
        light(scene, util, LONE_FIREBOX, true);
        scene.overlay().showText(60)
                .text("Right click it with any furnace fuel to light it")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(LONE_FIREBOX, Direction.WEST));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("It holds a single stack and burns it faster under load")
                .placeNearTarget()
                .pointAt(util.vector().topOf(LONE_FIREBOX));
        scene.idle(70);

        scene.overlay().showControls(util.vector().blockSurface(LONE_FIREBOX, Direction.WEST), Pointing.RIGHT, 30)
                .rightClick();
        scene.idle(20);
        scene.overlay().showText(60)
                .text("An empty hand will swing the doors open or closed")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(LONE_FIREBOX, Direction.WEST));
        scene.idle(70);

        scene.overlay().showControls(util.vector().blockSurface(LONE_FIREBOX, Direction.WEST), Pointing.RIGHT, 30)
                .rightClick()
                .withItem(new ItemStack(Items.IRON_SHOVEL));
        scene.idle(20);
        light(scene, util, LONE_FIREBOX, false);
        scene.overlay().showText(60)
                .text("Using a Shovel scrapes the fire back out")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(LONE_FIREBOX, Direction.WEST));
        scene.idle(70);

        scene.world().setBlock(LONE_LEG, BoilerBlocks.FIREBOX_STOCKER.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST), true);
        scene.idle(20);
        scene.overlay().showOutline(PonderPalette.GREEN, "stocker", util.select().position(LONE_LEG), 60);
        scene.overlay().showText(60)
                .text("A Firebox Stocker underneath holds 9 stacks of fuel")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(LONE_LEG, Direction.WEST));
        scene.idle(70);
    }

    public static void assembly(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "boiler_assembly", "Assembling a Boiler");

        scene.world().showSection(util.select().fromTo(LEGS_FROM, LEGS_TO), Direction.DOWN);
        scene.world().showSection(util.select().position(FIREBOX), Direction.DOWN);
        scene.idle(15);
        scene.world().showSection(util.select().fromTo(BODY_NEAR, BODY_FAR), Direction.DOWN);
        scene.overlay().showText(60)
                .text("Boiler Body blocks touching the Firebox form the barrel")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(BODY_MID));
        scene.idle(70);

        scene.world().showSection(util.select().position(SMOKESTACK), Direction.DOWN);
        scene.overlay().showText(60)
                .text("A Smokestack on top of any body block lets waste gas out")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(SMOKESTACK));
        scene.idle(70);

        showHatch(scene, util, HATCH);
        scene.overlay().showText(60)
                .text("Water enters through a Fluid Hatch touching a body block")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(HATCH));
        scene.idle(70);

        scene.world().showSection(util.select().fromTo(SHAFT, TAKEOFF), Direction.SOUTH);
        scene.overlay().showText(60)
                .text("A Steam Takeoff against the barrel turns steam into rotation")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(TAKEOFF));
        scene.idle(70);

        scene.overlay().showControls(util.vector().topOf(FIREBOX), Pointing.DOWN, 50)
                .withItem(AllItems.GOGGLES.asStack());
        scene.overlay().showText(70)
                .text("Engineer's Goggles on the Firebox name the next missing piece")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(FIREBOX));
        scene.idle(80);
    }

    public static void ratio(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "boiler_ratio", "Sizing a Boiler");
        base(scene, util);
        scene.idle(15);

        scene.overlay().showOutline(PonderPalette.GREEN, "fire", util.select().position(FIREBOX), 60);
        scene.overlay().showOutline(PonderPalette.BLUE, "barrel", util.select().fromTo(BODY_NEAR, BODY_FAR), 60);
        scene.overlay().showText(60)
                .text("One Firebox can heat 3 Boiler Body blocks")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(BODY_MID));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("Too much barrel leaves the boiler underfired and below full pressure")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(BODY_FAR));
        scene.idle(70);

        scene.overlay().showOutline(PonderPalette.RED, "fire", util.select().position(FIREBOX), 60);
        scene.overlay().showText(60)
                .text("Too much fire gains nothing and burns fuel faster")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(FIREBOX));
        scene.idle(70);

        scene.overlay().showControls(util.vector().topOf(FIREBOX), Pointing.DOWN, 50)
                .withItem(AllItems.GOGGLES.asStack());
        scene.overlay().showText(60)
                .text("The Efficiency reading shows how well the two match")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(FIREBOX));
        scene.idle(70);
    }

    public static void smokestack(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "boiler_smokestack", "Smokestack and Draft");
        base(scene, util);
        scene.idle(15);

        scene.world().showSection(util.select().position(SMOKESTACK), Direction.DOWN);
        scene.overlay().showText(60)
                .text("Waste gas leaves through a Smokestack on any body block")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(SMOKESTACK));
        scene.idle(70);

        scene.world().hideSection(util.select().position(SMOKESTACK), Direction.UP);
        scene.idle(15);
        scene.overlay().showOutline(PonderPalette.RED, "gas", util.select().position(BODY_FAR), 80);
        scene.overlay().showText(60)
                .text("Without a stack, gas builds up in the barrel...")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(BODY_FAR));
        scene.effects().emitParticles(util.vector().topOf(BODY_FAR),
                scene.effects().simpleParticleEmitter(ParticleTypes.LARGE_SMOKE,
                        util.vector().of(0.0, 0.1, 0.0)), 2.0f, 40);
        scene.idle(70);

        scene.overlay().showText(60)
                .text("...and chokes the output completely after 45 seconds")
                .placeNearTarget()
                .pointAt(util.vector().topOf(BODY_FAR));
        scene.idle(70);

        scene.world().showSection(util.select().position(SMOKESTACK), Direction.DOWN);
        scene.idle(20);
        scene.overlay().showText(60)
                .text("Fitting the stack clears the gas twice as fast")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(SMOKESTACK));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("The smoke color shows how hot the boiler is running")
                .placeNearTarget()
                .pointAt(util.vector().topOf(SMOKESTACK));
        scene.idle(70);
    }

    public static void water(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "boiler_water", "Watering a Boiler");

        scene.world().setBlock(PUMP, AllBlocks.FLUID_PIPE.get().getAxisState(Axis.X), false);
        scene.world().showSection(util.select().position(WET_BODY), Direction.DOWN);
        scene.idle(10);
        showHatch(scene, util, WET_HATCH);
        scene.overlay().showText(60)
                .text("A Fluid Hatch against a body block is a water inlet")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(WET_HATCH));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("Hose Nozzles can dock into it to fill the boiler")
                .placeNearTarget()
                .pointAt(util.vector().topOf(WET_HATCH));
        scene.idle(70);

        scene.overlay().showControls(util.vector().blockSurface(WET_BODY, Direction.WEST), Pointing.RIGHT, 40)
                .rightClick()
                .withItem(new ItemStack(AllBlocks.FLUID_PIPE.get()));
        scene.idle(25);
        scene.world().showSection(util.select().position(CONNECTOR), Direction.EAST);
        scene.overlay().showText(60)
                .text("Using a Fluid Pipe on the barrel places a Barrel Pipe Connector")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(CONNECTOR));
        scene.idle(70);

        scene.world().showSection(util.select().fromTo(TANK_LOW, TANK_HIGH), Direction.DOWN);
        scene.idle(5);
        fill(scene);
        scene.world().showSection(util.select().position(PUMP), Direction.EAST);
        scene.idle(15);
        scene.overlay().showOutline(PonderPalette.RED, "pipes", util.select().fromTo(PUMP, CONNECTOR), 60);
        scene.overlay().showText(60)
                .text("Pipes alone will not move any water")
                .attachKeyFrame()
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .pointAt(util.vector().topOf(PUMP));
        scene.idle(70);

        scene.world().destroyBlock(PUMP);
        scene.world().restoreBlocks(util.select().position(PUMP));
        scene.world().showSection(util.select().position(COG), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().fromTo(SHAFT_NEAR, SHAFT_FAR), Direction.WEST);
        scene.idle(15);
        scene.world().setKineticSpeed(feedLine(util), 32.0f);
        scene.world().propagatePipeChange(PUMP);
        scene.effects().rotationSpeedIndicator(COG);
        scene.overlay().showText(60)
                .text("A powered Mechanical Pump will push the water in")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(PUMP));
        scene.idle(70);

        scene.overlay().showOutline(PonderPalette.RED, "connector", util.select().position(CONNECTOR), 60);
        scene.overlay().showText(60)
                .text("The connector only takes water in, it cannot drain the boiler")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(CONNECTOR, Direction.NORTH));
        scene.idle(70);
    }

    public static void takeoff(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "boiler_takeoff", "Taking the Power Off");
        base(scene, util);
        scene.idle(15);

        Selection output = util.select().fromTo(SHAFT, TAKEOFF);
        scene.world().showSection(output, Direction.SOUTH);
        scene.world().setKineticSpeed(output, 32.0f);
        scene.effects().rotationSpeedIndicator(TAKEOFF);
        scene.overlay().showText(60)
                .text("Steam Takeoffs against the barrel generate Rotational Force")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(TAKEOFF));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("Speed follows the pressure, from 4 to 128 RPM")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(SHAFT));
        scene.idle(70);

        scene.overlay().showOutline(PonderPalette.BLUE, "barrel", util.select().fromTo(BODY_NEAR, BODY_FAR), 60);
        scene.overlay().showText(60)
                .text("Stress Capacity depends on the size of the barrel")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(BODY_MID));
        scene.idle(70);

        scene.world().setKineticSpeed(output, 0.0f);
        scene.overlay().showText(60)
                .text("Without the boiler, the shaft winds down in 3 seconds")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(TAKEOFF));
        scene.idle(70);
    }

    public static void pressure(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "boiler_pressure", "Pressure and the Safety Valve");
        base(scene, util);
        light(scene, util, FIREBOX, true);
        scene.idle(15);

        scene.overlay().showText(60)
                .text("A burning, watered boiler builds up pressure")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(BODY_MID));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("Working pressure is 1.0, and it bursts at 1.6")
                .placeNearTarget()
                .pointAt(util.vector().topOf(BODY_MID));
        scene.idle(70);

        scene.world().showSection(util.select().position(SAFETY_VALVE), Direction.DOWN);
        scene.idle(10);
        scene.effects().emitParticles(util.vector().topOf(SAFETY_VALVE),
                scene.effects().simpleParticleEmitter(ParticleTypes.CLOUD,
                        util.vector().of(0.0, 0.25, 0.0)), 3.0f, 30);
        scene.overlay().showText(60)
                .text("Safety Valves vent the pressure from the barrel")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(SAFETY_VALVE));
        scene.idle(70);

        scene.overlay().showControls(util.vector().blockSurface(SAFETY_VALVE, Direction.NORTH), Pointing.RIGHT, 40)
                .rightClick()
                .withItem(new ItemStack(BoilerBlocks.VALVE_ITEM.get()));
        scene.idle(25);
        scene.overlay().showText(60)
                .text("Right click a Safety Valve with a Valve to bind it")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(SAFETY_VALVE, Direction.NORTH));
        scene.idle(70);

        scene.world().setBlock(SAFETY_VALVE, BoilerBlocks.SMART_SAFETY_VALVE.get().defaultBlockState(), true);
        scene.idle(15);
        scene.overlay().showText(60)
                .text("Smart Safety Valves vent by themselves at 3/4 of burst pressure")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(SAFETY_VALVE));
        scene.idle(70);
    }

    public static void failure(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "boiler_failure", "When a Boiler Lets Go");
        base(scene, util);
        light(scene, util, FIREBOX, true);
        scene.idle(15);
        showHatch(scene, util, HATCH);
        scene.idle(10);

        scene.overlay().showOutline(PonderPalette.RED, "dry", util.select().position(HATCH), 60);
        scene.overlay().showText(60)
                .text("A hot boiler with no water will fail in 30 seconds...")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(HATCH));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("...but refilling it brings the countdown down twice as fast")
                .placeNearTarget()
                .pointAt(util.vector().topOf(HATCH));
        scene.idle(70);

        scene.overlay().showOutline(PonderPalette.RED, "burst", util.select().fromTo(BODY_NEAR, BODY_FAR), 60);
        scene.overlay().showText(60)
                .text("A full fire against a shut valve will burst the barrel")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(BODY_MID));
        scene.idle(70);

        scene.overlay().showOutline(PonderPalette.GREEN, "gas", util.select().position(SMOKESTACK), 60);
        scene.overlay().showText(60)
                .text("Waste gas only chokes the output, it never explodes")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(SMOKESTACK));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("The blast scales with the size of the barrel")
                .placeNearTarget()
                .pointAt(util.vector().topOf(BODY_MID));
        scene.idle(70);

        scene.overlay().showControls(util.vector().blockSurface(FIREBOX, Direction.WEST), Pointing.RIGHT, 40)
                .rightClick()
                .withItem(new ItemStack(Items.IRON_SHOVEL));
        scene.idle(25);
        light(scene, util, FIREBOX, false);
        scene.overlay().showText(60)
                .text("In Arcade mode it locks out, and a Shovel resets it")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(FIREBOX, Direction.WEST));
        scene.idle(70);
    }

    public static void gauge(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "boiler_gauge", "Reading a Boiler");
        base(scene, util);
        scene.idle(15);

        scene.overlay().showControls(util.vector().blockSurface(FIREBOX, Direction.WEST), Pointing.RIGHT, 40)
                .rightClick()
                .withItem(new ItemStack(BoilerBlocks.LOCOMOTIVE_GAUGE_ITEM.get()));
        scene.idle(25);
        scene.overlay().showText(60)
                .text("Right click a Firebox with a Locomotive Gauge to bind it")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(FIREBOX, Direction.WEST));
        scene.idle(70);

        scene.world().showSection(util.select().position(PANEL), Direction.SOUTH);
        scene.world().modifyBlockEntity(PANEL, GaugeBlockEntity.class,
                be -> be.addGauge(PanelSlot.TOP_LEFT, FIREBOX, GaugeMetric.PRESSURE));
        scene.idle(20);
        scene.overlay().showText(60)
                .text("It can be placed on any face in the same level")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(PANEL, Direction.NORTH));
        scene.idle(70);

        scene.overlay().showControls(util.vector().blockSurface(PANEL, Direction.NORTH), Pointing.RIGHT, 40)
                .rightClick()
                .withItem(AllItems.WRENCH.asStack());
        scene.idle(25);
        scene.overlay().showText(60)
                .text("Using a Wrench on it opens its settings")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(PANEL, Direction.NORTH));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("Gauges also read speedometers, stressometers and fluid valves")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(PANEL, Direction.NORTH));
        scene.idle(70);
    }

    private static CreateSceneBuilder open(SceneBuilder builder, String id, String title) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(id, title);
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);
        return scene;
    }

    private static void showHatch(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos hatch) {
        scene.world().showSection(util.select().position(hatch), Direction.DOWN);
        scene.idle(10);
        scene.world().modifyBlock(hatch, state -> state.setValue(BlockStateProperties.OPEN, true), false);
    }

    private static void base(CreateSceneBuilder scene, SceneBuildingUtil util) {
        scene.world().showSection(util.select().fromTo(LEGS_FROM, LEGS_TO), Direction.DOWN);
        scene.world().showSection(util.select().position(FIREBOX), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(BODY_NEAR, BODY_FAR), Direction.DOWN);
    }

    private static void light(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos firebox, boolean lit) {
        scene.world().modifyBlocks(util.select().position(firebox), state -> state.setValue(BlockStateProperties.LIT, lit), false);
    }

    private static Selection feedLine(SceneBuildingUtil util) {
        return util.select().position(PUMP)
                .add(util.select().position(COG))
                .add(util.select().fromTo(SHAFT_NEAR, SHAFT_FAR));
    }

    private static void fill(CreateSceneBuilder scene) {
        scene.world().modifyBlockEntity(TANK_LOW, FluidTankBlockEntity.class, be -> be.getTankInventory()
                .fill(new FluidStack(Fluids.WATER, 24000), FluidAction.EXECUTE));
    }
}

package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.cerisene.CeriseneBlocks;
import com.lycoris.loconautics.content.combustion.EnginePart;
import com.lycoris.loconautics.content.combustion.SmallEngineBlock;
import com.lycoris.loconautics.content.combustion.SmallEngineBlockEntity;
import com.lycoris.loconautics.content.combustion.SmallEngineTuning;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panellever.LeverPart;
import com.lycoris.loconautics.content.controls.panelswitch.SwitchPart;
import com.lycoris.loconautics.content.controls.panelvalve.ValvePart;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.redstone.analogLever.AnalogLeverBlockEntity;
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
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public class SmallEngineScenes {

    private static final float TOP_RPM = SmallEngineTuning.TOP_RPM;
    private static final float PUMP_RPM = 32f;
    private static final int OFF = SmallEngineTuning.OFF_SIGNAL;
    private static final int TANK_FILL = 24000;

    private static final BlockPos LONE = new BlockPos(3, 1, 2);
    private static final BlockPos LONE_OUT = new BlockPos(4, 1, 2);
    private static final BlockPos LONE_END = new BlockPos(5, 1, 2);

    private static final BlockPos BACK = new BlockPos(2, 2, 1);
    private static final BlockPos MIDDLE = new BlockPos(3, 2, 1);
    private static final BlockPos FRONT = new BlockPos(4, 2, 1);
    private static final BlockPos OUT = new BlockPos(5, 2, 1);
    private static final BlockPos FOOT_BACK = new BlockPos(2, 1, 1);
    private static final BlockPos FOOT_FRONT = new BlockPos(4, 1, 1);

    private static final BlockPos PIPE = new BlockPos(3, 1, 1);
    private static final BlockPos PUMP = new BlockPos(3, 1, 2);
    private static final BlockPos COG = new BlockPos(2, 1, 2);
    private static final BlockPos SHAFT_NEAR = new BlockPos(2, 1, 3);
    private static final BlockPos SHAFT_FAR = new BlockPos(2, 1, 5);
    private static final BlockPos TANK_LOW = new BlockPos(3, 1, 3);
    private static final BlockPos TANK_HIGH = new BlockPos(3, 3, 3);

    private static final BlockPos LEVER_BASE = new BlockPos(3, 1, 0);
    private static final BlockPos LEVER = new BlockPos(3, 2, 0);

    private static final BlockPos CASING_LOW = new BlockPos(1, 1, 4);
    private static final BlockPos CASING_HIGH = new BlockPos(1, 2, 4);
    private static final BlockPos PANEL = new BlockPos(1, 2, 3);

    public static void single(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "small_engine_single", "Generating Rotational Force using Small Combustion Engines");

        scene.world().showSection(util.select().position(LONE), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70)
                .text("Small Combustion Engines burn liquid fuel to generate Rotational Force")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(LONE));
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(LONE), Pointing.DOWN, 40)
                .rightClick()
                .withItem(new ItemStack(CeriseneBlocks.CERISENE_BUCKET.get()));
        scene.idle(25);
        scene.overlay().showText(60)
                .text("Right click it with a bucket of fuel to fill its tank")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(LONE));
        scene.idle(70);

        scene.world().showSection(util.select().fromTo(LONE_OUT, LONE_END), Direction.WEST);
        scene.idle(10);
        Selection turning = util.select().fromTo(LONE, LONE_END);
        drive(scene, util, 0, turning, LONE);
        scene.effects().rotationSpeedIndicator(LONE_OUT);
        scene.overlay().showText(70)
                .text("Rotational Force will be output through the shaft on its front")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(LONE_OUT));
        scene.idle(80);

        scene.overlay().showText(60)
                .text("At most it provides 256 RPM and 8192 Stress Units")
                .placeNearTarget()
                .pointAt(util.vector().topOf(LONE_OUT));
        scene.idle(70);

        scene.overlay().showOutline(PonderPalette.GREEN, "fuel", util.select().position(LONE), 60);
        scene.overlay().showText(60)
                .text("Cerisene is the best fuel, while Lava is the weakest")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(LONE));
        scene.idle(70);

        scene.overlay().showControls(util.vector().blockSurface(LONE, Direction.NORTH), Pointing.RIGHT, 40)
                .rightClick()
                .withItem(AllItems.GOGGLES.asStack());
        scene.idle(25);
        scene.overlay().showText(70)
                .text("Engineer's Goggles will show whether it is running or out of fuel")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(LONE, Direction.NORTH));
        scene.idle(80);
    }

    public static void pipes(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "small_engine_pipes", "Fueling Engines using Pipes");

        scene.world().setBlock(PUMP, AllBlocks.FLUID_PIPE.get().getAxisState(Axis.Z), false);
        join(scene, util, EnginePart.SINGLE, MIDDLE);
        ElementLink<WorldSectionElement> engine = ground(scene, util, util.select().position(MIDDLE));
        scene.idle(20);

        scene.overlay().showText(60)
                .text("Fuel is received through the bottom of the engine")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(MIDDLE.below(), Direction.DOWN));
        scene.idle(30);
        lift(scene, util, engine);
        scene.idle(40);

        scene.world().showSection(util.select().fromTo(TANK_LOW, TANK_HIGH), Direction.DOWN);
        scene.idle(5);
        fill(scene);
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(PIPE, PUMP), Direction.NORTH);
        scene.idle(15);
        scene.overlay().showText(60)
                .text("A Fluid Tank and Pipes can supply it")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(PIPE));
        scene.idle(70);

        scene.overlay().showOutline(PonderPalette.RED, "pipes", util.select().fromTo(PIPE, PUMP), 60);
        scene.overlay().showText(60)
                .text("Pipes alone will not move any fuel")
                .attachKeyFrame()
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .pointAt(util.vector().topOf(PUMP));
        scene.idle(70);

        scene.world().destroyBlock(PUMP);
        scene.world().restoreBlocks(util.select().position(PUMP));
        scene.world().showSection(util.select().position(COG), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().fromTo(SHAFT_NEAR, SHAFT_FAR), Direction.NORTH);
        scene.idle(15);
        Selection feed = feedLine(util);
        scene.world().setKineticSpeed(feed, PUMP_RPM);
        scene.world().propagatePipeChange(PUMP);
        scene.effects().rotationSpeedIndicator(COG);
        drive(scene, util, 0, util.select().position(MIDDLE), MIDDLE);
        scene.overlay().showText(70)
                .text("Once powered, the pump will push fuel into the engine...")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(PUMP));
        scene.idle(80);

        scene.overlay().showText(60)
                .text("...which will run until the tank is empty")
                .placeNearTarget()
                .pointAt(util.vector().topOf(MIDDLE));
        scene.idle(70);
    }

    public static void tandem(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "small_engine_tandem", "Combining Small Combustion Engines");

        join(scene, util, EnginePart.SINGLE, MIDDLE);
        ElementLink<WorldSectionElement> middle = ground(scene, util, util.select().position(MIDDLE));
        scene.idle(25);
        drive(scene, util, 0, util.select().position(MIDDLE), MIDDLE);

        ElementLink<WorldSectionElement> back = ground(scene, util, util.select().position(BACK));
        scene.idle(15);
        join(scene, util, EnginePart.FRONT, MIDDLE);
        drive(scene, util, 0, util.select().position(MIDDLE), BACK, MIDDLE);
        scene.overlay().showText(60)
                .text("Engines placed in a line facing the same way will connect...")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(BACK.below()));
        scene.idle(70);

        scene.overlay().showOutline(PonderPalette.BLUE, "row", util.select().fromTo(BACK.below(), MIDDLE.below()), 50);
        scene.overlay().showText(60)
                .text("...and switch to models that fit together")
                .placeNearTarget()
                .pointAt(util.vector().topOf(MIDDLE.below()));
        scene.idle(70);

        ElementLink<WorldSectionElement> front = ground(scene, util, util.select().position(FRONT));
        scene.idle(15);
        join(scene, util, EnginePart.MIDDLE, MIDDLE);
        drive(scene, util, 0, util.select().position(FRONT), BACK, MIDDLE, FRONT);
        scene.overlay().showText(50)
                .text("A row can hold up to 3 Engines")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(FRONT.below()));
        scene.idle(60);

        lift(scene, util, back, middle, front);
        scene.idle(20);
        scene.world().showSection(util.select().position(FOOT_BACK), Direction.UP);
        scene.world().showSection(util.select().position(FOOT_FRONT), Direction.UP);
        fuelLine(scene, util);
        scene.world().showSection(util.select().position(OUT), Direction.WEST);
        Selection turning = util.select().position(FRONT).add(util.select().position(OUT));
        drive(scene, util, 0, turning, BACK, MIDDLE, FRONT);
        scene.overlay().showOutline(PonderPalette.GREEN, "head", util.select().position(FRONT), 60);
        scene.overlay().showText(60)
                .text("The front Engine outputs the Rotational Force of the whole row")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(FRONT));
        scene.idle(70);

        scene.overlay().showText(70)
                .text("Each running engine adds 8192 Stress Units to the output")
                .placeNearTarget()
                .pointAt(util.vector().topOf(MIDDLE));
        scene.idle(80);

        scene.overlay().showOutline(PonderPalette.BLUE, "fuel", util.select().fromTo(BACK, FRONT), 60);
        scene.overlay().showText(60)
                .text("Fuel is shared, and a broken engine passes its own on")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(MIDDLE));
        scene.idle(70);

        scene.world().hideIndependentSection(back, Direction.UP);
        scene.world().hideSection(util.select().position(FOOT_BACK), Direction.DOWN);
        join(scene, util, EnginePart.BACK, MIDDLE);
        drive(scene, util, 0, turningRow(util), MIDDLE, FRONT);
        scene.idle(30);
    }

    public static void redstone(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "small_engine_redstone", "Controlling Engines with Redstone");

        buildRow(scene, util);
        scene.overlay().showText(60)
                .text("Without a Redstone signal engines run at full speed")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(MIDDLE));
        scene.idle(70);

        scene.world().showSection(util.select().fromTo(LEVER_BASE, LEVER), Direction.DOWN);
        scene.idle(15);
        power(scene, util, 4);
        scene.overlay().showText(70)
                .text("Every point of signal strength slows them by a sixteenth")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(LEVER));
        scene.idle(80);

        power(scene, util, 8);
        scene.overlay().showText(50)
                .text("A signal of 8 leaves half of the speed")
                .placeNearTarget()
                .pointAt(util.vector().topOf(LEVER));
        scene.idle(60);

        power(scene, util, SmallEngineTuning.SLOWEST_SIGNAL);
        scene.overlay().showText(50)
                .text("At 14 the engines run at their slowest")
                .placeNearTarget()
                .pointAt(util.vector().topOf(LEVER));
        scene.idle(60);

        power(scene, util, OFF);
        scene.overlay().showOutline(PonderPalette.RED, "off", util.select().fromTo(BACK, FRONT), 50);
        scene.overlay().showText(50)
                .text("A signal of 15 will turn them off")
                .attachKeyFrame()
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .pointAt(util.vector().topOf(LEVER));
        scene.idle(60);

        scene.overlay().showText(60)
                .text("A signal at any engine of the row controls the whole row")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(MIDDLE));
        scene.idle(70);
    }

    public static void panel(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "small_engine_panel", "Controlling Engines using Panels");

        buildRow(scene, util);
        scene.world().showSection(util.select().fromTo(CASING_LOW, CASING_HIGH), Direction.DOWN);
        scene.world().showSection(util.select().position(PANEL), Direction.SOUTH);
        scene.idle(15);

        scene.overlay().showControls(util.vector().topOf(FRONT), Pointing.DOWN, 40)
                .rightClick()
                .withItem(new ItemStack(BoilerBlocks.GAUGE_LEVER_ITEM.get()));
        scene.idle(25);
        scene.overlay().showText(50)
                .text("Right click an Engine with a Gauge Lever to bind it")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(FRONT));
        scene.idle(60);

        put(scene, PanelSlot.TOP_LEFT, lever(0));
        scene.idle(15);
        lever(scene, util, 8);
        scene.idle(15);
        lever(scene, util, OFF);
        scene.overlay().showText(70)
                .text("Its power acts as a Redstone signal, 15 turns the engine off")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(PANEL, Direction.NORTH));
        scene.idle(80);

        scene.world().modifyBlockEntity(PANEL, PanelBlockEntity.class, be -> be.removePart(PanelSlot.TOP_LEFT));
        scene.idle(10);
        put(scene, PanelSlot.TOP_RIGHT, new SwitchPart(0, FRONT, SwitchPart.DEFAULT_BODY, SwitchPart.DEFAULT_KNOB_COLOR,
                SwitchPart.DEFAULT_STYLE, SwitchPart.DEFAULT_OPTIONS));
        drive(scene, util, OFF, turningRow(util), BACK, MIDDLE, FRONT);
        scene.idle(15);
        switchTo(scene, util, true);
        scene.overlay().showText(60)
                .text("A Gauge Switch works the other way round, on is full speed")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(PANEL, Direction.NORTH));
        scene.idle(70);

        scene.world().modifyBlockEntity(PANEL, PanelBlockEntity.class, be -> be.removePart(PanelSlot.TOP_RIGHT));
        scene.idle(10);
        scene.overlay().showControls(util.vector().topOf(FRONT), Pointing.DOWN, 40)
                .rightClick()
                .withItem(new ItemStack(BoilerBlocks.VALVE_ITEM.get()));
        scene.idle(25);
        put(scene, PanelSlot.BOTTOM_LEFT, new ValvePart(360f, FRONT, ValvePart.DEFAULT_WHEEL_COLOR));
        drive(scene, util, 0, turningRow(util), BACK, MIDDLE, FRONT);
        scene.overlay().showText(50)
                .text("A Valve is bound the same way")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(PANEL, Direction.NORTH));
        scene.idle(60);

        valve(scene, util, 7);
        scene.idle(25);
        valve(scene, util, 0);
        scene.overlay().showText(60)
                .text("It goes from full speed to the slowest, never off")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(PANEL, Direction.NORTH));
        scene.idle(70);
    }

    // the whole row on the ground, then lifted once the fuel line goes in underneath
    private static void buildRow(CreateSceneBuilder scene, SceneBuildingUtil util) {
        join(scene, util, EnginePart.BACK, BACK);
        join(scene, util, EnginePart.MIDDLE, MIDDLE);
        join(scene, util, EnginePart.FRONT, FRONT);
        ElementLink<WorldSectionElement> back = ground(scene, util, util.select().position(BACK));
        ElementLink<WorldSectionElement> middle = ground(scene, util, util.select().position(MIDDLE));
        ElementLink<WorldSectionElement> front = ground(scene, util, util.select().position(FRONT));
        scene.idle(25);
        lift(scene, util, back, middle, front);
        scene.idle(20);
        scene.world().showSection(util.select().position(FOOT_BACK), Direction.UP);
        scene.world().showSection(util.select().position(FOOT_FRONT), Direction.UP);
        fuelLine(scene, util);
        scene.world().showSection(util.select().position(OUT), Direction.WEST);
        drive(scene, util, 0, turningRow(util), BACK, MIDDLE, FRONT);
        scene.idle(20);
    }

    private static Selection turningRow(SceneBuildingUtil util) {
        return util.select().position(FRONT).add(util.select().position(OUT));
    }

    private static Selection feedLine(SceneBuildingUtil util) {
        return util.select().position(PUMP)
                .add(util.select().position(COG))
                .add(util.select().fromTo(SHAFT_NEAR, SHAFT_FAR));
    }

    private static ElementLink<WorldSectionElement> ground(CreateSceneBuilder scene, SceneBuildingUtil util, Selection selection) {
        ElementLink<WorldSectionElement> link = scene.world().showIndependentSection(selection, Direction.DOWN);
        scene.world().moveSection(link, util.vector().of(0, -1, 0), 0);
        return link;
    }

    @SafeVarargs
    private static void lift(CreateSceneBuilder scene, SceneBuildingUtil util, ElementLink<WorldSectionElement>... links) {
        for (ElementLink<WorldSectionElement> link : links) {
            scene.world().moveSection(link, util.vector().of(0, 1, 0), 15);
        }
    }

    private static void fill(CreateSceneBuilder scene) {
        scene.world().modifyBlockEntity(TANK_LOW, FluidTankBlockEntity.class, be -> be.getTankInventory()
                .fill(new FluidStack(CeriseneBlocks.CERISENE.get(), TANK_FILL), FluidAction.EXECUTE));
    }

    // tank, pipe, pump and the cogwheel with its shaft coming in from outside the plate
    private static void fuelLine(CreateSceneBuilder scene, SceneBuildingUtil util) {
        scene.world().showSection(util.select().fromTo(TANK_LOW, TANK_HIGH), Direction.DOWN);
        scene.idle(5);
        fill(scene);
        scene.world().showSection(util.select().fromTo(PIPE, PUMP), Direction.NORTH);
        scene.idle(5);
        scene.world().showSection(util.select().position(COG), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(SHAFT_NEAR, SHAFT_FAR), Direction.NORTH);
        scene.idle(10);
        scene.world().setKineticSpeed(feedLine(util), PUMP_RPM);
        scene.world().propagatePipeChange(PUMP);
        scene.effects().rotationSpeedIndicator(COG);
        scene.idle(10);
    }

    private static void join(CreateSceneBuilder scene, SceneBuildingUtil util, EnginePart part, BlockPos engine) {
        scene.world().modifyBlocks(util.select().position(engine), state -> state.setValue(SmallEngineBlock.PART, part), false);
    }

    // members run tail to head, the last one is the head and the only one that turns the shaft
    private static void drive(CreateSceneBuilder scene, SceneBuildingUtil util, int signal, Selection turning, BlockPos... members) {
        float fraction = SmallEngineTuning.speedFraction(signal);
        float rpm = TOP_RPM * fraction;
        boolean on = !SmallEngineTuning.isOff(signal);
        BlockPos head = members[members.length - 1];
        for (BlockPos member : members) {
            boolean isHead = member.equals(head);
            scene.world().modifyBlockEntityNBT(util.select().position(member), SmallEngineBlockEntity.class, tag -> {
                tag.putBoolean("Head", isHead);
                tag.putBoolean("Running", on);
                tag.putInt("Signal", signal);
                tag.putInt("Fuel", 0);
                tag.putFloat("OwnRpm", rpm);
                tag.putInt("Active", isHead && on ? members.length : 0);
                tag.putFloat("RowRpm", isHead ? rpm : 0f);
            });
        }
        scene.world().setKineticSpeed(turning, rpm);
    }

    private static void power(CreateSceneBuilder scene, SceneBuildingUtil util, int strength) {
        scene.overlay().showControls(util.vector().centerOf(LEVER).add(0, -0.25, 0), Pointing.DOWN, 30).rightClick();
        scene.idle(7);
        scene.world().modifyBlockEntityNBT(util.select().position(LEVER), AnalogLeverBlockEntity.class,
                nbt -> nbt.putInt("State", strength));
        scene.effects().indicateRedstone(LEVER);
        drive(scene, util, strength, turningRow(util), BACK, MIDDLE, FRONT);
    }

    private static void valve(CreateSceneBuilder scene, SceneBuildingUtil util, int stop) {
        float degrees = stop * ValvePart.FULL_TURN / SmallEngineTuning.SLOWEST_SIGNAL;
        scene.world().modifyBlockEntity(PANEL, PanelBlockEntity.class,
                be -> be.replacePart(PanelSlot.BOTTOM_LEFT, new ValvePart(degrees, FRONT, ValvePart.DEFAULT_WHEEL_COLOR)));
        drive(scene, util, SmallEngineTuning.SLOWEST_SIGNAL - stop, turningRow(util), BACK, MIDDLE, FRONT);
    }

    private static void lever(CreateSceneBuilder scene, SceneBuildingUtil util, int power) {
        scene.world().modifyBlockEntity(PANEL, PanelBlockEntity.class,
                be -> be.replacePart(PanelSlot.TOP_LEFT, lever(power)));
        drive(scene, util, power, turningRow(util), BACK, MIDDLE, FRONT);
    }

    private static void switchTo(CreateSceneBuilder scene, SceneBuildingUtil util, boolean on) {
        scene.world().modifyBlockEntity(PANEL, PanelBlockEntity.class, be -> be.replacePart(PanelSlot.TOP_RIGHT,
                new SwitchPart(on ? SwitchPart.MAX_POWER : 0, FRONT, SwitchPart.DEFAULT_BODY, SwitchPart.DEFAULT_KNOB_COLOR,
                        SwitchPart.DEFAULT_STYLE, SwitchPart.DEFAULT_OPTIONS)));
        drive(scene, util, on ? 0 : OFF, turningRow(util), BACK, MIDDLE, FRONT);
    }

    private static LeverPart lever(int power) {
        return new LeverPart(power, FRONT, LeverPart.DEFAULT_BODY, LeverPart.DEFAULT_KNOB_COLOR,
                LeverPart.DEFAULT_STYLE, LeverPart.DEFAULT_OPTIONS);
    }

    private static void put(CreateSceneBuilder scene, PanelSlot slot, PanelPart part) {
        scene.world().modifyBlockEntity(PANEL, PanelBlockEntity.class, be -> {
            if (be.hasPart(slot)) {
                be.replacePart(slot, part);
            } else {
                be.putPart(slot, part);
            }
        });
    }

    private static CreateSceneBuilder open(SceneBuilder builder, String id, String title) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(id, title);
        scene.configureBasePlate(1, 0, 5);
        scene.showBasePlate();
        scene.idle(5);
        return scene;
    }
}

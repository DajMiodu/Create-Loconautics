package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.common.turntable.sequence.TurntableSpeedModifier;
import com.lycoris.loconautics.content.turntablebearing.TurntableBearingBlock;
import com.lycoris.loconautics.content.turntablebearing.TurntableBearingBlockEntity;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.trains.signal.SignalBlockEntity.SignalState;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class TurntableScenes {

    private record Drive(BlockPos bearing, BlockPos... chain) {
    }

    private static final int QUARTER_TICKS = 30;

    private static final BlockPos BEARING = new BlockPos(2, 1, 2);
    private static final BlockPos PLATE = new BlockPos(2, 2, 2);
    private static final BlockPos TOP_CELL = new BlockPos(2, 3, 2);
    private static final BlockPos BUTTON = new BlockPos(2, 1, 1);
    private static final BlockPos SMALL_COG = new BlockPos(3, 1, 2);
    private static final Vec3 VALUE_PANEL = new Vec3(2.5, 1.1875, 2.02);

    private static final BlockPos WIDE_BEARING = new BlockPos(4, 1, 2);
    private static final BlockPos WIDE_BOGEY = new BlockPos(2, 5, 2);
    private static final BlockPos WIDE_BOGEY_FAR = new BlockPos(6, 5, 2);
    private static final BlockPos WIDE_BLAZE = new BlockPos(3, 6, 2);
    private static final BlockPos DIAG_BEARING = new BlockPos(4, 1, 1);
    private static final BlockPos DIAG_BOGEY = new BlockPos(2, 5, 1);
    private static final BlockPos DIAG_BOGEY_FAR = new BlockPos(6, 5, 1);
    private static final BlockPos DIAG_BLAZE = new BlockPos(3, 6, 1);
    private static final BlockPos SIGNAL_BEARING = new BlockPos(4, 1, 2);
    private static final BlockPos SIGNAL_EAST = new BlockPos(11, 3, 0);
    private static final BlockPos SIGNAL_SOUTH = new BlockPos(6, 3, 9);
    private static final BlockPos YARD_BEARING = new BlockPos(11, 1, 11);
    private static final BlockPos YARD_BLAZE = new BlockPos(12, 6, 11);
    private static final BlockPos STATION_EAST = new BlockPos(22, 3, 8);
    private static final BlockPos STATION_NORTH = new BlockPos(14, 3, 0);
    private static final BlockPos STATION_SOUTH = new BlockPos(14, 3, 22);
    private static final Drive SMALL_DRIVE = new Drive(BEARING, SMALL_COG, new BlockPos(4, 1, 3),
            new BlockPos(5, 0, 3));
    private static final Drive FINAL_DRIVE = new Drive(WIDE_BEARING, new BlockPos(3, 1, 2), new BlockPos(2, 1, 3),
            new BlockPos(2, 0, 3));
    private static final Drive DIAG_DRIVE = new Drive(DIAG_BEARING, new BlockPos(3, 1, 1), new BlockPos(2, 1, 2),
            new BlockPos(2, 0, 2));
    private static final Drive YARD_DRIVE = new Drive(YARD_BEARING, new BlockPos(10, 1, 11), new BlockPos(9, 1, 12),
            new BlockPos(9, 0, 12));
    private static final int DRIVE_IN = 6;
    private static final int DRIVE_TICKS = 60;
    private static final int EXIT = 8;

    public static void assembly(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "turntable_bearing_assembly", "Turning Tracks using Turntable Bearings");

        scene.world().showSection(util.select().position(BEARING), Direction.DOWN);
        scene.idle(20);
        scene.overlay().showText(80)
                .text("Turntable Bearings attach to the block in front of them")
                .attachKeyFrame()
                .placeNearTarget()
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().topOf(BEARING));
        AABB front = AABB.unitCubeFromLowerCorner(new Vec3(2, 2, 2));
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, front, front, 90);
        scene.idle(80);

        ElementLink<WorldSectionElement> structure = scene.world().showIndependentSectionImmediately(
                util.select().position(2, 3, 2));
        scene.world().moveSection(structure, util.vector().of(0, -1, 0), 0);
        scene.world().configureCenterOfRotation(structure, util.vector().centerOf(2, 3, 2));
        scene.world().showSectionAndMerge(util.select().position(2, 4, 2), Direction.DOWN, structure);
        scene.idle(10);
        scene.world().showSectionAndMerge(util.select().fromTo(0, 3, 2, 1, 3, 2)
                .add(util.select().fromTo(3, 3, 2, 4, 3, 2)), Direction.DOWN, structure);
        scene.idle(10);
        scene.world().showSectionAndMerge(util.select().fromTo(0, 4, 2, 1, 4, 2)
                .add(util.select().fromTo(3, 4, 2, 4, 4, 2)), Direction.DOWN, structure);
        scene.idle(15);

        scene.overlay().showControls(util.vector().centerOf(4, 2, 2), Pointing.RIGHT, 40)
                .withItem(AllItems.SUPER_GLUE.asStack())
                .rightClick();
        scene.idle(5);
        AABB end = new AABB(util.grid().at(4, 2, 2));
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, end, end, 1);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, end, end.expandTowards(-4, 1, 0), 80);
        scene.idle(10);
        scene.overlay().showText(70)
                .text("Use Super Glue to select the blocks that will turn with it")
                .attachKeyFrame()
                .placeNearTarget()
                .colored(PonderPalette.OUTPUT)
                .pointAt(util.vector().centerOf(0, 2, 2));
        scene.idle(90);

        scene.overlay().showControls(util.vector().blockSurface(BEARING, Direction.NORTH), Pointing.DOWN, 40)
                .rightClick();
        scene.idle(7);
        assemble(scene, util, structure);
        scene.overlay().showText(70)
                .text("Right click the bearing to assemble them")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(BEARING, Direction.NORTH));
        scene.idle(80);

        scene.world().showSection(chain(util, SMALL_DRIVE), Direction.WEST);
        scene.idle(10);
        scene.overlay().showText(70)
                .text("A cogwheel beside the bearing sets how fast it turns")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(SMALL_COG));
        scene.idle(80);

        scene.world().showSection(util.select().position(BUTTON), Direction.EAST);
        scene.idle(15);
        signal(scene, util);
        turn(scene, util, 90, QUARTER_TICKS, structure);
        scene.overlay().showText(70)
                .text("Each Redstone pulse turns it by a set angle")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(BUTTON));
        scene.idle(80);

        signal(scene, util);
        turn(scene, util, 90, QUARTER_TICKS, structure);
        scene.overlay().showText(60)
                .text("It holds that angle until the next pulse")
                .placeNearTarget()
                .pointAt(util.vector().topOf(new BlockPos(2, 2, 2)));
        scene.idle(70);
    }

    public static void instructions(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "turntable_bearing_instructions", "Programming Turntable Bearings");

        ElementLink<WorldSectionElement> structure = standing(scene, util);
        scene.idle(20);

        TurntableGui gui = TurntableGui.open();
        scene.overlay().showControls(util.vector().blockSurface(BEARING, Direction.NORTH), Pointing.DOWN, 30)
                .rightClick()
                .whileSneaking();
        scene.idle(10);
        scene.overlay().showText(50)
                .text("Sneak and Right click it to open its Interface")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(BEARING, Direction.NORTH));
        scene.idle(55);

        gui.show(scene,
                TurntableGui.turn(90, TurntableSpeedModifier.FORWARD),
                TurntableGui.delay(40),
                TurntableGui.await(),
                TurntableGui.turn(45, TurntableSpeedModifier.BACK_FAST),
                TurntableGui.end());
        scene.idle(20);
        gui.mark(scene, 0);
        scene.overlay().showText(80)
                .text("Turn rotates the structure by an exact angle, up to a full circle")
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(90);

        gui.mark(scene, 3);
        scene.overlay().showText(80)
                .text("The arrows next to an angle set the direction and double the speed")
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(90);

        gui.mark(scene, 1);
        scene.overlay().showText(70)
                .text("Delay holds it still for a set time")
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(80);

        gui.mark(scene, 2);
        scene.overlay().showText(80)
                .text("Await pauses the list until the next Redstone signal")
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(90);

        gui.dock(scene);
        scene.idle(25);

        signal(scene, util);
        gui.mark(scene, 0);
        turn(scene, util, 90, QUARTER_TICKS, structure);
        gui.mark(scene, 1);
        scene.idle(40);
        gui.mark(scene, 2);
        scene.idle(45);
        signal(scene, util);
        gui.mark(scene, 3);
        turn(scene, util, -45, 8, structure);
        gui.mark(scene, 4);
        scene.overlay().showText(80)
                .text("The list stops at End and starts over on the next signal")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(PLATE));
        scene.idle(90);

        gui.mark(scene, -1);
        gui.hide(scene);
        scene.idle(15);
    }

    public static void trigger(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "turntable_bearing_trigger", "Triggering Turntable Bearings");

        ElementLink<WorldSectionElement> structure = standing(scene, util);
        scene.idle(20);

        scene.overlay().showText(80)
                .text("This behavior can be configured using the value panel")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(VALUE_PANEL);
        scene.overlay().showControls(VALUE_PANEL, Pointing.DOWN, 80).scroll();
        scene.overlay().showFilterSlotInput(VALUE_PANEL, Direction.UP, 80);
        scene.idle(90);

        signal(scene, util);
        turn(scene, util, 90, QUARTER_TICKS, structure);
        scene.overlay().showText(70)
                .text("Pulse runs the list once for each signal")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(BUTTON));
        scene.idle(80);

        scene.overlay().showControls(util.vector().centerOf(BUTTON).add(0, -0.4, 0), Pointing.DOWN, 30).rightClick();
        scene.idle(7);
        scene.world().toggleRedstonePower(util.select().position(BUTTON));
        powered(scene, util, true);
        scene.effects().indicateRedstone(BUTTON);
        scene.overlay().showText(80)
                .text("Repeat While Powered starts the list again until the signal ends")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(PLATE));
        for (int quarter = 0; quarter < 3; quarter++) {
            turn(scene, util, 90, QUARTER_TICKS, structure);
            scene.idle(5);
        }
        scene.world().toggleRedstonePower(util.select().position(BUTTON));
        powered(scene, util, false);
        scene.idle(20);

        signal(scene, util);
        turn(scene, util, 90, QUARTER_TICKS, structure);
        scene.overlay().showText(70)
                .text("Alternate Direction flips the turn with every signal")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(PLATE));
        scene.idle(30);
        signal(scene, util);
        turn(scene, util, -90, QUARTER_TICKS, structure);
        scene.idle(50);
    }

    public static void schedule(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("turntable_bearing_schedule", "Scheduling Turntable Bearings");
        scene.configureBasePlate(0, 0, 17);
        scene.scaleSceneView(.5f);
        scene.setSceneOffsetY(-1);
        scene.showBasePlate();
        scene.idle(5);

        scene.world().showSection(util.select().position(WIDE_BEARING), Direction.DOWN);
        scene.world().showSection(chain(util, FINAL_DRIVE), Direction.WEST);
        ElementLink<WorldSectionElement> platform = scene.world().showIndependentSectionImmediately(
                util.select().fromTo(0, 2, 2, 8, 4, 2));
        scene.world().moveSection(platform, util.vector().of(0, -1, 0), 0);
        scene.world().configureCenterOfRotation(platform, util.vector().centerOf(WIDE_BEARING));
        scene.world().modifyBlocks(util.select().position(WIDE_BEARING),
                state -> state.setValue(TurntableBearingBlock.ASSEMBLED, true), false);
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(9, 1, 2, 16, 3, 2), Direction.WEST);
        scene.world().showSection(util.select().fromTo(4, 1, 7, 4, 3, 14), Direction.NORTH);
        scene.idle(20);

        TurntableGui gui = TurntableGui.open();
        scene.overlay().showControls(util.vector().blockSurface(WIDE_BEARING, Direction.NORTH), Pointing.DOWN, 30)
                .rightClick()
                .whileSneaking();
        scene.idle(40);

        gui.show(scene, TurntableGui.schedule());
        scene.idle(20);
        gui.mark(scene, 0);
        scene.overlay().showText(90)
                .text("Setting the first instruction to Schedule lets Train Schedules turn the structure")
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(100);
        gui.mark(scene, -1);
        gui.hide(scene);
        scene.idle(15);

        scene.overlay().showText(80)
                .text("Rails on it meet the rails of the legs around it")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(new BlockPos(4, 3, 2)));
        scene.idle(90);

        ElementLink<WorldSectionElement> train = scene.world().showIndependentSectionImmediately(
                util.select().fromTo(WIDE_BOGEY, WIDE_BOGEY_FAR).add(util.select().position(2, 6, 2))
                        .add(util.select().position(WIDE_BLAZE)));
        scene.world().moveSection(train, util.vector().of(DRIVE_IN, -1, 0), 0);
        scene.world().configureCenterOfRotation(train, util.vector().centerOf(WIDE_BEARING));
        scene.world().modifyBlocks(util.select().position(WIDE_BLAZE),
                state -> state.setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.FADING), false);
        scene.idle(20);

        scene.overlay().showControls(util.vector().topOf(WIDE_BLAZE).add(DRIVE_IN, -1, 0), Pointing.DOWN, 50)
                .rightClick()
                .withItem(AllItems.SCHEDULE.asStack());
        scene.idle(10);
        scene.world().conductorBlaze(WIDE_BLAZE, true);
        scene.idle(45);

        scene.world().moveSection(train, util.vector().of(-DRIVE_IN, 0, 0), DRIVE_TICKS);
        scene.world().animateBogey(WIDE_BOGEY, (float) DRIVE_IN, DRIVE_TICKS);
        scene.world().animateBogey(WIDE_BOGEY_FAR, (float) DRIVE_IN, DRIVE_TICKS);
        scene.overlay().showText(80)
                .text("A Train carrying a Schedule for the other leg parks centered on it")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(WIDE_BLAZE).add(DRIVE_IN - 3, -1, 0));
        scene.idle(DRIVE_TICKS + 40);

        turn(scene, util, FINAL_DRIVE, 90, QUARTER_TICKS, platform, train);
        scene.overlay().showText(80)
                .text("It turns the Train to face that leg, no Redstone needed")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(new BlockPos(4, 4, 2)));
        scene.idle(90);

        scene.world().moveSection(train, util.vector().of(0, 0, EXIT), DRIVE_TICKS + 20);
        scene.world().animateBogey(WIDE_BOGEY, (float) EXIT, DRIVE_TICKS + 20);
        scene.world().animateBogey(WIDE_BOGEY_FAR, (float) EXIT, DRIVE_TICKS + 20);
        scene.idle(DRIVE_TICKS / 2);
        scene.world().hideIndependentSection(train, null);
        scene.idle(DRIVE_TICKS / 2 + 30);
        turn(scene, util, FINAL_DRIVE, -90, QUARTER_TICKS, platform);
        scene.overlay().showText(80)
                .text("Then the Train drives off and it turns back to where it started")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(new BlockPos(4, 3, 2)));
        scene.idle(90);

        scene.overlay().showText(80)
                .text("The cogwheel needs Rotational Force and the structure has to be as long as the Train")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(new BlockPos(3, 1, 2)));
        scene.idle(90);
    }

    public static void diagonal(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = wide(builder, 17);
        scene.title("turntable_bearing_diagonal", "Reaching Diagonal Legs with Turntable Bearings");

        scene.world().showSection(util.select().position(DIAG_BEARING), Direction.DOWN);
        scene.world().showSection(chain(util, DIAG_DRIVE), Direction.WEST);
        ElementLink<WorldSectionElement> platform = scene.world().showIndependentSectionImmediately(
                util.select().fromTo(0, 2, 1, 8, 4, 1));
        scene.world().moveSection(platform, util.vector().of(0, -1, 0), 0);
        scene.world().configureCenterOfRotation(platform, util.vector().centerOf(DIAG_BEARING));
        scene.world().modifyBlocks(util.select().position(DIAG_BEARING),
                state -> state.setValue(TurntableBearingBlock.ASSEMBLED, true), false);
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(9, 1, 1, 16, 3, 1), Direction.WEST);
        scene.world().showSection(util.select().fromTo(4, 1, 6, 4, 3, 13), Direction.NORTH);
        scene.idle(30);

        scene.overlay().showText(80)
                .text("A Turntable can turn to any angle, so a leg can leave it diagonally")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(new BlockPos(8, 3, 5)));
        for (int step = 0; step < 8; step++) {
            scene.world().showSection(util.select().fromTo(8 + step, 1, 5 + step, 8 + step, 3, 5 + step), Direction.DOWN);
            scene.idle(4);
        }
        scene.idle(60);

        ElementLink<WorldSectionElement> train = scene.world().showIndependentSectionImmediately(
                util.select().fromTo(DIAG_BOGEY, DIAG_BOGEY_FAR).add(util.select().position(2, 6, 1))
                        .add(util.select().position(DIAG_BLAZE)));
        scene.world().moveSection(train, util.vector().of(DRIVE_IN, -1, 0), 0);
        scene.world().configureCenterOfRotation(train, util.vector().centerOf(DIAG_BEARING));
        awake(scene, util, DIAG_BLAZE);
        scene.idle(10);
        scene.world().moveSection(train, util.vector().of(-DRIVE_IN, 0, 0), DRIVE_TICKS);
        scene.world().animateBogey(DIAG_BOGEY, (float) DRIVE_IN, DRIVE_TICKS);
        scene.world().animateBogey(DIAG_BOGEY_FAR, (float) DRIVE_IN, DRIVE_TICKS);
        scene.overlay().showText(80)
                .text("A Train whose Schedule needs the diagonal leg parks centered on the plate")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(DIAG_BLAZE).add(DRIVE_IN - 3, -1, 0));
        scene.idle(DRIVE_TICKS + 40);

        turn(scene, util, DIAG_DRIVE, 135, QUARTER_TICKS * 3 / 2, platform, train);
        scene.overlay().showText(80)
                .text("The plate turns it by whatever angle that leg needs")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(new BlockPos(4, 4, 1)));
        scene.idle(90);

        scene.world().moveSection(train, util.vector().of(EXIT, 0, EXIT), DRIVE_TICKS + 20);
        scene.world().animateBogey(DIAG_BOGEY, (float) (EXIT * 1.4142), DRIVE_TICKS + 20);
        scene.world().animateBogey(DIAG_BOGEY_FAR, (float) (EXIT * 1.4142), DRIVE_TICKS + 20);
        scene.idle(DRIVE_TICKS / 2);
        scene.world().hideIndependentSection(train, null);
        scene.overlay().showText(80)
                .text("Then the Train drives straight onto the diagonal leg")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(new BlockPos(11, 3, 8)));
        scene.idle(DRIVE_TICKS / 2 + 70);
    }

    public static void signals(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = wide(builder, 17);
        scene.title("turntable_bearing_signals", "Binding Train Signals to Turntable Bearings");

        scene.world().showSection(util.select().position(SIGNAL_BEARING), Direction.DOWN);
        scene.world().showSection(chain(util, FINAL_DRIVE), Direction.WEST);
        ElementLink<WorldSectionElement> platform = scene.world().showIndependentSectionImmediately(
                util.select().fromTo(0, 2, 2, 8, 4, 2));
        scene.world().moveSection(platform, util.vector().of(0, -1, 0), 0);
        scene.world().configureCenterOfRotation(platform, util.vector().centerOf(SIGNAL_BEARING));
        scene.world().modifyBlocks(util.select().position(SIGNAL_BEARING),
                state -> state.setValue(TurntableBearingBlock.ASSEMBLED, true), false);
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(9, 1, 2, 16, 3, 2), Direction.WEST);
        scene.world().showSection(util.select().fromTo(4, 1, 7, 4, 3, 14), Direction.NORTH);
        scene.idle(30);

        scene.overlay().showControls(util.vector().topOf(SIGNAL_BEARING), Pointing.DOWN, 60)
                .rightClick()
                .whileSneaking()
                .withItem(AllBlocks.TRACK_SIGNAL.asStack());
        scene.idle(15);
        scene.overlay().showText(70)
                .text("Sneak and Right click a Turntable Bearing with a Train Signal to bind it")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(SIGNAL_BEARING));
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(new BlockPos(11, 3, 2)), Pointing.DOWN, 40)
                .rightClick()
                .withItem(AllBlocks.TRACK_SIGNAL.asStack());
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(11, 1, 0, 11, 4, 0), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(6, 1, 9, 6, 4, 9), Direction.DOWN);
        scene.overlay().showText(70)
                .text("Signals placed afterwards are linked to that bearing")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(SIGNAL_EAST));
        scene.idle(80);

        scene.world().changeSignalState(SIGNAL_EAST, SignalState.RED);
        scene.world().changeSignalState(SIGNAL_SOUTH, SignalState.RED);
        startTurn(scene, util, FINAL_DRIVE, 90, QUARTER_TICKS, platform);
        scene.overlay().showText(80)
                .text("Linked signals stay red while the plate is turning or holds a Train")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(SIGNAL_EAST));
        scene.idle(QUARTER_TICKS);
        stopTurn(scene, util, FINAL_DRIVE);
        scene.idle(50);

        scene.world().changeSignalState(SIGNAL_EAST, SignalState.GREEN);
        scene.world().changeSignalState(SIGNAL_SOUTH, SignalState.GREEN);
        scene.overlay().showText(80)
                .text("They turn green again once the plate has settled")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(SIGNAL_SOUTH));
        scene.idle(90);
    }

    public static void parking(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = wide(builder, 23);
        scene.title("turntable_bearing_parking", "Parking Trains with Turntable Bearings");

        ElementLink<WorldSectionElement> platform = yard(scene, util);
        scene.world().animateTrainStation(STATION_EAST, true);
        scene.world().animateTrainStation(STATION_NORTH, true);
        ElementLink<WorldSectionElement> east = scene.world().showIndependentSectionImmediately(
                util.select().fromTo(9, 5, 8, 13, 6, 8));
        scene.world().moveSection(east, util.vector().of(8, -1, 3), 0);
        awake(scene, util, new BlockPos(12, 6, 8));
        ElementLink<WorldSectionElement> north = scene.world().showIndependentSectionImmediately(
                util.select().fromTo(9, 5, 14, 13, 6, 14));
        scene.world().rotateSection(north, 0, 90, 0, 0);
        scene.world().moveSection(north, util.vector().of(0, -1, -11), 0);
        awake(scene, util, new BlockPos(12, 6, 14));
        scene.idle(30);

        for (BlockPos station : new BlockPos[]{STATION_EAST, STATION_NORTH, STATION_SOUTH}) {
            AABB box = new AABB(station);
            scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, station, box, 90);
        }
        scene.overlay().showText(90)
                .text("Stations on different legs can share one name, with a * to match them all")
                .attachKeyFrame()
                .placeNearTarget()
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().topOf(STATION_SOUTH));
        scene.idle(100);

        ElementLink<WorldSectionElement> train = scene.world().showIndependentSectionImmediately(
                util.select().fromTo(9, 5, 11, 13, 6, 11));
        scene.world().moveSection(train, util.vector().of(-DRIVE_IN, -1, 0), 0);
        scene.world().configureCenterOfRotation(train, util.vector().centerOf(YARD_BEARING));
        awake(scene, util, YARD_BLAZE);
        scene.idle(10);
        scene.world().moveSection(train, util.vector().of(DRIVE_IN, 0, 0), DRIVE_TICKS);
        scene.world().animateBogey(new BlockPos(9, 5, 11), -(float) DRIVE_IN, DRIVE_TICKS);
        scene.world().animateBogey(new BlockPos(13, 5, 11), -(float) DRIVE_IN, DRIVE_TICKS);
        scene.overlay().showText(80)
                .text("A Train sent to that name takes a station that is free")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(YARD_BEARING.above(2)));
        scene.idle(DRIVE_TICKS + 40);

        startTurn(scene, util, YARD_DRIVE, -90, QUARTER_TICKS, platform, train);
        scene.overlay().showText(80)
                .text("The Turntable turns it toward the leg that has a free station")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(YARD_BEARING.above(2)));
        scene.idle(QUARTER_TICKS);
        stopTurn(scene, util, YARD_DRIVE);
        scene.idle(60);

        scene.world().moveSection(train, util.vector().of(0, 0, EXIT), DRIVE_TICKS + 20);
        scene.world().animateBogey(new BlockPos(9, 5, 11), -(float) EXIT, DRIVE_TICKS + 20);
        scene.world().animateBogey(new BlockPos(13, 5, 11), -(float) EXIT, DRIVE_TICKS + 20);
        scene.idle(DRIVE_TICKS + 20);
        scene.world().animateTrainStation(STATION_SOUTH, true);
        startTurn(scene, util, YARD_DRIVE, 90, QUARTER_TICKS, platform);
        scene.overlay().showText(80)
                .text("The Train parks there and the plate returns to its first angle")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(STATION_SOUTH));
        scene.idle(QUARTER_TICKS);
        stopTurn(scene, util, YARD_DRIVE);
        scene.idle(60);
    }

    public static void maneuver(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = wide(builder, 23);
        scene.title("turntable_bearing_maneuver", "Leaving a Parking with Turntable Bearings");

        ElementLink<WorldSectionElement> platform = yard(scene, util);
        scene.world().animateTrainStation(STATION_SOUTH, true);
        ElementLink<WorldSectionElement> parked = scene.world().showIndependentSectionImmediately(
                util.select().fromTo(9, 5, 17, 13, 6, 17));
        scene.world().rotateSection(parked, 0, -90, 0, 0);
        scene.world().moveSection(parked, util.vector().of(0, -1, 2), 0);
        awake(scene, util, new BlockPos(12, 6, 17));
        scene.idle(30);

        scene.overlay().showText(80)
                .text("A Train parked on a dead end leg has to back out when its Schedule moves on")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(new BlockPos(11, 3, 19)));
        scene.idle(90);

        startTurn(scene, util, YARD_DRIVE, 90, QUARTER_TICKS, platform);
        scene.overlay().showText(60)
                .text("The plate turns to meet the leg the Train waits on")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(YARD_BEARING.above(2)));
        scene.idle(QUARTER_TICKS);
        stopTurn(scene, util, YARD_DRIVE);
        scene.idle(40);

        ScheduleGui book = ScheduleGui.open(ScheduleGui.travel("Parking"), ScheduleGui.maneuver(50),
                ScheduleGui.travel("Dock"));
        book.show(scene);
        scene.idle(10);
        scene.overlay().showText(80)
                .text("A Maneuver instruction goes into the Schedule between two destinations")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(YARD_BEARING.above(2)));
        book.mark(scene, 1);
        scene.idle(90);
        scene.overlay().showText(80)
                .text("Its speed is a share of full speed, up to 75 percent")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(YARD_BEARING.above(2)));
        scene.idle(90);
        book.mark(scene, 2);
        scene.overlay().showText(80)
                .text("The destination after it is where the Train maneuvers to, so it needs one")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(YARD_BEARING.above(2)));
        scene.idle(90);
        book.hide(scene);
        scene.idle(15);

        scene.overlay().showText(80)
                .text("The Train reverses out of the parking at that speed")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(new BlockPos(11, 3, 19)));
        scene.world().animateTrainStation(STATION_SOUTH, false);
        scene.world().moveSection(parked, util.vector().of(0, 0, -8), 70);
        scene.world().animateBogey(new BlockPos(9, 5, 17), 8f, 70);
        scene.world().animateBogey(new BlockPos(13, 5, 17), 8f, 70);
        scene.idle(80);

        scene.world().hideIndependentSection(parked, null);
        ElementLink<WorldSectionElement> train = scene.world().showIndependentSectionImmediately(
                util.select().fromTo(9, 5, 11, 13, 6, 11));
        scene.world().rotateSection(train, 0, -90, 0, 0);
        scene.world().moveSection(train, util.vector().of(0, -1, 0), 0);
        scene.world().configureCenterOfRotation(train, util.vector().centerOf(YARD_BEARING));
        awake(scene, util, YARD_BLAZE);
        scene.overlay().showText(80)
                .text("It stops centered on the plate")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(YARD_BEARING.above(2)));
        scene.idle(90);

        startTurn(scene, util, YARD_DRIVE, -90, QUARTER_TICKS, platform, train);
        scene.overlay().showText(80)
                .text("The plate takes the Train and turns it to face the leg it needs next")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(YARD_BEARING.above(2)));
        scene.idle(QUARTER_TICKS);
        stopTurn(scene, util, YARD_DRIVE);
        scene.idle(60);

        scene.world().moveSection(train, util.vector().of(-EXIT - 4, 0, 0), DRIVE_TICKS + 40);
        scene.world().animateBogey(new BlockPos(9, 5, 11), -(float) (EXIT + 4), DRIVE_TICKS + 40);
        scene.world().animateBogey(new BlockPos(13, 5, 11), -(float) (EXIT + 4), DRIVE_TICKS + 40);
        scene.idle(DRIVE_TICKS / 2);
        scene.world().hideIndependentSection(train, null);
        scene.overlay().showText(80)
                .text("Then it drives off forward toward its next stop")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(new BlockPos(3, 3, 11)));
        scene.idle(DRIVE_TICKS / 2 + 80);
    }

    private static ElementLink<WorldSectionElement> yard(CreateSceneBuilder scene, SceneBuildingUtil util) {
        scene.world().showSection(util.select().position(YARD_BEARING), Direction.DOWN);
        scene.world().showSection(chain(util, YARD_DRIVE), Direction.WEST);
        ElementLink<WorldSectionElement> platform = scene.world().showIndependentSectionImmediately(
                util.select().fromTo(7, 2, 11, 15, 4, 11));
        scene.world().moveSection(platform, util.vector().of(0, -1, 0), 0);
        scene.world().configureCenterOfRotation(platform, util.vector().centerOf(YARD_BEARING));
        scene.world().modifyBlocks(util.select().position(YARD_BEARING),
                state -> state.setValue(TurntableBearingBlock.ASSEMBLED, true), false);
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(0, 1, 11, 6, 3, 11), Direction.EAST);
        scene.world().showSection(util.select().fromTo(16, 1, 11, 22, 3, 11), Direction.WEST);
        scene.world().showSection(util.select().fromTo(11, 1, 0, 11, 3, 6), Direction.SOUTH);
        scene.world().showSection(util.select().fromTo(11, 1, 16, 11, 3, 22), Direction.NORTH);
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(22, 1, 8, 22, 3, 8), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(14, 1, 0, 14, 3, 0), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(14, 1, 22, 14, 3, 22), Direction.DOWN);
        scene.idle(10);
        return platform;
    }

    private static void awake(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos blaze) {
        scene.world().modifyBlocks(util.select().position(blaze),
                state -> state.setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.FADING), false);
        scene.world().conductorBlaze(blaze, true);
    }

    private static CreateSceneBuilder wide(SceneBuilder builder, int size) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.configureBasePlate(0, 0, size);
        scene.scaleSceneView(size > 17 ? .5f : .55f);
        scene.setSceneOffsetY(-1);
        scene.showBasePlate();
        scene.idle(5);
        return scene;
    }

    private static void assemble(CreateSceneBuilder scene, SceneBuildingUtil util,
                                 ElementLink<WorldSectionElement> structure) {
        scene.world().modifyBlocks(util.select().position(BEARING),
                state -> state.setValue(TurntableBearingBlock.ASSEMBLED, true), false);
        scene.world().showSectionAndMerge(util.select().position(PLATE), Direction.DOWN, structure);
    }

    @SafeVarargs
    private static void turn(CreateSceneBuilder scene, SceneBuildingUtil util, int degrees, int ticks,
                             ElementLink<WorldSectionElement>... parts) {
        turn(scene, util, SMALL_DRIVE, degrees, ticks, parts);
    }

    @SafeVarargs
    private static void turn(CreateSceneBuilder scene, SceneBuildingUtil util, Drive drive, int degrees, int ticks,
                             ElementLink<WorldSectionElement>... parts) {
        startTurn(scene, util, drive, degrees, ticks, parts);
        scene.idle(ticks);
        stopTurn(scene, util, drive);
    }

    @SafeVarargs
    private static void startTurn(CreateSceneBuilder scene, SceneBuildingUtil util, Drive drive, int degrees,
                                  int ticks, ElementLink<WorldSectionElement>... parts) {
        float rpm = Math.abs(degrees) / (0.3f * ticks);
        spin(scene, util, drive, degrees < 0 ? -rpm : rpm);
        for (ElementLink<WorldSectionElement> part : parts) {
            scene.world().rotateSection(part, 0, degrees, 0, ticks);
        }
    }

    private static void stopTurn(CreateSceneBuilder scene, SceneBuildingUtil util, Drive drive) {
        spin(scene, util, drive, 0f);
    }

    private static void signal(CreateSceneBuilder scene, SceneBuildingUtil util) {
        scene.overlay().showControls(util.vector().centerOf(BUTTON).add(0, -0.4, 0), Pointing.DOWN, 30).rightClick();
        scene.idle(7);
        scene.world().toggleRedstonePower(util.select().position(BUTTON));
        powered(scene, util, true);
        scene.effects().indicateRedstone(BUTTON);
        scene.idle(5);
        scene.world().toggleRedstonePower(util.select().position(BUTTON));
        powered(scene, util, false);
    }

    private static void spin(CreateSceneBuilder scene, SceneBuildingUtil util, Drive drive, float rpm) {
        scene.world().setKineticSpeed(util.select().position(drive.bearing()), rpm);
        scene.world().modifyBlockEntityNBT(util.select().position(drive.bearing()),
                TurntableBearingBlockEntity.class, tag -> {
                    CompoundTag cog = tag.getCompound("TurntableCog");
                    cog.putFloat("Speed", rpm);
                    tag.put("TurntableCog", cog);
                });
        for (int link = 0; link < drive.chain().length; link++) {
            scene.world().setKineticSpeed(util.select().position(drive.chain()[link]), link % 2 == 0 ? -rpm : rpm);
        }
    }

    private static void powered(CreateSceneBuilder scene, SceneBuildingUtil util, boolean on) {
        scene.world().modifyBlocks(util.select().position(BEARING),
                state -> state.setValue(TurntableBearingBlock.POWERED, on), false);
    }

    private static ElementLink<WorldSectionElement> standing(CreateSceneBuilder scene, SceneBuildingUtil util) {
        scene.world().showSection(util.select().position(BEARING), Direction.DOWN);
        scene.world().showSection(chain(util, SMALL_DRIVE), Direction.WEST);
        scene.world().showSection(util.select().position(BUTTON), Direction.EAST);
        ElementLink<WorldSectionElement> structure = scene.world().showIndependentSectionImmediately(
                util.select().fromTo(0, 2, 2, 4, 4, 2));
        scene.world().moveSection(structure, util.vector().of(0, -1, 0), 0);
        scene.world().configureCenterOfRotation(structure, util.vector().centerOf(TOP_CELL));
        scene.world().modifyBlocks(util.select().position(BEARING),
                state -> state.setValue(TurntableBearingBlock.ASSEMBLED, true), false);
        return structure;
    }

    private static Selection chain(SceneBuildingUtil util, Drive drive) {
        Selection selection = util.select().position(drive.chain()[0]);
        for (int link = 1; link < drive.chain().length; link++) {
            selection = selection.add(util.select().position(drive.chain()[link]));
        }
        return selection;
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

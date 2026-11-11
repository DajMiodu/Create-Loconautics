package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.common.turntable.sequence.TurntableSpeedModifier;
import com.lycoris.loconautics.content.turntablebearing.TurntableBearingBlock;
import com.lycoris.loconautics.content.turntablebearing.TurntableBearingBlockEntity;
import com.simibubi.create.AllItems;
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

    private static final float INPUT_RPM = 32f;
    private static final int QUARTER_TICKS = 30;

    private static final BlockPos BEARING = new BlockPos(2, 1, 2);
    private static final BlockPos PLATE = new BlockPos(2, 2, 2);
    private static final BlockPos BUTTON = new BlockPos(2, 1, 3);
    private static final BlockPos SMALL_COG = new BlockPos(3, 1, 2);
    private static final Vec3 VALUE_PANEL = new Vec3(2.5, 1.1875, 2.02);

    private static final BlockPos WIDE_BEARING = new BlockPos(4, 1, 2);
    private static final BlockPos WIDE_BOGEY = new BlockPos(2, 5, 2);
    private static final BlockPos WIDE_BOGEY_FAR = new BlockPos(6, 5, 2);
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

        scene.world().showSection(cogs(util), Direction.WEST);
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
        scene.world().showSection(util.select().position(3, 1, 2), Direction.WEST);
        ElementLink<WorldSectionElement> platform = scene.world().showIndependentSectionImmediately(
                util.select().fromTo(0, 2, 2, 8, 4, 2));
        scene.world().moveSection(platform, util.vector().of(0, -1, 0), 0);
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
        gui.dock(scene);
        scene.idle(10);

        scene.overlay().showText(80)
                .text("Rails on it meet the rails of the legs around it")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(new BlockPos(4, 3, 2)));
        scene.idle(90);

        ElementLink<WorldSectionElement> train = scene.world().showIndependentSectionImmediately(
                util.select().fromTo(WIDE_BOGEY, WIDE_BOGEY_FAR).add(util.select().position(2, 6, 2)));
        scene.world().moveSection(train, util.vector().of(DRIVE_IN, -1, 0), 0);
        scene.idle(15);
        scene.world().moveSection(train, util.vector().of(-DRIVE_IN, 0, 0), DRIVE_TICKS);
        scene.world().animateBogey(WIDE_BOGEY, (float) DRIVE_IN, DRIVE_TICKS);
        scene.world().animateBogey(WIDE_BOGEY_FAR, (float) DRIVE_IN, DRIVE_TICKS);
        scene.idle(DRIVE_TICKS + 10);
        scene.overlay().showText(80)
                .text("A Train whose Schedule needs the other leg parks centered on it")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(new BlockPos(4, 4, 2)));
        scene.idle(90);

        spin(scene, util, util.select().position(3, 1, 2), WIDE_BEARING, INPUT_RPM);
        scene.world().rotateSection(platform, 0, 90, 0, QUARTER_TICKS * 2);
        scene.world().rotateSection(train, 0, 90, 0, QUARTER_TICKS * 2);
        scene.idle(QUARTER_TICKS * 2);
        spin(scene, util, util.select().position(3, 1, 2), WIDE_BEARING, 0f);
        scene.overlay().showText(80)
                .text("It turns the Train to face that leg, no Redstone needed")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(new BlockPos(4, 4, 2)));
        scene.idle(90);

        scene.world().moveSection(train, util.vector().of(0, 0, EXIT), DRIVE_TICKS + 20);
        scene.world().animateBogey(WIDE_BOGEY, (float) EXIT, DRIVE_TICKS + 20);
        scene.world().animateBogey(WIDE_BOGEY_FAR, (float) EXIT, DRIVE_TICKS + 20);
        scene.idle(DRIVE_TICKS + 20);
        scene.world().hideIndependentSection(train, Direction.SOUTH);
        scene.idle(10);
        spin(scene, util, util.select().position(3, 1, 2), WIDE_BEARING, -INPUT_RPM);
        scene.world().rotateSection(platform, 0, -90, 0, QUARTER_TICKS * 2);
        scene.idle(QUARTER_TICKS * 2);
        spin(scene, util, util.select().position(3, 1, 2), WIDE_BEARING, 0f);
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
        gui.hide(scene);
        scene.idle(15);
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
        spin(scene, util, degrees < 0 ? -INPUT_RPM : INPUT_RPM);
        for (ElementLink<WorldSectionElement> part : parts) {
            scene.world().rotateSection(part, 0, degrees, 0, ticks);
        }
        scene.idle(ticks);
        spin(scene, util, 0f);
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

    private static void spin(CreateSceneBuilder scene, SceneBuildingUtil util, float rpm) {
        spin(scene, util, cogs(util), BEARING, rpm);
    }

    private static void spin(CreateSceneBuilder scene, SceneBuildingUtil util, Selection cogs, BlockPos bearing,
                             float rpm) {
        scene.world().setKineticSpeed(cogs, rpm);
        scene.world().modifyBlockEntityNBT(util.select().position(bearing), TurntableBearingBlockEntity.class, tag -> {
            CompoundTag cog = tag.getCompound("TurntableCog");
            cog.putFloat("Speed", rpm);
            tag.put("TurntableCog", cog);
        });
    }

    private static void powered(CreateSceneBuilder scene, SceneBuildingUtil util, boolean on) {
        scene.world().modifyBlocks(util.select().position(BEARING),
                state -> state.setValue(TurntableBearingBlock.POWERED, on), false);
    }

    private static ElementLink<WorldSectionElement> standing(CreateSceneBuilder scene, SceneBuildingUtil util) {
        scene.world().showSection(util.select().position(BEARING), Direction.DOWN);
        scene.world().showSection(cogs(util), Direction.WEST);
        scene.world().showSection(util.select().position(BUTTON), Direction.EAST);
        ElementLink<WorldSectionElement> structure = scene.world().showIndependentSectionImmediately(
                util.select().fromTo(0, 2, 2, 4, 4, 2));
        scene.world().moveSection(structure, util.vector().of(0, -1, 0), 0);
        scene.world().modifyBlocks(util.select().position(BEARING),
                state -> state.setValue(TurntableBearingBlock.ASSEMBLED, true), false);
        return structure;
    }

    private static Selection cogs(SceneBuildingUtil util) {
        return util.select().position(SMALL_COG).add(util.select().position(4, 1, 3))
                .add(util.select().position(5, 0, 3));
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

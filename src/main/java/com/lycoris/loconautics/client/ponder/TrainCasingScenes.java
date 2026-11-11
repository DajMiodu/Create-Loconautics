package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.content.bearingaxle.BogeyShaft;
import com.lycoris.loconautics.content.casingpicker.BogeyChoice;
import com.lycoris.loconautics.content.casingpicker.BogeyItem;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import dev.simulated_team.simulated.index.SimItems;
import dev.simulated_team.simulated.ponder.SmoothMovementUtils;
import dev.simulated_team.simulated.ponder.instructions.CustomAnimateWorldSectionInstruction;
import dev.simulated_team.simulated.ponder.instructions.PullTheAssemblerKronkInstruction;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class TrainCasingScenes {

    private static final float SHAFT_RPM = 32f;

    private static final BlockPos STATION = new BlockPos(6, 1, 1);
    private static final BlockPos BOGEY_A = new BlockPos(2, 2, 4);
    private static final BlockPos BOGEY_B = new BlockPos(5, 2, 4);
    private static final BlockPos CASING_A = new BlockPos(3, 2, 4);
    private static final BlockPos CASING_B = new BlockPos(4, 2, 4);
    private static final BlockPos ASSEMBLER = new BlockPos(3, 3, 4);

    private static final BlockPos AXLE_BOGEY = new BlockPos(3, 2, 2);

    public static void bogeys(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("train_casing_bogeys", "Building Trains with Train Casing");
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(.8f);
        scene.setSceneOffsetY(-1);
        scene.showBasePlate();
        scene.idle(5);

        for (int x = 6; x >= 0; x--) {
            scene.world().showSection(util.select().position(x, 1, 4), Direction.DOWN);
            scene.idle(1);
        }
        scene.world().showSection(util.select().position(STATION), Direction.DOWN);
        scene.idle(10);

        ItemStack casingStack = AllBlocks.RAILWAY_CASING.asStack();
        ItemStack bogeyStack = BogeyItem.of(BogeyChoice.CREATE_STANDARD, 1);
        Vec3 railTop = util.vector().topOf(BOGEY_A.below());

        ScrollMenuPonder menu = ScrollMenuPonder.open(casingStack, bogeyStack);
        menu.show(scene);
        scene.idle(20);
        scene.overlay().showControls(railTop, Pointing.DOWN, 40).withItem(casingStack);
        scene.overlay().showText(70)
                .text("Holding C with a Train Casing opens the selection menu")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(railTop);
        scene.idle(80);

        scene.overlay().showControls(railTop, Pointing.DOWN, 40).scroll();
        scene.idle(15);
        menu.select(scene, 1);
        scene.overlay().showText(70)
                .text("Scrolling swaps the Casing for a Bogey")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(railTop);
        scene.idle(80);
        menu.hide(scene);
        scene.idle(10);

        scene.overlay().showControls(railTop, Pointing.DOWN, 30).rightClick().withItem(bogeyStack);
        scene.idle(10);
        ElementLink<WorldSectionElement> body = scene.world()
                .showIndependentSection(util.select().position(BOGEY_A), Direction.DOWN);
        scene.overlay().showText(70)
                .text("Right click a Track to place the Bogey on it")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(BOGEY_A));
        scene.idle(30);
        scene.overlay().showControls(util.vector().topOf(BOGEY_B.below()), Pointing.DOWN, 30)
                .rightClick().withItem(bogeyStack);
        scene.idle(10);
        scene.world().showSectionAndMerge(util.select().position(BOGEY_B), Direction.DOWN, body);
        scene.idle(50);

        scene.overlay().showControls(util.vector().topOf(CASING_A), Pointing.DOWN, 30)
                .rightClick().withItem(casingStack);
        scene.idle(10);
        scene.world().showSectionAndMerge(util.select().position(CASING_A), Direction.DOWN, body);
        scene.idle(5);
        scene.world().showSectionAndMerge(util.select().position(CASING_B), Direction.DOWN, body);
        scene.overlay().showText(70)
                .text("Train Casings placed between the Bogeys form the body of the train")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(CASING_B));
        scene.idle(60);
        scene.world().showSectionAndMerge(util.select().position(ASSEMBLER), Direction.DOWN, body);
        scene.idle(20);

        Selection glued = util.select().fromTo(BOGEY_A, BOGEY_B).add(util.select().position(ASSEMBLER));
        scene.overlay().showControls(util.vector().centerOf(CASING_A), Pointing.LEFT, 25)
                .withItem(SimItems.HONEY_GLUE.asStack()).rightClick();
        scene.overlay().showOutline(PonderPalette.OUTPUT, "glue", glued, 110);
        scene.idle(20);
        scene.overlay().showText(80)
                .text("Glue the blocks together and hold right click on a Physics Assembler")
                .attachKeyFrame()
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget()
                .pointAt(util.vector().topOf(ASSEMBLER));
        scene.idle(60);
        scene.overlay().showControls(util.vector().centerOf(ASSEMBLER), Pointing.DOWN, 20).rightClick();
        scene.idle(3);
        scene.addInstruction(new PullTheAssemblerKronkInstruction(ASSEMBLER, true, false));
        scene.idle(10);
        scene.addInstruction(CustomAnimateWorldSectionInstruction.move(body, new Vec3(0, .25, 0), 6,
                SmoothMovementUtils.quadraticJump()));
        scene.idle(50);

        scene.world().animateTrainStation(STATION, true);
        scene.effects().indicateSuccess(STATION);
        scene.idle(10);
        scene.overlay().showText(80)
                .text("The assembled body counts as a Train and can dock at Stations")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(STATION));
        scene.idle(90);
    }

    public static void shaft(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("train_casing_shaft", "Passing Rotation through Bogeys");
        scene.configureBasePlate(1, 0, 5);
        scene.setSceneOffsetY(-1);
        scene.showBasePlate();
        scene.idle(5);

        for (int x = 5; x >= 1; x--) {
            scene.world().showSection(util.select().position(x, 1, 2), Direction.DOWN);
            scene.idle(1);
        }
        scene.world().showSection(util.select().position(AXLE_BOGEY), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showControls(util.vector().topOf(AXLE_BOGEY), Pointing.DOWN, 40)
                .rightClick().withItem(AllBlocks.SHAFT.asStack());
        scene.idle(25);
        scene.world().modifyBlocks(util.select().position(AXLE_BOGEY),
                state -> state.setValue(BogeyShaft.SHAFTED, true), false);
        scene.overlay().showText(70)
                .text("Bogeys can be fitted with a Shaft by right clicking them with one")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(AXLE_BOGEY));
        scene.idle(80);

        Selection driven = util.select().fromTo(1, 2, 2, 2, 2, 2)
                .add(util.select().position(4, 2, 2)).add(util.select().position(5, 2, 2));
        scene.world().showSection(util.select().position(0, 2, 2), Direction.EAST);
        scene.idle(5);
        scene.world().showSection(util.select().fromTo(1, 2, 2, 2, 2, 2), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().fromTo(4, 2, 2, 5, 2, 2), Direction.DOWN);
        scene.idle(10);
        scene.world().setKineticSpeed(driven.add(util.select().position(0, 2, 2)), SHAFT_RPM);
        scene.effects().rotationSpeedIndicator(new BlockPos(1, 2, 2));
        scene.overlay().showText(70)
                .text("Rotation from one side passes straight through, like an encased Shaft")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(AXLE_BOGEY));
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(AXLE_BOGEY), Pointing.DOWN, 40)
                .rightClick().whileSneaking().withItem(AllItems.WRENCH.asStack());
        scene.idle(25);
        scene.world().modifyBlocks(util.select().position(AXLE_BOGEY),
                state -> state.setValue(BogeyShaft.SHAFTED, false), false);
        scene.world().setKineticSpeed(util.select().fromTo(4, 2, 2, 5, 2, 2), 0);
        scene.overlay().showText(70)
                .text("Sneak and Right click with a Wrench to remove the Shaft again")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(AXLE_BOGEY));
        scene.idle(80);

        scene.overlay().showText(60)
                .text("Without the Shaft, rotation stops at the Bogey")
                .attachKeyFrame()
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .pointAt(util.vector().topOf(new BlockPos(4, 2, 2)));
        scene.idle(70);
    }
}

package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.content.combustion.SmallEngineBlockEntity;
import com.lycoris.loconautics.content.transmission.TransmissionBlock;
import com.lycoris.loconautics.content.transmission.TransmissionBlockEntity;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.trains.station.StationBlock;
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
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;

public class TrainScenes {

    private static final float ENGINE_RPM = 64f;
    private static final float AXLE_RPM = 256f;

    private static final BlockPos STATION = new BlockPos(4, 1, 1);
    private static final BlockPos BOGEY = new BlockPos(5, 2, 4);
    private static final BlockPos ENGINE = new BlockPos(9, 2, 4);
    private static final BlockPos LINK = new BlockPos(8, 2, 4);
    private static final BlockPos TRANSMISSION = new BlockPos(7, 2, 4);
    private static final BlockPos AXLE = new BlockPos(6, 2, 4);
    private static final BlockPos CONTROLLER = new BlockPos(3, 3, 4);

    public static void wheels(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "locomotive_wheels", "Setting up Bearing Axles");
        showTrack(scene, util);

        scene.world().showSection(floor(util), Direction.DOWN);
        scene.world().showSection(util.select().position(BOGEY), Direction.DOWN);
        scene.idle(15);
        scene.world().showSection(util.select().position(AXLE), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(70)
                .text("Bearing Axles drive the wheels of the bogey they sit on")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(AXLE));
        scene.idle(80);

        scene.overlay().showOutline(PonderPalette.GREEN, "bogey", util.select().position(BOGEY), 60);
        scene.overlay().showText(60)
                .text("They have to sit right beside a bogey, never above or below it")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().centerOf(BOGEY));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("The axle has to run along the same line as the bogey")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(AXLE, Direction.NORTH));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("A bogey can only be driven by one Bearing Axle")
                .attachKeyFrame()
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .pointAt(util.vector().topOf(AXLE));
        scene.idle(70);

        scene.overlay().showControls(util.vector().topOf(AXLE), Pointing.DOWN, 40)
                .rightClick()
                .withItem(AllItems.GOGGLES.asStack());
        scene.idle(25);
        scene.overlay().showText(70)
                .text("Engineer's Goggles show whether an axle is valid")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(AXLE));
        scene.idle(80);
    }

    public static void drivetrain(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "locomotive_drive", "Powering Locomotives with Transmissions");
        showTrack(scene, util);

        scene.world().showSection(floor(util), Direction.DOWN);
        scene.world().showSection(util.select().position(BOGEY), Direction.DOWN);
        scene.world().showSection(util.select().position(AXLE), Direction.DOWN);
        scene.idle(20);

        scene.world().showSection(util.select().position(TRANSMISSION), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(70)
                .text("A Transmission goes on the shaft end of the Bearing Axle")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(TRANSMISSION));
        scene.idle(80);

        scene.world().showSection(util.select().position(LINK), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(ENGINE), Direction.DOWN);
        scene.idle(10);
        engine(scene, util, true);
        feed(scene, util);
        scene.world().setKineticSpeed(util.select().fromTo(LINK, TRANSMISSION), ENGINE_RPM);
        scene.overlay().showText(70)
                .text("Rotational Force from an Engine enters on its other end")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(ENGINE));
        scene.idle(80);

        signal(scene, util, 15);
        scene.world().setKineticSpeed(util.select().position(AXLE), AXLE_RPM);
        scene.effects().rotationSpeedIndicator(AXLE);
        scene.overlay().showText(70)
                .text("A Speed signal decides how fast the Bearing Axle turns")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(AXLE));
        scene.idle(80);

        scene.overlay().showOutline(PonderPalette.GREEN, "line", util.select().fromTo(ENGINE, AXLE), 60);
        scene.overlay().showText(60)
                .text("All of them have to sit in one straight line")
                .placeNearTarget()
                .pointAt(util.vector().topOf(LINK));
        scene.idle(70);
    }

    public static void controls(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "locomotive_controls", "Controlling Locomotives with Analog Controllers");
        showTrack(scene, util);

        scene.world().showSection(floor(util), Direction.DOWN);
        scene.world().showSection(util.select().position(BOGEY), Direction.DOWN);
        scene.world().showSection(drivetrain(util), Direction.DOWN);
        scene.idle(15);
        engine(scene, util, true);
        feed(scene, util);
        scene.world().setKineticSpeed(util.select().fromTo(LINK, TRANSMISSION), ENGINE_RPM);
        scene.world().showSection(util.select().position(CONTROLLER), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70)
                .text("Analog Controllers send the signals that drive a locomotive")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(CONTROLLER));
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(CONTROLLER), Pointing.DOWN, 40)
                .rightClick()
                .whileSneaking()
                .withItem(new ItemStack(LoconauticsRegistries.TRANSMISSION_ITEM.get()));
        scene.idle(25);
        scene.effects().indicateSuccess(TRANSMISSION);
        scene.overlay().showText(80)
                .text("Right click a Controller while Sneaking to copy Speed and Direction")
                .attachKeyFrame()
                .colored(PonderPalette.INPUT)
                .placeNearTarget()
                .pointAt(util.vector().topOf(CONTROLLER));
        scene.idle(90);

        scene.overlay().showControls(util.vector().topOf(CONTROLLER), Pointing.DOWN, 40)
                .rightClick()
                .whileSneaking()
                .withItem(new ItemStack(LoconauticsRegistries.BEARING_AXLE_ITEM.get()));
        scene.idle(25);
        scene.effects().indicateSuccess(AXLE);
        scene.overlay().showText(70)
                .text("A Bearing Axle copies the Left and Right steering instead")
                .attachKeyFrame()
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget()
                .pointAt(util.vector().topOf(AXLE));
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(CONTROLLER).add(0, 1, 0), Pointing.DOWN, 40)
                .rightClick()
                .whileSneaking()
                .withItem(new ItemStack(LoconauticsRegistries.TRANSMISSION_ITEM.get()));
        scene.idle(25);
        scene.overlay().showText(70)
                .text("Sneak and Right click in the air to clear the copied frequencies")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(CONTROLLER).add(0, 1, 0));
        scene.idle(80);
    }

    public static void assembly(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "locomotive_assembly", "Assembling Locomotives");
        showTrack(scene, util);

        scene.world().showSection(util.select().position(STATION), Direction.DOWN);
        scene.idle(20);
        Selection locomotive = floor(util).add(util.select().position(BOGEY)).add(drivetrain(util))
                .add(util.select().position(CONTROLLER));
        ElementLink<WorldSectionElement> train = scene.world().showIndependentSection(locomotive, Direction.DOWN);
        feed(scene, util);
        scene.world().setKineticSpeed(util.select().fromTo(LINK, TRANSMISSION), ENGINE_RPM);
        scene.world().cycleBlockProperty(STATION, StationBlock.ASSEMBLING);
        scene.idle(15);

        scene.overlay().showText(70)
                .text("A locomotive needs a Bearing Axle and a Transmission on its bogey")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(AXLE));
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(STATION), Pointing.DOWN, 40).rightClick();
        scene.idle(25);
        scene.overlay().showText(70)
                .text("Open the Station and confirm the Assembly process")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(STATION));
        scene.idle(80);

        scene.world().cycleBlockProperty(STATION, StationBlock.ASSEMBLING);
        scene.effects().indicateSuccess(STATION);
        scene.world().animateTrainStation(STATION, true);
        scene.idle(20);

        scene.world().moveSection(train, util.vector().of(4, 0, 0), 30);
        scene.world().animateBogey(BOGEY, -4f, 30);
        scene.world().animateTrainStation(STATION, false);
        scene.idle(40);
        scene.overlay().showText(70)
                .text("The Analog Controller then drives the assembled train")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(CONTROLLER).add(4, 0, 0));
        scene.idle(80);

        scene.world().moveSection(train, util.vector().of(-4, 0, 0), 30);
        scene.world().animateBogey(BOGEY, 4f, 30);
        scene.idle(40);
        scene.world().animateTrainStation(STATION, true);
        scene.overlay().showText(70)
                .text("Trains can only be disassembled back into blocks at Stations")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(STATION));
        scene.idle(80);
    }

    private static Selection floor(SceneBuildingUtil util) {
        return util.select().fromTo(2, 2, 4, 4, 2, 4);
    }

    private static Selection drivetrain(SceneBuildingUtil util) {
        return util.select().fromTo(ENGINE, AXLE);
    }

    private static void feed(CreateSceneBuilder scene, SceneBuildingUtil util) {
        scene.world().modifyBlockEntityNBT(util.select().position(TRANSMISSION), TransmissionBlockEntity.class,
                tag -> tag.put("Source", NbtUtils.writeBlockPos(LINK)));
    }

    private static void signal(CreateSceneBuilder scene, SceneBuildingUtil util, int power) {
        scene.world().modifyBlockEntityNBT(util.select().position(TRANSMISSION), TransmissionBlockEntity.class,
                tag -> tag.putInt("RedstonePower", power));
        scene.world().modifyBlocks(util.select().position(TRANSMISSION),
                state -> state.setValue(TransmissionBlock.POWERED, power > 0), false);
    }

    private static void engine(CreateSceneBuilder scene, SceneBuildingUtil util, boolean running) {
        scene.world().modifyBlockEntityNBT(util.select().position(ENGINE), SmallEngineBlockEntity.class, tag -> {
            tag.putBoolean("Head", true);
            tag.putBoolean("Running", running);
            tag.putInt("Signal", 0);
            tag.putInt("Fuel", 0);
            tag.putFloat("OwnRpm", ENGINE_RPM);
            tag.putInt("Active", running ? 1 : 0);
            tag.putFloat("RowRpm", running ? ENGINE_RPM : 0f);
        });
        scene.world().setKineticSpeed(util.select().position(ENGINE), running ? ENGINE_RPM : 0f);
    }

    private static CreateSceneBuilder open(SceneBuilder builder, String id, String title) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(id, title);
        scene.configureBasePlate(1, 0, 9);
        scene.scaleSceneView(.75f);
        scene.setSceneOffsetY(-1);
        scene.showBasePlate();
        return scene;
    }

    private static void showTrack(CreateSceneBuilder scene, SceneBuildingUtil util) {
        for (int i = 10; i >= 0; i--) {
            scene.world().showSection(util.select().position(i, 1, 4), Direction.DOWN);
            scene.idle(1);
        }
    }
}

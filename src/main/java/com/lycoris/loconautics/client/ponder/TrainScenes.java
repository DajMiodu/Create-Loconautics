package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerMenu;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleMenu;
import com.lycoris.loconautics.content.transmission.TransmissionBlock;
import com.lycoris.loconautics.content.transmission.TransmissionBlockEntity;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.trains.station.StationBlock;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackShape;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlockEntity;
import dev.simulated_team.simulated.service.SimItemService;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class TrainScenes {

    private static final float ENGINE_RPM = 32f;
    private static final float AXLE_RPM = 256f;
    private static final double SHIFT = -1.5;
    private static final int SLIDE = 40;

    private static final BlockPos STATION = new BlockPos(8, 1, 1);
    private static final BlockPos BOGEY = new BlockPos(7, 2, 4);
    private static final BlockPos AXLE = new BlockPos(6, 2, 4);
    private static final BlockPos TRANSMISSION = new BlockPos(5, 2, 4);
    private static final BlockPos ENGINE = new BlockPos(4, 2, 4);
    private static final BlockPos BOGEY_FAR = new BlockPos(3, 2, 4);
    private static final BlockPos CONTROLLER = new BlockPos(7, 3, 4);
    private static final BlockPos STATION_FAR = new BlockPos(48, 1, 1);

    public static void wheels(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "locomotive_wheels", "Setting up Bearing Axles");
        showTrack(scene, util);

        centered(scene, util, util.select().position(BOGEY));
        scene.idle(15);
        scene.overlay().showControls(util.vector().blockSurface(BOGEY, Direction.WEST).add(SHIFT, 0, 0), Pointing.LEFT, 40)
                .rightClick()
                .withItem(new ItemStack(LoconauticsRegistries.BEARING_AXLE_ITEM.get()));
        scene.idle(25);
        centered(scene, util, util.select().position(AXLE));
        scene.idle(10);
        scene.overlay().showText(70)
                .text("Bearing Axles drive the wheels of the bogey they sit beside")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(AXLE).add(SHIFT, 0, 0));
        scene.idle(80);

        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, "bogey", new AABB(BOGEY).move(SHIFT, 0, 0), 60);
        scene.overlay().showText(70)
                .text("They have to touch the bogey on its rail line, never above or below")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().centerOf(BOGEY).add(SHIFT, 0, 0));
        scene.idle(80);

        scene.overlay().showText(60)
                .text("A bogey can only be driven by one Bearing Axle")
                .attachKeyFrame()
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .pointAt(util.vector().topOf(AXLE).add(SHIFT, 0, 0));
        scene.idle(70);

        scene.overlay().showControls(util.vector().topOf(AXLE).add(SHIFT, 0, 0), Pointing.DOWN, 40)
                .rightClick()
                .withItem(AllItems.GOGGLES.asStack());
        scene.idle(25);
        scene.overlay().showText(70)
                .text("Engineer's Goggles show whether an axle is valid")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(AXLE).add(SHIFT, 0, 0));
        scene.idle(80);
    }

    public static void drivetrain(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "locomotive_drive", "Powering Locomotives with Transmissions");
        showTrack(scene, util);

        ElementLink<WorldSectionElement> pair = centered(scene, util, util.select().fromTo(BOGEY, AXLE));
        scene.idle(30);

        scene.world().moveSection(pair, util.vector().of(-SHIFT, 0, 0), 25);
        scene.world().animateBogey(BOGEY, (float) SHIFT, 25);
        scene.idle(35);

        scene.world().showSection(util.select().position(TRANSMISSION), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(70)
                .text("A Transmission goes on the shaft end of the Bearing Axle")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(TRANSMISSION));
        scene.idle(80);

        openHatch(scene);
        scene.world().showSection(util.select().position(ENGINE), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(BOGEY_FAR), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(70)
                .text("A Portable Engine provides the Rotational Force on the other side")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(ENGINE));
        scene.idle(80);

        scene.overlay().showControls(util.vector().centerOf(ENGINE), Pointing.DOWN, 30)
                .withItem(Items.COAL.getDefaultInstance());
        scene.idle(10);
        ignite(scene, util);
        scene.overlay().showText(70)
                .text("Burning fuel in the engine starts the rotation")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(ENGINE));
        scene.idle(80);

        scene.overlay().showText(70)
                .text("The Portable Engine is only a demonstration, any Rotational Force works")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(ENGINE));
        scene.idle(80);

        swapSource(scene, util, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        scene.overlay().showText(60)
                .text("A Creative Motor can power the Transmission as well")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(ENGINE));
        scene.idle(70);

        swapSource(scene, util, AllBlocks.WATER_WHEEL.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        scene.overlay().showText(60)
                .text("And so can a spinning Water Wheel")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(ENGINE));
        scene.idle(70);

        swapSource(scene, util, engineState());
        lit(scene, util);
        scene.idle(20);

        signal(scene, util, 15);
        scene.world().setKineticSpeed(util.select().position(AXLE), AXLE_RPM);
        scene.effects().rotationSpeedIndicator(AXLE);
        scene.overlay().showText(70)
                .text("A Speed signal decides how fast the Bearing Axle turns")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(AXLE));
        scene.idle(80);
    }

    public static void frequency(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "locomotive_frequency", "Binding Bearing Axles to Redstone Links");
        showTrack(scene, util);

        centered(scene, util, util.select().fromTo(BOGEY, AXLE));
        scene.idle(20);

        GuiPonder.Panel panel = GuiPonder.open(scene, GuiPonder.TWO_ROWS,
                Component.translatable(BearingAxleMenu.LEFT_KEY),
                Component.translatable(BearingAxleMenu.RIGHT_KEY));

        scene.overlay().showControls(util.vector().topOf(AXLE).add(SHIFT, 0, 0), Pointing.DOWN, 30).rightClick();
        scene.idle(10);
        scene.overlay().showText(50)
                .text("Right click it with empty hands to open its Interface")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(AXLE).add(SHIFT, 0, 0));
        scene.idle(55);

        panel.show(scene);
        scene.idle(20);
        panel.mark(scene, 0);
        scene.overlay().showText(80)
                .text("The Left row is the frequency that steers the train left")
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(20);
        panel.put(scene, 0, new ItemStack(Items.COPPER_INGOT));
        scene.idle(20);
        panel.put(scene, 1, new ItemStack(Items.EMERALD));
        scene.idle(70);

        panel.mark(scene, 1);
        scene.overlay().showText(80)
                .text("The Right row is the frequency that steers it right")
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(20);
        panel.put(scene, 2, new ItemStack(Items.QUARTZ));
        scene.idle(20);
        panel.put(scene, 3, new ItemStack(Items.AMETHYST_SHARD));
        scene.idle(70);

        panel.mark(scene, -1);
        scene.overlay().showText(80)
                .text("Each frequency is made of two Items, like a Redstone Link")
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(90);
        panel.hide(scene);
        scene.idle(15);
    }

    public static void controls(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "locomotive_controls", "Controlling Locomotives with Analog Controllers");
        showTrack(scene, util);

        scene.world().showSection(util.select().position(BOGEY), Direction.DOWN);
        scene.world().showSection(drivetrain(util), Direction.DOWN);
        scene.world().showSection(util.select().position(BOGEY_FAR), Direction.DOWN);
        scene.idle(15);
        lit(scene, util);
        feed(scene, util);
        scene.world().setKineticSpeed(util.select().fromTo(ENGINE, TRANSMISSION), ENGINE_RPM);
        scene.world().showSection(util.select().position(CONTROLLER), Direction.DOWN);
        scene.idle(15);

        GuiPonder.Panel panel = GuiPonder.open(scene, GuiPonder.FOUR_ROWS,
                Component.translatable(AnalogControllerMenu.FORWARD_KEY),
                Component.translatable(AnalogControllerMenu.BACKWARD_KEY),
                Component.translatable(AnalogControllerMenu.LEFT_KEY),
                Component.translatable(AnalogControllerMenu.RIGHT_KEY));

        scene.overlay().showText(70)
                .text("Analog Controllers send the signals that drive a locomotive")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(CONTROLLER));
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(CONTROLLER), Pointing.DOWN, 30)
                .rightClick()
                .whileSneaking();
        scene.idle(10);
        scene.overlay().showText(50)
                .text("Sneak and Right click it to open its Interface")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(CONTROLLER));
        scene.idle(55);

        panel.show(scene);
        scene.idle(20);
        panel.mark(scene, 0);
        scene.overlay().showText(80)
                .text("Speed and Direction are the frequencies the Transmission listens to")
                .attachKeyFrame()
                .colored(PonderPalette.INPUT)
                .placeNearTarget();
        scene.idle(20);
        panel.put(scene, 0, new ItemStack(Items.GOLD_INGOT));
        panel.put(scene, 1, new ItemStack(Items.REDSTONE));
        scene.idle(15);
        panel.mark(scene, 1);
        panel.put(scene, 2, new ItemStack(Items.IRON_INGOT));
        panel.put(scene, 3, new ItemStack(Items.LAPIS_LAZULI));
        scene.idle(60);

        panel.mark(scene, 2);
        scene.overlay().showText(80)
                .text("Left and Right are the frequencies the Bearing Axle listens to")
                .attachKeyFrame()
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget();
        scene.idle(20);
        panel.put(scene, 4, new ItemStack(Items.COPPER_INGOT));
        panel.put(scene, 5, new ItemStack(Items.EMERALD));
        scene.idle(15);
        panel.mark(scene, 3);
        panel.put(scene, 6, new ItemStack(Items.QUARTZ));
        panel.put(scene, 7, new ItemStack(Items.AMETHYST_SHARD));
        scene.idle(60);

        panel.mark(scene, -1);
        panel.hide(scene);
        scene.idle(15);

        scene.overlay().showControls(util.vector().topOf(CONTROLLER), Pointing.DOWN, 40)
                .rightClick()
                .whileSneaking()
                .withItem(new ItemStack(LoconauticsRegistries.TRANSMISSION_ITEM.get()));
        scene.idle(25);
        scene.effects().indicateSuccess(TRANSMISSION);
        scene.effects().indicateSuccess(AXLE);
        scene.overlay().showText(70)
                .text("Right click a Controller while Sneaking to copy them onto a block")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(CONTROLLER));
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
        CreateSceneBuilder scene = open(builder, "locomotive_assembly", "Assembling Locomotives", false);

        Selection groundBlocks = util.select().fromTo(1, 0, 0, 9, 0, 8).add(util.select().fromTo(0, 1, 4, 9, 1, 4));
        ElementLink<WorldSectionElement> ground = scene.world().showIndependentSection(groundBlocks, Direction.DOWN);
        scene.idle(15);
        scene.world().showSectionAndMerge(util.select().position(STATION), Direction.DOWN, ground);
        scene.idle(20);

        Selection locomotive = util.select().fromTo(BOGEY, BOGEY_FAR).add(util.select().position(CONTROLLER));
        lit(scene, util);
        feed(scene, util);
        scene.world().showIndependentSection(locomotive, Direction.DOWN);
        scene.world().setKineticSpeed(util.select().fromTo(ENGINE, TRANSMISSION), ENGINE_RPM);
        scene.idle(15);

        scene.overlay().showText(70)
                .text("A locomotive needs a Bearing Axle and a Transmission on its bogey")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(AXLE));
        scene.idle(80);

        StationGui gui = StationGui.open();
        scene.overlay().showControls(util.vector().topOf(STATION), Pointing.DOWN, 30).rightClick();
        scene.idle(10);
        scene.overlay().showText(50)
                .text("Right click the Station to open its Interface")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(STATION));
        scene.idle(55);

        gui.show(scene);
        scene.idle(20);
        scene.overlay().showText(70)
                .text("The New Train button switches the Station to Assembly mode")
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(30);
        gui.press(scene, true);
        scene.idle(10);
        gui.press(scene, false);
        gui.assembly(scene);
        scene.world().cycleBlockProperty(STATION, StationBlock.ASSEMBLING);
        scene.idle(50);

        scene.overlay().showText(70)
                .text("Assemble as Sable Train builds the locomotive as a physics train")
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(30);
        gui.press(scene, true);
        scene.idle(10);
        gui.press(scene, false);
        gui.hide(scene);
        scene.idle(45);

        scene.world().cycleBlockProperty(STATION, StationBlock.ASSEMBLING);
        scene.effects().indicateSuccess(STATION);
        scene.world().animateTrainStation(STATION, true);
        scene.idle(20);

        scene.world().setBlock(new BlockPos(10, 1, 4),
                AllBlocks.TRACK.getDefaultState().setValue(TrackBlock.SHAPE, TrackShape.XO), false);
        Selection strip = util.select().fromTo(10, 0, 0, 52, 0, 8)
                .add(util.select().fromTo(10, 1, 4, 52, 1, 4))
                .add(util.select().position(STATION_FAR));
        ElementLink<WorldSectionElement> far = scene.world().showIndependentSection(strip, null);
        scene.idle(5);
        scene.world().animateTrainStation(STATION, false);

        scene.overlay().showText(70)
                .text("Raising the Analog Controller output steps the train up to speed")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(CONTROLLER));
        slide(scene, util, ground, far, 8, 60);
        for (int level = 1; level <= 10; level++) {
            controllerPower(scene, util, level);
            scene.idle(6);
        }
        slide(scene, util, ground, far, 20, 70);
        for (int level = 11; level <= 15; level++) {
            controllerPower(scene, util, level);
            scene.idle(6);
        }
        scene.idle(40);
        scene.world().hideIndependentSection(ground, null);

        scene.overlay().showText(70)
                .text("Letting go steps the output and the train back down")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(CONTROLLER));
        slide(scene, util, null, far, 8, 70);
        for (int level = 14; level >= 4; level--) {
            controllerPower(scene, util, level);
            scene.idle(6);
        }
        scene.idle(4);

        scene.overlay().showText(70)
                .text("Hold Space near a Station to park the train on it")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(CONTROLLER));
        slide(scene, util, null, far, 4, 60);
        for (int level = 3; level >= 0; level--) {
            controllerPower(scene, util, level);
            scene.idle(5);
        }
        scene.idle(40);
        BlockPos parked = STATION_FAR.offset(-SLIDE, 0, 0);
        scene.world().animateTrainStation(STATION_FAR, true);
        scene.effects().indicateSuccess(parked);
        scene.idle(15);

        scene.overlay().showText(70)
                .text("A Physics Assembler can also turn a train back into blocks")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(parked));
        scene.idle(80);
    }

    private static void slide(CreateSceneBuilder scene, SceneBuildingUtil util,
                              ElementLink<WorldSectionElement> ground, ElementLink<WorldSectionElement> far,
                              int blocks, int ticks) {
        Vec3 offset = util.vector().of(-blocks, 0, 0);
        if (ground != null) {
            scene.world().moveSection(ground, offset, ticks);
        }
        scene.world().moveSection(far, offset, ticks);
        scene.world().animateBogey(BOGEY, -(float) blocks, ticks);
        scene.world().animateBogey(BOGEY_FAR, -(float) blocks, ticks);
    }

    private static void controllerPower(CreateSceneBuilder scene, SceneBuildingUtil util, int level) {
        scene.world().modifyBlockEntityNBT(util.select().position(CONTROLLER), AnalogControllerBlockEntity.class,
                tag -> tag.putInt("Power", level));
        scene.world().modifyBlockEntity(CONTROLLER, AnalogControllerBlockEntity.class,
                be -> be.animSpeed.startWithValue(level / 15.0));
    }

    private static ElementLink<WorldSectionElement> centered(CreateSceneBuilder scene, SceneBuildingUtil util, Selection selection) {
        ElementLink<WorldSectionElement> link = scene.world().showIndependentSection(selection, Direction.DOWN);
        scene.world().moveSection(link, util.vector().of(SHIFT, 0, 0), 0);
        return link;
    }

    private static Selection drivetrain(SceneBuildingUtil util) {
        return util.select().fromTo(AXLE, ENGINE);
    }

    private static void swapSource(CreateSceneBuilder scene, SceneBuildingUtil util, BlockState state) {
        scene.world().setBlock(ENGINE, state, true);
        scene.world().setKineticSpeed(util.select().fromTo(ENGINE, TRANSMISSION), ENGINE_RPM);
    }

    private static BlockState engineState() {
        BlockState state = BuiltInRegistries.BLOCK
                .get(ResourceLocation.fromNamespaceAndPath("simulated", "red_portable_engine")).defaultBlockState();
        return state.getProperties().stream().filter(property -> property.getName().equals("facing"))
                .findFirst().map(property -> withValue(state, property, "east")).orElse(state);
    }

    private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> property, String name) {
        return property.getValue(name).map(value -> state.setValue(property, value)).orElse(state);
    }

    private static void feed(CreateSceneBuilder scene, SceneBuildingUtil util) {
        scene.world().modifyBlockEntityNBT(util.select().position(TRANSMISSION), TransmissionBlockEntity.class,
                tag -> tag.put("Source", NbtUtils.writeBlockPos(ENGINE)));
    }

    private static void signal(CreateSceneBuilder scene, SceneBuildingUtil util, int power) {
        scene.world().modifyBlockEntityNBT(util.select().position(TRANSMISSION), TransmissionBlockEntity.class,
                tag -> tag.putInt("RedstonePower", power));
        scene.world().modifyBlocks(util.select().position(TRANSMISSION),
                state -> state.setValue(TransmissionBlock.POWERED, power > 0), false);
    }

    private static void openHatch(CreateSceneBuilder scene) {
        scene.world().modifyBlockEntity(ENGINE, PortableEngineBlockEntity.class, be -> {
            be.openHatchOverride = true;
            be.hatchOpenTime = 1.0f;
            be.lastHatchOpenTime = 1.0f;
        });
    }

    private static void ignite(CreateSceneBuilder scene, SceneBuildingUtil util) {
        scene.world().cycleBlockProperty(ENGINE, AbstractFurnaceBlock.LIT);
        scene.world().modifyBlockEntity(ENGINE, PortableEngineBlockEntity.class, be -> {
            be.setCurrentBurnTime(SimItemService.INSTANCE.getBurnTime(Items.COAL.getDefaultInstance()));
            be.openHatchOverride = false;
        });
        feed(scene, util);
        scene.world().setKineticSpeed(util.select().fromTo(ENGINE, TRANSMISSION), ENGINE_RPM);
        scene.effects().emitParticles(util.vector().of(4.1, 2.2, 4.5),
                scene.effects().simpleParticleEmitter(ParticleTypes.LAVA, Vec3.ZERO), 3.0f, 1);
    }

    private static void lit(CreateSceneBuilder scene, SceneBuildingUtil util) {
        scene.world().modifyBlocks(util.select().position(ENGINE),
                state -> state.setValue(AbstractFurnaceBlock.LIT, true), false);
        scene.world().modifyBlockEntity(ENGINE, PortableEngineBlockEntity.class, be -> {
            be.setCurrentBurnTime(SimItemService.INSTANCE.getBurnTime(Items.COAL.getDefaultInstance()));
            be.openHatchOverride = false;
        });
    }

    private static CreateSceneBuilder open(SceneBuilder builder, String id, String title) {
        return open(builder, id, title, true);
    }

    private static CreateSceneBuilder open(SceneBuilder builder, String id, String title, boolean plate) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(id, title);
        scene.configureBasePlate(1, 0, 9);
        scene.scaleSceneView(.75f);
        scene.setSceneOffsetY(-1);
        if (plate) {
            scene.showBasePlate();
        }
        return scene;
    }

    private static void showTrack(CreateSceneBuilder scene, SceneBuildingUtil util) {
        for (int i = 9; i >= 0; i--) {
            scene.world().showSection(util.select().position(i, 1, 4), Direction.DOWN);
            scene.idle(1);
        }
    }
}

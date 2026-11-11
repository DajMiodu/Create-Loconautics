package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panellever.LeverPart;
import com.lycoris.loconautics.content.controls.panelswitch.SwitchPart;
import com.lycoris.loconautics.content.transmission.TransmissionBlock;
import com.lycoris.loconautics.content.transmission.TransmissionMenu;
import com.lycoris.loconautics.content.transmission.TransmissionBlockEntity;
import com.simibubi.create.content.redstone.analogLever.AnalogLeverBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TransmissionScenes {

    private static final float INPUT_RPM = 64f;

    private static final BlockPos TRANSMISSION = new BlockPos(2, 1, 2);
    private static final BlockPos SPEED_LEVER = new BlockPos(1, 1, 2);
    private static final BlockPos DIRECTION_LEVER = new BlockPos(3, 1, 2);

    private static final BlockPos CASING_LOW = new BlockPos(4, 1, 3);
    private static final BlockPos CASING_HIGH = new BlockPos(4, 2, 3);
    private static final BlockPos PANEL = new BlockPos(4, 2, 2);

    public static void single(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "transmission_single", "Relaying rotational force using Transmissions");

        scene.world().showSection(util.select().position(TRANSMISSION), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70)
                .text("Transmissions relay Rotational Force and set its speed from a signal")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(TRANSMISSION));
        scene.idle(80);

        scene.world().showSection(input(util), Direction.NORTH);
        feed(scene, util);
        scene.world().setKineticSpeed(util.select().position(TRANSMISSION).add(input(util)), INPUT_RPM);
        scene.idle(10);
        scene.overlay().showText(60)
                .text("Rotational Force enters through the shaft on one end")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(new BlockPos(2, 1, 4)));
        scene.idle(70);

        scene.world().showSection(output(util), Direction.SOUTH);
        scene.idle(10);
        scene.overlay().showText(60)
                .text("With no signal the output stays still")
                .placeNearTarget()
                .pointAt(util.vector().topOf(new BlockPos(2, 1, 1)));
        scene.idle(70);

        scene.world().showSection(util.select().position(SPEED_LEVER), Direction.EAST);
        scene.idle(10);
        scene.overlay().showText(70)
                .text("A Redstone signal on one side sets the output speed")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(SPEED_LEVER));
        scene.idle(80);

        speed(scene, util, 4);
        scene.effects().rotationSpeedIndicator(new BlockPos(2, 1, 1));
        scene.overlay().showText(60)
                .text("The output grows with the strength of the signal")
                .placeNearTarget()
                .pointAt(util.vector().topOf(new BlockPos(2, 1, 1)));
        scene.idle(70);

        speed(scene, util, 15);
        scene.effects().rotationSpeedIndicator(new BlockPos(2, 1, 1));
        scene.overlay().showText(60)
                .text("At full strength it turns at 256 RPM")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(new BlockPos(2, 1, 1)));
        scene.idle(70);
    }

    public static void direction(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "transmission_direction", "Reversing rotational force using Transmissions");

        scene.world().showSection(util.select().position(TRANSMISSION), Direction.DOWN);
        scene.world().showSection(input(util), Direction.NORTH);
        scene.world().showSection(output(util), Direction.SOUTH);
        scene.world().showSection(util.select().position(SPEED_LEVER), Direction.EAST);
        feed(scene, util);
        scene.world().setKineticSpeed(util.select().position(TRANSMISSION).add(input(util)), INPUT_RPM);
        scene.world().modifyBlockEntityNBT(util.select().position(SPEED_LEVER), AnalogLeverBlockEntity.class,
                nbt -> nbt.putInt("State", 8));
        drive(scene, util, 8, false);
        scene.idle(20);

        scene.world().showSection(util.select().position(DIRECTION_LEVER), Direction.WEST);
        scene.idle(10);
        scene.overlay().showText(70)
                .text("A signal on the opposite side reverses the output")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(DIRECTION_LEVER));
        scene.idle(80);

        scene.rotateCameraY(90);
        scene.idle(25);
        scene.overlay().showControls(util.vector().topOf(DIRECTION_LEVER), Pointing.DOWN, 30).rightClick();
        scene.idle(7);
        scene.world().toggleRedstonePower(util.select().position(DIRECTION_LEVER));
        scene.effects().indicateRedstone(DIRECTION_LEVER);
        drive(scene, util, 8, true);
        scene.effects().rotationDirectionIndicator(new BlockPos(2, 1, 1));
        scene.overlay().showText(60)
                .text("The speed stays the same while the direction flips")
                .placeNearTarget()
                .pointAt(util.vector().topOf(new BlockPos(2, 1, 1)));
        scene.idle(70);
    }

    public static void frequency(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "transmission_frequency", "Binding Transmissions to Redstone Links");

        scene.world().showSection(util.select().position(TRANSMISSION), Direction.DOWN);
        scene.world().showSection(input(util), Direction.NORTH);
        scene.world().showSection(output(util), Direction.SOUTH);
        feed(scene, util);
        scene.world().setKineticSpeed(util.select().position(TRANSMISSION).add(input(util)), INPUT_RPM);
        scene.idle(20);

        GuiPonder.Panel panel = GuiPonder.open(scene,
                Component.translatable(TransmissionMenu.SPEED_KEY),
                Component.translatable(TransmissionMenu.DIRECTION_KEY));

        scene.overlay().showControls(util.vector().topOf(TRANSMISSION), Pointing.DOWN, 40).rightClick();
        scene.idle(25);
        scene.overlay().showText(80)
                .text("Right click it with empty hands to open its Interface")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(TRANSMISSION));
        scene.idle(110);

        panel.show(scene);
        scene.idle(20);
        panel.mark(scene, 0);
        scene.overlay().showText(80)
                .text("The Speed row is the frequency that sets the output speed")
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(20);
        panel.put(scene, 0, new ItemStack(Items.GOLD_INGOT));
        scene.idle(20);
        panel.put(scene, 1, new ItemStack(Items.REDSTONE));
        scene.idle(70);

        panel.mark(scene, 1);
        scene.overlay().showText(80)
                .text("The Direction row is the frequency that reverses it")
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(20);
        panel.put(scene, 2, new ItemStack(Items.IRON_INGOT));
        scene.idle(20);
        panel.put(scene, 3, new ItemStack(Items.LAPIS_LAZULI));
        scene.idle(70);

        panel.mark(scene, -1);
        scene.overlay().showText(80)
                .text("Redstone Links on the same frequencies now control the Transmission")
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .placeNearTarget();
        scene.idle(90);
        panel.hide(scene);
        scene.idle(15);
    }

    public static void panel(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "transmission_panel", "Driving Transmissions from Panels");

        scene.world().showSection(util.select().position(TRANSMISSION), Direction.DOWN);
        scene.world().showSection(input(util), Direction.NORTH);
        scene.world().showSection(output(util), Direction.SOUTH);
        feed(scene, util);
        scene.world().setKineticSpeed(util.select().position(TRANSMISSION).add(input(util)), INPUT_RPM);
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(CASING_LOW, CASING_HIGH), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(PANEL), Direction.SOUTH);
        scene.idle(15);

        scene.overlay().showControls(util.vector().topOf(TRANSMISSION), Pointing.DOWN, 40)
                .rightClick()
                .whileSneaking()
                .withItem(new ItemStack(BoilerBlocks.GAUGE_LEVER_ITEM.get()));
        scene.idle(25);
        put(scene, PanelSlot.TOP_LEFT, lever(0));
        scene.overlay().showText(70)
                .text("Sneak and Right click a Transmission with a Gauge Lever to bind it")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(TRANSMISSION));
        scene.idle(80);

        put(scene, PanelSlot.TOP_LEFT, lever(8));
        drive(scene, util, 8, false);
        scene.effects().rotationSpeedIndicator(new BlockPos(2, 1, 1));
        scene.overlay().showText(60)
                .text("Dragging the lever sets the speed of the Transmission")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(PANEL, Direction.NORTH));
        scene.idle(70);

        scene.overlay().showControls(util.vector().topOf(TRANSMISSION), Pointing.DOWN, 40)
                .rightClick()
                .whileSneaking()
                .withItem(new ItemStack(BoilerBlocks.GAUGE_SWITCH_ITEM.get()));
        scene.idle(25);
        put(scene, PanelSlot.TOP_RIGHT, toggle(false));
        scene.overlay().showText(60)
                .text("A Gauge Switch bound the same way sets the direction")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(PANEL, Direction.NORTH));
        scene.idle(70);

        scene.overlay().showControls(util.vector().blockSurface(PANEL, Direction.NORTH), Pointing.RIGHT, 30)
                .rightClick();
        scene.idle(10);
        put(scene, PanelSlot.TOP_RIGHT, toggle(true));
        drive(scene, util, 8, true);
        scene.effects().rotationDirectionIndicator(new BlockPos(2, 1, 1));
        scene.idle(30);

        scene.overlay().showText(70)
                .text("Redstone signals are ignored while a panel drives it")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(TRANSMISSION));
        scene.idle(80);
    }

    private static Selection input(SceneBuildingUtil util) {
        return util.select().fromTo(2, 1, 3, 2, 1, 5);
    }

    private static Selection output(SceneBuildingUtil util) {
        return util.select().fromTo(2, 1, 0, 2, 1, 1);
    }

    private static void speed(CreateSceneBuilder scene, SceneBuildingUtil util, int strength) {
        scene.overlay().showControls(util.vector().centerOf(SPEED_LEVER).add(0, -0.25, 0), Pointing.DOWN, 30).rightClick();
        scene.idle(7);
        scene.world().modifyBlockEntityNBT(util.select().position(SPEED_LEVER), AnalogLeverBlockEntity.class,
                nbt -> nbt.putInt("State", strength));
        scene.effects().indicateRedstone(SPEED_LEVER);
        drive(scene, util, strength, false);
    }

    private static void feed(CreateSceneBuilder scene, SceneBuildingUtil util) {
        scene.world().modifyBlockEntityNBT(util.select().position(TRANSMISSION), TransmissionBlockEntity.class,
                tag -> tag.put("Source", NbtUtils.writeBlockPos(new BlockPos(2, 1, 3))));
    }

    private static void drive(CreateSceneBuilder scene, SceneBuildingUtil util, int power, boolean reversed) {
        float rpm = power == 0 ? 0f : (float) Math.ceil(power * 256.0 / 15.0);
        scene.world().modifyBlockEntityNBT(util.select().position(TRANSMISSION), TransmissionBlockEntity.class, tag -> {
            tag.putInt("RedstonePower", power);
            tag.putBoolean("DirectionActive", reversed);
        });
        scene.world().modifyBlocks(util.select().position(TRANSMISSION),
                state -> state.setValue(TransmissionBlock.POWERED, power > 0), false);
        scene.world().setKineticSpeed(output(util), reversed ? -rpm : rpm);
    }

    private static LeverPart lever(int power) {
        return new LeverPart(power, TRANSMISSION, LeverPart.DEFAULT_BODY, LeverPart.DEFAULT_KNOB_COLOR,
                LeverPart.DEFAULT_STYLE, LeverPart.DEFAULT_OPTIONS);
    }

    private static SwitchPart toggle(boolean on) {
        return new SwitchPart(on ? SwitchPart.MAX_POWER : 0, TRANSMISSION, SwitchPart.DEFAULT_BODY,
                SwitchPart.DEFAULT_KNOB_COLOR, SwitchPart.DEFAULT_STYLE, SwitchPart.DEFAULT_OPTIONS);
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
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);
        return scene;
    }
}

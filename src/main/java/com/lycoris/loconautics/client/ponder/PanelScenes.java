package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.content.controls.ControlsBlocks;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panelbutton.ButtonPart;
import com.lycoris.loconautics.content.controls.panellever.LeverPart;
import com.lycoris.loconautics.content.controls.panelswitch.SwitchPart;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

public class PanelScenes {

    private static final BlockPos CASING_LOW = new BlockPos(3, 1, 4);
    private static final BlockPos CASING_HIGH = new BlockPos(3, 2, 4);
    private static final BlockPos PANEL = new BlockPos(3, 2, 3);

    private static final BlockPos LEFT_LINK = new BlockPos(1, 1, 3);
    private static final BlockPos LEFT_LAMP = new BlockPos(1, 1, 2);
    private static final BlockPos RIGHT_LINK = new BlockPos(5, 1, 3);
    private static final BlockPos RIGHT_LAMP = new BlockPos(5, 1, 2);

    public static void parts(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "panel_parts", "The Instrument Panel");

        scene.world().showSection(util.select().fromTo(CASING_LOW, CASING_HIGH), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(PANEL), Direction.SOUTH);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .pointAt(util.vector().blockSurface(PANEL, Direction.NORTH))
                .placeNearTarget()
                .text("Gauges, valves, buttons, levers and switches all live in the same block, a panel with four quadrants");
        scene.idle(90);

        addButton(scene, PanelSlot.TOP_LEFT, LEFT_LINK);
        scene.idle(15);
        addSwitch(scene, PanelSlot.TOP_RIGHT, RIGHT_LINK, false);
        scene.idle(15);
        addLever(scene, PanelSlot.BOTTOM_LEFT, RIGHT_LINK, 0);
        scene.idle(15);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .pointAt(util.vector().blockSurface(PANEL, Direction.NORTH))
                .placeNearTarget()
                .text("Aim at a free quadrant and the next part lands there. Aim at the middle of a bare face and the first part sits centered instead, until a second one moves it into a corner");
        scene.idle(100);

        scene.overlay().showControls(util.vector().blockSurface(PANEL, Direction.NORTH), Pointing.RIGHT, 40)
                .rightClick()
                .withItem(AllItems.WRENCH.asStack());
        scene.idle(25);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .pointAt(util.vector().blockSurface(PANEL, Direction.NORTH))
                .placeNearTarget()
                .text("A wrench configures whichever part you are pointing at, and every part carries its own body and color choices");
        scene.idle(100);

        scene.overlay().showControls(util.vector().blockSurface(PANEL, Direction.NORTH), Pointing.RIGHT, 40)
                .rightClick()
                .whileSneaking()
                .withItem(AllItems.WRENCH.asStack());
        scene.idle(25);
        scene.world().modifyBlockEntity(PANEL, PanelBlockEntity.class,
                be -> be.removePart(PanelSlot.BOTTOM_LEFT));
        scene.idle(15);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .pointAt(util.vector().blockSurface(PANEL, Direction.NORTH))
                .placeNearTarget()
                .text("Sneak with the wrench to take one part back. Breaking the panel with parts still in it takes them one at a time, the block only goes when the last one leaves");
        scene.idle(100);
    }

    public static void controls(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "panel_controls", "Panel Controls");

        scene.world().showSection(util.select().fromTo(CASING_LOW, CASING_HIGH), Direction.DOWN);
        scene.world().showSection(util.select().position(PANEL), Direction.SOUTH);
        scene.world().showSection(util.select().fromTo(LEFT_LAMP, LEFT_LINK), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(RIGHT_LAMP, RIGHT_LINK), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showControls(util.vector().blockSurface(LEFT_LINK, Direction.NORTH), Pointing.RIGHT, 40)
                .rightClick()
                .withItem(new ItemStack(ControlsBlocks.GAUGE_BUTTON_ITEM.get()));
        scene.idle(25);
        addButton(scene, PanelSlot.TOP_LEFT, LEFT_LINK);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .pointAt(util.vector().blockSurface(LEFT_LINK, Direction.NORTH))
                .placeNearTarget()
                .text("Bind a control to a Redstone Link receiver first. It has to be a receiver, a transmitter is not something a panel can drive");
        scene.idle(100);

        scene.overlay().showControls(util.vector().blockSurface(PANEL, Direction.NORTH), Pointing.RIGHT, 30)
                .rightClick();
        scene.idle(10);
        press(scene, util, LEFT_LAMP, true);
        scene.idle(20);
        press(scene, util, LEFT_LAMP, false);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .pointAt(util.vector().blockSurface(PANEL, Direction.NORTH))
                .placeNearTarget()
                .text("A panelbutton sends a full signal for one second");
        scene.idle(100);

        addSwitch(scene, PanelSlot.TOP_RIGHT, RIGHT_LINK, false);
        scene.idle(15);
        scene.overlay().showControls(util.vector().blockSurface(PANEL, Direction.NORTH), Pointing.RIGHT, 30)
                .rightClick();
        scene.idle(10);
        addSwitch(scene, PanelSlot.TOP_RIGHT, RIGHT_LINK, true);
        press(scene, util, RIGHT_LAMP, true);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .pointAt(util.vector().blockSurface(PANEL, Direction.NORTH))
                .placeNearTarget()
                .text("A switch is the same signal, held. It stays where you left it");
        scene.idle(100);

        addLever(scene, PanelSlot.BOTTOM_LEFT, LEFT_LINK, 0);
        scene.idle(15);
        for (int power = 3; power <= 15; power += 4) {
            addLever(scene, PanelSlot.BOTTOM_LEFT, LEFT_LINK, power);
            scene.idle(12);
        }
        scene.overlay().showOutline(PonderPalette.GREEN, PANEL, util.select().position(PANEL), 90);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .pointAt(util.vector().blockSurface(PANEL, Direction.NORTH))
                .placeNearTarget()
                .text("A panellever is analog. Hold the click and drag to sweep it anywhere from zero to fifteen");
        scene.idle(100);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .pointAt(util.vector().blockSurface(PANEL, Direction.NORTH))
                .placeNearTarget()
                .text("Two controls on one link do not fight. The strongest one wins, and the link falls back when it lets go");
        scene.idle(100);
    }

    private static void addButton(CreateSceneBuilder scene, PanelSlot slot, BlockPos link) {
        put(scene, slot, new ButtonPart(0L, link, ButtonPart.DEFAULT_BODY, ButtonPart.DEFAULT_CAP_COLOR,
                ButtonPart.DEFAULT_STYLE, ButtonPart.DEFAULT_OPTIONS));
    }

    private static void addSwitch(CreateSceneBuilder scene, PanelSlot slot, BlockPos link, boolean on) {
        put(scene, slot, new SwitchPart(on ? SwitchPart.MAX_POWER : 0, link,
                SwitchPart.DEFAULT_BODY, SwitchPart.DEFAULT_KNOB_COLOR, SwitchPart.DEFAULT_STYLE, SwitchPart.DEFAULT_OPTIONS));
    }

    private static void addLever(CreateSceneBuilder scene, PanelSlot slot, BlockPos link, int power) {
        put(scene, slot, new LeverPart(power, link, LeverPart.DEFAULT_BODY, LeverPart.DEFAULT_KNOB_COLOR, LeverPart.DEFAULT_STYLE, LeverPart.DEFAULT_OPTIONS));
    }

    private static void put(CreateSceneBuilder scene, PanelSlot slot,
                            com.lycoris.loconautics.content.controls.panel.PanelPart part) {
        scene.world().modifyBlockEntity(PANEL, PanelBlockEntity.class, be -> {
            if (be.hasPart(slot)) {
                be.replacePart(slot, part);
            } else {
                be.putPart(slot, part);
            }
        });
    }

    private static void press(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos lamp, boolean lit) {
        scene.world().modifyBlocks(util.select().position(lamp),
                state -> state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, lit),
                false);
        if (lit) scene.effects().indicateRedstone(lamp);
    }

    private static CreateSceneBuilder open(SceneBuilder builder, String id, String title) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(id, title);
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(0.85f);
        scene.showBasePlate();
        scene.idle(10);
        return scene;
    }
}

package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.client.ponder.PartGui.Region;
import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.content.controls.panelbutton.ButtonPart;
import com.lycoris.loconautics.content.controls.panellever.LeverPart;
import com.lycoris.loconautics.content.controls.panelswitch.SwitchPart;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public class PanelScenes {

    private static final BlockPos CASING_LOW = new BlockPos(2, 1, 3);
    private static final BlockPos CASING_HIGH = new BlockPos(2, 2, 3);
    private static final BlockPos PANEL = new BlockPos(2, 2, 2);

    private static final BlockPos LEFT_LAMP = new BlockPos(0, 1, 2);
    private static final BlockPos LEFT_LINK = new BlockPos(0, 2, 2);
    private static final BlockPos RIGHT_LAMP = new BlockPos(4, 1, 2);
    private static final BlockPos RIGHT_LINK = new BlockPos(4, 2, 2);

    private static final int CLICK_TICKS = 20;
    private static final int CLICK_LANDS = 8;

    public static void parts(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "panel_parts", "Using Instrument Panels");
        Vec3 face = util.vector().blockSurface(PANEL, Direction.NORTH);

        scene.world().showSection(util.select().fromTo(CASING_LOW, CASING_HIGH), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(PANEL), Direction.SOUTH);
        scene.overlay().showText(60)
                .text("Panels hold up to 4 parts, one in each quadrant")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(face);
        scene.idle(70);

        click(scene, face, new ItemStack(BoilerBlocks.GAUGE_LEVER_ITEM.get()), false,
                () -> putCentered(scene, PanelSlot.BOTTOM_LEFT, new LeverPart(0, LEFT_LINK, LeverPart.DEFAULT_BODY,
                        LeverPart.DEFAULT_KNOB_COLOR, LeverPart.DEFAULT_STYLE, LeverPart.DEFAULT_OPTIONS)));
        scene.overlay().showText(70)
                .text("A lone part sits centered until a second one moves it to a corner")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(face);
        scene.idle(80);

        click(scene, face, new ItemStack(BoilerBlocks.GAUGE_BUTTON_ITEM.get()), false,
                () -> addButton(scene, PanelSlot.TOP_LEFT, LEFT_LINK));
        click(scene, face, new ItemStack(BoilerBlocks.GAUGE_SWITCH_ITEM.get()), false,
                () -> addSwitch(scene, PanelSlot.TOP_RIGHT, RIGHT_LINK, false));
        scene.overlay().showText(60)
                .text("Aim at a free quadrant to place the next part there")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(face);
        scene.idle(70);

        click(scene, face, AllItems.WRENCH.asStack(), true,
                () -> scene.world().modifyBlockEntity(PANEL, PanelBlockEntity.class,
                        be -> be.removePart(PanelSlot.BOTTOM_LEFT)));
        scene.overlay().showText(60)
                .text("Sneak and right click with a Wrench to take a part back")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(face);
        scene.idle(70);

        scene.overlay().showControls(face, Pointing.RIGHT, 50).leftClick();
        scene.idle(15);
        scene.world().modifyBlockEntity(PANEL, PanelBlockEntity.class, be -> be.removePart(PanelSlot.TOP_LEFT));
        scene.idle(15);
        scene.world().modifyBlockEntity(PANEL, PanelBlockEntity.class, be -> be.removePart(PanelSlot.TOP_RIGHT));
        scene.idle(25);
        scene.overlay().showText(60)
                .text("Breaking the panel takes its parts out one at a time")
                .placeNearTarget()
                .pointAt(face);
        scene.idle(70);
    }

    public static void controls(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "panel_controls", "Controlling Redstone with Panels");
        Vec3 face = util.vector().blockSurface(PANEL, Direction.NORTH);
        Vec3 leftLinkTop = util.vector().blockSurface(LEFT_LINK, Direction.UP);

        scene.world().showSection(util.select().fromTo(CASING_LOW, CASING_HIGH), Direction.DOWN);
        scene.world().showSection(util.select().position(PANEL), Direction.SOUTH);
        scene.world().showSection(util.select().fromTo(LEFT_LAMP, LEFT_LINK), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(RIGHT_LAMP, RIGHT_LINK), Direction.DOWN);
        scene.idle(15);

        click(scene, leftLinkTop, new ItemStack(BoilerBlocks.GAUGE_BUTTON_ITEM.get()), false,
                () -> addButton(scene, PanelSlot.TOP_LEFT, LEFT_LINK));
        scene.overlay().showText(60)
                .text("Controls are bound to a Redstone Link receiver first")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(leftLinkTop);
        scene.idle(70);

        click(scene, face, ItemStack.EMPTY, false, () -> press(scene, util, LEFT_LAMP, true));
        scene.idle(20);
        press(scene, util, LEFT_LAMP, false);
        scene.overlay().showText(60)
                .text("A Gauge Button sends a full signal for 1 second")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(face);
        scene.idle(70);

        click(scene, face, new ItemStack(BoilerBlocks.GAUGE_SWITCH_ITEM.get()), false,
                () -> addSwitch(scene, PanelSlot.TOP_RIGHT, RIGHT_LINK, false));
        click(scene, face, ItemStack.EMPTY, false, () -> {
            addSwitch(scene, PanelSlot.TOP_RIGHT, RIGHT_LINK, true);
            press(scene, util, RIGHT_LAMP, true);
        });
        scene.overlay().showText(60)
                .text("A Gauge Switch holds the signal until it is toggled")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(face);
        scene.idle(70);

        click(scene, face, new ItemStack(BoilerBlocks.GAUGE_LEVER_ITEM.get()), false,
                () -> addLever(scene, PanelSlot.BOTTOM_LEFT, LEFT_LINK, 0));
        scene.overlay().showControls(face, Pointing.RIGHT, 50).rightClick();
        for (int power = 3; power <= 15; power += 4) {
            addLever(scene, PanelSlot.BOTTOM_LEFT, LEFT_LINK, power);
            if (power == 3) press(scene, util, LEFT_LAMP, true);
            scene.idle(12);
        }
        scene.idle(5);
        scene.overlay().showText(60)
                .text("A Gauge Lever is analog, drag it from 0 to 15")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(face);
        scene.idle(70);

        click(scene, face, ItemStack.EMPTY, false, () -> scene.effects().indicateRedstone(LEFT_LINK));
        scene.overlay().showText(60)
                .text("Two controls on one link do not fight, the strongest wins")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(face);
        scene.idle(70);
    }

    public static void gui(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "panel_gui", "Setting up Panel Parts");
        Vec3 face = util.vector().blockSurface(PANEL, Direction.NORTH);
        PartGui gui = PartGui.open();

        showPlain(scene, util);
        click(scene, face, AllItems.WRENCH.asStack(), false, () -> {
        });
        scene.overlay().showText(50)
                .text("Using a Wrench on a part opens its Interface")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(face);
        scene.idle(55);

        gui.show(scene);
        scene.idle(20);
        gui.mark(scene, Region.LABEL);
        gui.label(scene, "Whistle");
        scene.overlay().showText(70)
                .text("The top bar holds a label for the part")
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(80);

        gui.mark(scene, Region.SIGNAL);
        scene.overlay().showText(80)
                .text("The Signal row takes 2 Items that set the Frequency it sends on")
                .attachKeyFrame()
                .placeNearTarget();
        gui.frequency(scene, 0, new ItemStack(Items.GOLD_INGOT));
        scene.idle(30);
        gui.frequency(scene, 1, new ItemStack(Items.REDSTONE));
        scene.idle(60);

        gui.mark(scene, Region.VIEW);
        scene.overlay().showText(70)
                .text("The preview shows the part as it will look")
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(80);

        gui.mark(scene, Region.STYLE);
        scene.overlay().showText(80)
                .text("The arrows and dots below it switch between styles")
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(20);
        gui.style(scene, 1);
        scene.idle(30);
        gui.style(scene, 0);
        scene.idle(40);

        gui.mark(scene);
        gui.hide(scene);
        scene.idle(15);
    }

    public static void look(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "panel_look", "Styling Panel Parts");
        Vec3 face = util.vector().blockSurface(PANEL, Direction.NORTH);
        PartGui gui = PartGui.open();
        int look = ButtonPart.DEFAULT_OPTIONS ^ (1 << PanelStyles.BUTTON_LIGHT) ^ (1 << PanelStyles.BUTTON_GLASS);

        showPlain(scene, util);
        click(scene, face, AllItems.WRENCH.asStack(), false, () -> {
        });
        gui.show(scene);
        scene.idle(20);

        gui.mark(scene, Region.BODY);
        scene.overlay().showText(70)
                .text("The Body column picks the casing of the part")
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(20);
        gui.body(scene, 1);
        scene.idle(60);

        gui.mark(scene, Region.COLOR);
        scene.overlay().showText(70)
                .text("The Color column paints the cap, knob or wheel")
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(20);
        gui.color(scene, 14);
        scene.idle(60);

        gui.mark(scene, Region.OPTIONS);
        scene.overlay().showText(80)
                .text("Toggles add a Plate, a Light or a Glass case")
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(20);
        gui.toggleOption(scene, PanelStyles.BUTTON_LIGHT);
        scene.idle(20);
        gui.toggleOption(scene, PanelStyles.BUTTON_GLASS);
        scene.idle(50);

        gui.mark(scene, Region.INVERT);
        scene.overlay().showText(80)
                .text("Invert swaps the signal, on sends off and off sends on")
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(20);
        gui.inverted(scene, true);
        scene.idle(70);

        gui.mark(scene, Region.CONFIRM);
        scene.overlay().showText(60)
                .text("Confirm applies the new look to the part")
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(70);

        gui.mark(scene);
        gui.hide(scene);
        scene.idle(15);
        scene.world().modifyBlockEntity(PANEL, PanelBlockEntity.class,
                be -> be.replacePart(PanelSlot.TOP_LEFT, new ButtonPart(0L, LEFT_LINK, 1, 14,
                        ButtonPart.DEFAULT_STYLE, look)));
        scene.idle(30);
    }

    public static void glass(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "panel_glass", "Protecting Panel Parts with Glass");
        Vec3 face = util.vector().blockSurface(PANEL, Direction.NORTH);
        int cased = ButtonPart.DEFAULT_OPTIONS | (1 << PanelStyles.BUTTON_GLASS);

        scene.world().showSection(util.select().fromTo(CASING_LOW, CASING_HIGH), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(PANEL), Direction.SOUTH);
        scene.world().showSection(util.select().fromTo(LEFT_LAMP, LEFT_LINK), Direction.DOWN);
        putCentered(scene, PanelSlot.TOP_LEFT, new ButtonPart(0L, LEFT_LINK, ButtonPart.DEFAULT_BODY,
                ButtonPart.DEFAULT_CAP_COLOR, ButtonPart.DEFAULT_STYLE, cased));
        scene.idle(20);

        click(scene, face, ItemStack.EMPTY, false, () -> {
        });
        scene.overlay().showText(60)
                .text("A closed Glass case keeps a part from being used")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(face);
        scene.idle(70);

        click(scene, face, ItemStack.EMPTY, true,
                () -> scene.world().modifyBlockEntity(PANEL, PanelBlockEntity.class,
                        be -> be.setGlassOpen(PanelSlot.TOP_LEFT, true)));
        scene.idle(15);
        scene.overlay().showText(60)
                .text("Sneak and right click to open the case")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(face);
        scene.idle(70);

        click(scene, face, ItemStack.EMPTY, false, () -> press(scene, util, LEFT_LAMP, true));
        scene.idle(20);
        press(scene, util, LEFT_LAMP, false);
        scene.overlay().showText(60)
                .text("Once the case is open the part works as usual")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(face);
        scene.idle(70);

        click(scene, face, ItemStack.EMPTY, true,
                () -> scene.world().modifyBlockEntity(PANEL, PanelBlockEntity.class,
                        be -> be.setGlassOpen(PanelSlot.TOP_LEFT, false)));
        scene.idle(15);
        scene.overlay().showText(60)
                .text("Sneak and right click again to close it")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(face);
        scene.idle(70);
    }

    private static void showPlain(CreateSceneBuilder scene, SceneBuildingUtil util) {
        scene.world().showSection(util.select().fromTo(CASING_LOW, CASING_HIGH), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(PANEL), Direction.SOUTH);
        putCentered(scene, PanelSlot.TOP_LEFT, new ButtonPart(0L, LEFT_LINK, ButtonPart.DEFAULT_BODY,
                ButtonPart.DEFAULT_CAP_COLOR, ButtonPart.DEFAULT_STYLE, ButtonPart.DEFAULT_OPTIONS));
        scene.idle(20);
    }

    private static void click(CreateSceneBuilder scene, Vec3 at, ItemStack item, boolean sneaking, Runnable change) {
        var hand = scene.overlay().showControls(at, Pointing.RIGHT, CLICK_TICKS).rightClick();
        if (sneaking) hand = hand.whileSneaking();
        if (!item.isEmpty()) hand = hand.withItem(item);
        scene.idle(CLICK_LANDS);
        change.run();
        scene.idle(CLICK_TICKS - CLICK_LANDS + 2);
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
        put(scene, slot, new LeverPart(power, link, LeverPart.DEFAULT_BODY, LeverPart.DEFAULT_KNOB_COLOR,
                LeverPart.DEFAULT_STYLE, LeverPart.DEFAULT_OPTIONS));
    }

    private static void putCentered(CreateSceneBuilder scene, PanelSlot slot, PanelPart part) {
        scene.world().modifyBlockEntity(PANEL, PanelBlockEntity.class, be -> be.putPart(slot, part, true));
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

    private static void press(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos lamp, boolean lit) {
        scene.world().modifyBlocks(util.select().position(lamp),
                state -> state.setValue(BlockStateProperties.LIT, lit), false);
        if (lit) scene.effects().indicateRedstone(lamp.above());
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

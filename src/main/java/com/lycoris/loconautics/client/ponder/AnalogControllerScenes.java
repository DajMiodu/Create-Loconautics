package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerMenu;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class AnalogControllerScenes {

    private static final int TOP_LEVEL = 15;
    private static final int RAISE_TICKS = 6;
    private static final int RELEASE_TICKS = 4;

    private static final BlockPos CONTROLLER = new BlockPos(2, 1, 2);

    public static void intro(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "analog_controller_intro", "Driving with Analog Controllers");

        scene.world().showSection(util.select().position(CONTROLLER), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70)
                .text("Analog Controllers send throttle and steering over Redstone Link frequencies")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(CONTROLLER));
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(CONTROLLER), Pointing.DOWN, 30).rightClick();
        scene.idle(10);
        scene.overlay().showText(60)
                .text("Right click one to take control of it")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(CONTROLLER));
        scene.idle(70);

        scene.overlay().showText(70)
                .text("Holding forward raises the output one level at a time")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(CONTROLLER));
        for (int level = 1; level <= TOP_LEVEL; level++) {
            power(scene, util, level);
            scene.idle(RAISE_TICKS);
        }
        scene.idle(10);

        scene.overlay().showText(60)
                .text("Letting go steps the output back down")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(CONTROLLER));
        for (int level = TOP_LEVEL - 1; level >= 0; level--) {
            power(scene, util, level);
            scene.idle(RELEASE_TICKS);
        }
        scene.idle(10);

        power(scene, util, 8);
        scene.overlay().showText(70)
                .text("Sprint locks the output where it is")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(CONTROLLER));
        scene.idle(80);

        scene.overlay().showText(70)
                .text("The mouse wheel sets the highest level the output can reach")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(CONTROLLER));
        scene.idle(80);
    }

    public static void frequency(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "analog_controller_frequency", "Binding Analog Controllers to Redstone Links");

        scene.world().showSection(util.select().position(CONTROLLER), Direction.DOWN);
        scene.idle(20);

        GuiPonder.Panel panel = GuiPonder.open(scene, GuiPonder.FOUR_ROWS,
                Component.translatable(AnalogControllerMenu.FORWARD_KEY),
                Component.translatable(AnalogControllerMenu.BACKWARD_KEY),
                Component.translatable(AnalogControllerMenu.LEFT_KEY),
                Component.translatable(AnalogControllerMenu.RIGHT_KEY));

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
                .text("The Speed row is the frequency sent while driving forward")
                .attachKeyFrame()
                .placeNearTarget();
        fill(scene, panel, 0, Items.GOLD_INGOT, Items.REDSTONE);

        panel.mark(scene, 1);
        scene.overlay().showText(80)
                .text("The Direction row is the one sent while driving backward")
                .attachKeyFrame()
                .placeNearTarget();
        fill(scene, panel, 1, Items.IRON_INGOT, Items.LAPIS_LAZULI);

        panel.mark(scene, 2);
        scene.overlay().showText(80)
                .text("The Left row is the frequency sent while steering left")
                .attachKeyFrame()
                .placeNearTarget();
        fill(scene, panel, 2, Items.COPPER_INGOT, Items.EMERALD);

        panel.mark(scene, 3);
        scene.overlay().showText(80)
                .text("The Right row is the frequency sent while steering right")
                .attachKeyFrame()
                .placeNearTarget();
        fill(scene, panel, 3, Items.QUARTZ, Items.AMETHYST_SHARD);

        panel.mark(scene, -1);
        panel.hide(scene);
        scene.idle(15);
    }

    private static void fill(CreateSceneBuilder scene, GuiPonder.Panel panel, int row,
                             net.minecraft.world.item.Item first, net.minecraft.world.item.Item second) {
        scene.idle(20);
        panel.put(scene, row * 2, new ItemStack(first));
        scene.idle(20);
        panel.put(scene, row * 2 + 1, new ItemStack(second));
        scene.idle(50);
    }

    private static void power(CreateSceneBuilder scene, SceneBuildingUtil util, int level) {
        scene.world().modifyBlockEntityNBT(util.select().position(CONTROLLER), AnalogControllerBlockEntity.class,
                tag -> tag.putInt("Power", level));
        scene.world().modifyBlockEntity(CONTROLLER, AnalogControllerBlockEntity.class,
                be -> be.animSpeed.startWithValue(level / (double) TOP_LEVEL));
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

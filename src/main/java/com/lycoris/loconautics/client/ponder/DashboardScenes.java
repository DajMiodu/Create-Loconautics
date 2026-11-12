package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panellever.LeverPart;
import com.lycoris.loconautics.content.controls.panelswitch.SwitchPart;
import com.lycoris.loconautics.content.dashboard.DashboardBlock;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

public class DashboardScenes {

    private static final BlockPos LEFT = new BlockPos(1, 1, 2);
    private static final BlockPos MIDDLE = new BlockPos(2, 1, 2);
    private static final BlockPos RIGHT = new BlockPos(3, 1, 2);
    private static final BlockPos LINK = new BlockPos(2, 1, 0);

    public static void desk(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("dashboard_desk", "Using Dashboards");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        scene.world().showSection(util.select().position(MIDDLE), Direction.DOWN);
        scene.world().showSection(util.select().position(LINK), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(60)
                .text("Dashboards are sloped desks that hold panel parts")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(MIDDLE));
        scene.idle(70);

        scene.overlay().showControls(util.vector().topOf(MIDDLE), Pointing.DOWN, 40)
                .rightClick()
                .withItem(new ItemStack(BoilerBlocks.GAUGE_LEVER_ITEM.get()));
        scene.idle(25);
        put(scene, MIDDLE, PanelSlot.TOP_LEFT, new LeverPart(0, LINK, LeverPart.DEFAULT_BODY,
                LeverPart.DEFAULT_KNOB_COLOR, LeverPart.DEFAULT_STYLE, LeverPart.DEFAULT_OPTIONS));
        scene.idle(15);
        put(scene, MIDDLE, PanelSlot.TOP_RIGHT, switchPart(0));
        scene.idle(20);
        put(scene, MIDDLE, PanelSlot.TOP_RIGHT, switchPart(SwitchPart.MAX_POWER));
        scene.world().modifyBlocks(util.select().position(LINK),
                state -> state.setValue(RedstoneLinkBlock.POWERED, true), false);
        scene.effects().indicateRedstone(LINK);
        scene.overlay().showText(60)
                .text("Each Dashboard holds 2 parts, one on each side")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(MIDDLE));
        scene.idle(70);

        scene.world().showSection(util.select().position(LEFT), Direction.EAST);
        scene.idle(5);
        scene.world().showSection(util.select().position(RIGHT), Direction.WEST);
        scene.idle(10);
        join(scene, util);
        scene.overlay().showText(70)
                .text("Dashboards placed side by side join into one desk of up to 5")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(MIDDLE));
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(MIDDLE), Pointing.DOWN, 40)
                .rightClick()
                .withItem(AllItems.WRENCH.asStack());
        scene.idle(25);
        scene.overlay().showText(60)
                .text("Using a Wrench configures the part it points at")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(MIDDLE));
        scene.idle(70);
    }

    private static void join(CreateSceneBuilder scene, SceneBuildingUtil util) {
        scene.world().modifyBlocks(util.select().position(LEFT),
                state -> state.setValue(DashboardBlock.JOIN_CW, true), false);
        scene.world().modifyBlocks(util.select().position(MIDDLE),
                state -> state.setValue(DashboardBlock.JOIN_CW, true).setValue(DashboardBlock.JOIN_CCW, true), false);
        scene.world().modifyBlocks(util.select().position(RIGHT),
                state -> state.setValue(DashboardBlock.JOIN_CCW, true), false);
    }

    private static SwitchPart switchPart(int power) {
        return new SwitchPart(power, LINK, SwitchPart.DEFAULT_BODY, SwitchPart.DEFAULT_KNOB_COLOR,
                SwitchPart.DEFAULT_STYLE, SwitchPart.DEFAULT_OPTIONS);
    }

    private static void put(CreateSceneBuilder scene, BlockPos pos, PanelSlot slot, PanelPart part) {
        scene.world().modifyBlockEntity(pos, PanelBlockEntity.class, be -> {
            if (be.hasPart(slot)) {
                be.replacePart(slot, part);
            } else {
                be.putPart(slot, part);
            }
        });
    }
}

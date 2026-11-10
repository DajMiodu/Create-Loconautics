package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class HoseScenes {

    private static final BlockPos CASING_LOW = new BlockPos(2, 1, 4);
    private static final BlockPos CASING_HIGH = new BlockPos(2, 2, 4);
    private static final BlockPos LEVER = new BlockPos(2, 3, 4);

    private static final BlockPos WINCH = new BlockPos(2, 2, 3);
    private static final BlockPos COGWHEEL = new BlockPos(1, 2, 3);

    private static final BlockPos PUMP = new BlockPos(2, 2, 2);
    private static final BlockPos TANK = new BlockPos(2, 1, 1);

    private static final BlockPos HATCH = new BlockPos(4, 1, 4);
    private static final BlockPos NOZZLE = new BlockPos(4, 2, 4);

    private static final BlockPos CAMPFIRE = new BlockPos(5, 1, 5);

    public static void winch(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "hose_winch", "The Hose Winch");

        scene.world().showSection(util.select().fromTo(CASING_LOW, CASING_HIGH), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(WINCH), Direction.SOUTH);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .pointAt(util.vector().blockSurface(WINCH, Direction.NORTH))
                .placeNearTarget()
                .text("A Hose Winch bolts flat against whatever face you click. Its reel axle always lies horizontal, whichever way it is mounted");
        scene.idle(90);

        scene.world().showSection(util.select().position(COGWHEEL), Direction.EAST);
        scene.world().setKineticSpeed(util.select().fromTo(COGWHEEL, WINCH), 32.0f);
        scene.effects().rotationSpeedIndicator(COGWHEEL);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .pointAt(util.vector().topOf(COGWHEEL))
                .placeNearTarget()
                .text("Rotation is the reel. One way pays hose out, the other hauls it back, and faster rotation moves it faster");
        scene.idle(100);

        scene.world().showSection(util.select().position(LEVER), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showControls(util.vector().blockSurface(LEVER, Direction.UP), Pointing.DOWN, 30)
                .rightClick();
        scene.idle(7);
        scene.world().toggleRedstonePower(util.select().fromTo(LEVER, WINCH));
        scene.effects().indicateRedstone(LEVER);
        scene.world().setKineticSpeed(util.select().fromTo(COGWHEEL, WINCH), 0.0f);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .pointAt(util.vector().blockSurface(WINCH, Direction.NORTH))
                .placeNearTarget()
                .text("A redstone signal locks the reel like a ratchet. The shaft can keep turning, the hose stays where it is");
        scene.idle(100);

        scene.overlay().showOutline(PonderPalette.RED, WINCH, util.select().position(WINCH), 80);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .pointAt(util.vector().blockSurface(WINCH, Direction.NORTH))
                .placeNearTarget()
                .text("Break the winch and the hose snaps, popping the Hose item back out where the winch stood. The winch owns the hose, the nozzle only holds the far end");
        scene.idle(100);
    }

    public static void connect(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "hose_connect", "Running a Hose");

        station(scene, util);
        scene.idle(15);

        scene.world().showSection(util.select().position(NOZZLE), Direction.DOWN);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .pointAt(util.vector().topOf(NOZZLE))
                .placeNearTarget()
                .text("A Hose Nozzle turns into its own physics body the moment it is placed. It falls, swings and is pushed around like any loose object");
        scene.idle(100);

        scene.overlay().showControls(util.vector().blockSurface(WINCH, Direction.NORTH), Pointing.RIGHT, 40)
                .rightClick()
                .withItem(new ItemStack(LoconauticsRegistries.HOSE.get()));
        scene.idle(25);
        scene.overlay().showControls(util.vector().topOf(NOZZLE), Pointing.DOWN, 40)
                .rightClick()
                .withItem(new ItemStack(LoconauticsRegistries.HOSE.get()));
        scene.idle(25);
        scene.overlay().showLine(PonderPalette.GREEN,
                util.vector().blockSurface(WINCH, Direction.NORTH), util.vector().topOf(NOZZLE), 90);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .pointAt(util.vector().topOf(NOZZLE))
                .placeNearTarget()
                .text("Click the winch with a Hose, then the nozzle, in either order. A dotted line previews the run while one end is selected");
        scene.idle(100);

        scene.overlay().showControls(util.vector().topOf(NOZZLE), Pointing.DOWN, 40)
                .rightClick()
                .whileSneaking()
                .withItem(new ItemStack(LoconauticsRegistries.HOSE.get()));
        scene.idle(25);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .pointAt(util.vector().topOf(NOZZLE))
                .placeNearTarget()
                .text("The preview goes orange while you are pointing at something that is not the other end, and red once you are past twenty eight blocks. Sneak click to drop the selection");
        scene.idle(100);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .pointAt(util.vector().blockSurface(WINCH, Direction.NORTH))
                .placeNearTarget()
                .text("The hose stretches a bit more than has been laid out, once it is taut it cannot stretch more, you can however pull more hose out from the winch");
        scene.idle(100);
    }

    public static void nozzle(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "hose_nozzle", "Handling the Nozzle");

        station(scene, util);
        scene.world().showSection(util.select().position(NOZZLE), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showControls(util.vector().topOf(NOZZLE), Pointing.DOWN, 40)
                .rightClick();
        scene.idle(25);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .pointAt(util.vector().topOf(NOZZLE))
                .placeNearTarget()
                .text("Right click it with an empty hand to take hold, and again to let go. Scroll to push it further out or pull it closer, and hold Tab while moving the mouse to turn it");
        scene.idle(100);

        scene.world().showSection(util.select().position(HATCH), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showOutline(PonderPalette.GREEN, HATCH, util.select().position(HATCH), 90);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .pointAt(util.vector().topOf(HATCH))
                .placeNearTarget()
                .text("Bring it near a Fluid Hatch and it docks itself, upright and locked to the socket. It does not have to be held for that");
        scene.idle(100);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .pointAt(util.vector().topOf(NOZZLE))
                .placeNearTarget()
                .text("Grabbing a docked nozzle pops it straight back out of the socket. Driving away does the same on its own once the hose goes taut, which is what saves a hose left plugged in");
        scene.idle(100);
    }

    public static void fluid(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "hose_fluid", "Pumping Through a Hose");

        station(scene, util);
        scene.world().showSection(util.select().position(NOZZLE), Direction.DOWN);
        scene.world().showSection(util.select().position(HATCH), Direction.DOWN);
        scene.idle(15);

        scene.world().showSection(util.select().fromTo(TANK, PUMP), Direction.SOUTH);
        scene.world().setKineticSpeed(util.select().position(PUMP), 64.0f);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .pointAt(util.vector().topOf(PUMP))
                .placeNearTarget()
                .text("A Mechanical Pump on the face opposite the one the winch is bolted to is what pressurizes the line. No pump, no flow, however full the tank is");
        scene.idle(100);

        scene.overlay().showLine(PonderPalette.BLUE,
                util.vector().topOf(PUMP), util.vector().topOf(NOZZLE), 90);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .pointAt(util.vector().blockSurface(WINCH, Direction.NORTH))
                .placeNearTarget()
                .text("Pump speed sets how far the pressure reaches, up to twenty eight blocks at Create's top rpm. A hose longer than that reach delivers nothing");
        scene.idle(100);

        scene.overlay().showOutline(PonderPalette.GREEN, HATCH, util.select().position(HATCH), 90);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .pointAt(util.vector().topOf(HATCH))
                .placeNearTarget()
                .text("Docked, everything the pump sends goes into the hatch's tank, and it can draw back the other way too");
        scene.idle(100);

        scene.world().modifyBlock(NOZZLE,
                state -> state.setValue(BlockStateProperties.POWERED, false), false);
        scene.effects().emitParticles(util.vector().topOf(NOZZLE),
                scene.effects().simpleParticleEmitter(ParticleTypes.FALLING_WATER,
                        util.vector().of(0.0, -0.15, 0.2)), 3.0f, 40);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .pointAt(util.vector().topOf(NOZZLE))
                .placeNearTarget()
                .text("Loose, it sprays instead, and the jet kicks back hard enough to shove the nozzle around");
        scene.idle(100);
    }

    public static void spray(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "hose_spray", "What the Spray Does");

        station(scene, util);
        scene.world().showSection(util.select().position(NOZZLE), Direction.DOWN);
        scene.world().modifyBlock(NOZZLE,
                state -> state.setValue(BlockStateProperties.POWERED, false), false);
        scene.world().showSection(util.select().position(CAMPFIRE), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showLine(PonderPalette.BLUE,
                util.vector().topOf(NOZZLE), util.vector().topOf(CAMPFIRE), 80);
        scene.idle(20);
        scene.world().modifyBlock(CAMPFIRE,
                state -> state.setValue(BlockStateProperties.LIT, false), true);
        scene.effects().emitParticles(util.vector().topOf(CAMPFIRE),
                scene.effects().simpleParticleEmitter(ParticleTypes.CLOUD,
                        util.vector().of(0.0, 0.1, 0.0)), 2.0f, 20);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .pointAt(util.vector().topOf(CAMPFIRE))
                .placeNearTarget()
                .text("Water puts fires out, soaks farmland to full and takes the burning off anything caught in the jet");
        scene.idle(100);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .pointAt(util.vector().topOf(NOZZLE))
                .placeNearTarget()
                .text("Lava does the opposite, lighting whatever surface the jet lands on and setting mobs alight");
        scene.idle(100);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .pointAt(util.vector().topOf(TANK))
                .placeNearTarget()
                .text("Potions carry their own effects to anyone sprayed, and honey bogs them down. The jet is colored from the fluid it is throwing");
        scene.idle(100);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .pointAt(util.vector().topOf(NOZZLE))
                .placeNearTarget()
                .text("Any other fluid is fair game. A datapack binds it to one of the named effects, or an addon registers its own");
        scene.idle(100);
    }

    private static void station(CreateSceneBuilder scene, SceneBuildingUtil util) {
        scene.world().showSection(util.select().fromTo(CASING_LOW, CASING_HIGH), Direction.DOWN);
        scene.world().showSection(util.select().position(WINCH), Direction.SOUTH);
        scene.world().showSection(util.select().position(COGWHEEL), Direction.EAST);
    }

    private static CreateSceneBuilder open(SceneBuilder builder, String id, String title) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(id, title);
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(0.75f);
        scene.showBasePlate();
        scene.idle(10);
        return scene;
    }
}

package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlocks;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class TrainPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return LoconauticsConstants.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(LoconauticsRegistries.TRANSMISSION.getId())
                .addStoryBoard("transmission/transmission", TransmissionScenes::single)
                .addStoryBoard("transmission/transmission", TransmissionScenes::direction)
                .addStoryBoard("transmission/transmission", TransmissionScenes::frequency)
                .addStoryBoard("transmission/transmission", TransmissionScenes::panel)
                .addStoryBoard("train/train", TrainScenes::drivetrain)
                .addStoryBoard("train/train", TrainScenes::controls)
                .addStoryBoard("train/train", TrainScenes::assembly);

        helper.forComponents(controllers())
                .addStoryBoard("analog_controller/analog_controller", AnalogControllerScenes::intro)
                .addStoryBoard("analog_controller/analog_controller", AnalogControllerScenes::frequency)
                .addStoryBoard("train/train", TrainScenes::controls);

        helper.forComponents(LoconauticsRegistries.TURNTABLE_BEARING.getId(), LoconauticsRegistries.TURNTABLE_PLATE.getId())
                .addStoryBoard("turntable/turntable", TurntableScenes::assembly)
                .addStoryBoard("turntable/turntable", TurntableScenes::instructions)
                .addStoryBoard("turntable/turntable", TurntableScenes::trigger)
                .addStoryBoard("turntable/turntable_schedule", TurntableScenes::schedule);

        helper.forComponents(LoconauticsRegistries.BEARING_AXLE.getId())
                .addStoryBoard("train/train", TrainScenes::wheels)
                .addStoryBoard("train/train", TrainScenes::frequency)
                .addStoryBoard("train/train", TrainScenes::drivetrain)
                .addStoryBoard("train/train", TrainScenes::controls)
                .addStoryBoard("train/train", TrainScenes::assembly);
    }

    private static List<ResourceLocation> controllers() {
        List<ResourceLocation> ids = new ArrayList<>();
        AnalogControllerBlocks.CONTROLLERS.values().forEach(holder -> ids.add(holder.getId()));
        return ids;
    }
}

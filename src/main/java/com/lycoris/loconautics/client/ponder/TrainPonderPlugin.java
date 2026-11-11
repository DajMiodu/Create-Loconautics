package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

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
                .addStoryBoard("transmission/transmission", TransmissionScenes::panel)
                .addStoryBoard("train/train", TrainScenes::drivetrain)
                .addStoryBoard("train/train", TrainScenes::controls)
                .addStoryBoard("train/train", TrainScenes::assembly);

        helper.forComponents(LoconauticsRegistries.BEARING_AXLE.getId())
                .addStoryBoard("train/train", TrainScenes::wheels)
                .addStoryBoard("train/train", TrainScenes::drivetrain)
                .addStoryBoard("train/train", TrainScenes::controls)
                .addStoryBoard("train/train", TrainScenes::assembly);
    }
}

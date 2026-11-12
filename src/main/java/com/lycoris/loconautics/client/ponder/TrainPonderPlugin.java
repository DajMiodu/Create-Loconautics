package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlocks;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.AllBlocks;
import dev.simulated_team.simulated.ponder.new_ponder_tooltip.NewPonderTooltipManager;
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
                .addStoryBoard("turntable/turntable_schedule", TurntableScenes::schedule)
                .addStoryBoard("turntable/turntable_diagonal", TurntableScenes::diagonal)
                .addStoryBoard("turntable/turntable_signal", TurntableScenes::signals)
                .addStoryBoard("turntable/turntable_yard", TurntableScenes::parking)
                .addStoryBoard("turntable/turntable_yard", TurntableScenes::maneuver);

        helper.forComponents(LoconauticsRegistries.BEARING_AXLE.getId())
                .addStoryBoard("train/train", TrainScenes::wheels)
                .addStoryBoard("train/train", TrainScenes::front)
                .addStoryBoard("train/train", TrainScenes::frequency)
                .addStoryBoard("train/train", TrainScenes::drivetrain)
                .addStoryBoard("train/train", TrainScenes::controls)
                .addStoryBoard("train/train", TrainScenes::assembly);

        registerCasing(helper);
    }

    private static void registerCasing(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(AllBlocks.RAILWAY_CASING.getId(), LoconauticsRegistries.BOGEY.getId())
                .addStoryBoard("train_casing/train_casing", TrainCasingScenes::bogeys);
        helper.forComponents(AllBlocks.RAILWAY_CASING.getId(), LoconauticsRegistries.BOGEY.getId(),
                        AllBlocks.SMALL_BOGEY.getId(), AllBlocks.LARGE_BOGEY.getId())
                .addStoryBoard("train_casing/bogey_shaft", TrainCasingScenes::shaft);
        NewPonderTooltipManager.forItems(AllBlocks.RAILWAY_CASING.asItem(), LoconauticsRegistries.BOGEY.get(),
                        AllBlocks.SMALL_BOGEY.asItem(), AllBlocks.LARGE_BOGEY.asItem())
                .addScenes(LoconauticsConstants.id("train_casing_bogeys"),
                        LoconauticsConstants.id("train_casing_shaft"));
    }

    private static List<ResourceLocation> controllers() {
        List<ResourceLocation> ids = new ArrayList<>();
        AnalogControllerBlocks.CONTROLLERS.values().forEach(holder -> ids.add(holder.getId()));
        return ids;
    }
}

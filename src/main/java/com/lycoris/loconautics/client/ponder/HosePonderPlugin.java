package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class HosePonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return LoconauticsConstants.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(LoconauticsRegistries.HOSE.getId())
                .addStoryBoard("hose/hose", HoseScenes::connect)
                .addStoryBoard("hose/hose", HoseScenes::fluid)
                .addStoryBoard("hose/rope_connector", HoseScenes::ropeConnector);

        helper.forComponents(LoconauticsRegistries.HOSE_WINCH.getId())
                .addStoryBoard("hose/hose", HoseScenes::winch)
                .addStoryBoard("hose/hose", HoseScenes::connect)
                .addStoryBoard("hose/hose", HoseScenes::fluid);

        helper.forComponents(LoconauticsRegistries.HOSE_NOZZLE.getId())
                .addStoryBoard("hose/hose", HoseScenes::nozzle)
                .addStoryBoard("hose/hose", HoseScenes::fluid)
                .addStoryBoard("hose/socket", HoseScenes::socket);

        helper.forComponents(LoconauticsRegistries.HOSE_CONNECTOR.getId())
                .addStoryBoard("hose/connector", HoseScenes::connector)
                .addStoryBoard("hose/rope_connector", HoseScenes::ropeConnector);

        helper.forComponents(LoconauticsConstants.id("fluid_hatch_gray"))
                .addStoryBoard("hose/socket", HoseScenes::socket)
                .addStoryBoard("hose/hose", HoseScenes::nozzle);

        helper.forComponents(ResourceLocation.fromNamespaceAndPath("simulated", "rope_connector"))
                .addStoryBoard("hose/rope_connector", HoseScenes::ropeConnector);
    }
}

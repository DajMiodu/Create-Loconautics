package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class BoilerPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return LoconauticsConstants.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(BoilerBlocks.FIREBOX.getId(), BoilerBlocks.FIREBOX_STOCKER.getId())
                .addStoryBoard("boiler/firebox", BoilerScenes::firebox)
                .addStoryBoard("boiler/barrel", BoilerScenes::assembly)
                .addStoryBoard("boiler/barrel", BoilerScenes::ratio)
                .addStoryBoard("boiler/barrel", BoilerScenes::failure);

        helper.forComponents(BoilerBlocks.BOILER_BODY.getId(),
                        com.lycoris.loconautics.registry.LoconauticsRegistries.SMOKESTACK.getId())
                .addStoryBoard("boiler/barrel", BoilerScenes::assembly)
                .addStoryBoard("boiler/water", BoilerScenes::water)
                .addStoryBoard("boiler/barrel", BoilerScenes::ratio)
                .addStoryBoard("boiler/barrel", BoilerScenes::smokestack)
                .addStoryBoard("boiler/barrel", BoilerScenes::takeoff);

        helper.forComponents(BoilerBlocks.STEAM_TAKEOFF.getId())
                .addStoryBoard("boiler/barrel", BoilerScenes::takeoff)
                .addStoryBoard("boiler/barrel", BoilerScenes::pressure);

        helper.forComponents(BoilerBlocks.SMART_SAFETY_VALVE.getId(),
                        BoilerBlocks.VALVE_ITEM.getId())
                .addStoryBoard("boiler/barrel", BoilerScenes::pressure)
                .addStoryBoard("boiler/barrel", BoilerScenes::failure);

        helper.forComponents(BoilerBlocks.LOCOMOTIVE_GAUGE.getId())
                .addStoryBoard("boiler/barrel", BoilerScenes::gauge)
                .addStoryBoard("panel/panel", PanelScenes::parts);

        helper.forComponents(BoilerBlocks.GAUGE_BUTTON_ITEM.getId(), BoilerBlocks.GAUGE_LEVER_ITEM.getId(),
                        BoilerBlocks.GAUGE_SWITCH_ITEM.getId())
                .addStoryBoard("panel/panel", PanelScenes::parts)
                .addStoryBoard("panel/panel", PanelScenes::controls);
    }
}

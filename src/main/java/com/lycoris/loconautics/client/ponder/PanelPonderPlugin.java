package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.controls.ControlsBlocks;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class PanelPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return LoconauticsConstants.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(ControlsBlocks.LOCOMOTIVE_GAUGE.getId())
                .addStoryBoard("panel", PanelScenes::parts);

        helper.forComponents(ControlsBlocks.GAUGE_BUTTON_ITEM.getId(), ControlsBlocks.GAUGE_LEVER_ITEM.getId(),
                        ControlsBlocks.GAUGE_SWITCH_ITEM.getId())
                .addStoryBoard("panel", PanelScenes::parts)
                .addStoryBoard("panel", PanelScenes::controls);
    }
}

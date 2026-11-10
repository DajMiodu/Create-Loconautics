package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.combustion.SmallEngineBlocks;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

import java.util.ArrayList;
import java.util.List;

public class SmallEnginePonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return LoconauticsConstants.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(engines())
                .addStoryBoard("small_engine", SmallEngineScenes::single)
                .addStoryBoard("small_engine_row", SmallEngineScenes::pipes)
                .addStoryBoard("small_engine_row", SmallEngineScenes::tandem)
                .addStoryBoard("small_engine_row", SmallEngineScenes::redstone)
                .addStoryBoard("small_engine_row", SmallEngineScenes::panel);
    }

    private static List<ResourceLocation> engines() {
        List<ResourceLocation> ids = new ArrayList<>();
        for (DyeColor color : DyeColor.values()) {
            ids.add(SmallEngineBlocks.ENGINES.get(color).getId());
        }
        return ids;
    }
}

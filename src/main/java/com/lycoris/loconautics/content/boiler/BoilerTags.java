package com.lycoris.loconautics.content.boiler;

import com.lycoris.loconautics.LoconauticsConstants;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class BoilerTags {

    public static final TagKey<Block> BOILER_FIREBOX = BlockTags.create(LoconauticsConstants.id("boiler_firebox"));

    public static final TagKey<Block> BOILER_BODY = BlockTags.create(LoconauticsConstants.id("boiler_body"));

    public static final TagKey<Block> SMOKESTACK = BlockTags.create(LoconauticsConstants.id("smokestack"));

    public static final TagKey<Block> HATCHES = BlockTags.create(LoconauticsConstants.id("hatches"));

    private BoilerTags() {}
}

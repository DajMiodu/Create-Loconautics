package com.lycoris.loconautics.content.dashboard;

import com.lycoris.loconautics.LoconauticsConstants;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class DashboardTags {

    public static final TagKey<Block> DASHBOARD = BlockTags.create(LoconauticsConstants.id("dashboard"));

    private DashboardTags() {}
}

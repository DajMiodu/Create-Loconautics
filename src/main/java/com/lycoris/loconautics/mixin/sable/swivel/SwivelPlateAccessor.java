package com.lycoris.loconautics.mixin.sable.swivel;

import dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlockEntity;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

@Mixin(value = SwivelBearingPlateBlockEntity.class, remap = false)
public interface SwivelPlateAccessor {

    @Accessor("parent")
    BlockPos loconautics$getParent();

    @Accessor("parentSubLevelId")
    UUID loconautics$getParentSubLevelId();
}

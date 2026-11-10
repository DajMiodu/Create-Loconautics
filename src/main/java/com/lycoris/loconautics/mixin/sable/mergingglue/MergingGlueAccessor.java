package com.lycoris.loconautics.mixin.sable.mergingglue;

import dev.simulated_team.simulated.content.blocks.merging_glue.MergingGlueBlockEntity;
import net.minecraft.world.level.block.Rotation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = MergingGlueBlockEntity.class, remap = false)
public interface MergingGlueAccessor {

    @Invoker("breakGlue")
    void loconautics$breakGlue();

    @Invoker("disassembleToPartner")
    void loconautics$disassembleToPartner(Rotation rotation);
}

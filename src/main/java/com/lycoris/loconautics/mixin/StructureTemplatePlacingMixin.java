package com.lycoris.loconautics.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.lycoris.loconautics.common.strand.StrandTemplates;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = StructureTemplate.class, remap = false)
public abstract class StructureTemplatePlacingMixin {

    @WrapMethod(method = "placeInWorld")
    private boolean loconautics$markPlacing(ServerLevelAccessor level, BlockPos pos, BlockPos pivot,
                                            StructurePlaceSettings settings, RandomSource random, int flags,
                                            Operation<Boolean> original) {
        StrandTemplates.beginPlacing();
        try {
            return original.call(level, pos, pivot, settings, random, flags);
        } finally {
            StrandTemplates.endPlacing();
        }
    }
}

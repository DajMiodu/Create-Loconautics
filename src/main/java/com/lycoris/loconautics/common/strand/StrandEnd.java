package com.lycoris.loconautics.common.strand;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public record StrandEnd(@Nullable UUID subLevelId, BlockPos local) {

    public boolean matches(@Nullable UUID sub, @Nullable BlockPos pos) {
        return Objects.equals(subLevelId, sub) && local.equals(pos);
    }
}

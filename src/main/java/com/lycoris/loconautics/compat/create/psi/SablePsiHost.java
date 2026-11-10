package com.lycoris.loconautics.compat.create.psi;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface SablePsiHost {

    void loconautics$link(@Nullable UUID partnerSubLevel, BlockPos partnerPos);

    void loconautics$unlink();

    boolean loconautics$linked();

    @Nullable
    UUID loconautics$partnerSubLevel();

    @Nullable
    BlockPos loconautics$partnerPos();

    void loconautics$setDistance(float distance);

    float loconautics$distance();
}

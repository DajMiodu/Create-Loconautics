package com.lycoris.loconautics.common.turntable;

import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface Turntable {

    UUID plateSubLevelId();

    Vec3 turntableAxisWorld();

    Vec3 turntableAxisNormal();

    boolean turntablePowered();

    boolean turntableScheduleDriven();

    void turntableAlignTo(TrackNodeLocation movingHead, TrackNodeLocation target);

    void turntableClearAlignment();

    @Nullable
    TrackNodeLocation turntableAlignTarget();

    boolean turntableAligned();

    boolean turntableSettled();
}

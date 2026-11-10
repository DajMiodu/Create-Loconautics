package com.lycoris.loconautics.common.sable;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record BodyRef(@Nullable ServerSubLevel sub, boolean absent) {

    private static final BodyRef WORLD = new BodyRef(null, false);
    private static final BodyRef ABSENT = new BodyRef(null, true);

    public static BodyRef of(@Nullable ServerLevel level, @Nullable UUID id) {
        if (id == null) {
            return WORLD;
        }
        ServerSubLevel resolved = SubLevelData.resolve(level, id);
        return resolved == null ? ABSENT : new BodyRef(resolved, false);
    }

    public static BodyRef ofBody(@Nullable ServerSubLevel sub) {
        if (sub == null) {
            return WORLD;
        }
        return sub.isRemoved() ? ABSENT : new BodyRef(sub, false);
    }
}

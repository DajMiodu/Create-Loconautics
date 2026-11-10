package com.lycoris.loconautics.compat.create.patch;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.UUID;

public final class FakePloughPlayer extends FakePlayer {

    private static final GameProfile PROFILE =
            new GameProfile(UUID.fromString("9e2faded-eeee-4ec2-c314-dad129ae971d"), "[Plough]");

    private FakePloughPlayer(ServerLevel level) {
        super(level, PROFILE);
    }

    public static FakePloughPlayer get(ServerLevel level) {
        return new FakePloughPlayer(level);
    }
}
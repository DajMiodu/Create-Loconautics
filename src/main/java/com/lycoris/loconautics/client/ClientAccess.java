package com.lycoris.loconautics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public final class ClientAccess {

    private ClientAccess() {
    }

    @Nullable
    public static Level level() {
        return Minecraft.getInstance().level;
    }

    @Nullable
    public static Player player() {
        return Minecraft.getInstance().player;
    }

    public static boolean hasScreen() {
        return Minecraft.getInstance().screen != null;
    }

    @Nullable
    public static HitResult hitResult() {
        return Minecraft.getInstance().hitResult;
    }
}

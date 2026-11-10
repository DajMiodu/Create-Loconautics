package com.lycoris.loconautics.compat.simulated;

import com.lycoris.loconautics.trainphysics.coupling.TwistLink;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlock;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class SpringTwistLinks {

    private SpringTwistLinks() {
    }

    @Nullable
    public static TwistLink link(ServerSubLevel own, SpringBlockEntity spring) {
        UUID partnerId = spring.getPartnerSubLevelID();
        SpringBlockEntity partner = spring.getPairedSpring();
        if (!spring.isController() || partnerId == null || partner == null) {
            return null;
        }
        return new TwistLink(own.getUniqueId(), spring.getCenter(), partnerId, partner.getCenter(),
                TwistLink.Kind.SPRING, strength(spring.getBlockState().getValue(SpringBlock.SIZE)), null);
    }

    private static double strength(SpringBlock.Size size) {
        return switch (size) {
            case LARGE -> 1.0;
            case MEDIUM -> 0.5;
            case SMALL -> 0.25;
        };
    }
}

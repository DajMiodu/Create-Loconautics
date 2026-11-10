package com.lycoris.loconautics.content.knuckle;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.ConnectorDetacher;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.common.sable.SubLevelData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class KnuckleDetacher implements ConnectorDetacher {

    @Override
    public int detachAll(ServerLevel level, UUID subLevelId) {
        ServerSubLevel sub = SubLevelData.resolve(level, subLevelId);
        if (sub == null) {
            return 0;
        }
        List<BlockPos> connected = new ArrayList<>();
        for (KnuckleBlockEntity knuckle : SubLevelBlocks.findAll(sub, KnuckleBlockEntity.class)) {
            if (knuckle.isConnected()) {
                connected.add(knuckle.getBlockPos());
            }
        }
        for (BlockPos pos : connected) {
            KnuckleConnectionManager.disconnect(sub.getLevel(), pos);
        }
        return connected.size();
    }

    @Override
    public boolean decoupleBetween(ServerLevel level, UUID subA, UUID subB) {
        ServerSubLevel sub = SubLevelData.resolve(level, subA);
        ServerSubLevel other = SubLevelData.resolve(level, subB);
        if (sub == null || other == null) {
            return false;
        }
        boolean split = false;
        for (KnuckleBlockEntity knuckle : SubLevelBlocks.findAll(sub, KnuckleBlockEntity.class)) {
            BlockPos partnerPos = knuckle.getPartnerLocalPos();
            if (!knuckle.isConnected() || partnerPos == null || !subB.equals(knuckle.getPartnerSubLevelId())
                    || !(other.getLevel().getBlockEntity(partnerPos) instanceof KnuckleBlockEntity partner)) {
                continue;
            }
            BlockPos ownPos = knuckle.getBlockPos();
            KnuckleConnectionManager.disconnect(sub.getLevel(), ownPos);
            knuckle.clearPartner();
            partner.clearPartner();
            knuckle.holdApartFrom(subB, partnerPos);
            partner.holdApartFrom(subA, ownPos);
            LoconauticsConstants.LOGGER.info("[maneuver] decoupled knuckles {} on {} and {} on {}", ownPos, subA,
                    partnerPos, subB);
            split = true;
        }
        return split;
    }
}

package com.lycoris.loconautics.content.controls.panel;

import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public interface RedstoneLinkedPart extends PanelPart {

    int MAX_SIGNAL = 15;

    @Nullable
    BlockPos link();

    int signal(Level level);

    @Override
    default float lightLevel(PanelBlockEntity panel, PanelSlot slot) {
        return panel.outputSignal(slot, signal(panel.getLevel())) / (float) MAX_SIGNAL;
    }

    default boolean connected(PanelBlockEntity panel) {
        return link() != null || panel.hasLinkFrequency(this);
    }

    default void driveLink(PanelBlockEntity panel, PanelSlot slot, Level level, BlockPos link,
                           PanelPart unlinked, int signal) {
        if (!PanelLink.stillThere(level, link)) {
            PanelDriveBus.release(level, link);
            panel.replacePart(slot, unlinked);
            return;
        }
        float driven = PanelDriveBus.report(level, link, panel.outputSignal(slot, signal));
        if (level.getBlockEntity(link) instanceof RedstoneLinkBlockEntity receiver) {
            receiver.setSignal(Math.round(driven));
        }
        PanelLink.set(level, link, true);
    }

    @Override
    default void onRemoved(PanelBlockEntity panel) {
        Level level = panel.getLevel();
        BlockPos link = link();
        if (link == null || level == null) return;
        PanelDriveBus.release(level, link);
        if (level.getBlockEntity(link) instanceof RedstoneLinkBlockEntity receiver) receiver.setSignal(0);
        PanelLink.set(level, link, false);
    }
}

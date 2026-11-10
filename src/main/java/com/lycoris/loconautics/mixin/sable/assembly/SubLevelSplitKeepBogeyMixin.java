package com.lycoris.loconautics.mixin.sable.assembly;

import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager.class, remap = false)
public abstract class SubLevelSplitKeepBogeyMixin {

    @Redirect(method = "split", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/sublevel/plot/ServerLevelPlot;destroyAllBlocks()V"))
    private void loconautics$keepBogeyIslandBlocks(ServerLevelPlot plot) {
        if (!loconautics$plotHasBogey(plot)) {
            plot.destroyAllBlocks();
        }
    }

    @Redirect(method = "split", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/sublevel/SubLevelContainer;"
                    + "removeSubLevel(Ldev/ryanhcode/sable/sublevel/SubLevel;"
                    + "Ldev/ryanhcode/sable/sublevel/storage/SubLevelRemovalReason;)V"))
    private void loconautics$keepBogeyIsland(SubLevelContainer container, SubLevel subLevel,
                                             SubLevelRemovalReason reason) {
        if (subLevel instanceof ServerSubLevel ssl && loconautics$plotHasBogey(ssl.getPlot())) {
            return; // bogey island keep it processpendingsplits will register it as a train next tick
        }
        container.removeSubLevel(subLevel, reason);
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean loconautics$plotHasBogey(ServerLevelPlot plot) {
        for (PlotChunkHolder holder : plot.getLoadedChunks()) {
            LevelChunk chunk = holder.getChunk();
            LevelChunkSection[] sections = chunk.getSections();
            for (int si = 0; si < chunk.getSectionsCount(); si++) {
                LevelChunkSection section = sections[si];
                if (section == null || section.hasOnlyAir()) {
                    continue;
                }
                for (int bx = 0; bx < 16; bx++) {
                    for (int by = 0; by < 16; by++) {
                        for (int bz = 0; bz < 16; bz++) {
                            if (section.getBlockState(bx, by, bz).getBlock() instanceof AbstractBogeyBlock) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
}

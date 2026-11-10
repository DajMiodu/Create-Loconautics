package com.lycoris.loconautics.mixin.create.contraption;

import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.foundation.item.ItemHelper;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RollerMovementBehaviour.class, remap = false)
public abstract class RollerMovementBehaviourMixin {

    private static final int TUNNEL_PAVE_MODE = 0;

    @Shadow(remap = false)
    protected abstract BlockState getStateToPaveWith(MovementContext context);

    @Shadow(remap = false)
    public abstract boolean canBreak(Level world, BlockPos pos, BlockState state);

    @Inject(method = "visitNewPosition", at = @At("HEAD"), cancellable = true, remap = false)
    private void loconautics$directPaveBlockedRoadbed(MovementContext context, BlockPos pos, CallbackInfo ci) {
        if (context == null || context.world == null || context.world.isClientSide || context.blockEntityData == null) return;

        // ghost wheel pads are physics props, never ground. creates own pave path would fill them in too
        if (context.world instanceof ServerLevel server && GhostWheelBodies.isWheelBodyAt(server, pos)) {
            ci.cancel();
            return;
        }

        if (context.blockEntityData.getInt("ScrollValue") != TUNNEL_PAVE_MODE || context.contraption == null) return;
        if (!context.world.isLoaded(pos)) return;

        // a contraption already inside a sub level has sables own guarded path, this only covers plain world ones
        if (isInsideSubLevel(context.world, context.contraption.anchor)) return;

        BlockState paveWith = getStateToPaveWith(context);
        if (paveWith.isAir()) return;

        BlockState existing = context.world.getBlockState(pos);
        if (existing.is(paveWith.getBlock()) || !canClearForPaving(context, pos, existing)) return;

        if (consumePavingItem(context)) {
            context.world.setBlockAndUpdate(pos, paveWith);
        }

        ci.cancel();
    }

    private static boolean isInsideSubLevel(Level world, BlockPos anchor) {
        if (anchor == null) return false;
        SubLevelAccess access = SableCompanion.INSTANCE.getContaining(world, anchor.getX() >> 4, anchor.getZ() >> 4);
        return access != null;
    }

    private boolean canClearForPaving(MovementContext context, BlockPos pos, BlockState existing) {
        if (existing.isAir() || existing.is(BlockTags.PORTALS) || !existing.getFluidState().isEmpty()) return false;
        if (existing.canBeReplaced() || existing.is(BlockTags.LEAVES) || existing.getCollisionShape(context.world, pos).isEmpty()) return true;
        return canBreak(context.world, pos, existing);
    }

    private static boolean consumePavingItem(MovementContext context) {
        if (context == null) return false;
        if (context.contraption != null && context.contraption.hasUniversalCreativeCrate) return true;
        if (context.contraption == null) return false;

        var storage = context.contraption.getStorage();
        if (storage != null) {
            if (consumeFrom(context, storage.getAllItems())) return true;
            if (consumeFrom(context, storage.getMountedItems())) return true;
            return consumeFrom(context, context.getItemStorage());
        }
        return false;
    }

    private static boolean consumeFrom(MovementContext context, IItemHandler inventory) {
        if (context == null || context.world == null || inventory == null) return false;
        FilterItemStack filter = context.getFilterFromBE();
        if (filter == null) return false;
        ItemStack extracted = ItemHelper.extract(inventory, stack -> filter.test(context.world, stack), 1, false);
        return !extracted.isEmpty();
    }
}
package com.lycoris.loconautics.content.steelcable;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.hose.link.HoseLinks;
import com.lycoris.loconautics.content.steelcable.rope.SteelCableRope;
import com.lycoris.loconautics.content.steelcable.rope.SteelCableRopeManager;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.UUID;

public class SteelCableItem extends Item {

    public SteelCableItem(Properties properties) {
        super(properties);
    }

    public static DataComponentType<BlockPos> firstConnection() {
        return LoconauticsRegistries.STEEL_CABLE_FIRST_CONNECTION.get();
    }

    public SteelCableType cableType() {
        return SteelCableType.DEFAULT;
    }

    public double maxRange() {
        return LoconauticsConfig.STEEL_CABLE_MAX_RANGE.get();
    }

    public boolean isValidAttachment(Level level, BlockPos pos) {
        SteelCableHolder holder = getHolder(level, pos);
        return holder != null && holder.accepts(cableType()) && holder.canAttachStrand();
    }

    @Nullable
    public static SteelCableHolder getHolder(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof SteelCableHolder holder ? holder : null;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos clickedPos = context.getClickedPos();
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();

        if (player != null && player.isShiftKeyDown()) {
            stack.remove(firstConnection());
            return InteractionResult.SUCCESS;
        }

        if (isValidAttachment(level, clickedPos)) {
            if (stack.has(firstConnection())) {
                if (!level.isClientSide) {
                    BlockPos firstPos = stack.get(firstConnection());
                    if (!createSteelCable((ServerLevel) level, firstPos, clickedPos)) {
                        stack.remove(firstConnection());
                        return InteractionResult.SUCCESS;
                    }
                }
                stack.remove(firstConnection());
                if (player != null && !player.isCreative()) {
                    stack.shrink(1);
                }
                return InteractionResult.SUCCESS;
            }
            stack.set(firstConnection(), clickedPos);
            return InteractionResult.SUCCESS;
        }

        return super.useOn(context);
    }

    public boolean createSteelCable(ServerLevel level, BlockPos posA, BlockPos posB) {
        SteelCableHolder holderA = getHolder(level, posA);
        SteelCableHolder holderB = getHolder(level, posB);
        // world space off a normal block, plot local off one riding a train, so never call these a world position
        BlockPos clickedA = posA;
        BlockPos clickedB = posB;
        if (holderA == null || holderB == null || holderA == holderB
                || !holderA.accepts(cableType()) || !holderB.accepts(cableType())) {
            return false;
        }

        if (holderB.isWinch() && !holderA.isWinch()) {
            SteelCableHolder tmpHolder = holderA;
            holderA = holderB;
            holderB = tmpHolder;
            BlockPos tmpPos = clickedA;
            clickedA = clickedB;
            clickedB = tmpPos;
        }
        if (holderA.isWinch() && holderB.isWinch()) {
            return false;
        }
        if (!holderA.canAttachStrand() || !holderB.canAttachStrand()) {
            return false;
        }
        if (cableType().carriesFluid() && (HoseLinks.atLinkLimit((BlockEntity) holderA)
                || HoseLinks.atLinkLimit((BlockEntity) holderB))) {
            return false;
        }

        Vec3 localPointA = holderA.getAttachmentPoint();
        Vec3 localPointB = holderB.getAttachmentPoint();
        SubLevel subLevelA = Sable.HELPER.getContaining(level, localPointA);
        SubLevel subLevelB = Sable.HELPER.getContaining(level, localPointB);
        Vec3 worldA = Sable.HELPER.projectOutOfSubLevel(level, localPointA);
        Vec3 worldB = Sable.HELPER.projectOutOfSubLevel(level, localPointB);

        if (!worldB.closerThan(worldA, maxRange())) {
            return false;
        }

        UUID id = UUID.randomUUID();
        double restLength = worldA.distanceTo(worldB);
        SteelCableStrand strand = new SteelCableStrand(id,
                subLevelA != null ? subLevelA.getUniqueId() : null, clickedA,
                subLevelB != null ? subLevelB.getUniqueId() : null, clickedB,
                cableType());
        strand.restLength = restLength;

        SteelCableRope rope = SteelCableRope.build(id, strand.type, restLength,
                strand.subA, strand.localA, new Vector3d(worldA.x, worldA.y, worldA.z),
                strand.subB, strand.localB, new Vector3d(worldB.x, worldB.y, worldB.z));
        strand.points = new ArrayList<>(rope.getPoints());

        SteelCableStrandManager strandsMgr = SteelCableStrandManager.get(level);
        SteelCableRopeManager ropesMgr = SteelCableRopeManager.get(level);
        if (strandsMgr == null || ropesMgr == null) {
            return false;
        }
        strandsMgr.create(strand);
        ropesMgr.add(rope);

        holderA.onStrandAttached(strand);
        holderB.onStrandAttached(strand);
        if (holderA instanceof SmartBlockEntity sbeA) {
            sbeA.notifyUpdate();
        }
        if (holderB instanceof SmartBlockEntity sbeB) {
            sbeB.notifyUpdate();
        }

        level.playSound(null, posA, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.5f, 1.0f);
        level.playSound(null, posB, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.5f, 1.0f);
        return true;
    }
}

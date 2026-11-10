package com.lycoris.loconautics.content.schedule;

import com.lycoris.loconautics.content.analogcontroller.TrainHorn;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.decoration.steamWhistle.WhistleBlock;
import com.simibubi.create.content.equipment.bell.AbstractBellBlockEntity;
import com.simibubi.create.content.trains.entity.ArrivalSoundQueue;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ArrivalChime {

    private static final float VOLUME = 5.0f;

    private record Source(UUID subLevel, BlockPos pos, int step) {}

    private final ServerLevel level;
    private final List<Source> sources;
    private final int lastStep;
    private int step;
    private int ticks;

    private ArrivalChime(ServerLevel level, List<Source> sources, int lastStep) {
        this.level = level;
        this.sources = sources;
        this.lastStep = lastStep;
    }

    @Nullable
    public static ArrivalChime start(SableTrain train, Vec3 heading) {
        ServerLevel level = train.level();
        UUID id = train.car() == null ? null : train.car().subLevelId();
        if (level == null || id == null) {
            return null;
        }
        List<UUID> subs = new ArrayList<>();
        List<BlockPos> positions = new ArrayList<>();
        List<Double> reaches = new ArrayList<>();
        for (UUID member : TrainConsist.connectedSubLevels(level, id)) {
            ServerSubLevel sub = SubLevelData.resolve(level, member);
            SubLevelBlocks.forEachBlock(sub, (pos, state) -> {
                if (ArrivalSoundQueue.isPlayable(state)) {
                    subs.add(member);
                    positions.add(pos.immutable());
                    reaches.add(sub.logicalPose().transformPosition(Vec3.atCenterOf(pos)).dot(heading));
                }
                return true;
            });
        }
        if (positions.isEmpty()) {
            return null;
        }
        double front = reaches.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        List<Source> sources = new ArrayList<>();
        int lastStep = 0;
        for (int i = 0; i < positions.size(); i++) {
            int step = (int) Math.round(front - reaches.get(i));
            sources.add(new Source(subs.get(i), positions.get(i), step));
            lastStep = Math.max(lastStep, step);
        }
        return new ArrivalChime(level, sources, lastStep);
    }

    public boolean tick() {
        if (ticks++ % 2 != 0) {
            return true;
        }
        for (Source source : sources) {
            if (source.step() == step) {
                play(source);
            }
        }
        return ++step <= lastStep;
    }

    private void play(Source source) {
        ServerSubLevel sub = SubLevelData.resolve(level, source.subLevel());
        if (sub == null || sub.isRemoved()) {
            return;
        }
        ServerLevel plotLevel = sub.getLevel();
        BlockState state = plotLevel.getBlockState(source.pos());
        Vec3 at = sub.logicalPose().transformPosition(Vec3.atCenterOf(source.pos()));
        if (state.getBlock() instanceof BellBlock) {
            SoundEvent bell = AllBlocks.HAUNTED_BELL.has(state)
                    ? AllSoundEvents.HAUNTED_BELL_USE.getMainEvent() : SoundEvents.BELL_BLOCK;
            playAt(at, bell, 1.0f);
            swing(plotLevel, source.pos(), state);
        }
        if (state.getBlock() instanceof NoteBlock) {
            float pitch = (float) Math.pow(2, (state.getValue(NoteBlock.NOTE) - 12) / 12.0);
            playAt(at, state.getValue(NoteBlock.INSTRUMENT).getSoundEvent().value(), pitch);
        }
        if (state.getBlock() instanceof WhistleBlock) {
            TrainHorn.blow(level, source.pos(), true);
        }
    }

    private static void swing(ServerLevel plotLevel, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(BellBlock.FACING);
        if (plotLevel.getBlockEntity(pos) instanceof AbstractBellBlockEntity bell) {
            bell.ring(plotLevel, pos, facing);
            return;
        }
        plotLevel.blockEvent(pos, state.getBlock(), 1, facing.get3DDataValue());
    }

    private void playAt(Vec3 at, SoundEvent event, float pitch) {
        level.playSound(null, at.x, at.y, at.z, event, SoundSource.NEUTRAL, VOLUME, pitch);
    }
}

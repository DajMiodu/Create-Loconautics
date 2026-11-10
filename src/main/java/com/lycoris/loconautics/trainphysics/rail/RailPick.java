package com.lycoris.loconautics.trainphysics.rail;

import com.simibubi.create.content.trains.graph.TrackGraphHelper;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.track.BezierTrackPointLocation;
import com.simibubi.create.content.trains.track.ITrackBlock;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public record RailPick(BlockPos pos, boolean againstCurve, @Nullable BezierTrackPointLocation bezier) {

    public static final StreamCodec<RegistryFriendlyByteBuf, RailPick> STREAM_CODEC = StreamCodec.of(
            (buf, pick) -> {
                buf.writeBlockPos(pick.pos());
                buf.writeBoolean(pick.againstCurve());
                buf.writeBoolean(pick.bezier() != null);
                if (pick.bezier() != null) {
                    BezierTrackPointLocation.STREAM_CODEC.encode(buf, pick.bezier());
                }
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                boolean againstCurve = buf.readBoolean();
                BezierTrackPointLocation bezier = buf.readBoolean()
                        ? BezierTrackPointLocation.STREAM_CODEC.decode(buf) : null;
                return new RailPick(pos, againstCurve, bezier);
            });

    public boolean sameSpot(@Nullable RailPick other) {
        return other != null && pos.equals(other.pos) && Objects.equals(bezier, other.bezier);
    }

    @Nullable
    public TrackGraphLocation graphLocation(Level level, ITrackBlock track, BlockState state, Vec3 look) {
        if (bezier != null) {
            return TrackGraphHelper.getBezierGraphLocationAt(level, pos,
                    againstCurve ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE, bezier);
        }
        Pair<Vec3, Direction.AxisDirection> axis = track.getNearestTrackAxis(level, pos, state, look);
        return TrackGraphHelper.getGraphLocationAt(level, pos, axis.getSecond(), axis.getFirst());
    }
}

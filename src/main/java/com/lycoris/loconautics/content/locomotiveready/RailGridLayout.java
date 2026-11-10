package com.lycoris.loconautics.content.locomotiveready;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlock;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.content.trains.track.TrackShape;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class RailGridLayout {

    public static final int SPAN = 192;
    public static final int HALF = SPAN / 2;

    private static final int LANE = 3;
    private static final int RIGHT_TURN_OFFSET = 20;
    private static final int LEFT_TURN_OFFSET = 30;
    private static final int SIGNAL_OFFSET = LEFT_TURN_OFFSET + 3;
    private static final int ARM_FIRST = 40;
    private static final int ARM_LAST = HALF - 6;
    private static final int PLATFORM_LENGTH = 9;
    private static final int SIDE_GAP = 4;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    public record Placement(BlockPos pos, BlockState state, CompoundTag nbt) {}

    private final int cx;
    private final int oy;
    private final int cz;
    private final RandomSource random;
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private final Map<BlockPos, CompoundTag> blockEntities = new HashMap<>();
    private final Map<BlockPos, ListTag> connections = new HashMap<>();

    private RailGridLayout(int cx, int oy, int cz, long seed) {
        this.cx = cx;
        this.oy = oy;
        this.cz = cz;
        this.random = RandomSource.create(seed);
    }

    public static Map<Long, List<Placement>> buildByChunk(int cx, int oy, int cz, long seed) {
        RailGridLayout layout = new RailGridLayout(cx, oy, cz, seed);
        layout.lay();
        Map<Long, List<Placement>> out = new HashMap<>();
        layout.blocks.forEach((pos, state) -> out
                .computeIfAbsent(new ChunkPos(pos).toLong(), k -> new ArrayList<>())
                .add(new Placement(pos, state, layout.blockEntities.get(pos))));
        return out;
    }

    private BlockPos at(int dx, int dz) {
        return new BlockPos(cx + dx, oy, cz + dz);
    }

    private static int travel(boolean alongX, int laneSide) {
        return alongX ? laneSide : -laneSide;
    }

    private BlockPos laneCell(boolean alongX, int laneSide, int along) {
        return alongX ? at(along, laneSide * LANE) : at(laneSide * LANE, along);
    }

    private BlockPos sideCell(boolean alongX, int laneSide, int along, int distance) {
        int across = laneSide * distance;
        return alongX ? at(along, across) : at(across, along);
    }

    private void lay() {
        for (int d = -HALF; d < HALF; d++) {
            for (int side = -1; side <= 1; side += 2) {
                track(laneCell(true, side, d), TrackShape.XO);
                track(laneCell(false, side, d), TrackShape.ZO);
            }
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                track(at(sx * LANE, sz * LANE), TrackShape.CR_O);
                arc(at(sx * RIGHT_TURN_OFFSET, sz * LANE), at(sx * LANE, sz * RIGHT_TURN_OFFSET), sx, sz);
                arc(at(sx * LEFT_TURN_OFFSET, -sz * LANE), at(-sx * LANE, sz * LEFT_TURN_OFFSET), sx, sz);
            }
        }
        for (int i = 0; i < 4; i++) {
            boolean alongX = (i & 1) == 0;
            int side = (i & 2) == 0 ? 1 : -1;
            int ts = travel(alongX, side);
            signal(laneCell(alongX, side, -ts * SIGNAL_OFFSET), alongX, side, ts);
        }
        for (int i = 0; i < 4; i++) {
            layArm((i & 1) == 0, (i & 2) == 0 ? 1 : -1);
        }
        connections.forEach((pos, list) -> {
            blocks.put(pos, blocks.get(pos).setValue(TrackBlock.HAS_BE, true));
            CompoundTag tag = entityTag(AllBlockEntityTypes.TRACK.get(), pos);
            tag.put("Connections", list);
            blockEntities.put(pos, tag);
        });
    }

    private void track(BlockPos pos, TrackShape shape) {
        blocks.put(pos, AllBlocks.TRACK.get().defaultBlockState()
                .setValue(TrackBlock.SHAPE, shape)
                .setValue(TrackBlock.HAS_BE, false));
    }

    private void arc(BlockPos a, BlockPos b, int sx, int sz) {
        Vec3 axisA = new Vec3(-sx, 0, 0);
        Vec3 axisB = new Vec3(0, 0, -sz);
        Vec3 startA = new Vec3(a.getX() + 0.5 + axisA.x * 0.5, oy, a.getZ() + 0.5);
        Vec3 startB = new Vec3(b.getX() + 0.5, oy, b.getZ() + 0.5 + axisB.z * 0.5);
        BezierConnection curve = new BezierConnection(Couple.create(a, b), Couple.create(startA, startB),
                Couple.create(axisA, axisB), Couple.create(UP, UP), true, false, TrackMaterial.ANDESITE);
        connections.computeIfAbsent(a, k -> new ListTag()).add(curve.write(a));
        connections.computeIfAbsent(b, k -> new ListTag()).add(curve.secondary().write(b));
    }

    private void layArm(boolean alongX, int sign) {
        List<Integer> used = new ArrayList<>();
        if (random.nextFloat() < 0.5f) {
            int start = ARM_FIRST + random.nextInt(ARM_LAST - ARM_FIRST - PLATFORM_LENGTH);
            for (int side = -1; side <= 1; side += 2) {
                station(alongX, sign, side, start);
            }
            for (int t = start - 6; t < start + PLATFORM_LENGTH + 6; t++) {
                used.add(t);
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            List<Integer> laneUsed = new ArrayList<>(used);
            int wanted = 1 + random.nextInt(2);
            for (int attempt = 0; attempt < 12 && wanted > 0; attempt++) {
                int t = ARM_FIRST + random.nextInt(ARM_LAST - ARM_FIRST);
                boolean clear = true;
                for (int taken : laneUsed) {
                    clear &= Math.abs(taken - t) > 6;
                }
                if (!clear) {
                    continue;
                }
                laneUsed.add(t);
                wanted--;
                signal(laneCell(alongX, side, sign * t), alongX, side, travel(alongX, side));
            }
        }
    }

    private void station(boolean alongX, int sign, int side, int start) {
        for (int t = start; t < start + PLATFORM_LENGTH; t++) {
            for (int distance = LANE + 1; distance <= LANE + SIDE_GAP + 1; distance++) {
                blocks.put(sideCell(alongX, side, sign * t, distance).below(), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        int mid = sign * (start + PLATFORM_LENGTH / 2);
        BlockPos trackPos = laneCell(alongX, side, mid);
        BlockPos sidePos = sideCell(alongX, side, mid, LANE + SIDE_GAP);
        blocks.put(sidePos, AllBlocks.TRACK_STATION.get().defaultBlockState());
        CompoundTag tag = targetingTag(AllBlockEntityTypes.TRACK_STATION.get(), sidePos, trackPos,
                travel(alongX, side) < 0);
        CompoundTag migrate = new CompoundTag();
        migrate.putString("Name", StationNames.random(random));
        tag.put("Migrate", migrate);
        blockEntities.put(sidePos, tag);
    }

    private void signal(BlockPos trackPos, boolean alongX, int side, int travelSign) {
        int out = side * (LANE + SIDE_GAP) - side * LANE;
        BlockPos sidePos = alongX
                ? new BlockPos(trackPos.getX(), oy, trackPos.getZ() + out)
                : new BlockPos(trackPos.getX() + out, oy, trackPos.getZ());
        Direction towardTrack = alongX ? (side > 0 ? Direction.NORTH : Direction.SOUTH)
                : (side > 0 ? Direction.WEST : Direction.EAST);
        blocks.put(sidePos, AllBlocks.TRACK_SIGNAL.get().defaultBlockState());
        blocks.put(sidePos.above(), AllBlocks.ORANGE_NIXIE_TUBE.get().defaultBlockState()
                .setValue(NixieTubeBlock.FACING, towardTrack));
        blockEntities.put(sidePos,
                targetingTag(AllBlockEntityTypes.TRACK_SIGNAL.get(), sidePos, trackPos, travelSign < 0));
    }

    private CompoundTag targetingTag(BlockEntityType<?> type, BlockPos pos, BlockPos trackPos, boolean positive) {
        CompoundTag tag = entityTag(type, pos);
        tag.putBoolean("TargetDirection", positive);
        tag.put("TargetTrack", NbtUtils.writeBlockPos(trackPos.subtract(pos)));
        tag.putUUID("Id", new UUID(random.nextLong(), random.nextLong()));
        return tag;
    }

    private static CompoundTag entityTag(BlockEntityType<?> type, BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type).toString());
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        return tag;
    }
}

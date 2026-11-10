package com.lycoris.loconautics.content.locomotiveready;

import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackShape;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

public final class RailGridPiece extends StructurePiece {

    private static final Map<LayoutKey, Map<Long, List<RailGridLayout.Placement>>> CACHE =
            new LinkedHashMap<>(8, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<LayoutKey, Map<Long, List<RailGridLayout.Placement>>> eldest) {
                    return size() > 6;
                }
            };

    private final long seed;

    public RailGridPiece(int cx, int oy, int cz, long seed) {
        super(LocomotiveReadyWorld.RAIL_GRID_PIECE.get(), 0, new BoundingBox(cx - RailGridLayout.HALF, oy - 1,
                cz - RailGridLayout.HALF, cx + RailGridLayout.HALF - 1, oy + 4, cz + RailGridLayout.HALF - 1));
        this.seed = seed;
    }

    public RailGridPiece(CompoundTag tag) {
        super(LocomotiveReadyWorld.RAIL_GRID_PIECE.get(), tag);
        this.seed = tag.getLong("Seed");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putLong("Seed", seed);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        BoundingBox own = getBoundingBox();
        Map<Long, List<RailGridLayout.Placement>> layout = layoutFor(own.minX() + RailGridLayout.HALF,
                own.minY() + 1, own.minZ() + RailGridLayout.HALF, seed);
        for (RailGridLayout.Placement placement : layout.getOrDefault(chunkPos.toLong(), List.of())) {
            BlockPos at = placement.pos();
            if (!box.isInside(at)) {
                continue;
            }
            BlockState state = placement.state();
            level.setBlock(at, state, 2);
            if (placement.nbt() != null) {
                BlockEntity be = level.getBlockEntity(at);
                if (be != null) {
                    be.loadWithComponents(placement.nbt(), level.registryAccess());
                } else {
                    level.getChunk(at).setBlockEntityNbt(placement.nbt());
                }
            }
            if (state.getBlock() instanceof TrackBlock) {
                if (!state.getValue(TrackBlock.HAS_BE)) {
                    level.getChunk(at).removeBlockEntity(at);
                }
                if (needsTick(at, state)) {
                    level.scheduleTick(at, state.getBlock(), 1);
                }
            }
        }
    }

    private static boolean needsTick(BlockPos at, BlockState state) {
        int x = at.getX() & 15;
        int z = at.getZ() & 15;
        return state.getValue(TrackBlock.HAS_BE) || state.getValue(TrackBlock.SHAPE) == TrackShape.CR_O
                || x == 0 || x == 15 || z == 0 || z == 15;
    }

    private static Map<Long, List<RailGridLayout.Placement>> layoutFor(int cx, int oy, int cz, long seed) {
        LayoutKey key = new LayoutKey(cx, oy, cz, seed);
        synchronized (CACHE) {
            Map<Long, List<RailGridLayout.Placement>> cached = CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }
        Map<Long, List<RailGridLayout.Placement>> built = RailGridLayout.buildByChunk(cx, oy, cz, seed);
        synchronized (CACHE) {
            CACHE.put(key, built);
        }
        return built;
    }

    private record LayoutKey(int cx, int oy, int cz, long seed) {}
}

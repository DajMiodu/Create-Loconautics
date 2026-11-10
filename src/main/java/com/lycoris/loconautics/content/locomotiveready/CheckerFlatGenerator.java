package com.lycoris.loconautics.content.locomotiveready;

import com.lycoris.loconautics.LoconauticsConstants;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

public final class CheckerFlatGenerator extends ChunkGenerator {

    public static final int SURFACE_Y = 63;

    public static final MapCodec<CheckerFlatGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.CODEC.fieldOf("biome").forGetter(g -> g.biome),
            Codec.intRange(1, 64).fieldOf("tile_size").forGetter(g -> g.tileSize))
            .apply(i, CheckerFlatGenerator::new));

    private static final ResourceKey<StructureSet> RAIL_GRID_SET = ResourceKey.create(Registries.STRUCTURE_SET,
            ResourceLocation.fromNamespaceAndPath(LoconauticsConstants.MOD_ID, "rail_grid"));

    private static final BlockState LIGHT = Blocks.SNOW_BLOCK.defaultBlockState();
    private static final BlockState DARK = Blocks.WHITE_CONCRETE.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();

    private final Holder<Biome> biome;
    private final int tileSize;

    public CheckerFlatGenerator(Holder<Biome> biome, int tileSize) {
        super(new FixedBiomeSource(biome), b -> BiomeGenerationSettings.EMPTY);
        this.biome = biome;
        this.tileSize = tileSize;
    }

    private BlockState tile(int x, int z) {
        int lx = Math.floorMod(x, tileSize);
        int lz = Math.floorMod(z, tileSize);
        int ring = Math.min(Math.min(lx, tileSize - 1 - lx), Math.min(lz, tileSize - 1 - lz));
        int parity = Math.floorDiv(x, tileSize) + Math.floorDiv(z, tileSize) + ring;
        return (parity & 1) == 0 ? LIGHT : DARK;
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState,
                                                    long seed) {
        Stream<Holder<StructureSet>> railGrid = structureSets.get(RAIL_GRID_SET)
                .<Stream<Holder<StructureSet>>>map(Stream::of)
                .orElseGet(Stream::empty);
        return ChunkGeneratorStructureState.createForFlat(randomState, seed, biomeSource, railGrid);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
                                                        StructureManager structureManager, ChunkAccess chunk) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Heightmap floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                BlockState fill = tile(minX + dx, minZ + dz);
                for (int y = 0; y <= SURFACE_Y; y++) {
                    BlockState state = y == 0 ? BEDROCK : fill;
                    chunk.setBlockState(pos.set(dx, y, dz), state, false);
                    floor.update(dx, y, dz, state);
                    surface.update(dx, y, dz, state);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState random,
                             ChunkAccess chunk) {
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor level) {
        return SURFACE_Y + 1;
    }

    @Override
    public int getGenDepth() {
        return 256;
    }

    @Override
    public int getSeaLevel() {
        return SURFACE_Y;
    }

    @Override
    public int getMinY() {
        return 0;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return SURFACE_Y + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        BlockState fill = tile(x, z);
        BlockState[] column = new BlockState[SURFACE_Y + 1];
        for (int y = 0; y < column.length; y++) {
            column[y] = y == 0 ? BEDROCK : fill;
        }
        return new NoiseColumn(0, column);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
    }
}

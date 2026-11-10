package com.lycoris.loconautics.client.copycat;

import com.lycoris.loconautics.client.connected.ConnectedBlock;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class CopycatBedModel implements IDynamicBakedModel {

    public static final ModelProperty<Bed> BED = new ModelProperty<>();

    private static final ChunkRenderTypeSet RENDER_TYPES =
            ChunkRenderTypeSet.of(RenderType.solid(), RenderType.cutout());

    private final float low;
    private final float high;
    private final float inset;
    private final TextureAtlasSprite particle;
    private final ItemTransforms transforms;

    public CopycatBedModel(float low, float high, float inset,
                           TextureAtlasSprite particle, ItemTransforms transforms) {
        this.low = low;
        this.high = high;
        this.inset = inset;
        this.particle = particle;
        this.transforms = transforms;
    }

    public record Bed(BlockState material, boolean[] neighbors) {

        public boolean neighbor(Direction dir) {
            return neighbors[dir.get2DDataValue()];
        }
    }

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos,
                                           @NotNull BlockState state, @NotNull ModelData modelData) {
        if (!(state.getBlock() instanceof CopycatMaterialSource source)) return modelData;

        BlockState material = source.copycatMaterial(level, pos, state);
        if (material == null) return modelData;

        boolean[] neighbors = new boolean[4];
        if (state.getBlock() instanceof ConnectedBlock block) {
            Set<BlockPos> group = block.connectionBounds()
                    .group(block, level, state, block.groupAnchor(level, state, pos));
            if (group.contains(pos)) {
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    neighbors[dir.get2DDataValue()] = group.contains(pos.relative(dir));
                }
            }
        }
        return modelData.derive().with(BED, new Bed(material, neighbors)).build();
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face,
                                             @NotNull RandomSource rand, @NotNull ModelData modelData,
                                             @Nullable RenderType renderType) {
        Bed bed = modelData.get(BED);
        if (face != null || bed == null) return Collections.emptyList();

        List<BakedQuad> quads = new ArrayList<>();
        CopycatQuads.crop(quads, bed.material(), rand, renderType, box(inset, 1f - inset, inset, 1f - inset));

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (!bed.neighbor(dir)) continue;
            CopycatQuads.crop(quads, bed.material(), rand, renderType, arm(dir));

            Direction next = dir.getClockWise();
            if (bed.neighbor(next)) {
                CopycatQuads.crop(quads, bed.material(), rand, renderType, corner(dir, next));
            }
        }
        return quads;
    }

    private AABB arm(Direction dir) {
        return switch (dir) {
            case NORTH -> box(inset, 1f - inset, 0f, inset);
            case SOUTH -> box(inset, 1f - inset, 1f - inset, 1f);
            case WEST -> box(0f, inset, inset, 1f - inset);
            default -> box(1f - inset, 1f, inset, 1f - inset);
        };
    }

    private AABB corner(Direction a, Direction b) {
        boolean west = a == Direction.WEST || b == Direction.WEST;
        boolean north = a == Direction.NORTH || b == Direction.NORTH;
        float minX = west ? 0f : 1f - inset;
        float minZ = north ? 0f : 1f - inset;
        return box(minX, minX + inset, minZ, minZ + inset);
    }

    private AABB box(float minX, float maxX, float minZ, float maxZ) {
        return new AABB(minX, low, minZ, maxX, high, maxZ);
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand,
                                                      @NotNull ModelData data) {
        Bed bed = data.get(BED);
        if (bed == null) {
            return RENDER_TYPES;
        }
        return CopycatQuads.donorModel(bed.material()).getRenderTypes(bed.material(), rand, ModelData.EMPTY);
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face,
                                             @NotNull RandomSource rand) {
        return Collections.emptyList();
    }

    @Override public boolean useAmbientOcclusion() { return true; }
    @Override public boolean isGui3d() { return true; }
    @Override public boolean usesBlockLight() { return true; }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public @NotNull ItemTransforms getTransforms() { return transforms; }
    @Override public @NotNull ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    @Override public @NotNull TextureAtlasSprite getParticleIcon() { return particle; }
}

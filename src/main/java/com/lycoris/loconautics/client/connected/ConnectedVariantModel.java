package com.lycoris.loconautics.client.connected;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public class ConnectedVariantModel implements IDynamicBakedModel {

    public static final ModelProperty<Variant> VARIANT = new ModelProperty<>();

    public enum Variant {
        SINGLE,
        START,
        MIDDLE,
        END
    }

    private final BakedModel single;
    private final BakedModel start;
    private final BakedModel middle;
    private final BakedModel end;

    public ConnectedVariantModel(BakedModel single, BakedModel start, BakedModel middle, BakedModel end) {
        this.single = single;
        this.start = start;
        this.middle = middle;
        this.end = end;
    }

    private BakedModel pick(@NotNull ModelData data) {
        Variant variant = data.get(VARIANT);
        if (variant == null) return single;
        return switch (variant) {
            case SINGLE -> single;
            case START -> start;
            case MIDDLE -> middle;
            case END -> end;
        };
    }

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos,
                                           @NotNull BlockState state, @NotNull ModelData modelData) {
        if (!(state.getBlock() instanceof ConnectedBlock block)) return modelData;
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return modelData;

        Set<BlockPos> group = block.connectionBounds()
                .group(block, level, state, block.groupAnchor(level, state, pos));

        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        boolean toStart = group.contains(pos.relative(facing.getCounterClockWise()));
        boolean toEnd = group.contains(pos.relative(facing.getClockWise()));

        Variant variant;
        if (toStart && toEnd) {
            variant = Variant.MIDDLE;
        } else if (toEnd) {
            variant = Variant.START;
        } else if (toStart) {
            variant = Variant.END;
        } else {
            variant = Variant.SINGLE;
        }
        return modelData.derive().with(VARIANT, variant).build();
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face,
                                             @NotNull RandomSource rand, @NotNull ModelData modelData,
                                             @Nullable RenderType renderType) {
        return pick(modelData).getQuads(state, face, rand, ModelData.EMPTY, renderType);
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand,
                                                      @NotNull ModelData data) {
        return pick(data).getRenderTypes(state, rand, ModelData.EMPTY);
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face,
                                             @NotNull RandomSource rand) {
        return getQuads(state, face, rand, ModelData.EMPTY, null);
    }

    @Override public boolean useAmbientOcclusion() { return single.useAmbientOcclusion(); }
    @Override public boolean isGui3d() { return single.isGui3d(); }
    @Override public boolean usesBlockLight() { return single.usesBlockLight(); }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public @NotNull ItemTransforms getTransforms() { return single.getTransforms(); }
    @Override public @NotNull ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    @Override public @NotNull TextureAtlasSprite getParticleIcon() { return single.getParticleIcon(); }
}

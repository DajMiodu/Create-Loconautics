package com.lycoris.loconautics.content.bearingaxle;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.client.LoconauticsPartialModels;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DrivenBogeyModel extends BakedModelWrapper<BakedModel> {

    public static final ModelProperty<Boolean> DRIVEN = new ModelProperty<>();

    private static final IQuadTransformer TURN_TO_X =
            QuadTransformers.applying(BlockModelRotation.X0_Y90.getRotation().applyOrigin(new Vector3f(0.5f, 0.5f, 0.5f)));

    private static final ResourceLocation CREATE_TOP = ResourceLocation.fromNamespaceAndPath("create", "block/bogey/top");
    private static final ResourceLocation GRAY_TOP = LoconauticsConstants.id("block/bearingaxle/bogey/top");
    private static final int VERTEX_INTS = 8;

    private final boolean standardShape;

    public DrivenBogeyModel(BakedModel original, boolean standardShape) {
        super(original);
        this.standardShape = standardShape;
    }

    public static boolean isStandardShape(ResourceLocation block) {
        String path = block.getPath();
        if (block.getNamespace().equals("railways")) {
            return !(path.contains("narrow") || path.contains("wide") || path.contains("mono") || path.contains("invisible"));
        }
        return block.getNamespace().equals("create") || block.getNamespace().equals("create_bb");
    }

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos,
                                           @NotNull BlockState state, @NotNull ModelData modelData) {
        return modelData.derive().with(DRIVEN, driven(level, pos)).build();
    }

    private static boolean driven(BlockAndTintGetter level, BlockPos bogey) {
        for (Direction dir : Direction.values()) {
            BlockPos axlePos = bogey.relative(dir);
            if (!(level.getBlockEntity(axlePos) instanceof BearingAxleBlockEntity axle)) {
                continue;
            }
            if (axle.getValidity() != BearingAxleValidity.Status.VALID) {
                continue;
            }
            if (bogey.equals(BearingAxleValidity.driverBogey(level, axlePos))) {
                return true;
            }
        }
        return false;
    }

    private static PartialModel pick(boolean driven, boolean shafted) {
        if (shafted) {
            return driven ? LoconauticsPartialModels.BOGEY_DRIVEN_SHAFTED : LoconauticsPartialModels.BOGEY_SHAFTED;
        }
        return LoconauticsPartialModels.BOGEY_DRIVEN_TOP;
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face,
                                             @NotNull RandomSource rand, @NotNull ModelData data,
                                             @Nullable RenderType renderType) {
        boolean shafted = state != null && BogeyShaft.isShafted(state);
        boolean driven = data.get(DRIVEN) == Boolean.TRUE;
        if (state == null || (!driven && !shafted)) {
            return super.getQuads(state, face, rand, data, renderType);
        }
        if (!standardShape) {
            List<BakedQuad> own = super.getQuads(state, face, rand, data, renderType);
            return driven ? grayed(own) : own;
        }
        BakedModel swapped = pick(driven, shafted).get();
        boolean turned = state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)
                && state.getValue(BlockStateProperties.HORIZONTAL_AXIS) == Direction.Axis.X;
        if (!turned) {
            return swapped.getQuads(state, face, rand, ModelData.EMPTY, renderType);
        }
        Direction source = face == null || face.getAxis().isVertical() ? face : face.getCounterClockWise();
        return TURN_TO_X.process(swapped.getQuads(state, source, rand, ModelData.EMPTY, renderType));
    }

    private static List<BakedQuad> grayed(List<BakedQuad> quads) {
        TextureAtlasSprite gray = null;
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            TextureAtlasSprite old = quad.getSprite();
            if (!old.contents().name().equals(CREATE_TOP)) {
                out.add(quad);
                continue;
            }
            if (gray == null) {
                gray = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(GRAY_TOP);
            }
            int[] vertices = quad.getVertices().clone();
            for (int i = 0; i < 4; i++) {
                int u = i * VERTEX_INTS + 4;
                int v = u + 1;
                float fu = (Float.intBitsToFloat(vertices[u]) - old.getU0()) / (old.getU1() - old.getU0());
                float fv = (Float.intBitsToFloat(vertices[v]) - old.getV0()) / (old.getV1() - old.getV0());
                vertices[u] = Float.floatToRawIntBits(gray.getU0() + fu * (gray.getU1() - gray.getU0()));
                vertices[v] = Float.floatToRawIntBits(gray.getV0() + fv * (gray.getV1() - gray.getV0()));
            }
            out.add(new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), gray, quad.isShade()));
        }
        return out;
    }
}

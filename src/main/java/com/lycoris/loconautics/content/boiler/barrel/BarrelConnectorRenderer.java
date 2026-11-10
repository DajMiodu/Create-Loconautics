package com.lycoris.loconautics.content.boiler.barrel;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SpriteShiftEntry;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BarrelConnectorRenderer extends SafeBlockEntityRenderer<BarrelConnectorBlockEntity> {

    private static final ResourceLocation CREATE_PIPES =
            ResourceLocation.fromNamespaceAndPath("create", "block/pipes");

    private static final Map<String, Block> PIPE_BY_NAMESPACE = new HashMap<>();

    public BarrelConnectorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(BarrelConnectorBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        Direction face = be.getBlockState().getValue(BarrelConnectorBlock.FACING);
        SuperByteBuffer buf = CachedBuffers.partial(
                LoconauticsPartialModels.BARREL_PIPE_CONNECTOR, be.getBlockState());
        SpriteShiftEntry skin = skinOf(level.getBlockState(be.getBlockPos().relative(face)));
        if (skin != null) buf.shiftUV(skin);
        orient(buf, face);
        buf.light(light).renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
    }

    private static SpriteShiftEntry skinOf(BlockState neighbor) {
        Block pipe = pipeOfSameMod(neighbor.getBlock());
        if (pipe == null) return null;

        TextureAtlasSprite target = pipeSheet(pipe);
        if (target == null || target.contents().name().equals(CREATE_PIPES)) return null;

        TextureAtlasSprite original = Minecraft.getInstance().getModelManager()
                .getAtlas(InventoryMenu.BLOCK_ATLAS).getSprite(CREATE_PIPES);
        return original == null ? null : new AtlasSwap(original, target);
    }

    private static TextureAtlasSprite pipeSheet(Block pipe) {
        BakedModel model = Minecraft.getInstance().getItemRenderer()
                .getItemModelShaper().getItemModel(pipe.asItem());
        if (model == null) return null;
        List<BakedQuad> quads = model.getQuads(null, null, RandomSource.create());
        return quads.isEmpty() ? null : quads.get(0).getSprite();
    }

    static Block pipeOfSameMod(Block block) {
        String namespace = BuiltInRegistries.BLOCK.getKey(block).getNamespace();
        Block pipe = PIPE_BY_NAMESPACE.computeIfAbsent(namespace, BarrelConnectorRenderer::findPipe);
        return pipe == Blocks.AIR ? null : pipe;
    }

    private static Block findPipe(String namespace) {
        for (Block candidate : BuiltInRegistries.BLOCK) {
            if (candidate.getClass() != FluidPipeBlock.class) continue;
            if (BuiltInRegistries.BLOCK.getKey(candidate).getNamespace().equals(namespace)) return candidate;
        }
        return Blocks.AIR;
    }

    private static final class AtlasSwap extends SpriteShiftEntry {

        private final TextureAtlasSprite from;

        private final TextureAtlasSprite to;

        AtlasSwap(TextureAtlasSprite from, TextureAtlasSprite to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public TextureAtlasSprite getOriginal() {
            return from;
        }

        @Override
        public TextureAtlasSprite getTarget() {
            return to;
        }
    }

    private static void orient(SuperByteBuffer buf, Direction face) {
        buf.center();
        switch (face) {
            case SOUTH -> buf.rotateYDegrees(180);
            case WEST -> buf.rotateYDegrees(90);
            case EAST -> buf.rotateYDegrees(270);
            case UP -> buf.rotateXDegrees(90);
            case DOWN -> buf.rotateXDegrees(270);
            default -> { }
        }
        buf.uncenter();
    }
}

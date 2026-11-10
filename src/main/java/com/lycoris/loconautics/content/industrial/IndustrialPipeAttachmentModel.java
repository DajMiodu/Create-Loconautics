package com.lycoris.loconautics.content.industrial;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.FluidTransportBehaviour.AttachmentTypes;
import com.simibubi.create.content.fluids.FluidTransportBehaviour.AttachmentTypes.ComponentPartials;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.model.BakedModelWrapperWithData;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.data.Iterate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class IndustrialPipeAttachmentModel extends BakedModelWrapperWithData {

    private static final ModelProperty<PipeModelData> PIPE_PROPERTY = new ModelProperty<>();

    private static final Map<ComponentPartials, Map<Direction, PartialModel>> ATTACHMENTS =
            LoconauticsPartialModels.INDUSTRIAL_PIPE_ATTACHMENTS;

    private static final PartialModel CASING = LoconauticsPartialModels.INDUSTRIAL_PIPE_CASING;

    public static IndustrialPipeAttachmentModel withAO(BakedModel template) {
        return new IndustrialPipeAttachmentModel(template);
    }

    private IndustrialPipeAttachmentModel(BakedModel template) {
        super(template);
    }

    @Override
    protected ModelData.Builder gatherModelData(ModelData.Builder builder, BlockAndTintGetter world, BlockPos pos,
                                                BlockState state, ModelData blockEntityData) {
        PipeModelData data = new PipeModelData();
        FluidTransportBehaviour transport = BlockEntityBehaviour.get(world, pos, FluidTransportBehaviour.TYPE);
        BracketedBlockEntityBehaviour bracket = BlockEntityBehaviour.get(world, pos, BracketedBlockEntityBehaviour.TYPE);

        if (transport != null) {
            for (Direction d : Iterate.directions) {
                data.putAttachment(d, transport.getRenderedRimAttachment(world, pos, state, d));
            }
        }
        if (bracket != null) {
            data.putBracket(bracket.getBracket());
        }

        data.setEncased(FluidPipeBlock.shouldDrawCasing(world, pos, state));
        return builder.with(PIPE_PROPERTY, data);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand,
                                             @NotNull ModelData data) {
        List<ChunkRenderTypeSet> set = new ArrayList<>();
        set.add(super.getRenderTypes(state, rand, data));
        set.add(CASING.get().getRenderTypes(state, rand, data));

        if (data.has(PIPE_PROPERTY)) {
            PipeModelData pipeData = data.get(PIPE_PROPERTY);
            for (Direction d : Iterate.directions) {
                for (ComponentPartials partial : pipeData.getAttachment(d).partials) {
                    set.add(ATTACHMENTS.get(partial).get(d).get().getRenderTypes(state, rand, data));
                }
            }
        }
        return ChunkRenderTypeSet.union(set);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data,
                                    RenderType renderType) {
        List<BakedQuad> quads = super.getQuads(state, side, rand, data, renderType);
        if (!data.has(PIPE_PROPERTY)) return quads;

        PipeModelData pipeData = data.get(PIPE_PROPERTY);
        quads = new ArrayList<>(quads);

        BakedModel bracket = pipeData.getBracket();
        if (bracket != null) {
            quads.addAll(bracket.getQuads(state, side, rand, data, renderType));
        }
        for (Direction d : Iterate.directions) {
            for (ComponentPartials partial : pipeData.getAttachment(d).partials) {
                quads.addAll(ATTACHMENTS.get(partial).get(d).get()
                        .getQuads(state, side, rand, data, renderType));
            }
        }
        if (pipeData.isEncased()) {
            quads.addAll(CASING.get().getQuads(state, side, rand, data, renderType));
        }
        return quads;
    }

    @Override
    public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
        return TriState.TRUE;
    }

    @Override
    public boolean useAmbientOcclusion() {
        return true;
    }

    private static class PipeModelData {

        private final AttachmentTypes[] attachments = new AttachmentTypes[6];

        private boolean encased;

        private BakedModel bracket;

        PipeModelData() {
            Arrays.fill(attachments, AttachmentTypes.NONE);
        }

        void putBracket(BlockState state) {
            if (state != null) {
                bracket = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
            }
        }

        BakedModel getBracket() {
            return bracket;
        }

        void putAttachment(Direction face, AttachmentTypes rim) {
            attachments[face.get3DDataValue()] = rim;
        }

        AttachmentTypes getAttachment(Direction face) {
            return attachments[face.get3DDataValue()];
        }

        void setEncased(boolean value) {
            encased = value;
        }

        boolean isEncased() {
            return encased;
        }
    }
}

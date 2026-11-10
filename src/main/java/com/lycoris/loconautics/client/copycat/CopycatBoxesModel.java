package com.lycoris.loconautics.client.copycat;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.copycat.CopycatModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

public class CopycatBoxesModel extends CopycatModel {

    private final List<AABB> boxes;
    private final int depthAxis;

    public CopycatBoxesModel(BakedModel originalModel, List<AABB> boxes, ModelState modelState) {
        super(originalModel);
        this.boxes = boxes.stream().map(box -> rotate(box, modelState.getRotation().getMatrix())).toList();
        this.depthAxis = thinnestAxis(this.boxes);
    }

    private static int thinnestAxis(List<AABB> boxes) {
        double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (AABB box : boxes) {
            min[0] = Math.min(min[0], box.minX);
            min[1] = Math.min(min[1], box.minY);
            min[2] = Math.min(min[2], box.minZ);
            max[0] = Math.max(max[0], box.maxX);
            max[1] = Math.max(max[1], box.maxY);
            max[2] = Math.max(max[2], box.maxZ);
        }
        int thinnest = 0;
        for (int axis = 1; axis < 3; axis++) {
            if (max[axis] - min[axis] < max[thinnest] - min[thinnest]) thinnest = axis;
        }
        return thinnest;
    }

    // the blockstate y rotation never reaches these quads, they are built at render time, so bake it into the boxes
    private static AABB rotate(AABB box, Matrix4f matrix) {
        Vector4f min = matrix.transform(
                new Vector4f((float) box.minX - 0.5f, (float) box.minY - 0.5f, (float) box.minZ - 0.5f, 1.0f));
        Vector4f max = matrix.transform(
                new Vector4f((float) box.maxX - 0.5f, (float) box.maxY - 0.5f, (float) box.maxZ - 0.5f, 1.0f));
        return new AABB(
                Math.min(min.x, max.x) + 0.5, Math.min(min.y, max.y) + 0.5, Math.min(min.z, max.z) + 0.5,
                Math.max(min.x, max.x) + 0.5, Math.max(min.y, max.y) + 0.5, Math.max(min.z, max.z) + 0.5);
    }

    @Override
    protected List<BakedQuad> getCroppedQuads(BlockState state, Direction side, RandomSource rand,
                                              BlockState material, ModelData wrappedData, RenderType renderType) {
        List<BakedQuad> template = getModelOf(material).getQuads(material, side, rand, wrappedData, renderType);
        List<BakedQuad> quads = new ArrayList<>(template.size() * boxes.size());
        for (AABB box : boxes) {
            CopycatQuads.cropQuads(quads, template, box, depthAxis);
        }
        return quads;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        BlockState material = getMaterial(data);
        if (AllBlocks.COPYCAT_BASE.has(material)) {
            return originalModel.getRenderTypes(state, rand, data);
        }
        return getModelOf(material).getRenderTypes(material, rand, ModelData.EMPTY);
    }
}

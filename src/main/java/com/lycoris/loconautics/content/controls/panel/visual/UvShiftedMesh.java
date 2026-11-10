package com.lycoris.loconautics.content.controls.panel.visual;

import dev.engine_room.flywheel.api.model.IndexSequence;
import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.api.vertex.MutableVertexList;
import org.joml.Vector4fc;

public record UvShiftedMesh(Mesh mesh, float du, float dv) implements Mesh {

    @Override
    public int vertexCount() {
        return mesh.vertexCount();
    }

    @Override
    public void write(MutableVertexList vertexList) {
        mesh.write(vertexList);
        for (int i = 0; i < mesh.vertexCount(); i++) {
            vertexList.u(i, vertexList.u(i) + du);
            vertexList.v(i, vertexList.v(i) + dv);
        }
    }

    @Override
    public IndexSequence indexSequence() {
        return mesh.indexSequence();
    }

    @Override
    public int indexCount() {
        return mesh.indexCount();
    }

    @Override
    public Vector4fc boundingSphere() {
        return mesh.boundingSphere();
    }
}

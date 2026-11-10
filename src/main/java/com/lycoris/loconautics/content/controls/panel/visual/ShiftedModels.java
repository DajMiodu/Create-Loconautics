package com.lycoris.loconautics.content.controls.panel.visual;

import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.SimpleModel;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.util.RendererReloadCache;
import net.createmod.catnip.render.SpriteShiftEntry;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ShiftedModels {

    private static final float SHEET_PIXELS = 64f;

    private record Key(PartialModel partial, @Nullable SpriteShiftEntry entry, int pixelsU, int pixelsV,
                       @Nullable Transparency transparency) {
    }

    private static final RendererReloadCache<Key, Model> CACHE = new RendererReloadCache<>(ShiftedModels::build);

    private ShiftedModels() {}

    public static Model plain(PartialModel partial) {
        return Models.partial(partial);
    }

    public static Model shifted(PartialModel partial, SpriteShiftEntry entry, int pixelsU, int pixelsV) {
        return CACHE.get(new Key(partial, entry, pixelsU, pixelsV, null));
    }

    public static Model withTransparency(PartialModel partial, Transparency transparency) {
        return CACHE.get(new Key(partial, null, 0, 0, transparency));
    }

    private static Model build(Key key) {
        float du = 0f;
        float dv = 0f;
        if (key.entry() != null) {
            TextureAtlasSprite original = key.entry().getOriginal();
            TextureAtlasSprite target = key.entry().getTarget();
            du = target.getU0() - original.getU0() + (target.getU1() - target.getU0()) / SHEET_PIXELS * key.pixelsU();
            dv = target.getV0() - original.getV0() + (target.getV1() - target.getV0()) / SHEET_PIXELS * key.pixelsV();
        }
        List<Model.ConfiguredMesh> meshes = new ArrayList<>();
        for (Model.ConfiguredMesh configured : Models.partial(key.partial()).meshes()) {
            Material material = configured.material();
            if (key.transparency() != null) {
                material = SimpleMaterial.builderOf(material).transparency(key.transparency()).build();
            }
            Mesh mesh = du == 0f && dv == 0f ? configured.mesh() : new UvShiftedMesh(configured.mesh(), du, dv);
            meshes.add(new Model.ConfiguredMesh(material, mesh));
        }
        return new SimpleModel(meshes);
    }
}

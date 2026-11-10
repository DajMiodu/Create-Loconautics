package com.lycoris.loconautics.client.copycat;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class CopycatBoxesLoader implements IGeometryLoader<CopycatBoxesLoader.Geometry> {

    @Override
    public Geometry read(JsonObject json, JsonDeserializationContext context) {
        List<AABB> boxes = new ArrayList<>();
        for (JsonElement entry : json.getAsJsonArray("elements")) {
            JsonObject element = entry.getAsJsonObject();
            JsonArray from = element.getAsJsonArray("from");
            JsonArray to = element.getAsJsonArray("to");
            boxes.add(new AABB(
                    from.get(0).getAsDouble() / 16.0, from.get(1).getAsDouble() / 16.0, from.get(2).getAsDouble() / 16.0,
                    to.get(0).getAsDouble() / 16.0, to.get(1).getAsDouble() / 16.0, to.get(2).getAsDouble() / 16.0));
        }
        return new Geometry(List.copyOf(boxes), ResourceLocation.parse(json.get("base").getAsString()));
    }

    public record Geometry(List<AABB> boxes, ResourceLocation base) implements IUnbakedGeometry<Geometry> {

        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter,
                                   IGeometryBakingContext context) {
            modelGetter.apply(base).resolveParents(modelGetter);
        }

        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                               Function<Material, TextureAtlasSprite> spriteGetter,
                               ModelState modelState, ItemOverrides overrides) {
            return new CopycatBoxesModel(baker.bake(base, modelState), boxes, modelState);
        }
    }
}

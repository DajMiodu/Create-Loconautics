package com.lycoris.loconautics.client.connected;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ConnectedVariantModelLoader implements IGeometryLoader<ConnectedVariantModelLoader.Geometry> {

    private static final String[] KEYS = {"single", "start", "middle", "end"};

    @Override
    public Geometry read(JsonObject json, JsonDeserializationContext context) {
        List<ResourceLocation> parts = new ArrayList<>(KEYS.length);
        ResourceLocation fallback = ResourceLocation.parse(GsonHelper.getAsString(json, "single"));
        for (String key : KEYS) {
            parts.add(json.has(key) ? ResourceLocation.parse(GsonHelper.getAsString(json, key)) : fallback);
        }
        return new Geometry(List.copyOf(parts));
    }

    public record Geometry(List<ResourceLocation> parts) implements IUnbakedGeometry<Geometry> {

        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter,
                                   IGeometryBakingContext context) {
            for (ResourceLocation part : parts) {
                modelGetter.apply(part).resolveParents(modelGetter);
            }
        }

        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                               Function<Material, TextureAtlasSprite> spriteGetter,
                               ModelState modelState, ItemOverrides overrides) {
            List<BakedModel> baked = new ArrayList<>(parts.size());
            for (ResourceLocation part : parts) {
                baked.add(baker.bake(part, modelState, spriteGetter));
            }
            return new ConnectedVariantModel(baked.get(0), baked.get(1), baked.get(2), baked.get(3));
        }
    }
}

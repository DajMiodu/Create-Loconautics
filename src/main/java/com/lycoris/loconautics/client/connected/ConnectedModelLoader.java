package com.lycoris.loconautics.client.connected;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class ConnectedModelLoader implements IGeometryLoader<ConnectedModelLoader.Geometry> {

    @Override
    public Geometry read(JsonObject json, JsonDeserializationContext context) {
        return new Geometry();
    }

    public record Geometry() implements IUnbakedGeometry<Geometry> {

        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                               Function<Material, TextureAtlasSprite> spriteGetter,
                               ModelState modelState, ItemOverrides overrides) {
            TextureAtlasSprite strip = sprite(context, spriteGetter, "strip");
            TextureAtlasSprite cap = sprite(context, spriteGetter, "cap");
            TextureAtlasSprite inner = sprite(context, spriteGetter, "inner");
            TextureAtlasSprite innerCap = sprite(context, spriteGetter, "inner_cap");
            TextureAtlasSprite particle = sprite(context, spriteGetter, "particle");

            if (inner == null) inner = strip;
            if (innerCap == null) innerCap = cap;
            if (particle == null) particle = strip != null ? strip : cap;
            return new ConnectedModel(strip, cap, inner, innerCap, particle, context.getTransforms());
        }

        private static @Nullable TextureAtlasSprite sprite(IGeometryBakingContext context,
                                                           Function<Material, TextureAtlasSprite> spriteGetter,
                                                           String key) {
            return context.hasMaterial(key) ? spriteGetter.apply(context.getMaterial(key)) : null;
        }
    }
}

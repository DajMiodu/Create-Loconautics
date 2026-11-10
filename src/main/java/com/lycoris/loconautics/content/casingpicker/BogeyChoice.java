package com.lycoris.loconautics.content.casingpicker;

import com.lycoris.loconautics.compat.railways.RailwaysBogeyChoice;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllBogeyStyles;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

import java.util.UUID;

public record BogeyChoice(BogeyStyle style, BogeySizes.BogeySize size) {

    public static final BogeyChoice CREATE_STANDARD = new BogeyChoice(AllBogeyStyles.STANDARD, BogeySizes.SMALL);

    public static final Codec<BogeyChoice> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("style").forGetter(choice -> choice.style().id),
            ResourceLocation.CODEC.fieldOf("size").forGetter(choice -> choice.size().id())
    ).apply(instance, BogeyChoice::fromIds));

    public static final StreamCodec<ByteBuf, BogeyChoice> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, choice -> choice.style().id,
            ResourceLocation.STREAM_CODEC, choice -> choice.size().id(),
            BogeyChoice::fromIds);

    public static BogeyChoice of(UUID player) {
        return ModList.get().isLoaded("railways") ? RailwaysBogeyChoice.selected(player) : CREATE_STANDARD;
    }

    private static BogeyChoice fromIds(ResourceLocation styleId, ResourceLocation sizeId) {
        BogeyStyle style = AllBogeyStyles.BOGEY_STYLES.get(styleId);
        BogeySizes.BogeySize size = BogeySizes.all().get(sizeId);
        if (style == null || size == null || !style.validSizes().contains(size)) return CREATE_STANDARD;
        return new BogeyChoice(style, size);
    }

    public boolean isStandard() {
        return style == AllBogeyStyles.STANDARD;
    }

    public AbstractBogeyBlock<?> block() {
        return style.getBlockForSize(size);
    }
}

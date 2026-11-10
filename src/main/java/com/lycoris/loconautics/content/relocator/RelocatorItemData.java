package com.lycoris.loconautics.content.relocator;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RelocatorItemData {

    public static final String TYPE_CONSIST = "CONSIST";
    public static final String TYPE_CARRIAGE = "CARRIAGE";
    public static final String TYPE_STORED = "STORED";

    private static final String KEY_TYPE = "RelocatorType";
    private static final String KEY_TRAIN = "TrainId";
    private static final String KEY_NAME = "TrainName";
    private static final String KEY_CONSIST = "ConsistOrder";
    private static final String KEY_CARRIAGE = "CarriageId";
    private static final String KEY_LABEL = "CarriageLabel";
    private static final String KEY_STRUCTURES = "Structures";
    private static final String KEY_SPAN = "Span";

    private RelocatorItemData() {
    }

    public static ItemStack forConsist(UUID trainId, @Nullable String trainName, List<UUID> order) {
        CompoundTag tag = base(TYPE_CONSIST, trainId, trainName);
        ListTag list = new ListTag();
        for (UUID id : order) {
            list.add(StringTag.valueOf(id.toString()));
        }
        tag.put(KEY_CONSIST, list);
        return build(tag, trainName, order.size() + " carriage(s)");
    }

    public static ItemStack forCarriage(UUID trainId, @Nullable String trainName, UUID subLevelId, String label) {
        CompoundTag tag = base(TYPE_CARRIAGE, trainId, trainName);
        tag.putUUID(KEY_CARRIAGE, subLevelId);
        tag.putString(KEY_LABEL, label);
        return build(tag, trainName, "carriage " + label);
    }

    // the whole cart travels in the item, so it can be put down in a world that never saw the original body
    public static ItemStack forStored(UUID trainId, @Nullable String trainName, List<CompoundTag> structures,
                                      double span) {
        CompoundTag tag = base(TYPE_STORED, trainId, trainName);
        ListTag list = new ListTag();
        list.addAll(structures);
        tag.put(KEY_STRUCTURES, list);
        tag.putDouble(KEY_SPAN, span);
        return build(tag, trainName, structures.size() + " carriage(s), right click a track to place");
    }

    private static CompoundTag base(String type, UUID trainId, @Nullable String trainName) {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_TYPE, type);
        tag.putUUID(KEY_TRAIN, trainId);
        if (trainName != null && !trainName.isBlank()) {
            tag.putString(KEY_NAME, trainName);
        }
        return tag;
    }

    private static ItemStack build(CompoundTag tag, @Nullable String trainName, String subtitle) {
        ItemStack stack = new ItemStack(LoconauticsRegistries.CREATIVE_TRAIN_RELOCATOR.get());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(
                        trainName != null && !trainName.isBlank() ? trainName : "Stored Train")
                .withStyle(ChatFormatting.AQUA).withStyle(Style.EMPTY.withItalic(false)));
        stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(
                List.of(Component.literal(subtitle).withStyle(ChatFormatting.GRAY))));
        return stack;
    }

    @Nullable
    public static CompoundTag read(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        return tag.contains(KEY_TYPE) ? tag : null;
    }

    public static boolean isStored(CompoundTag tag) {
        return TYPE_STORED.equals(tag.getString(KEY_TYPE));
    }

    public static List<CompoundTag> structures(CompoundTag tag) {
        List<CompoundTag> out = new ArrayList<>();
        ListTag list = tag.getList(KEY_STRUCTURES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            out.add(list.getCompound(i));
        }
        return out;
    }

    public static double span(CompoundTag tag) {
        return tag.getDouble(KEY_SPAN);
    }

    public static boolean isConsist(CompoundTag tag) {
        return TYPE_CONSIST.equals(tag.getString(KEY_TYPE));
    }

    @Nullable
    public static UUID trainId(CompoundTag tag) {
        return tag.hasUUID(KEY_TRAIN) ? tag.getUUID(KEY_TRAIN) : null;
    }

    @Nullable
    public static UUID carriageId(CompoundTag tag) {
        return tag.hasUUID(KEY_CARRIAGE) ? tag.getUUID(KEY_CARRIAGE) : null;
    }

    @Nullable
    public static String name(CompoundTag tag) {
        return tag.contains(KEY_NAME) ? tag.getString(KEY_NAME) : null;
    }

    public static String label(CompoundTag tag) {
        return tag.getString(KEY_LABEL);
    }

    public static List<UUID> consistOrder(CompoundTag tag) {
        List<UUID> out = new ArrayList<>();
        ListTag list = tag.getList(KEY_CONSIST, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            try {
                out.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException stale) {
            }
        }
        return out;
    }
}

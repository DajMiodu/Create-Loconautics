package com.lycoris.loconautics.content.frequency;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

public final class FrequencyBindUtil {

    public static final String TAG = "BoundFrequencies";

    public enum Result {
        EMPTY,
        PARTIAL,
        FULL
    }

    public record Pair(String key, ItemStack first, ItemStack second) {
        public boolean isPresent() {
            return !first.isEmpty() || !second.isEmpty();
        }
    }

    private FrequencyBindUtil() {
    }

    public static Result bind(ItemStack target, HolderLookup.Provider registries, List<Pair> pairs) {
        int present = 0;
        for (Pair p : pairs) {
            if (p.isPresent()) {
                present++;
            }
        }
        if (present == 0) {
            return Result.EMPTY;
        }

        CompoundTag tag = target.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag bound = tag.getCompound(TAG);
        for (Pair p : pairs) {
            CompoundTag entry = new CompoundTag();
            entry.put("First", p.first().saveOptional(registries));
            entry.put("Second", p.second().saveOptional(registries));
            bound.put(p.key(), entry);
        }
        tag.put(TAG, bound);
        target.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        target.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);

        return present == pairs.size() ? Result.FULL : Result.PARTIAL;
    }

    private static CompoundTag pairsToTag(HolderLookup.Provider registries, List<Pair> pairs) {
        CompoundTag root = new CompoundTag();
        for (Pair p : pairs) {
            CompoundTag entry = new CompoundTag();
            entry.put("First", p.first().saveOptional(registries));
            entry.put("Second", p.second().saveOptional(registries));
            root.put(p.key(), entry);
        }
        return root;
    }

    public static ItemStack[] read(ItemStack stack, HolderLookup.Provider registries, String key) {
        CompoundTag tag =
                stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(TAG);
        if (!tag.contains(key)) {
            return null;
        }
        CompoundTag entry = tag.getCompound(key);
        return new ItemStack[]{
                ItemStack.parseOptional(registries, entry.getCompound("First")),
                ItemStack.parseOptional(registries, entry.getCompound("Second"))
        };
    }

    public static boolean isBound(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains(TAG);
    }

    public static boolean hasGlint(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
    }

    public static boolean conflicts(ItemStack stack, HolderLookup.Provider registries, List<Pair> pairs) {
        CompoundTag stored =
                stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(TAG);
        CompoundTag incoming = pairsToTag(registries, pairs);
        for (Pair p : pairs) {
            if (stored.contains(p.key()) && !stored.getCompound(p.key()).equals(incoming.getCompound(p.key()))) {
                return true;
            }
        }
        return false;
    }

    public static boolean unbind(ItemStack stack) {
        boolean changed = false;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(TAG)) {
            tag.remove(TAG);
            if (tag.isEmpty()) {
                stack.remove(DataComponents.CUSTOM_DATA);
            } else {
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
            changed = true;
        }
        if (hasGlint(stack)) {
            stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
            changed = true;
        }
        return changed;
    }
}

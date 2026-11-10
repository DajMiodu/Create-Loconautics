package com.lycoris.loconautics.content.trainmail;

import net.createmod.catnip.data.Pair;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;

public final class PostalOrder {

    private static final Set<ResourceLocation> POSTAL_IDS = Set.of(
            ResourceLocation.fromNamespaceAndPath("create", "package_delivery"),
            ResourceLocation.fromNamespaceAndPath("create", "package_retrieval"));

    private PostalOrder() {
    }

    public static <T> void moveLast(List<Pair<ResourceLocation, T>> types) {
        int lastOther = -1;
        int firstPostal = Integer.MAX_VALUE;
        for (int i = 0; i < types.size(); i++) {
            if (POSTAL_IDS.contains(types.get(i).getFirst())) {
                firstPostal = Math.min(firstPostal, i);
            } else {
                lastOther = i;
            }
        }
        if (firstPostal > lastOther) {
            return;
        }
        List<Pair<ResourceLocation, T>> postal = types.stream()
                .filter(pair -> POSTAL_IDS.contains(pair.getFirst()))
                .toList();
        types.removeAll(postal);
        types.addAll(postal);
    }
}

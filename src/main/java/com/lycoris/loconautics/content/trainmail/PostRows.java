package com.lycoris.loconautics.content.trainmail;

import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.data.Glob;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class PostRows {

    public static final String NAME_KEY = "Text";
    public static final String STATION_KEY = "PostStation";

    private PostRows() {
    }

    public static String nameRegex(String text) {
        return Glob.toRegexPattern(text.isBlank() ? "*" : text, "");
    }

    @Nullable
    public static String stationRegex(String text) {
        return text.isBlank() ? null : Glob.toRegexPattern(text, "");
    }

    public static List<Component> deliverTitle(String station, String name) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("loconautics.schedule.post.title.office").withStyle(ChatFormatting.GOLD));
        lines.add(quoted(station, "loconautics.schedule.post.any_station"));
        lines.add(gray("loconautics.schedule.post.title.deliver"));
        lines.add(quoted(name, "loconautics.schedule.post.any_address"));
        return lines;
    }

    public static List<Component> fetchTitle(String station, String name) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("loconautics.schedule.post.title.office").withStyle(ChatFormatting.GOLD));
        lines.add(quoted(station, "loconautics.schedule.post.any_station"));
        lines.add(gray("loconautics.schedule.post.title.fetch"));
        lines.add(quoted(name, "loconautics.schedule.post.any_address"));
        return lines;
    }

    public static List<Component> stationTooltip(boolean delivers) {
        String kind = delivers ? "deliver" : "fetch";
        return List.of(Component.translatable("loconautics.schedule.post.station"),
                gray("loconautics.schedule.post.wildcard"),
                darkGray("loconautics.schedule.post." + kind + ".station.1"),
                darkGray("loconautics.schedule.post." + kind + ".station.2"));
    }

    public static List<Component> addressTooltip(boolean delivers) {
        String kind = delivers ? "deliver" : "fetch";
        return List.of(Component.translatable("loconautics.schedule.post.address"),
                gray("loconautics.schedule.post.wildcard"),
                darkGray("loconautics.schedule.post." + kind + ".address.1"),
                darkGray("loconautics.schedule.post." + kind + ".address.2"));
    }

    private static Component quoted(String text, String blankKey) {
        return CreateLang.translateDirect("generic.in_quotes", text.isBlank()
                ? Component.translatable(blankKey)
                : Component.literal(text));
    }

    private static Component gray(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.GRAY);
    }

    private static Component darkGray(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY);
    }
}

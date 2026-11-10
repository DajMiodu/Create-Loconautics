package com.lycoris.loconautics.foundation;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Optional;

public final class LabelText {

    private LabelText() {
    }

    public static Component parse(Component name) {
        if (!name.getStyle().isEmpty() || !name.getSiblings().isEmpty()) return name;
        String raw = name.getString();
        if (raw.indexOf('&') < 0 && raw.indexOf('§') < 0) return name;

        MutableComponent out = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            ChatFormatting format = (c == '&' || c == '§') && i + 1 < raw.length()
                    ? ChatFormatting.getByCode(raw.charAt(i + 1)) : null;
            if (format == null) {
                run.append(c);
                continue;
            }
            if (!run.isEmpty()) out.append(Component.literal(run.toString()).withStyle(style));
            run.setLength(0);
            style = format == ChatFormatting.RESET ? Style.EMPTY : style.applyFormat(format);
            i++;
        }
        if (!run.isEmpty()) out.append(Component.literal(run.toString()).withStyle(style));
        return out;
    }

    public static String toRaw(Component label) {
        StringBuilder raw = new StringBuilder();
        Style[] previous = {Style.EMPTY};
        label.visit((style, text) -> {
            if (text.isEmpty()) return Optional.empty();
            appendStyleChange(raw, previous[0], style);
            raw.append(text);
            previous[0] = style;
            return Optional.empty();
        }, Style.EMPTY);
        return raw.toString();
    }

    private static void appendStyleChange(StringBuilder raw, Style from, Style to) {
        boolean dropped = from.getColor() != null && to.getColor() == null
                || from.isBold() && !to.isBold()
                || from.isItalic() && !to.isItalic()
                || from.isUnderlined() && !to.isUnderlined()
                || from.isStrikethrough() && !to.isStrikethrough()
                || from.isObfuscated() && !to.isObfuscated();
        if (dropped) {
            raw.append('&').append(ChatFormatting.RESET.getChar());
            from = Style.EMPTY;
        }
        TextColor color = to.getColor();
        if (color != null && !color.equals(from.getColor())) {
            for (ChatFormatting format : ChatFormatting.values()) {
                if (format.isColor() && format.getColor() == color.getValue()) {
                    raw.append('&').append(format.getChar());
                    break;
                }
            }
        }
        if (to.isBold() && !from.isBold()) raw.append('&').append(ChatFormatting.BOLD.getChar());
        if (to.isItalic() && !from.isItalic()) raw.append('&').append(ChatFormatting.ITALIC.getChar());
        if (to.isUnderlined() && !from.isUnderlined()) raw.append('&').append(ChatFormatting.UNDERLINE.getChar());
        if (to.isStrikethrough() && !from.isStrikethrough()) {
            raw.append('&').append(ChatFormatting.STRIKETHROUGH.getChar());
        }
        if (to.isObfuscated() && !from.isObfuscated()) raw.append('&').append(ChatFormatting.OBFUSCATED.getChar());
    }
}

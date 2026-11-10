package com.lycoris.loconautics.foundation;

import net.createmod.catnip.lang.Lang;
import net.createmod.catnip.lang.LangBuilder;

import java.util.Locale;

public final class LoconauticsLang {

    private LoconauticsLang() {
    }

    public static LangBuilder builder() {
        return Lang.builder("loconautics");
    }

    public static LangBuilder translate(String key, Object... args) {
        return builder().translate(key, args);
    }

    public static LangBuilder number(double number) {
        return builder().text(String.format(Locale.ROOT, "%.1f", number));
    }
}
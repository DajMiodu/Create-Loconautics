package com.lycoris.loconautics.content.controls.panel.style;

import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class PanelStyleSet {

    private final String partName;
    private final List<PanelStyle> styles = new ArrayList<>();
    private final List<PanelOption> options = new ArrayList<>();

    PanelStyleSet(String partName) {
        this.partName = partName;
    }

    void register(String id, @Nullable PanelStyle.ModelKit kit) {
        styles.add(new PanelStyle(id, kit));
    }

    void register(String id, @Nullable PanelStyle.ModelKit kit, boolean options) {
        styles.add(new PanelStyle(id, kit, options));
    }

    int registerOption(String id, boolean defaultOn) {
        options.add(new PanelOption(id, defaultOn));
        return options.size() - 1;
    }

    public int count() {
        return styles.size();
    }

    public int clamp(int index) {
        return Mth.clamp(index, 0, styles.size() - 1);
    }

    public List<PanelStyle> all() {
        return styles;
    }

    public PanelStyle get(int index) {
        return styles.get(clamp(index));
    }

    public String langKey(int index) {
        return "gui.loconautics.selector.style." + partName + "." + get(index).id();
    }

    public int optionCount() {
        return options.size();
    }

    public PanelOption option(int index) {
        return options.get(index);
    }

    public String optionLangKey(int index) {
        return "gui.loconautics.selector.option." + partName + "." + options.get(index).id();
    }

    public int defaultOptions() {
        int mask = 0;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).defaultOn()) mask |= 1 << i;
        }
        return mask;
    }

    public int clampOptions(int mask) {
        return mask & ((1 << options.size()) - 1);
    }

    public boolean hasOptions(int style) {
        return get(style).options();
    }

    public int effectiveOptions(int mask, int style) {
        return hasOptions(style) ? mask : 0;
    }

    public boolean isOn(int mask, int option) {
        return (mask & (1 << option)) != 0;
    }

    public int toggle(int mask, int option) {
        return mask ^ (1 << option);
    }
}

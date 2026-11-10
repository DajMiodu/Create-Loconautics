package com.lycoris.loconautics.content.controls.panel.style;

import org.jetbrains.annotations.Nullable;

public record PanelStyle(String id, @Nullable ModelKit kit, boolean options) {

    public PanelStyle(String id, @Nullable ModelKit kit) {
        this(id, kit, true);
    }

    public record ModelKit(String name, int knobStrideX, int knobStrideY, boolean base) {
    }
}

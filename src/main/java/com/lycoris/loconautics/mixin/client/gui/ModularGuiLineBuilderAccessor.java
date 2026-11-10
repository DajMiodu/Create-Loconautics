package com.lycoris.loconautics.mixin.client.gui;

import com.simibubi.create.foundation.gui.ModularGuiLine;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ModularGuiLineBuilder.class)
public interface ModularGuiLineBuilderAccessor {
    @Accessor("target")
    ModularGuiLine loconautics$getTarget();

    @Accessor("font")
    Font loconautics$getFont();

    @Accessor("x")
    int loconautics$getX();

    @Accessor("y")
    int loconautics$getY();
}

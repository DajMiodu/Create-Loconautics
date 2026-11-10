package com.lycoris.loconautics.mixin.client.gui;

import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.config.ui.SubMenuConfigScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SubMenuConfigScreen.class, remap = false)
public interface SubMenuConfigScreenAccessor {

    @Accessor("title")
    String loconautics$getTitle();

    @Accessor("list")
    ConfigScreenList loconautics$getList();
}

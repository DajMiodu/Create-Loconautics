package com.lycoris.loconautics.content.boiler.barrel;

import com.lycoris.loconautics.LoconauticsConstants;
import com.simibubi.create.foundation.block.connected.CTType;
import com.simibubi.create.foundation.block.connected.CTTypeRegistry;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import net.minecraft.resources.ResourceLocation;

public class BoilerCTType implements CTType {

    public static final BoilerCTType INSTANCE = new BoilerCTType();
    public static final int ROWS = 3;

    private static boolean registered;

    private final ResourceLocation id = LoconauticsConstants.id("boiler_barrel");

    public static void register() {
        if (registered) return;
        registered = true;
        CTTypeRegistry.register(INSTANCE);
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public int getSheetSize() {
        return 1;
    }

    @Override
    public ConnectedTextureBehaviour.ContextRequirement getContextRequirement() {
        return ConnectedTextureBehaviour.ContextRequirement.builder().horizontal().build();
    }

    @Override
    public int getTextureIndex(ConnectedTextureBehaviour.CTContext context) {
        boolean r = context.right;
        boolean l = context.left;
        if (r && !l) return 0;
        if (l && !r) return 2;
        return 1;
    }
}

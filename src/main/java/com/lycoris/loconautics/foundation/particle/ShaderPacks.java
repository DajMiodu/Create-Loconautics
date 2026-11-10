package com.lycoris.loconautics.foundation.particle;

import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

public final class ShaderPacks {

    private static final Object API;
    private static final Method IN_USE;

    static {
        Object api = null;
        Method inUse = null;
        if (ModList.get().isLoaded("iris")) {
            try {
                Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = type.getMethod("getInstance").invoke(null);
                inUse = type.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException e) {
                api = null;
            }
        }
        API = api;
        IN_USE = inUse;
    }

    private ShaderPacks() {
    }

    public static boolean inUse() {
        if (API == null) {
            return false;
        }
        try {
            return (boolean) IN_USE.invoke(API);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("iris shader pack check failed", e);
        }
    }
}

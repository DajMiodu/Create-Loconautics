package com.lycoris.loconautics.compat;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

// reads optional mods without a compile time link: every lookup that does not resolve answers null
public final class Reflected {

    private static final Map<String, Optional<Method>> METHODS = new ConcurrentHashMap<>();

    private Reflected() {
    }

    @Nullable
    public static Class<?> classOf(String name) {
        try {
            return Class.forName(name, false, Reflected.class.getClassLoader());
        } catch (Throwable missing) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public static Class<? extends BlockEntity> blockEntity(String name) {
        Class<?> type = classOf(name);
        return type != null && BlockEntity.class.isAssignableFrom(type) ? (Class<? extends BlockEntity>) type : null;
    }

    @SafeVarargs
    public static List<Class<? extends BlockEntity>> present(Class<? extends BlockEntity>... types) {
        List<Class<? extends BlockEntity>> found = new ArrayList<>();
        for (Class<? extends BlockEntity> type : types) {
            if (type != null) found.add(type);
        }
        return found;
    }

    public static boolean is(@Nullable Class<?> type, Object target) {
        return type != null && type.isInstance(target);
    }

    @Nullable
    private static Method method(Class<?> type, String name) {
        return METHODS.computeIfAbsent(type.getName() + "#" + name, key -> {
            try {
                Method found = type.getMethod(name);
                try {
                    found.setAccessible(true);
                } catch (RuntimeException closed) {
                    // a public method on a public class works without it
                }
                return Optional.of(found);
            } catch (Throwable missing) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    // public method without arguments, null when it does not exist or throws
    @Nullable
    public static Object call(@Nullable Object target, String name) {
        if (target == null) return null;
        Method found = method(target.getClass(), name);
        if (found == null) return null;
        try {
            return found.invoke(target);
        } catch (Throwable failed) {
            return null;
        }
    }

    @Nullable
    public static Double number(@Nullable Object target, String name) {
        return call(target, name) instanceof Number value ? value.doubleValue() : null;
    }

    @Nullable
    public static Object callStatic(@Nullable Class<?> type, String name) {
        if (type == null) return null;
        Method found = method(type, name);
        if (found == null) return null;
        try {
            return found.invoke(null);
        } catch (Throwable failed) {
            return null;
        }
    }
}

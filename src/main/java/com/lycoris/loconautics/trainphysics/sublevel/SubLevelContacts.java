package com.lycoris.loconautics.trainphysics.sublevel;

import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SubLevelContacts {

    private SubLevelContacts() {
    }

    public record Pair(UUID first, UUID second) {

        public static Pair of(UUID a, UUID b) {
            return a.compareTo(b) <= 0 ? new Pair(a, b) : new Pair(b, a);
        }
    }

    private record Contact(FreeConstraintHandle handle, ServerSubLevel a, ServerSubLevel b) {}

    private static final Map<Pair, Contact> DISABLED = new HashMap<>();

    public static void clear() {
        DISABLED.clear();
    }

    public static boolean isDisabled(UUID a, UUID b) {
        Contact contact = DISABLED.get(Pair.of(a, b));
        return contact != null && contact.handle().isValid();
    }

    public static Set<Pair> disabledPairs() {
        return Set.copyOf(DISABLED.keySet());
    }

    // a constraint with contacts off is the only panellever sable gives, so this one is free on all six axes and carries no motor
    public static boolean disable(ServerSubLevel a, ServerSubLevel b) {
        if (a == null || b == null || a == b || a.isRemoved() || b.isRemoved()) {
            return false;
        }
        Pair key = Pair.of(a.getUniqueId(), b.getUniqueId());
        Contact live = DISABLED.get(key);
        if (live != null && live.handle().isValid()) {
            return true;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(a.getLevel());
        if (container == null) {
            return false;
        }
        FreeConstraintConfiguration config =
                new FreeConstraintConfiguration(plotCenter(a), plotCenter(b), new Quaterniond());
        FreeConstraintHandle handle = container.physicsSystem().getPipeline().addConstraint(a, b, config);
        if (handle == null) {
            return false;
        }
        handle.setContactsEnabled(false);
        DISABLED.put(key, new Contact(handle, a, b));
        return true;
    }

    // the wheel plates are their own bodies, so a coupled pair has to switch them off against the other car too
    public static boolean disableCoupled(ServerSubLevel a, ServerSubLevel b) {
        boolean done = disable(a, b);
        ServerSubLevelContainer container = a == null ? null : SubLevelContainer.getContainer(a.getLevel());
        if (container == null || b == null) {
            return done;
        }
        List<ServerSubLevel> sidesA = withWheels(container, a);
        List<ServerSubLevel> sidesB = withWheels(container, b);
        for (ServerSubLevel x : sidesA) {
            for (ServerSubLevel y : sidesB) {
                if (x != a || y != b) {
                    disable(x, y);
                }
            }
        }
        return done;
    }

    public static void enableCoupled(UUID a, UUID b) {
        List<UUID> sidesA = new ArrayList<>(GhostWheelBodies.wheelBodiesOf(a));
        List<UUID> sidesB = new ArrayList<>(GhostWheelBodies.wheelBodiesOf(b));
        sidesA.add(a);
        sidesB.add(b);
        for (UUID x : sidesA) {
            for (UUID y : sidesB) {
                enable(x, y);
            }
        }
    }

    private static List<ServerSubLevel> withWheels(ServerSubLevelContainer container, ServerSubLevel car) {
        List<ServerSubLevel> out = new ArrayList<>();
        out.add(car);
        for (UUID id : GhostWheelBodies.wheelBodiesOf(car.getUniqueId())) {
            if (container.getSubLevel(id) instanceof ServerSubLevel wheel && !wheel.isRemoved()) {
                out.add(wheel);
            }
        }
        return out;
    }

    public static void enable(UUID a, UUID b) {
        Contact contact = DISABLED.remove(Pair.of(a, b));
        if (contact != null && contact.handle().isValid()) {
            contact.handle().remove();
        }
    }

    // a constraint outliving its body is the whole problem, so release before removal
    public static void releaseAll(UUID body) {
        DISABLED.entrySet().removeIf(entry -> {
            if (!entry.getKey().first().equals(body) && !entry.getKey().second().equals(body)) {
                return false;
            }
            if (entry.getValue().handle().isValid()) {
                entry.getValue().handle().remove();
            }
            return true;
        });
    }

    // an unload takes the constraint with it, but a removal leaves it live on a body rapier no longer resolves
    public static void sweep() {
        DISABLED.values().removeIf(contact -> {
            if (!contact.handle().isValid()) {
                return true;
            }
            if (!contact.a().isRemoved() && !contact.b().isRemoved()) {
                return false;
            }
            contact.handle().remove();
            return true;
        });
    }

    // validateanchors demands a point inside each bodys own plot, and the plot center always is
    private static Vector3d plotCenter(ServerSubLevel sub) {
        BlockPos center = sub.getPlot().getCenterBlock();
        return new Vector3d(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5);
    }
}

package com.lycoris.loconautics.content.trainmail;

import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface PostalWorld<P> {

    boolean tripped();

    List<String> cargoAddresses(String regex);

    List<GlobalStation> stations();

    @Nullable
    GlobalStation here();

    MinecraftServer server();

    @Nullable
    P routeTo(Collection<GlobalStation> wanted);

    void cannotDeliver(String address);

    double legCost(@Nullable GlobalStation from, GlobalStation to);

    Set<UUID> warned();

    void sayUnreachable(GlobalStation station);

    default void warnUnreachable(GlobalStation station) {
        if (warned().add(station.id)) {
            sayUnreachable(station);
        }
    }

    static boolean same(@Nullable GlobalStation a, @Nullable GlobalStation b) {
        return a == b || (a != null && b != null && a.id != null && a.id.equals(b.id));
    }

    default List<GlobalStation> waitingStations(String regex) {
        List<GlobalStation> out = new ArrayList<>();
        GlobalStation here = here();
        for (GlobalStation station : stations()) {
            if (!same(station, here) && MailPorts.holdsWaitingPackage(server(), station, regex)) {
                out.add(station);
            }
        }
        return out;
    }
}

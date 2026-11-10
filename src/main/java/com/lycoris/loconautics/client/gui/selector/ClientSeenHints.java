package com.lycoris.loconautics.client.gui.selector;

import com.lycoris.loconautics.LoconauticsConstants;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ClientSeenHints {

    private static Set<String> seen;

    private ClientSeenHints() {
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("loconautics-seen-hints.txt");
    }

    private static Set<String> load() {
        if (seen == null) {
            seen = new HashSet<>();
            try {
                if (Files.exists(file())) seen.addAll(Files.readAllLines(file()));
            } catch (IOException e) {
                LoconauticsConstants.LOGGER.warn("could not read the seen hints file", e);
            }
        }
        return seen;
    }

    public static boolean seen(String id) {
        return load().contains(id);
    }

    public static void markSeen(String id) {
        if (!load().add(id)) return;
        try {
            Files.write(file(), List.copyOf(seen));
        } catch (IOException e) {
            LoconauticsConstants.LOGGER.warn("could not save the seen hints file", e);
        }
    }
}

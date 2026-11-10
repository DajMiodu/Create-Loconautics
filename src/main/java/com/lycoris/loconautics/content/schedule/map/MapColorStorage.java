package com.lycoris.loconautics.content.schedule.map;

import com.lycoris.loconautics.LoconauticsConstants;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapColorStorage {

    private static final LevelResource MAP_COLOR_RESOURCE =
            new LevelResource("loconautics/map");

    // one store per dimension, region files are named by chunk coordinate so a shared store makes the nether and overworld fight over a slot
    private static final Map<ResourceKey<Level>, MapColorStorage> STORES = new HashMap<>();

    private final Path                           baseDir;
    private final Map<Long, MapColorRegionFile>  openFiles = new HashMap<>();

    private MapColorStorage(Path baseDir) {
        this.baseDir = baseDir;
    }

    public static synchronized MapColorStorage get(ServerLevel level) {
        return STORES.computeIfAbsent(level.dimension(), key -> {
            Path dir = level.getServer().getWorldPath(MAP_COLOR_RESOURCE)
                    .resolve(key.location().getNamespace())
                    .resolve(key.location().getPath());
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                LoconauticsConstants.LOGGER.error("Failed to create map color directory", e);
            }
            return new MapColorStorage(dir);
        });
    }

    public static synchronized void closeAll() {
        for (MapColorStorage store : STORES.values()) {
            for (MapColorRegionFile file : store.openFiles.values()) {
                try { file.close(); } catch (IOException e) {
                    LoconauticsConstants.LOGGER.error("Failed to close map color region file", e);
                }
            }
            store.openFiles.clear();
        }
        STORES.clear();
    }

    public synchronized void write(int chunkX, int chunkZ, byte[] colors) {
        try {
            MapColorRegionFile region = getOrOpen(regionX(chunkX), regionZ(chunkZ));
            region.write(localX(chunkX), localZ(chunkZ), colors);
        } catch (IOException e) {
            LoconauticsConstants.LOGGER.error(
                    "Failed to write map colors for chunk [{}, {}]", chunkX, chunkZ, e);
        }
    }

    public synchronized byte[] read(int chunkX, int chunkZ) {
        try {
            int rx = regionX(chunkX);
            int rz = regionZ(chunkZ);
            long key = regionKey(rx, rz);
            if (!openFiles.containsKey(key) && !regionFileExists(rx, rz)) return null;
            MapColorRegionFile region = getOrOpen(rx, rz);
            return region.read(localX(chunkX), localZ(chunkZ));
        } catch (IOException e) {
            LoconauticsConstants.LOGGER.error(
                    "Failed to read map colors for chunk [{}, {}]", chunkX, chunkZ, e);
            return null;
        }
    }

    public synchronized List<MapColorEntry> readBatch(List<long[]> coords) {
        List<MapColorEntry> results = new ArrayList<>(coords.size());
        for (long[] coord : coords) {
            int    cx     = (int) coord[0];
            int    cz     = (int) coord[1];
            byte[] colors = read(cx, cz);
            if (colors != null) {
                results.add(new MapColorEntry(cx, cz, colors));
            }
        }
        return results;
    }

    private MapColorRegionFile getOrOpen(int rx, int rz) throws IOException {
        long key = regionKey(rx, rz);
        MapColorRegionFile file = openFiles.get(key);
        if (file == null) {
            Path filePath = baseDir.resolve("r." + rx + "." + rz + ".lms");
            file = new MapColorRegionFile(filePath);
            openFiles.put(key, file);
        }
        return file;
    }

    private boolean regionFileExists(int rx, int rz) {
        return Files.exists(baseDir.resolve("r." + rx + "." + rz + ".lms"));
    }

    private static int regionX(int chunkX) {
        return Math.floorDiv(chunkX, MapColorRegionFile.REGION_SIZE);
    }

    private static int regionZ(int chunkZ) {
        return Math.floorDiv(chunkZ, MapColorRegionFile.REGION_SIZE);
    }

    private static int localX(int chunkX) {
        return Math.floorMod(chunkX, MapColorRegionFile.REGION_SIZE);
    }

    private static int localZ(int chunkZ) {
        return Math.floorMod(chunkZ, MapColorRegionFile.REGION_SIZE);
    }

    private static long regionKey(int rx, int rz) {
        return ((long) rx << 32) | (rz & 0xFFFFFFFFL);
    }

    public record MapColorEntry(int chunkX, int chunkZ, byte[] colors) {}
}

package com.lycoris.loconautics.content.locomotiveready;

import com.lycoris.loconautics.LoconauticsConstants;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class LocomotiveReadyWorld {

    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, LoconauticsConstants.MOD_ID);

    private static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, LoconauticsConstants.MOD_ID);

    private static final DeferredRegister<StructurePieceType> PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, LoconauticsConstants.MOD_ID);

    public static final DeferredHolder<StructureType<?>, StructureType<RailGridStructure>> RAIL_GRID =
            STRUCTURE_TYPES.register("rail_grid", () -> () -> RailGridStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> RAIL_GRID_PIECE =
            PIECE_TYPES.register("rail_grid", () -> (context, tag) -> new RailGridPiece(tag));

    static {
        GENERATORS.register("checker_flat", () -> CheckerFlatGenerator.CODEC);
    }

    private LocomotiveReadyWorld() {}

    public static void register(IEventBus modEventBus) {
        GENERATORS.register(modEventBus);
        STRUCTURE_TYPES.register(modEventBus);
        PIECE_TYPES.register(modEventBus);
    }
}

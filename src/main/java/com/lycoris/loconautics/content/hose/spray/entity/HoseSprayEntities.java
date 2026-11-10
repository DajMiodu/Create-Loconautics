package com.lycoris.loconautics.content.hose.spray.entity;

import com.lycoris.loconautics.LoconauticsConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HoseSprayEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, LoconauticsConstants.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<HoseSprayEntity>> HOSE_SPRAY =
            ENTITY_TYPES.register("hose_spray", () ->
                    EntityType.Builder.of(HoseSprayEntity::new, MobCategory.MISC)
                            .sized(0.2f, 0.2f)
                            .fireImmune()
                            .clientTrackingRange(6)
                            .updateInterval(1)
                            .noSave()
                            .build("hose_spray"));

    private HoseSprayEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
    }
}

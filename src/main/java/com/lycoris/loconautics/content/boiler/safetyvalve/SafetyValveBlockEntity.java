package com.lycoris.loconautics.content.boiler.safetyvalve;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.lycoris.loconautics.foundation.smoke.SmokeOptions;
import com.lycoris.loconautics.foundation.smoke.SmokeStyle;
import com.lycoris.loconautics.trainphysics.fx.ParticleWorldLock;
import com.lycoris.loconautics.trainphysics.portal.PortalCutsClient;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public abstract class SafetyValveBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    private static final ResourceKey<DamageType> SCALD =
            ResourceKey.create(Registries.DAMAGE_TYPE, LoconauticsConstants.id("safety_valve"));
    private static final float SCALD_DAMAGE = 1f;
    private static final int STEAM_COLOR = 0xF2F5F7;

    private float opening;
    private boolean venting;
    private int hissCooldown;

    public SafetyValveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || !venting || opening <= 0f) return;
        if (level.isClientSide) {
            ventSteam();
            return;
        }
        scaldEntities();
    }

    protected Vec3 mouthDirection() {
        return new Vec3(0.0, 1.0, 0.0);
    }

    protected double mouthDistance() {
        return 0.5;
    }

    private Vec3 localMouth() {
        return Vec3.atCenterOf(worldPosition).add(mouthDirection().scale(mouthDistance()));
    }

    private void scaldEntities() {
        Vec3 localMouth = localMouth();
        Vec3 worldMouth = Sable.HELPER.projectOutOfSubLevel(level, localMouth);
        Vec3 worldUp = Sable.HELPER.projectOutOfSubLevel(level, localMouth.add(mouthDirection())).subtract(worldMouth);
        Vec3 tip = worldMouth.add(worldUp.scale(0.5 + 1.5 * opening));
        AABB steam = new AABB(worldMouth, tip).inflate(0.3);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, steam)) {
            entity.igniteForSeconds(3f);
            entity.hurt(level.damageSources().source(SCALD), SCALD_DAMAGE);
        }
    }

    private void ventSteam() {
        RandomSource random = level.random;
        Vec3 localMouth = localMouth();
        if (PortalCutsClient.hiddenAtPlot(localMouth.x, localMouth.y, localMouth.z)) {
            return;
        }
        Vec3 worldMouth = Sable.HELPER.projectOutOfSubLevel(level, localMouth);
        Vec3 worldUp = Sable.HELPER.projectOutOfSubLevel(level, localMouth.add(mouthDirection())).subtract(worldMouth);

        float perTick = 0.8f + opening * 2.2f;
        int count = (int) perTick + (random.nextFloat() < perTick - (int) perTick ? 1 : 0);
        double speed = 0.18 + 0.42 * opening;
        float scale = 0.8f + 0.7f * opening;
        Vec3 spread = new Vec3(1.0 - Math.abs(worldUp.x), 1.0 - Math.abs(worldUp.y), 1.0 - Math.abs(worldUp.z));
        for (int i = 0; i < count; i++) {
            ParticleWorldLock.addParticle(level, new SmokeOptions(STEAM_COLOR, SmokeStyle.STEAM, scale),
                    worldMouth.x + (random.nextDouble() - 0.5) * 0.25 * spread.x,
                    worldMouth.y + (random.nextDouble() - 0.5) * 0.25 * spread.y,
                    worldMouth.z + (random.nextDouble() - 0.5) * 0.25 * spread.z,
                    worldUp.x * speed + (random.nextDouble() - 0.5) * 0.05 * spread.x,
                    worldUp.y * speed + (random.nextDouble() - 0.5) * 0.05 * spread.y,
                    worldUp.z * speed + (random.nextDouble() - 0.5) * 0.05 * spread.z);
        }

        if (--hissCooldown <= 0) {
            AllSoundEvents.STEAM.playAt(level, worldMouth, 0.3f + 0.7f * opening, 0.7f + 0.5f * opening, false);
            hissCooldown = 4 + Math.round((1f - opening) * 8f);
        }
    }

    public float opening() {
        return opening;
    }

    public void setVenting(boolean value) {
        if (venting == value) return;
        venting = value;
        setChanged();
        sendData();
    }

    public void setOpening(float value) {
        float clamped = Mth.clamp(value, 0f, 1f);
        if (Math.abs(clamped - opening) < 0.001f) return;
        opening = clamped;
        setChanged();
        sendData();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        LoconauticsLang.translate("gui.goggles.safety_valve_stats").forGoggles(tooltip);
        LoconauticsLang.translate("gui.goggles.safety_valve_opening")
                .style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.builder().text(Mth.floor(opening * 100f) + "%")
                .style(opening <= 0f ? ChatFormatting.RED : ChatFormatting.GOLD)
                .forGoggles(tooltip, 1);
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("Opening", opening);
        tag.putBoolean("Venting", venting);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        opening = tag.getFloat("Opening");
        venting = tag.getBoolean("Venting");
    }
}

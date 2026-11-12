package com.lycoris.loconautics.network.packets.train;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.drive.TrainSyncCache;
import com.lycoris.loconautics.trainphysics.portal.PortalTwins;
import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record SableTrainSyncPacket(UUID subLevelId, UUID trainId, boolean present, double bogeySpacing,
                                   double consistLength,
                                   boolean derailed, boolean brakeHard, double speed, double wheelDrive,
                                   int offTrackBogeys,
                                   Map<BlockPos, Float> drivenBogeys, float slipFactor,
                                   Set<BlockPos> slippingBogeys, double topSpeed, Set<BlockPos> workingBogeys,
                                   boolean endOfTrack)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SableTrainSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("sable_train_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SableTrainSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUUID(p.subLevelId());
                        buf.writeUUID(p.trainId());
                        buf.writeBoolean(p.present());
                        buf.writeDouble(p.bogeySpacing());
                        buf.writeDouble(p.consistLength());
                        buf.writeBoolean(p.derailed());
                        buf.writeBoolean(p.brakeHard());
                        buf.writeDouble(p.speed());
                        buf.writeDouble(p.wheelDrive());
                        buf.writeByte(p.offTrackBogeys());
                        buf.writeVarInt(p.drivenBogeys().size());
                        for (Map.Entry<BlockPos, Float> entry : p.drivenBogeys().entrySet()) {
                            buf.writeBlockPos(entry.getKey());
                            buf.writeFloat(entry.getValue());
                        }
                        buf.writeFloat(p.slipFactor());
                        buf.writeVarInt(p.slippingBogeys().size());
                        for (BlockPos pos : p.slippingBogeys()) {
                            buf.writeBlockPos(pos);
                        }
                        buf.writeDouble(p.topSpeed());
                        buf.writeVarInt(p.workingBogeys().size());
                        for (BlockPos pos : p.workingBogeys()) {
                            buf.writeBlockPos(pos);
                        }
                        buf.writeBoolean(p.endOfTrack());
                    },
                    buf -> new SableTrainSyncPacket(buf.readUUID(), buf.readUUID(), buf.readBoolean(), buf.readDouble(),
                            buf.readDouble(), buf.readBoolean(), buf.readBoolean(), buf.readDouble(), buf.readDouble(),
                            buf.readByte(), readDriven(buf), buf.readFloat(), readSlipping(buf), buf.readDouble(), readSlipping(buf),
                            buf.readBoolean())
            );

    private static Map<BlockPos, Float> readDriven(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        Map<BlockPos, Float> driven = new LinkedHashMap<>(Math.max(1, count));
        for (int i = 0; i < count; i++) {
            driven.put(buf.readBlockPos(), buf.readFloat());
        }
        return driven;
    }

    private static Set<BlockPos> readSlipping(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        Set<BlockPos> slipping = new LinkedHashSet<>(Math.max(1, count));
        for (int i = 0; i < count; i++) {
            slipping.add(buf.readBlockPos());
        }
        return slipping;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SableTrainSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (packet.present()) {
                SableTrainClientRegistry.put(packet.subLevelId(), packet.trainId(), packet.bogeySpacing(),
                        packet.consistLength(), packet.derailed(), packet.brakeHard(), packet.speed(), packet.wheelDrive(), packet.offTrackBogeys(),
                        packet.drivenBogeys(), packet.slipFactor(), packet.slippingBogeys(), packet.topSpeed(), packet.workingBogeys(), packet.endOfTrack());
            } else {
                SableTrainClientRegistry.remove(packet.subLevelId());
            }
        });
    }

    public static SableTrainSyncPacket add(SableTrain train) {
        SableTrain.Car car = train.car();
        double spacing = car.carriage() != null ? car.carriage().bogeySpacing() : 1.0;
        Map<BlockPos, Float> driven = new LinkedHashMap<>();
        train.drivenBogeys().forEach((pos, rate) -> driven.put(pos, rate.floatValue()));
        double consist = TrainSyncCache.syncedConsistLength(train);
        return new SableTrainSyncPacket(car.subLevelId(), train.id(), true, spacing, consist, train.isDerailed(),
                train.brakeHard(), train.speed(), train.wheelDrive(), train.offTrackBogeyCount(), driven,
                (float) train.slipFactor(), Set.copyOf(train.slippingBogeys()),
                com.lycoris.loconautics.trainphysics.drive.TrainPropulsion.gearedTopBpt(train),
                rollingBogeys(train), train.derailedAtTrackEnd());
    }

    private static Set<BlockPos> rollingBogeys(SableTrain train) {
        Set<BlockPos> rolling = new HashSet<>();
        train.car().bogeys().forEach(b -> rolling.add(b.localPos()));
        rolling.addAll(com.lycoris.loconautics.trainphysics.spawn.SubLevelSplitWatcher.idleBogeysOnRail(train));
        return rolling;
    }

    public static SableTrainSyncPacket remove(UUID subLevelId) {
        return new SableTrainSyncPacket(subLevelId, subLevelId, false, 0.0, 0.0, false, false, 0.0, 0.0, 0, Map.of(), 1.0f, Set.of(), 0.0, Set.of(), false);
    }

    public static void broadcast(SableTrain train) {
        if (train.car().subLevelId() == null) {
            return;
        }
        PacketDistributor.sendToAllPlayers(add(train));
        announceTwin(train);
    }

    public static void announceTwin(SableTrain train) {
        UUID twinId = PortalTwins.twinOf(train.car().subLevelId());
        if (twinId != null) {
            PacketDistributor.sendToAllPlayers(asBody(add(train), twinId));
        }
    }

    private static SableTrainSyncPacket asBody(SableTrainSyncPacket p, UUID bodyId) {
        return new SableTrainSyncPacket(bodyId, p.trainId(), p.present(), p.bogeySpacing(), p.consistLength(),
                p.derailed(), p.brakeHard(), p.speed(), p.wheelDrive(), p.offTrackBogeys(), p.drivenBogeys(),
                p.slipFactor(), p.slippingBogeys(), p.topSpeed(), p.workingBogeys(), p.endOfTrack());
    }

    public static void broadcastRemoval(UUID subLevelId) {
        if (subLevelId == null) {
            return;
        }
        PacketDistributor.sendToAllPlayers(remove(subLevelId));
    }

    public static void syncAllTo(ServerPlayer player) {
        for (SableTrain train : SableTrainRegistry.all()) {
            if (train.car().subLevelId() != null) {
                PacketDistributor.sendToPlayer(player, add(train));
                UUID twinId = PortalTwins.twinOf(train.car().subLevelId());
                if (twinId != null) {
                    PacketDistributor.sendToPlayer(player, asBody(add(train), twinId));
                }
            }
        }
    }
}

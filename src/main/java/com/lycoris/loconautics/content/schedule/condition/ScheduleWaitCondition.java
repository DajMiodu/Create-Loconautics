package com.lycoris.loconautics.content.schedule.condition;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.schedule.Schedule;
import com.lycoris.loconautics.content.schedule.ScheduleDataEntry;
import com.lycoris.loconautics.trainphysics.storage.ConsistAccess;
import com.lycoris.loconautics.trainphysics.SableTrain;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

public abstract class ScheduleWaitCondition extends ScheduleDataEntry {
	public static final StreamCodec<RegistryFriendlyByteBuf, ScheduleWaitCondition> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public ScheduleWaitCondition decode(RegistryFriendlyByteBuf buffer) {
			return ScheduleWaitCondition.fromTag(buffer.registryAccess(), buffer.readNbt());
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buffer, ScheduleWaitCondition value) {
			buffer.writeNbt(value.write(buffer.registryAccess()));
		}
	};

	public abstract boolean tickCompletion(Level level, SableTrain train, CompoundTag context);

	// true when the answer comes from blocks inside the bodies, a parked body would read as empty
	public boolean needsBody() {
		return false;
	}

	// true when the condition can only come true once the station chunk is no longer entity ticking
	public boolean needsStationUnloaded() {
		return false;
	}

	public int carriageIndex() {
		return data.contains("Carriage") ? data.getInt("Carriage") : ConsistAccess.WHOLE_CONSIST;
	}

	protected void requestStatusToUpdate(CompoundTag context) {
		context.putInt("StatusVersion", context.getInt("StatusVersion") + 1);
	}

	public static ScheduleWaitCondition fromTag(HolderLookup.Provider registries, CompoundTag tag) {
		ResourceLocation location = ResourceLocation.parse(tag.getString("Id"));
		Supplier<? extends ScheduleWaitCondition> supplier = null;
		for (Pair<ResourceLocation, Supplier<? extends ScheduleWaitCondition>> pair : Schedule.CONDITION_TYPES)
			if (pair.getFirst()
				.equals(location))
				supplier = pair.getSecond();

		if (supplier == null) {
			LoconauticsConstants.LOGGER.warn("Could not parse waiting condition type: " + location);
			return null;
		}

		ScheduleWaitCondition condition = supplier.get();
		CompoundTag data = tag.getCompound("Data");
		condition.readAdditional(registries, data);
		condition.data = data;
		return condition;
	}

	public abstract MutableComponent getWaitingStatus(Level level, SableTrain train, CompoundTag tag);

}

package com.lycoris.loconautics.content.schedule.destination;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime.State;
import com.lycoris.loconautics.content.schedule.Schedule;
import com.lycoris.loconautics.content.schedule.ScheduleDataEntry;
import com.lycoris.loconautics.content.schedule.SchedulePath;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public abstract class ScheduleInstruction extends ScheduleDataEntry {
	public static final StreamCodec<RegistryFriendlyByteBuf, ScheduleInstruction> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public ScheduleInstruction decode(RegistryFriendlyByteBuf buffer) {
			return ScheduleInstruction.fromTag(buffer.registryAccess(), buffer.readNbt());
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buffer, ScheduleInstruction value) {
			buffer.writeNbt(value.write(buffer.registryAccess()));
		}
	};

	public abstract boolean supportsConditions();

	@Nullable
	public abstract SchedulePath start(SableScheduleRuntime runtime, Level level);

	@Nullable
	protected final SchedulePath finishInstant(SableScheduleRuntime runtime) {
		runtime.announcer().action(this);
		runtime.state = State.PRE_TRANSIT;
		runtime.currentEntry++;
		return null;
	}

	@Nullable
	protected final SchedulePath skip(SableScheduleRuntime runtime) {
		runtime.startCooldown();
		runtime.state = State.PRE_TRANSIT;
		runtime.currentEntry++;
		return null;
	}

	public static ScheduleInstruction fromTag(HolderLookup.Provider registries, CompoundTag tag) {
		ResourceLocation location = ResourceLocation.parse(tag.getString("Id"));
		Supplier<? extends ScheduleInstruction> supplier = null;
		for (Pair<ResourceLocation, Supplier<? extends ScheduleInstruction>> pair : Schedule.INSTRUCTION_TYPES)
			if (pair.getFirst()
				.equals(location))
				supplier = pair.getSecond();

		if (supplier == null) {
			LoconauticsConstants.LOGGER.warn("Could not parse schedule instruction type: " + location);
			UnknownInstruction unknown = new UnknownInstruction(location);
			unknown.data = tag.getCompound("Data");
			return unknown;
		}

		ScheduleInstruction scheduleDestination = supplier.get();
		CompoundTag data = tag.getCompound("Data");
		scheduleDestination.readAdditional(registries, data);
		scheduleDestination.data = data;
		return scheduleDestination;
	}

}

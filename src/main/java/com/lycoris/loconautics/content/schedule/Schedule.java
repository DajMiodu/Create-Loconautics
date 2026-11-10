package com.lycoris.loconautics.content.schedule;

import com.lycoris.loconautics.content.schedule.condition.FluidThresholdCondition;
import com.lycoris.loconautics.content.schedule.condition.IdleCargoCondition;
import com.lycoris.loconautics.content.schedule.condition.ItemThresholdCondition;
import com.lycoris.loconautics.content.schedule.condition.PlayerPassengerCondition;
import com.lycoris.loconautics.content.schedule.condition.RedstoneLinkCondition;
import com.lycoris.loconautics.content.schedule.condition.ScheduleWaitCondition;
import com.lycoris.loconautics.content.schedule.condition.ScheduledDelay;
import com.lycoris.loconautics.content.schedule.condition.StationPoweredCondition;
import com.lycoris.loconautics.content.schedule.condition.StationUnloadedCondition;
import com.lycoris.loconautics.content.schedule.condition.TimeOfDayCondition;
import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.schedule.destination.ChangeThrottleInstruction;
import com.lycoris.loconautics.content.schedule.destination.CoupleInstruction;
import com.lycoris.loconautics.content.schedule.destination.DecoupleInstruction;
import com.lycoris.loconautics.content.schedule.destination.DeliverPackagesInstruction;
import com.lycoris.loconautics.content.schedule.destination.DestinationInstruction;
import com.lycoris.loconautics.content.schedule.destination.FetchPackagesInstruction;
import com.lycoris.loconautics.content.schedule.destination.ManeuverInstruction;
import com.lycoris.loconautics.content.schedule.destination.ScheduleInstruction;
import com.lycoris.loconautics.content.schedule.destination.WaypointInstruction;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class Schedule {
	public static List<Pair<ResourceLocation, Supplier<? extends ScheduleInstruction>>> INSTRUCTION_TYPES =
		new ArrayList<>();
	public static List<Pair<ResourceLocation, Supplier<? extends ScheduleWaitCondition>>> CONDITION_TYPES =
		new ArrayList<>();

	static {
		registerInstruction("destination", DestinationInstruction::new);
		registerInstruction("throttle", ChangeThrottleInstruction::new);
		registerInstruction("package_delivery", DeliverPackagesInstruction::new);
		registerInstruction("package_retrieval", FetchPackagesInstruction::new);
		registerOwnInstruction("decouple", DecoupleInstruction::new);
		registerOwnInstruction("maneuver", ManeuverInstruction::new);
		registerOwnInstruction("couple", CoupleInstruction::new);
		registerInstructionWithId(WaypointInstruction.ID, WaypointInstruction::new);

		registerCondition("delay", ScheduledDelay::new);
		registerCondition("time_of_day", TimeOfDayCondition::new);
		registerCondition("item_threshold", ItemThresholdCondition::new);
		registerCondition("fluid_threshold", FluidThresholdCondition::new);
		registerCondition("redstone_link", RedstoneLinkCondition::new);
		registerCondition("player_count", PlayerPassengerCondition::new);
		registerCondition("idle", IdleCargoCondition::new);
		registerCondition("unloaded", StationUnloadedCondition::new);
		registerCondition("powered", StationPoweredCondition::new);
	}

	public static ResourceLocation portedId(String name) {
		return ResourceLocation.fromNamespaceAndPath("create", name);
	}

	private static void registerInstruction(String name, Supplier<? extends ScheduleInstruction> factory) {
		INSTRUCTION_TYPES.add(Pair.of(portedId(name), factory));
	}

	private static void registerOwnInstruction(String name, Supplier<? extends ScheduleInstruction> factory) {
		INSTRUCTION_TYPES.add(Pair.of(LoconauticsConstants.id(name), factory));
	}

	private static void registerInstructionWithId(ResourceLocation id, Supplier<? extends ScheduleInstruction> factory) {
		INSTRUCTION_TYPES.add(Pair.of(id, factory));
	}

	private static void registerCondition(String name, Supplier<? extends ScheduleWaitCondition> factory) {
		CONDITION_TYPES.add(Pair.of(portedId(name), factory));
	}

	public List<ScheduleEntry> entries;
	public boolean cyclic;
	public int savedProgress;

	public Schedule() {
		this(new ArrayList<>(), true, 0);
	}

	public Schedule(List<ScheduleEntry> entries, boolean cyclic, int savedProgress) {
		this.entries = entries;
		this.cyclic = cyclic;
		this.savedProgress = savedProgress;
	}

	public CompoundTag write(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		ListTag list = NBTHelper.writeCompoundList(entries, t -> t.write(registries));
		tag.put("Entries", list);
		tag.putBoolean("Cyclic", cyclic);
		if (savedProgress > 0)
			tag.putInt("Progress", savedProgress);
		return tag;
	}

	public static Schedule fromTag(HolderLookup.Provider registries, CompoundTag tag) {
		Schedule schedule = new Schedule();
		schedule.entries = new ArrayList<>(NBTHelper.readCompoundList(tag.getList("Entries", Tag.TAG_COMPOUND), t -> ScheduleEntry.fromTag(registries, t)));
		schedule.entries.removeIf(Objects::isNull);
		schedule.cyclic = tag.getBoolean("Cyclic");
		if (tag.contains("Progress"))
			schedule.savedProgress = tag.getInt("Progress");
		return schedule;
	}

}

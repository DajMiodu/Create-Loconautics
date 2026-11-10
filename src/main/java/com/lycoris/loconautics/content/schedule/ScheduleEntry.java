package com.lycoris.loconautics.content.schedule;

import com.lycoris.loconautics.content.schedule.condition.ScheduleWaitCondition;
import com.lycoris.loconautics.content.schedule.destination.ScheduleInstruction;
import net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ScheduleEntry {
	public static final StreamCodec<RegistryFriendlyByteBuf, ScheduleEntry> STREAM_CODEC = StreamCodec.composite(
			ScheduleInstruction.STREAM_CODEC, entry -> entry.instruction,
			CatnipStreamCodecBuilders.list(CatnipStreamCodecBuilders.list(ScheduleWaitCondition.STREAM_CODEC)), entry -> entry.conditions,
			ScheduleEntry::new
	);

	public ScheduleInstruction instruction;
	public List<List<ScheduleWaitCondition>> conditions;

	public ScheduleEntry() {
		conditions = new ArrayList<>();
	}

	public ScheduleEntry(ScheduleInstruction instruction, List<List<ScheduleWaitCondition>> conditions) {
		this.instruction = instruction;
		this.conditions = conditions;
	}

	public ScheduleEntry clone(HolderLookup.Provider registries) {
		return fromTag(registries, write(registries));
	}

	public CompoundTag write(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		ListTag outer = new ListTag();
		tag.put("Instruction", instruction.write(registries));
		if (!instruction.supportsConditions())
			return tag;
		for (List<ScheduleWaitCondition> column : conditions)
			outer.add(NBTHelper.writeCompoundList(column, t -> t.write(registries)));
		tag.put("Conditions", outer);
		return tag;
	}

	public static ScheduleEntry fromTag(HolderLookup.Provider registries, CompoundTag tag) {
		ScheduleEntry entry = new ScheduleEntry();
		entry.instruction = ScheduleInstruction.fromTag(registries, tag.getCompound("Instruction"));
		if (entry.instruction == null)
			return null;
		entry.conditions = new ArrayList<>();
		if (entry.instruction.supportsConditions())
			for (Tag t : tag.getList("Conditions", Tag.TAG_LIST))
				if (t instanceof ListTag list) {
					List<ScheduleWaitCondition> column = new ArrayList<>(NBTHelper.readCompoundList(list,
						conditionTag -> ScheduleWaitCondition.fromTag(registries, conditionTag)));
					column.removeIf(Objects::isNull);
					entry.conditions.add(column);
				}
		return entry;
	}

}

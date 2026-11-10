package com.lycoris.loconautics.content.schedule.condition;

import com.google.common.collect.ImmutableList;
import com.lycoris.loconautics.content.schedule.Schedule;
import com.lycoris.loconautics.trainphysics.storage.ConsistAccess;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.lang.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

public class ItemThresholdCondition extends CargoThresholdCondition {

	private FilterItemStack stack = FilterItemStack.empty();

	private static final int MEASURE_STACKS = 1;
	private static final int MEASURE_PERCENT = 2;

	@Override
	protected Component getUnit() {
		return Component.literal(switch (getMeasure()) {
			case MEASURE_STACKS -> "▤";
			case MEASURE_PERCENT -> "%";
			default -> "";
		});
	}

	@Override
	protected ItemStack getIcon() {
		return stack.item();
	}

	@Override
	protected boolean test(Level level, SableTrain train, CompoundTag context) {
		Ops operator = getOperator();
		int target = getThreshold();
		int measure = getMeasure();

		long foundItems = 0;
		long capacity = 0;
		for (IItemHandler items : ConsistAccess.itemsIn(train.level(), train.car().subLevelId(), carriageIndex())) {
			for (int i = 0; i < items.getSlots(); i++) {
				ItemStack stackInSlot = items.getStackInSlot(i);
				capacity += Math.min(items.getSlotLimit(i),
					stackInSlot.isEmpty() ? Item.DEFAULT_MAX_STACK_SIZE : stackInSlot.getMaxStackSize());
				if (!stack.test(level, stackInSlot))
					continue;

				if (measure == MEASURE_STACKS)
					foundItems += stackInSlot.getCount() == stackInSlot.getMaxStackSize() ? 1 : 0;
				else
					foundItems += stackInSlot.getCount();
			}
		}

		int amount = measure == MEASURE_PERCENT ? percentOf(foundItems, capacity) : (int) foundItems;
		requestStatusToUpdate(amount, context);
		return operator.test(amount, target);
	}

	@Override
	protected void writeAdditional(HolderLookup.Provider registries, CompoundTag tag) {
		super.writeAdditional(registries, tag);
		tag.put("Item", stack.serializeNBT(registries));
	}

	@Override
	protected void readAdditional(HolderLookup.Provider registries, CompoundTag tag) {
		super.readAdditional(registries, tag);
		if (tag.contains("Item"))
			stack = FilterItemStack.of(registries, tag.getCompound("Item"));
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		this.stack = FilterItemStack.of(stack);
	}

	@Override
	public ItemStack getItem(int slot) {
		return stack.item();
	}

	@Override
	public List<Component> getTitleAs(String type) {
		return ImmutableList.of(
			CreateLang.translateDirect("schedule.condition.threshold.train_holds",
				CreateLang.translateDirect("schedule.condition.threshold." + Lang.asId(getOperator().name()))),
			CreateLang.translateDirect("schedule.condition.threshold.x_units_of_item", getThreshold(),
				measureName(),
				stack.isEmpty() ? CreateLang.translateDirect("schedule.condition.threshold.anything")
					: stack.isFilterItem()
						? CreateLang.translateDirect("schedule.condition.threshold.matching_content")
						: stack.item().getHoverName())
				.withStyle(ChatFormatting.DARK_AQUA));
	}

	private Component measureName() {
		return switch (getMeasure()) {
			case MEASURE_STACKS -> CreateLang.translateDirect("schedule.condition.threshold.stacks");
			case MEASURE_PERCENT -> percentUnit();
			default -> CreateLang.translateDirect("schedule.condition.threshold.items");
		};
	}

	@Override
	public ResourceLocation getId() {
		return Schedule.portedId("item_threshold");
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void initConfigurationWidgets(ModularGuiLineBuilder builder) {
		super.initConfigurationWidgets(builder);
		builder.addSelectionScrollInput(71, 50, (i, l) -> {
			i.forOptions(ImmutableList.of(CreateLang.translateDirect("schedule.condition.threshold.items"),
				CreateLang.translateDirect("schedule.condition.threshold.stacks"), percentUnit()))
				.titled(CreateLang.translateDirect("schedule.condition.threshold.item_measure"));
		}, "Measure");
	}

	@Override
	public MutableComponent getWaitingStatus(Level level, SableTrain train, CompoundTag tag) {
		int lastDisplaySnapshot = getLastDisplaySnapshot(tag);
		if (lastDisplaySnapshot == -1)
			return Component.empty();
		int offset = getOperator() == Ops.LESS ? -1 : getOperator() == Ops.GREATER ? 1 : 0;
		return CreateLang.translateDirect("schedule.condition.threshold.status", lastDisplaySnapshot,
			Math.max(0, getThreshold() + offset), measureName());
	}

}

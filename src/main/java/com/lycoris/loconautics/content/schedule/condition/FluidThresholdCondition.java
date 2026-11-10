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
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;

public class FluidThresholdCondition extends CargoThresholdCondition {

	private FilterItemStack compareStack = FilterItemStack.empty();

	private static final int MEASURE_PERCENT = 1;

	@Override
	protected Component getUnit() {
		return Component.literal(inPercent() ? "%" : "b");
	}

	private boolean inPercent() {
		return getMeasure() == MEASURE_PERCENT;
	}

	private Component measureName() {
		return inPercent() ? percentUnit() : CreateLang.translateDirect("schedule.condition.threshold.buckets");
	}

	@Override
	protected ItemStack getIcon() {
		return compareStack.item();
	}

	@Override
	protected boolean test(Level level, SableTrain train, CompoundTag context) {
		Ops operator = getOperator();
		int target = getThreshold();

		long foundFluid = 0;
		long capacity = 0;
		for (IFluidHandler fluids : ConsistAccess.fluidsIn(train.level(), train.car().subLevelId(), carriageIndex())) {
			for (int i = 0; i < fluids.getTanks(); i++) {
				capacity += fluids.getTankCapacity(i);
				FluidStack fluidInTank = fluids.getFluidInTank(i);
				if (!compareStack.test(level, fluidInTank))
					continue;
				foundFluid += fluidInTank.getAmount();
			}
		}

		if (inPercent()) {
			int percent = percentOf(foundFluid, capacity);
			requestStatusToUpdate(percent, context);
			return operator.test(percent, target);
		}
		requestStatusToUpdate((int) (foundFluid / 1000), context);
		return operator.test((int) foundFluid, target * 1000);
	}

	@Override
	protected void writeAdditional(HolderLookup.Provider registries, CompoundTag tag) {
		super.writeAdditional(registries, tag);
		tag.put("Bucket", compareStack.serializeNBT(registries));
	}

	@Override
	protected void readAdditional(HolderLookup.Provider registries, CompoundTag tag) {
		super.readAdditional(registries, tag);
		if (tag.contains("Bucket"))
			compareStack = FilterItemStack.of(registries, tag.getCompound("Bucket"));
	}

	@OnlyIn(Dist.CLIENT)
	private FluidStack loadFluid() {
		return compareStack.fluid(Minecraft.getInstance().level);
	}

	@Override
	public List<Component> getTitleAs(String type) {
		return ImmutableList.of(
			CreateLang.translateDirect("schedule.condition.threshold.train_holds",
				CreateLang.translateDirect("schedule.condition.threshold." + Lang.asId(getOperator().name()))),
			CreateLang.translateDirect("schedule.condition.threshold.x_units_of_item", getThreshold(),
				measureName(),
				compareStack.isEmpty() ? CreateLang.translateDirect("schedule.condition.threshold.anything")
					: compareStack.isFilterItem()
						? CreateLang.translateDirect("schedule.condition.threshold.matching_content")
						: loadFluid().getHoverName())
				.withStyle(ChatFormatting.DARK_AQUA));
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		compareStack = FilterItemStack.of(stack);
	}

	@Override
	public ItemStack getItem(int slot) {
		return compareStack.item();
	}

	@Override
	public ResourceLocation getId() {
		return Schedule.portedId("fluid_threshold");
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void initConfigurationWidgets(ModularGuiLineBuilder builder) {
		super.initConfigurationWidgets(builder);
		builder.addSelectionScrollInput(71, 50, (i, l) -> {
			i.forOptions(ImmutableList.of(CreateLang.translateDirect("schedule.condition.threshold.buckets"),
					percentUnit()))
				.titled(null);
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

package com.lycoris.loconautics.content.schedule;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import net.createmod.catnip.data.Pair;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface IScheduleInput {

	default Pair<ItemStack, Component> getSummary() {
		return Pair.of(ItemStack.EMPTY, Component.empty());
	}

	ResourceLocation getId();

	CompoundTag getData();

	void setData(HolderLookup.Provider registries, CompoundTag data);

	default int slotsTargeted() {
		return 0;
	}

	default List<Component> getTitleAs(String type) {
		ResourceLocation id = getId();
        return ImmutableList
			.of(Component.translatable(id.getNamespace() + ".schedule." + type + "." + id.getPath()));
	}

	default ItemStack getSecondLineIcon() {
		return ItemStack.EMPTY;
	}

	default void setItem(int slot, ItemStack stack) {}

	default ItemStack getItem(int slot) {
		return ItemStack.EMPTY;
	}

	@Nullable
    default List<Component> getSecondLineTooltip(int slot) {
		return null;
	}

	@OnlyIn(Dist.CLIENT)
    default void initConfigurationWidgets(ModularGuiLineBuilder builder) {}

    @OnlyIn(Dist.CLIENT)
    default boolean renderSpecialIcon(GuiGraphics graphics, int x, int y) {
		return false;
	}

}

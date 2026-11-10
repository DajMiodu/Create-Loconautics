package com.lycoris.loconautics.content.schedule.condition;

import com.lycoris.loconautics.content.schedule.Schedule;
import com.lycoris.loconautics.trainphysics.storage.ConsistAccess;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.data.Pair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class IdleCargoCondition extends TimedWaitCondition {

	private static final int SAMPLE_RATE = 20;

	@Override
	public Pair<ItemStack, Component> getSummary() {
		return Pair.of(ItemStack.EMPTY, CreateLang.translateDirect("schedule.condition.idle_short", formatTime(true)));
	}

	@Override
	public ResourceLocation getId() {
		return Schedule.portedId("idle");
	}

	@Override
	public boolean needsBody() {
		return true;
	}

	private int cargoSignature(SableTrain train) {
		int items = ConsistAccess.countItems(train.level(), train.car().subLevelId(), carriageIndex(), stack -> true);
		int fluid = ConsistAccess.countFluid(train.level(), train.car().subLevelId(), carriageIndex(), stack -> true);
		return items * 31 + fluid;
	}

	@Override
	public boolean tickCompletion(Level level, SableTrain train, CompoundTag context) {
		int idleTime = context.getInt("Time");
		int sinceSample = context.getInt("SinceSample");

		if (sinceSample >= SAMPLE_RATE) {
			int signature = cargoSignature(train);
			if (!context.contains("Signature") || context.getInt("Signature") != signature) {
				context.putInt("Signature", signature);
				idleTime = 0;
			}
			sinceSample = 0;
		}

		context.putInt("SinceSample", sinceSample + 1);
		context.putInt("Time", idleTime + 1);
		requestDisplayIfNecessary(context, idleTime);
		return idleTime > totalWaitTicks();
	}

}

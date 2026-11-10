package com.lycoris.loconautics.content.schedule.destination;

import com.lycoris.loconautics.content.schedule.Schedule;
import com.lycoris.loconautics.content.schedule.SchedulePath;
import com.lycoris.loconautics.content.trainmail.PostRows;
import com.lycoris.loconautics.content.trainmail.PostalPlan;
import com.lycoris.loconautics.content.trainmail.SablePostalWorld;
import net.minecraft.resources.ResourceLocation;

public class DeliverPackagesInstruction extends PostalInstruction {

	@Override
	public ResourceLocation getId() {
		return Schedule.portedId("package_delivery");
	}

	@Override
	public boolean loconautics$delivers() {
		return true;
	}

	@Override
	protected PostalPlan.Step<SchedulePath> plan(SablePostalWorld world) {
		return PostalPlan.deliver(world, getLabelText(), textData(PostRows.STATION_KEY));
	}

}

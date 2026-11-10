package com.lycoris.loconautics.content.schedule.destination;

import com.lycoris.loconautics.content.schedule.Schedule;
import com.lycoris.loconautics.content.schedule.SchedulePath;
import com.lycoris.loconautics.content.trainmail.PostRows;
import com.lycoris.loconautics.content.trainmail.PostalPlan;
import com.lycoris.loconautics.content.trainmail.SablePostalWorld;
import net.minecraft.resources.ResourceLocation;

public class FetchPackagesInstruction extends PostalInstruction {

	public String getFilter() {
		return getLabelText();
	}

	@Override
	public ResourceLocation getId() {
		return Schedule.portedId("package_retrieval");
	}

	@Override
	public boolean loconautics$delivers() {
		return false;
	}

	@Override
	protected PostalPlan.Step<SchedulePath> plan(SablePostalWorld world) {
		return PostalPlan.fetch(world, getLabelText(), textData(PostRows.STATION_KEY));
	}

}

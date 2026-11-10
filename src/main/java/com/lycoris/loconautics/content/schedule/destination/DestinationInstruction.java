package com.lycoris.loconautics.content.schedule.destination;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.schedule.RouteDistance;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.content.schedule.Schedule;
import com.lycoris.loconautics.content.schedule.SchedulePath;
import com.lycoris.loconautics.content.schedule.StationRouting;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.data.Glob;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class DestinationInstruction extends TextScheduleInstruction {

	@Override
	public boolean supportsConditions() {
		return true;
	}

	@Override
	public ResourceLocation getId() {
		return Schedule.portedId("destination");
	}

	// a one cab maneuver holds its nose to the station facing, a waypoint is only driven through
	public boolean landsFront() {
		return true;
	}

	public String getFilter() {
		return getLabelText();
	}

	public String getFilterForRegex() {
		return Glob.toRegexPattern(getFilter(), "");
	}

	@Override
	@Nullable
	public SchedulePath start(SableScheduleRuntime runtime, Level level) {
		String regex = getFilterForRegex();
		StationRouting.Plan plan = StationRouting.plan(runtime, station -> station.name.matches(regex), true,
			landsFront());
		if (plan == null) {
			runtime.startCooldown();
			return null;
		}

		if (plan.best == null && plan.waitFor != null) {
			if (plan.noRoom != null) {
				runtime.status("no_room:" + plan.noRoom, Component.literal(plan.noRoom));
			} else {
				runtime.status("waiting_for_platform:" + plan.waitFor.name, LoconauticsLang
					.translate("train.status.waiting_for_platform", plan.waitFor.name).component());
			}
			runtime.waitHere();
			runtime.startCooldown();
			return null;
		}
		if (plan.best == null) {
			String why = plan.rejected.isEmpty() ? "no matching station" : String.join("; ", plan.rejected);
			LoconauticsConstants.LOGGER.info("[schedule] {} no route to \"{}\" from {}, {}",
				CreateLang.translateDirect("train.status.no_path").getString(), getFilter(),
				RouteDistance.describe(plan.nose), why);
			runtime.noPath(getFilter(), why, plan.anyMatch);
			runtime.startCooldown();
		} else {
			runtime.clearStatus();
			if (!plan.rejected.isEmpty()) {
				LoconauticsConstants.LOGGER.info("[schedule] \"{}\" picked {}, passed over {}", getFilter(),
					plan.best.station() == null ? "a turntable" : plan.best.station().name,
					String.join("; ", plan.rejected));
			}
		}
		return plan.best;
	}

}

package com.lycoris.loconautics.content.schedule.destination;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.content.schedule.SchedulePath;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class ManeuverInstruction extends ScheduleInstruction {

	public ManeuverInstruction() {
		super();
		data.putInt("Value", LoconauticsConstants.MANEUVER_DEFAULT_PERCENT);
	}

	@Override
	public ResourceLocation getId() {
		return LoconauticsConstants.id("maneuver");
	}

	public int percent() {
		int value = intData("Value");
		return value <= 0 ? LoconauticsConstants.MANEUVER_DEFAULT_PERCENT
			: Math.min(value, LoconauticsConstants.MANEUVER_MAX_PERCENT);
	}

	@Override
	public boolean supportsConditions() {
		return false;
	}

	@Override
	@Nullable
	public SchedulePath start(SableScheduleRuntime runtime, Level level) {
		runtime.startManeuver(runtime.former(), percent());
		return finishInstant(runtime);
	}

}

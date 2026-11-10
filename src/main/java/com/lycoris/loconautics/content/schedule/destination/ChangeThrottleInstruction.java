package com.lycoris.loconautics.content.schedule.destination;

import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.content.schedule.Schedule;
import com.lycoris.loconautics.content.schedule.SchedulePath;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class ChangeThrottleInstruction extends ScheduleInstruction {

	public ChangeThrottleInstruction() {
		super();
		data.putInt("Value", 100);
	}

	@Override
	public ResourceLocation getId() {
		return Schedule.portedId("throttle");
	}

	@Override
	public boolean supportsConditions() {
		return false;
	}

	public float getThrottle() {
		return intData("Value") / 100f;
	}

	@Override
	@Nullable
	public SchedulePath start(SableScheduleRuntime runtime, Level level) {
		int notches = Math.round(getThrottle() * runtime.maxPower());
		runtime.setPowerLimit(getThrottle() > 0.0f ? Math.max(1, notches) : notches);
		return finishInstant(runtime);
	}

}

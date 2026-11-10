package com.lycoris.loconautics.content.schedule.destination;

import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.content.schedule.SchedulePath;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class UnknownInstruction extends ScheduleInstruction {

	private final ResourceLocation id;

	public UnknownInstruction(ResourceLocation id) {
		this.id = id;
	}

	@Override
	public ResourceLocation getId() {
		return id;
	}

	@Override
	public boolean supportsConditions() {
		return true;
	}

	@Override
	@Nullable
	public SchedulePath start(SableScheduleRuntime runtime, Level level) {
		return finishInstant(runtime);
	}

}

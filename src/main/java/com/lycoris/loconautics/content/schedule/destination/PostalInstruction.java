package com.lycoris.loconautics.content.schedule.destination;

import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.content.schedule.SchedulePath;
import com.lycoris.loconautics.content.trainmail.PostRows;
import com.lycoris.loconautics.content.trainmail.PostalEntry;
import com.lycoris.loconautics.content.trainmail.PostalPlan;
import com.lycoris.loconautics.content.trainmail.SablePostalWorld;
import com.lycoris.loconautics.content.trainmail.TrainMailTransfer;
import com.lycoris.loconautics.common.train.TrainStatusChat;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public abstract class PostalInstruction extends TextScheduleInstruction implements PostalEntry {

	protected abstract PostalPlan.Step<SchedulePath> plan(SablePostalWorld world);

	@Override
	public boolean supportsConditions() {
		return true;
	}

	@Override
	public String loconautics$address() {
		return getLabelText();
	}

	@Override
	public String loconautics$post() {
		return textData(PostRows.STATION_KEY);
	}

	@Override
	@Nullable
	public SchedulePath start(SableScheduleRuntime runtime, Level level) {
		if (runtime.train().isGhost()) {
			runtime.startCooldown();
			return null;
		}
		SablePostalWorld world = new SablePostalWorld(runtime);
		PostalPlan.Step<SchedulePath> step = plan(world);
		switch (step.kind()) {
			case SKIP -> {
				runtime.loconautics$setTripped(false);
				runtime.loconautics$setDeposit(false);
				return skip(runtime);
			}
			case WAIT -> {
				TrainMailTransfer.run(runtime.train(), world.here());
				runtime.waitHere();
				return null;
			}
			case FAILED -> {
				TrainStatusChat.failedNavigation(runtime.train());
				runtime.startCooldown();
				return null;
			}
			default -> {
				runtime.loconautics$setTripped(step.tripped());
				runtime.loconautics$setRepeat(step.repeat());
				runtime.loconautics$setDeposit(step.deposit());
				runtime.clearStatus();
				return step.path();
			}
		}
	}

}

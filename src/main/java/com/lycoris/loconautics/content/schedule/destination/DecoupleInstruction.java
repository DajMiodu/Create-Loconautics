package com.lycoris.loconautics.content.schedule.destination;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.ConsistDecoupler;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.content.schedule.SchedulePath;
import com.lycoris.loconautics.foundation.LogFormat;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.drive.TrainBrakes;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

public class DecoupleInstruction extends ScheduleInstruction {

	public DecoupleInstruction() {
		super();
		data.putInt("Value", 1);
	}

	@Override
	public ResourceLocation getId() {
		return LoconauticsConstants.id("decouple");
	}

	@Override
	public boolean supportsConditions() {
		return false;
	}

	public int cars() {
		return Math.max(1, intData("Value"));
	}

	@Override
	@Nullable
	public SchedulePath start(SableScheduleRuntime runtime, Level level) {
		SableTrain train = runtime.train();
		if (!(level instanceof ServerLevel serverLevel) || train == null) {
			return finishInstant(runtime);
		}
		Set<UUID> cut = ConsistDecoupler.carsBeyond(serverLevel, train.id(), cars());
		if (!ConsistDecoupler.split(serverLevel, train.id(), cars())) {
			LoconauticsConstants.LOGGER.info("[maneuver] {} decouple after {} cars refused",
				LogFormat.shortId(train.id()), cars());
			runtime.announcer().line(Component.literal("Nothing to decouple after car " + cars() + ", the locomotive counts as car 1"));
			return finishInstant(runtime);
		}
		runtime.rememberFormer(cut);
		for (UUID id : cut) {
			SableTrain wagon = SableTrainRegistry.get(id);
			if (wagon != null) {
				wagon.setScheduleHold(true);
				wagon.setTargetSpeed(0.0);
				TrainBrakes.holdSoft(wagon);
			}
		}
		LoconauticsConstants.LOGGER.info("[maneuver] {} decoupled after {} cars, {} trains left behind",
			LogFormat.shortId(train.id()), cars(), cut.size());
		return finishInstant(runtime);
	}

}

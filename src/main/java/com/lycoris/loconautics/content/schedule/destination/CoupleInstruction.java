package com.lycoris.loconautics.content.schedule.destination;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.content.schedule.SchedulePath;
import net.minecraft.network.chat.Component;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.common.train.TrainStatusChat;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CoupleInstruction extends ScheduleInstruction {

	private static final Map<UUID, Vector3d> CREEP_START = new HashMap<>();

	public CoupleInstruction() {
		super();
		data.putInt("Value", 32);
	}

	@Override
	public ResourceLocation getId() {
		return LoconauticsConstants.id("couple");
	}

	@Override
	public boolean supportsConditions() {
		return false;
	}

	public int limit() {
		return Math.max(4, intData("Value"));
	}

	@Override
	@Nullable
	public SchedulePath start(SableScheduleRuntime runtime, Level level) {
		SableTrain train = runtime.train();
		Set<UUID> former = runtime.former();
		if (!(level instanceof ServerLevel serverLevel) || train == null || former.isEmpty()
			|| coupled(serverLevel, train, former)) {
			finish(runtime, train, former);
			return finishInstant(runtime);
		}
		if (!runtime.maneuver()) {
			runtime.startManeuver(former, LoconauticsConstants.MANEUVER_DEFAULT_PERCENT);
		}
		Vector3d at = TrainStatusChat.bodyPosition(train);
		Vector3d from = at == null ? null : CREEP_START.computeIfAbsent(train.id(), id -> at);
		if (from != null && from.distance(at) > limit()) {
			CREEP_START.remove(train.id());
			runtime.announcer().line(Component.literal("Could not couple within " + limit() + " blocks"));
			runtime.endManeuver();
			return finishInstant(runtime);
		}
		runtime.waitHere();
		return null;
	}

	private static void finish(SableScheduleRuntime runtime, @Nullable SableTrain train, Set<UUID> former) {
		if (train != null) {
			CREEP_START.remove(train.id());
		}
		for (UUID id : former) {
			SableTrain wagon = SableTrainRegistry.get(id);
			if (wagon != null) {
				wagon.setScheduleHold(false);
			}
		}
		runtime.clearFormer();
		runtime.endManeuver();
	}

	private static boolean coupled(ServerLevel level, SableTrain train, Set<UUID> former) {
		Set<UUID> consist = TrainConsist.connectedSubLevels(level, train.car().subLevelId());
		for (UUID id : former) {
			SableTrain wagon = SableTrainRegistry.get(id);
			if (wagon != null && consist.contains(wagon.car().subLevelId())) {
				return true;
			}
		}
		return false;
	}

}

package com.lycoris.loconautics.content.schedule.destination;

import net.minecraft.resources.ResourceLocation;

public class WaypointInstruction extends DestinationInstruction {

	public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("railways", "waypoint_destination");

	@Override
	public boolean supportsConditions() {
		return false;
	}

	@Override
	public boolean landsFront() {
		return false;
	}

	@Override
	public ResourceLocation getId() {
		return ID;
	}

}

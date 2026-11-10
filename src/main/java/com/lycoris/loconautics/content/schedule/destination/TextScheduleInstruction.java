package com.lycoris.loconautics.content.schedule.destination;

public abstract class TextScheduleInstruction extends ScheduleInstruction {

	protected String getLabelText() {
		return textData("Text");
	}

}

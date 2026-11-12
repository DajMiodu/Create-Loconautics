package com.lycoris.loconautics.client.ponder;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.GuiGraphics;

public interface DeferredGui {

    void drawDeferred(PonderScene scene, PonderUI ui, GuiGraphics graphics, float partialTicks, float fade);
}

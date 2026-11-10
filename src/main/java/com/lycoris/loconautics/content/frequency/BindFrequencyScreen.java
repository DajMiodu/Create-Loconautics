package com.lycoris.loconautics.content.frequency;

import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class BindFrequencyScreen<T, M extends BindFrequencyMenu<T>>
        extends AbstractSimiContainerScreen<M> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "loconautics", "textures/gui/frequency/frequency_bind.png");

    private static final int PANEL_U = 0;
    private static final int PANEL_V = 0;
    private static final int PANEL_W = 214;
    private static final int PANEL_H = 95;
    private static final int TEXTURE_W = 214;
    private static final int TEXTURE_H = 127;

    private static final int INV_X = 19;
    private static final int INV_Y = 100;

    private static final int BTN_Y         = 60;
    private static final int BTN_CONFIRM_X = 157;
    private static final int BTN_TRASH_X   = 179;

    private static final int LABEL_CENTER_X = 54;
    private static final int ROW_LABEL_Y0   = 40;
    private static final int ROW_LABEL_STEP = 29;

    private final ResourceLocation texture;
    private final int panelW;
    private final int panelH;
    private final int textureW;
    private final int textureH;
    private final int invX;
    private final int invY;
    private final int btnY;
    private final int btnConfirmX;
    private final int btnTrashX;

    private IconButton confirmButton;
    private IconButton trashButton;
    private Rect2i inventoryArea = new Rect2i(0, 0, 0, 0);

    public BindFrequencyScreen(M menu, Inventory inv, Component title) {
        this(menu, inv, title, TEXTURE, PANEL_W, PANEL_H, TEXTURE_W, TEXTURE_H,
                INV_X, INV_Y, BTN_Y, BTN_CONFIRM_X, BTN_TRASH_X);
    }

    protected BindFrequencyScreen(M menu, Inventory inv, Component title, ResourceLocation texture,
                                  int panelW, int panelH, int textureW, int textureH,
                                  int invX, int invY, int btnY, int btnConfirmX, int btnTrashX) {
        super(menu, inv, title);
        this.texture = texture;
        this.panelW = panelW;
        this.panelH = panelH;
        this.textureW = textureW;
        this.textureH = textureH;
        this.invX = invX;
        this.invY = invY;
        this.btnY = btnY;
        this.btnConfirmX = btnConfirmX;
        this.btnTrashX = btnTrashX;
    }

    @Override
    protected void init() {
        setWindowSize(panelW, invY + 108);
        super.init();

        menu.slotsActive = true;

        confirmButton = new IconButton(leftPos + btnConfirmX, topPos + btnY, AllIcons.I_CONFIRM);
        confirmButton.withCallback(() -> minecraft.player.closeContainer());
        addRenderableWidget(confirmButton);

        trashButton = new IconButton(leftPos + btnTrashX, topPos + btnY, AllIcons.I_TRASH);
        trashButton.withCallback(this::clearAllFrequencies);
        addRenderableWidget(trashButton);

        inventoryArea = new Rect2i(leftPos + invX, topPos + invY, 176, 108);
    }

    private void clearAllFrequencies() {
        for (int i = 0; i < menu.rowLabels.length * 2; i++) {
            menu.ghostInventory.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        graphics.blit(texture, leftPos, topPos, PANEL_U, PANEL_V, panelW, panelH, textureW, textureH);
        renderPlayerInventory(graphics, leftPos + invX, topPos + invY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        var font = minecraft.font;
        int color = 0xFFFFFFFF;
        int textH = font.lineHeight;

        for (int row = 0; row < menu.rowLabels.length; row++) {
            Component label = menu.rowLabels[row];
            int y = ROW_LABEL_Y0 + ROW_LABEL_STEP * row;
            graphics.drawString(font, label,
                    LABEL_CENTER_X - font.width(label) / 2,
                    y - textH / 2,
                    color, true);
        }
    }

    @Override
    public List<Rect2i> getExtraAreas() {
        return List.of(inventoryArea);
    }
}

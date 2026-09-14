package com.crispywafer.crispywaferguntracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class HudPositionScreen extends Screen {
    private final Screen parent;
    private final double originalX;
    private final double originalY;
    private boolean dragging;
    private boolean committed;
    private AimHud.HudBounds bounds = new AimHud.HudBounds(0, 0, 0, 0);

    public HudPositionScreen(Screen parent) {
        super(Component.translatable("invisiblekeybinding.hud_position.title"));
        this.parent = parent;
        this.originalX = Config.HUD_X_NORMALIZED.get();
        this.originalY = Config.HUD_Y_NORMALIZED.get();
    }

    @Override
    protected void init() {
        int y = height - 30;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> commitAndClose())
                .bounds(width / 2 + 4, y, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("invisiblekeybinding.config.hud_reset_position"), button -> {
            Config.HUD_X_NORMALIZED.set(0.50D);
            Config.HUD_Y_NORMALIZED.set(0.62D);
        }).bounds(width / 2 - 100, y, 96, 20).build());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && bounds.contains(mouseX, mouseY)) {
            dragging = true;
            updatePosition(mouseX, mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            updatePosition(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            updatePosition(mouseX, mouseY);
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updatePosition(double mouseX, double mouseY) {
        Config.HUD_X_NORMALIZED.set(HudPositionMath.clampNormalized(mouseX / Math.max(1.0D, width)));
        Config.HUD_Y_NORMALIZED.set(HudPositionMath.clampNormalized(mouseY / Math.max(1.0D, height)));
    }

    private void commitAndClose() {
        committed = true;
        Config.CLIENT_SPEC.save();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        if (!committed) {
            Config.HUD_X_NORMALIZED.set(originalX);
            Config.HUD_Y_NORMALIZED.set(originalY);
        }
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x66000000);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        graphics.drawCenteredString(font,
                Component.translatable("invisiblekeybinding.hud_position.instruction"),
                width / 2, 26, 0xD0D0D0);

        Minecraft mc = Minecraft.getInstance();
        int x = (int) Math.round(HudPositionMath.clampNormalized(Config.HUD_X_NORMALIZED.get()) * width);
        int y = (int) Math.round(HudPositionMath.clampNormalized(Config.HUD_Y_NORMALIZED.get()) * height);
        bounds = AimHud.renderStatusBox(graphics, mc, x, y, true);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}

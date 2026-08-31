package ru.okeygoogle.respawnsystem.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.okeygoogle.respawnsystem.client.net.Wire;
import ru.okeygoogle.respawnsystem.client.ui.SoundFx;

public final class WelcomeScreen extends Screen {
    private EditBox nickname;
    private String error = "";
    private int boxX, boxY, boxW, boxH;

    public WelcomeScreen() {
        super(Component.literal("Система — регистрация"));
    }

    @Override
    protected void init() {
        super.init();
        boxW = Math.min(420, this.width - 40);
        boxH = 190;
        boxX = (this.width - boxW) / 2;
        boxY = (this.height - boxH) / 2;

        nickname = new EditBox(this.font, boxX + 46, boxY + 86, boxW - 92, 20, Component.literal("Никнейм"));
        nickname.setMaxLength(32);
        this.addRenderableWidget(nickname);
        this.setInitialFocus(nickname);

        this.addRenderableWidget(Button.builder(Component.literal("ПОДКЛЮЧИТЬСЯ"), button -> submit())
                .bounds(boxX + boxW / 2 - 75, boxY + 126, 150, 20)
                .build());
    }

    private void submit() {
        String value = nickname.getValue().trim();
        if (value.length() < 2) {
            error = "Никнейм должен содержать минимум 2 символа.";
            SoundFx.error();
            return;
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            error = "Никнейм содержит недопустимые символы.";
            SoundFx.error();
            return;
        }
        error = "Подключение к Системе...";
        SoundFx.confirm();
        Wire.initialNick(value);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xF0060A0E);
        for (int x = 0; x < width; x += 24) g.fill(x, 0, x + 1, height, 0x161D6074);
        for (int y = 0; y < height; y += 24) g.fill(0, y, width, y + 1, 0x161D6074);
        g.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xF0131B22);
        g.outline(boxX, boxY, boxW, boxH, 0xFF57D7FF);
        g.fill(boxX, boxY, boxX + 4, boxY + boxH, 0xFF57D7FF);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.centeredText(font, Component.literal("Добро пожаловать!"), width / 2, boxY + 28, 0xFFF2F7FA);
        g.centeredText(font, Component.literal("Введите пожалуйста свой никнейм."), width / 2, boxY + 51, 0xFF9FC5D2);
        g.text(font, Component.literal("ИДЕНТИФИКАЦИЯ ПОЛЬЗОВАТЕЛЯ"), boxX + 46, boxY + 73, 0xFF57D7FF, false);
        if (!error.isBlank()) g.centeredText(font, Component.literal(error), width / 2, boxY + 158, error.startsWith("Подключение") ? 0xFF67E69B : 0xFFFF7580);
        g.text(font, Component.literal("SYSTEM // RESPAWN"), boxX + 12, boxY + 10, 0xFF6A7E89, false);
        g.text(font, Component.literal("NODE: ONLINE"), boxX + boxW - 85, boxY + 10, 0xFF67E69B, false);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

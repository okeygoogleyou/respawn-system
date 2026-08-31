package ru.okeygoogle.respawnsystem.client.ui;

import java.util.Locale;

public record StatusStyle(int color, String warning) {
    public static StatusStyle of(String status, int fallback) {
        String s = status == null ? "" : status.toUpperCase(Locale.ROOT);
        if (s.contains("НЕСТАБ")) return new StatusStyle(0xFFFF9A52, "ВНИМАНИЕ: зафиксирована нестабильность профиля");
        if (s.contains("АНОМАЛ")) return new StatusStyle(0xFFFF5DAF, "ПРЕДУПРЕЖДЕНИЕ: аномальная сигнатура");
        if (s.contains("ПОТЕР")) return new StatusStyle(0xFF8B96A0, "Связь с объектом нестабильна");
        if (s.contains("МЕРТВ") || s.contains("НЕАКТИВ")) return new StatusStyle(0xFF70777D, "Профиль переведён в архивный режим");
        if (s.contains("НАБЛЮД")) return new StatusStyle(0xFF62E7E0, "Объект находится под наблюдением");
        if (s.contains("НЕОПОЗН")) return new StatusStyle(0xFFB18CFF, "Недостаточно данных для идентификации");
        if (s.contains("АКТИВ")) return new StatusStyle(0xFF67E69B, "");
        return new StatusStyle(fallback, "");
    }
}

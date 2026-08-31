package ru.okeygoogle.respawnsystem.client.model;

import java.util.Locale;

public enum Role {
    PLAYER, HELPER, MODERATOR, ADMIN, OWNER;

    public boolean canSupport() {
        return this == MODERATOR || this == ADMIN || this == OWNER;
    }

    public boolean canAdmin() {
        return this == ADMIN || this == OWNER;
    }

    public static Role safe(String value) {
        try { return Role.valueOf(value == null ? "PLAYER" : value.trim().toUpperCase(Locale.ROOT)); }
        catch (Exception ignored) { return PLAYER; }
    }
}

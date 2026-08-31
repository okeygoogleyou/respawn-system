package ru.okeygoogle.respawnsystem.client.model;

import java.util.List;
import java.util.UUID;

public record ProfileData(
        UUID uuid,
        String minecraftName,
        String systemName,
        String race,
        String origin,
        String status,
        String faction,
        String accessRole,
        List<String> abilities,
        List<String> traits,
        String subtitle,
        String theme,
        String accent,
        String frame,
        String decor,
        String adminNote
) {
    public static ProfileData empty(UUID uuid, String minecraftName) {
        return new ProfileData(uuid, minecraftName, minecraftName, "НЕ ОПРЕДЕЛЕНА", "—", "НЕОПОЗНАННЫЙ", "—", "PLAYER",
                List.of(), List.of(), "", "SYSTEM", "#57D7FF", "SYSTEM", "NONE", "");
    }
}

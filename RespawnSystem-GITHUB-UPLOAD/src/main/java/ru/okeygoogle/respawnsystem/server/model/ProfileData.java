package ru.okeygoogle.respawnsystem.server.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ProfileData {
    public final UUID uuid;
    public String minecraftName;
    public String systemName;
    public String race = "НЕ ОПРЕДЕЛЕНА";
    public String origin = "—";
    public String status = "НЕОПОЗНАННЫЙ";
    public String faction = "—";
    public Role role = Role.PLAYER;
    public List<String> abilities = new ArrayList<>();
    public List<String> traits = new ArrayList<>();
    public String subtitle = "";
    public String theme = "SYSTEM";
    public String accent = "#57D7FF";
    public String adminNote = "";
    public boolean registered;

    public ProfileData(UUID uuid, String minecraftName) {
        this.uuid = uuid;
        this.minecraftName = minecraftName;
        this.systemName = minecraftName;
    }

    public ProfileData copy(boolean includeAdminNote) {
        ProfileData p = new ProfileData(uuid, minecraftName);
        p.systemName = systemName;
        p.race = race;
        p.origin = origin;
        p.status = status;
        p.faction = faction;
        p.role = role;
        p.abilities = new ArrayList<>(abilities);
        p.traits = new ArrayList<>(traits);
        p.subtitle = subtitle;
        p.theme = theme;
        p.accent = accent;
        p.adminNote = includeAdminNote ? adminNote : "";
        p.registered = registered;
        return p;
    }
}

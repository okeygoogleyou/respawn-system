package ru.okeygoogle.respawnsystem.client.ui;

import java.util.List;

public final class ThemeCatalog {
    private ThemeCatalog() {}

    public static final List<Theme> THEMES = List.of(
            new Theme("SYSTEM", "Системная", 0xFF57D7FF, 0xE6090E13, 0xEC101820, 0xE817222C, 0xFFF2F7FA, 0xFF84939E, 0xFFFF5D6C),
            new Theme("CRIMSON", "Багровая", 0xFFFF536E, 0xE60E080B, 0xEC1D1015, 0xE827141B, 0xFFFFF2F4, 0xFFB18C94, 0xFFFF536E),
            new Theme("VIOLET", "Фиолетовая", 0xFFC995FF, 0xE60B0811, 0xEC171020, 0xE8211730, 0xFFF7F0FF, 0xFFA594B5, 0xFFFF6D9B),
            new Theme("MONO", "Монохром", 0xFFE8EEF2, 0xE6090A0C, 0xEC151719, 0xE81D2023, 0xFFF5F5F5, 0xFF969B9F, 0xFFFF6B6B)
    );

    public static Theme byId(String id) {
        return THEMES.stream().filter(t -> t.id().equalsIgnoreCase(id)).findFirst().orElse(THEMES.getFirst());
    }

    public static String next(String id) {
        for (int i = 0; i < THEMES.size(); i++) {
            if (THEMES.get(i).id().equalsIgnoreCase(id)) return THEMES.get((i + 1) % THEMES.size()).id();
        }
        return THEMES.getFirst().id();
    }
}

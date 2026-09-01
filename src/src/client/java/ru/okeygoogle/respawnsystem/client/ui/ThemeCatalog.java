package ru.okeygoogle.respawnsystem.client.ui;

import java.util.List;

public final class ThemeCatalog {
    private ThemeCatalog() {}

    public static final List<Theme> THEMES = List.of(
            new Theme("SYSTEM", "Системная", 0xFF57D7FF, 0xF2070B10, 0xF20C131B, 0xF2141D27, 0xFFF2F7FA, 0xFF8C9AA5, 0xFFFF5D6C),
            new Theme("CRIMSON", "Багровая", 0xFFFF536E, 0xF2090709, 0xF2160D11, 0xF2201117, 0xFFFFF2F4, 0xFFB18C94, 0xFFFF536E),
            new Theme("VIOLET", "Фиолетовая", 0xFFC995FF, 0xF2080710, 0xF2140E1D, 0xF21C1429, 0xFFF7F0FF, 0xFFA594B5, 0xFFFF6D9B),
            new Theme("MONO", "Монохром", 0xFFE8EEF2, 0xF207080A, 0xF2111316, 0xF2191C20, 0xFFF5F5F5, 0xFF969B9F, 0xFFFF6B6B),

            new Theme("FOX_RED", "Лиса — красная · минимал", 0xFFFF5B32, 0xF2070607, 0xF20D0A0A, 0xF2140E0D, 0xFFFFF5EF, 0xFFBCA093, 0xFFFF5966),
            new Theme("FOX_WHITE", "Лиса — белая", 0xFFF4F1E8, 0xF2080A0C, 0xF214171A, 0xF21C2125, 0xFFFFFFFF, 0xFFAAB0B5, 0xFFFF6B6B),
            new Theme("FOX_ORANGE", "Лиса — рыжая", 0xFFFF9A3D, 0xF20E0905, 0xF21A1109, 0xF226180C, 0xFFFFF5E9, 0xFFC1A186, 0xFFFF6A55),

            new Theme("SHULKER_PURPLE", "Шалкер — фиолетовый", 0xFFB983D8, 0xF20A0710, 0xF2161020, 0xF2211730, 0xFFF8EFFF, 0xFFA995B8, 0xFFFF6D9B),
            new Theme("SHULKER_DARK", "Шалкер — тёмный", 0xFF7E6A9E, 0xF2050509, 0xF20E0C15, 0xF2171321, 0xFFECE8F2, 0xFF8E8798, 0xFFE45F80),
            new Theme("SHULKER_CHORUS", "Шалкер — хорус", 0xFFD49BEF, 0xF20C0610, 0xF2190D20, 0xF2261530, 0xFFFFF0FF, 0xFFB999C6, 0xFFFF739C),

            new Theme("SNOW_CLASSIC", "Снеговик — классика", 0xFFBCEBFF, 0xF2050B10, 0xF20C1720, 0xF213222D, 0xFFF4FCFF, 0xFF91AAB6, 0xFFFF755E),
            new Theme("SNOW_ICE", "Снеговик — ледяной", 0xFF73D8FF, 0xF2030B12, 0xF2081723, 0xF20E2534, 0xFFEAFBFF, 0xFF87AEBE, 0xFFFF6B72),
            new Theme("SNOW_NIGHT", "Снеговик — ночной", 0xFF8EB8FF, 0xF2040610, 0xF20A1020, 0xF2111A2E, 0xFFEFF4FF, 0xFF8998B3, 0xFFFF657C)
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

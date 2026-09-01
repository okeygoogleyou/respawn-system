package ru.okeygoogle.respawnsystem.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Красная пиксельная лиса для темы Respawn System.
 *
 * Спрайт хранится прямо в коде, поэтому не зависит от загрузчика текстур
 * и стабильно работает в интерфейсе. Цвета лисы фиксированные:
 * RGB-тема меняет только рамки и линии вокруг неё.
 */
public final class FoxSprite {
    private static final int[] COLORS = {0xFF2A1417, 0xFF49201D, 0xFF7E281A, 0xFFBE341A, 0xFFEB481D, 0xFFFF6F30, 0xFFEFBCA6, 0xFFFFEADB};

    private static final String[] PIXELS = {
            "             B",
            "            AGA",
            "            HH",
            "           CHHHA",
            "    AABA  CDHGGA",
            "     GAACEEEDEGB",
            "     AGAEEEEEEDCC",
            "      GDEEEDEEEDD",
            "       CDEDAADEEEC",
            "       C EDAEHHFEC",
            "        BEEFHGGCCC",
            "      CDBFHGGGGGDC",
            "        AHAGGGGGEB",
            "         CFGGGHHEEC",
            "         DGGHHGHGEC",
            "        CDEHHHHHHEC",
            "        EEEGHHHHGGE",
            "       CEDEEGGHGGGD",
            "      DEEDEEEGGGFD    GD",
            "      EEEDDEDEGGDD    CHH",
            "     DEEDDCDDDGGDC    DHHHD",
            "     EDCCDCCBAGCBC    HHHHH",
            "    DDEEEECBBADAA    CHHHHG",
            "  CECEEEEEB BABAA   CHHHGGG",
            " CEDCDDDEDDCAACA CCDEEHGHGG",
            " CEDCDDDDDCCAACAACDDEGGEGEG",
            " EEEDCDDDCBCBACACCDDEDDGDGC",
            "CDEEEEBECCCC ABACCCDDDDDDD",
            " DDEEEEDDBAAAAAABCCDDDDCDC",
            " BCDDDDCBAAAAAAAACCCCCCCC",
            "  CCCDCCBAAAAA ABABBCCBB",
            "    CC"
    };

    private FoxSprite() {}

    public static void draw(GuiGraphicsExtractor g, int x, int y, int scale) {
        int s = Math.max(1, scale);
        for (int row = 0; row < PIXELS.length; row++) {
            String line = PIXELS[row];
            int col = 0;
            while (col < line.length()) {
                char ch = line.charAt(col);
                if (ch == ' ') {
                    col++;
                    continue;
                }
                int start = col;
                while (col + 1 < line.length() && line.charAt(col + 1) == ch) col++;
                int colorIndex = ch - 'A';
                if (colorIndex >= 0 && colorIndex < COLORS.length) {
                    g.fill(
                            x + start * s,
                            y + row * s,
                            x + (col + 1) * s,
                            y + (row + 1) * s,
                            COLORS[colorIndex]
                    );
                }
                col++;
            }
        }
    }

    public static void drawPaw(GuiGraphicsExtractor g, int x, int y, int scale, int color) {
        int s = Math.max(1, scale);
        // Подушечка.
        g.fill(x + 3 * s, y + 4 * s, x + 7 * s, y + 8 * s, color);
        // Пальцы.
        g.fill(x + 1 * s, y + 1 * s, x + 3 * s, y + 3 * s, color);
        g.fill(x + 4 * s, y, x + 6 * s, y + 2 * s, color);
        g.fill(x + 7 * s, y + 1 * s, x + 9 * s, y + 3 * s, color);
    }
}

package ru.okeygoogle.respawnsystem.client.ui;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class UiConfig {
    public static final UiConfig INSTANCE = new UiConfig();

    public boolean showGrid = true;
    public int gridSpacing = 28;
    public String brandText = "RESPAWN // СИСТЕМА";
    public boolean playUiSounds = true;

    // 0.3 UI
    public boolean rgbCycle = false;
    public int rgbSpeed = 3;

    private UiConfig() {}

    private Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("respawn-system-ui.properties");
    }

    public void load() {
        Path path = path();
        Properties p = new Properties();
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) { p.load(in); }
            catch (IOException ignored) {}
        }

        showGrid = Boolean.parseBoolean(p.getProperty("showGrid", "true"));
        try { gridSpacing = Math.max(12, Math.min(64, Integer.parseInt(p.getProperty("gridSpacing", "28")))); }
        catch (NumberFormatException ignored) { gridSpacing = 28; }

        brandText = p.getProperty("brandText", "RESPAWN // СИСТЕМА").trim();
        playUiSounds = Boolean.parseBoolean(p.getProperty("playUiSounds", "true"));
        rgbCycle = Boolean.parseBoolean(p.getProperty("rgbCycle", "false"));
        try { rgbSpeed = Math.max(1, Math.min(5, Integer.parseInt(p.getProperty("rgbSpeed", "3")))); }
        catch (NumberFormatException ignored) { rgbSpeed = 3; }

        save();
    }

    public void save() {
        Path path = path();
        Properties p = new Properties();
        p.setProperty("showGrid", Boolean.toString(showGrid));
        p.setProperty("gridSpacing", Integer.toString(gridSpacing));
        p.setProperty("brandText", brandText == null ? "RESPAWN // СИСТЕМА" : brandText);
        p.setProperty("playUiSounds", Boolean.toString(playUiSounds));
        p.setProperty("rgbCycle", Boolean.toString(rgbCycle));
        p.setProperty("rgbSpeed", Integer.toString(rgbSpeed));

        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                p.store(out, "Respawn System UI — author: okeygoogle");
            }
        } catch (IOException ignored) {}
    }
}

package ru.okeygoogle.respawnsystem;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class RespawnSounds {
    public static final SoundEvent CLICK = register("ui.click");
    public static final SoundEvent CONFIRM = register("ui.confirm");
    public static final SoundEvent ERROR = register("ui.error");
    public static final SoundEvent NOTIFICATION = register("ui.notification");

    private RespawnSounds() {}

    private static SoundEvent register(String path) {
        Identifier id = Identifier.fromNamespaceAndPath(RespawnSystemMod.MOD_ID, path);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    public static void initialize() {
        // Инициализация статических регистраций до заморозки реестров.
    }
}

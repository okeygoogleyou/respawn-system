package ru.okeygoogle.respawnsystem.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import ru.okeygoogle.respawnsystem.client.gui.SystemScreen;
import ru.okeygoogle.respawnsystem.net.SystemPayload;
import ru.okeygoogle.respawnsystem.client.net.Wire;
import ru.okeygoogle.respawnsystem.client.ui.UiConfig;

public final class RespawnSystemClient implements ClientModInitializer {
    public static final String MOD_ID = "respawn_system";

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "system")
    );

    private static final KeyMapping OPEN_SYSTEM = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.respawn_system.open",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_R,
            CATEGORY
    ));

    @Override
    public void onInitializeClient() {
        UiConfig.INSTANCE.load();
        ClientPlayNetworking.registerGlobalReceiver(SystemPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientSystemState.INSTANCE.accept(payload.data()))
        );
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientSystemState.INSTANCE.reset();
            client.execute(Wire::requestState);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientSystemState.INSTANCE.reset());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_SYSTEM.consumeClick()) {
                if (client.player != null && ClientSystemState.INSTANCE.registered()) {
                    client.gui.setScreen(new SystemScreen());
                } else if (client.player != null) {
                    Wire.requestState();
                }
            }
        });
    }
}

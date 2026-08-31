package ru.okeygoogle.respawnsystem;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import ru.okeygoogle.respawnsystem.net.SystemPayload;
import ru.okeygoogle.respawnsystem.server.ServerWire;
import ru.okeygoogle.respawnsystem.server.data.DataStore;
import ru.okeygoogle.respawnsystem.server.model.*;

import java.io.DataInputStream;
import java.util.*;

public final class RespawnSystemMod implements ModInitializer {
    public static final String MOD_ID = "respawn_system";
    private static final Set<String> THEMES =
            Set.of("SYSTEM", "CRIMSON", "VIOLET", "MONO");

    private static final DataStore STORE = new DataStore();

    @Override
    public void onInitialize() {
        RespawnSounds.initialize();

        PayloadTypeRegistry.clientboundPlay()
                .register(SystemPayload.TYPE, SystemPayload.CODEC);

        PayloadTypeRegistry.serverboundPlay()
                .register(SystemPayload.TYPE, SystemPayload.CODEC);

        ServerLifecycleEvents.SERVER_STARTING.register(server -> STORE.load());

        ServerPlayNetworking.registerGlobalReceiver(
                SystemPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();

                    try {
                        ServerWire.Incoming packet =
                                ServerWire.incoming(payload.data());

                        if (packet.protocol() != ServerWire.PROTOCOL) {
                            ServerWire.sendNotice(
                                    player,
                                    "Версия Системы клиента несовместима с сервером."
                            );
                            return;
                        }

                        handle(
                                player,
                                packet.action(),
                                packet.in()
                        );

                    } catch (Exception e) {
                        System.err.println(
                                "[Respawn System] Ошибка пакета от "
                                        + player.getName().getString()
                                        + ": "
                                        + e.getMessage()
                        );

                        ServerWire.sendNotice(
                                player,
                                "Система отклонила некорректный запрос."
                        );
                    }
                }
        );

        ServerPlayerEvents.JOIN.register(RespawnSystemMod::sendFullNow);

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> STORE.save());

        System.out.println(
                "[Respawn System] Загружена. Автор: okeygoogle"
        );
    }

    private static void handle(
            ServerPlayer player,
            String action,
            DataInputStream in
    ) throws Exception {

        switch (action) {

            case "REQUEST_STATE" ->
                    sendFullNow(player);

            case "INITIAL_NICK" ->
                    initialNick(
                            player,
                            ServerWire.clean(in.readUTF(), 32)
                    );

            case "COSMETICS" ->
                    cosmetics(
                            player,
                            ServerWire.clean(in.readUTF(), 16),
                            ServerWire.clean(in.readUTF(), 9),
                            ServerWire.clean(in.readUTF(), 60)
                    );

            case "CHAT_SEND" ->
                    chat(
                            player,
                            ServerWire.clean(in.readUTF(), 280)
                    );

            case "MARKER_CREATE" -> {
                String title =
                        ServerWire.clean(in.readUTF(), 48);

                String type =
                        ServerWire.clean(in.readUTF(), 24);

                in.readUTF();
                in.readInt();
                in.readInt();
                in.readInt();

                markerCreate(
                        player,
                        title,
                        type
                );
            }

            case "MARKER_DELETE" ->
                    markerDelete(
                            player,
                            in.readInt()
                    );

            case "TICKET_CREATE" ->
                    ticketCreate(
                            player,
                            ServerWire.clean(in.readUTF(), 40),
                            ServerWire.clean(in.readUTF(), 500)
                    );

            case "TICKET_MESSAGE" ->
                    ticketMessage(
                            player,
                            in.readInt(),
                            ServerWire.clean(in.readUTF(), 500),
                            false
                    );

            case "TICKET_INTERNAL" ->
                    ticketMessage(
                            player,
                            in.readInt(),
                            ServerWire.clean(in.readUTF(), 500),
                            true
                    );

            case "TICKET_TAKE" ->
                    ticketTake(
                            player,
                            in.readInt()
                    );

            case "TICKET_CLOSE" ->
                    ticketClose(
                            player,
                            in.readInt()
                    );

            case "SYSTEM_ANNOUNCE" ->
                    announcement(
                            player,
                            ServerWire.clean(in.readUTF(), 280)
                    );

            case "ADMIN_PROFILE_UPDATE" ->
                    adminProfileUpdate(
                            player,
                            in
                    );

            default ->
                    ServerWire.sendNotice(
                            player,
                            "Неизвестный запрос Системы: " + action
                    );
        }
    }

    private static void initialNick(
            ServerPlayer player,
            String nick
    ) {
        ProfileData p =
                STORE.getOrCreate(
                        player.getUUID(),
                        player.getName().getString()
                );

        if (p.registered) {
            ServerWire.sendNotice(
                    player,
                    "Никнейм уже зарегистрирован. Его может изменить администратор."
            );
            return;
        }

        if (nick.length() < 2) {
            ServerWire.sendNotice(
                    player,
                    "Никнейм слишком короткий."
            );
            return;
        }

        if (STORE.systemNameTaken(
                nick,
                player.getUUID()
        )) {
            ServerWire.sendNotice(
                    player,
                    "Этот системный никнейм уже занят."
            );
            return;
        }

        p.minecraftName =
                player.getName().getString();

        p.systemName = nick;
        p.registered = true;
        p.status = "АКТИВЕН";

        STORE.addChat(
                new ChatEntry(
                        System.currentTimeMillis(),
                        "СИСТЕМА",
                        "Новый профиль зарегистрирован: " + nick,
                        true
                )
        );

        persistAndSync(
                player.level().getServer()
        );
    }

    private static void cosmetics(
            ServerPlayer player,
            String theme,
            String accent,
            String subtitle
    ) {
        ProfileData p = registered(player);

        if (p == null)
            return;

        String normalizedTheme =
                theme.toUpperCase(Locale.ROOT);

        p.theme =
                THEMES.contains(normalizedTheme)
                        ? normalizedTheme
                        : "SYSTEM";

        p.accent =
                accent.matches("#[0-9A-Fa-f]{6}")
                        ? accent.toUpperCase(Locale.ROOT)
                        : "#57D7FF";

        p.subtitle = subtitle;

        persistAndSync(
                player.level().getServer()
        );
    }

    private static void chat(
            ServerPlayer player,
            String message
    ) {
        ProfileData p =
                registered(player);

        if (p == null || message.isBlank())
            return;

        STORE.addChat(
                new ChatEntry(
                        System.currentTimeMillis(),
                        p.systemName,
                        message,
                        false
                )
        );

        persistAndSync(
                player.level().getServer()
        );
    }

    private static void markerCreate(
            ServerPlayer player,
            String title,
            String type
    ) {
        ProfileData p =
                registered(player);

        if (p == null)
            return;

        var pos =
                player.blockPosition();

        String dimension =
                player.level()
                        .dimension()
                        .identifier()
                        .toString();

        STORE.addMarker(
                player.getUUID(),
                p.systemName,
                title.isBlank()
                        ? "Метка"
                        : title,
                type.isBlank()
                        ? "ОБЩАЯ"
                        : type,
                dimension,
                pos.getX(),
                pos.getY(),
                pos.getZ()
        );

        persistAndSync(
                player.level().getServer()
        );
    }

    private static void markerDelete(
            ServerPlayer player,
            int id
    ) {
        if (!STORE.removeMarker(
                id,
                player.getUUID(),
                role(player).canAdmin()
        )) {
            ServerWire.sendNotice(
                    player,
                    "У вас нет права удалить эту метку."
            );
            return;
        }

        persistAndSync(
                player.level().getServer()
        );
    }

    private static void ticketCreate(
            ServerPlayer player,
            String category,
            String message
    ) {
        ProfileData p =
                registered(player);

        if (p == null || message.isBlank())
            return;

        STORE.createTicket(
                player.getUUID(),
                p.systemName,
                category.isBlank()
                        ? "Помощь"
                        : category,
                message
        );

        persistAndSync(
                player.level().getServer()
        );
    }

    private static void ticketMessage(
            ServerPlayer player,
            int id,
            String message,
            boolean internal
    ) {
        if (message.isBlank())
            return;

        TicketData ticket =
                STORE.ticket(id);

        if (ticket == null) {
            ServerWire.sendNotice(
                    player,
                    "Обращение не найдено."
            );
            return;
        }

        Role role =
                role(player);

        boolean owner =
                ticket.owner.equals(
                        player.getUUID()
                );

        if (!owner && !role.canSupport()) {
            ServerWire.sendNotice(
                    player,
                    "Нет доступа к этому обращению."
            );
            return;
        }

        if (internal && !role.canSupport()) {
            ServerWire.sendNotice(
                    player,
                    "Внутренние заметки доступны только поддержке."
            );
            return;
        }

        if ("ЗАКРЫТО".equals(ticket.status)
                && !role.canSupport()) {

            ServerWire.sendNotice(
                    player,
                    "Обращение уже закрыто."
            );
            return;
        }

        ProfileData p =
                STORE.getOrCreate(
                        player.getUUID(),
                        player.getName().getString()
                );

        String sender =
                p.registered
                        ? p.systemName
                        : player.getName().getString();

        ticket.messages.add(
                new TicketMessage(
                        System.currentTimeMillis(),
                        sender,
                        role.name(),
                        message,
                        internal
                )
        );

        persistAndSync(
                player.level().getServer()
        );
    }

    private static void ticketTake(
            ServerPlayer player,
            int id
    ) {
        Role role =
                role(player);

        if (!role.canSupport()) {
            ServerWire.sendNotice(
                    player,
                    "Нет доступа к центру поддержки."
            );
            return;
        }

        TicketData ticket =
                STORE.ticket(id);

        if (ticket == null)
            return;

        ProfileData p =
                STORE.getOrCreate(
                        player.getUUID(),
                        player.getName().getString()
                );

        ticket.assignedTo =
                p.registered
                        ? p.systemName
                        : player.getName().getString();

        ticket.status =
                "В РАБОТЕ";

        persistAndSync(
                player.level().getServer()
        );
    }

    private static void ticketClose(
            ServerPlayer player,
            int id
    ) {
        if (!role(player).canSupport()) {
            ServerWire.sendNotice(
                    player,
                    "Нет доступа к центру поддержки."
            );
            return;
        }

        TicketData ticket =
                STORE.ticket(id);

        if (ticket == null)
            return;

        ticket.status =
                "ЗАКРЫТО";

        persistAndSync(
                player.level().getServer()
        );
    }

    private static void announcement(
            ServerPlayer player,
            String text
    ) {
        if (!role(player).canAdmin()) {
            ServerWire.sendNotice(
                    player,
                    "Недостаточный уровень доступа."
            );
            return;
        }

        if (text.isBlank())
            return;

        STORE.addChat(
                new ChatEntry(
                        System.currentTimeMillis(),
                        "СИСТЕМА",
                        text,
                        true
                )
        );

        persistAndSync(
                player.level().getServer()
        );
    }

    private static void adminProfileUpdate(
            ServerPlayer actor,
            DataInputStream in
    ) throws Exception {

        Role actorRole =
                role(actor);

        if (!actorRole.canAdmin()) {
            ServerWire.sendNotice(
                    actor,
                    "Недостаточный уровень доступа."
            );
            return;
        }

        UUID targetId =
                UUID.fromString(
                        in.readUTF()
                );

        ProfileData p =
                STORE.profile(targetId);

        if (p == null || !p.registered) {
            ServerWire.sendNotice(
                    actor,
                    "Профиль не найден."
            );
            return;
        }

        String requestedSystemName =
                nonEmpty(
                        ServerWire.clean(
                                in.readUTF(),
                                32
                        ),
                        p.systemName
                );

        if (STORE.systemNameTaken(
                requestedSystemName,
                targetId
        )) {
            ServerWire.sendNotice(
                    actor,
                    "Этот системный никнейм уже занят."
            );
            return;
        }

        p.systemName =
                requestedSystemName;

        p.race =
                nonEmpty(
                        ServerWire.clean(in.readUTF(), 48),
                        "НЕ ОПРЕДЕЛЕНА"
                );

        p.origin =
                nonEmpty(
                        ServerWire.clean(in.readUTF(), 64),
                        "—"
                );

        p.status =
                nonEmpty(
                        ServerWire.clean(in.readUTF(), 48),
                        "НЕОПОЗНАННЫЙ"
                );

        p.faction =
                nonEmpty(
                        ServerWire.clean(in.readUTF(), 64),
                        "—"
                );

        Role requestedRole =
                Role.safe(
                        ServerWire.clean(
                                in.readUTF(),
                                16
                        )
                );

        if (requestedRole == Role.OWNER
                && !isOpLike(actor)) {

            ServerWire.sendNotice(
                    actor,
                    "Только OP может назначить роль OWNER."
            );

        } else if (
                actorRole == Role.ADMIN
                        && requestedRole.rank()
                        >= Role.ADMIN.rank()
        ) {

            ServerWire.sendNotice(
                    actor,
                    "Администратор может назначать роли только до MODERATOR."
            );

        } else {
            p.role =
                    requestedRole;
        }

        p.abilities =
                new ArrayList<>(
                        ServerWire.readList(
                                in,
                                32,
                                80
                        )
                );

        p.traits =
                new ArrayList<>(
                        ServerWire.readList(
                                in,
                                32,
                                80
                        )
                );

        p.adminNote =
                ServerWire.clean(
                        in.readUTF(),
                        500
                );

        persistAndSync(
                actor.level().getServer()
        );
    }

    private static ProfileData registered(
            ServerPlayer player
    ) {
        ProfileData p =
                STORE.getOrCreate(
                        player.getUUID(),
                        player.getName().getString()
                );

        if (!p.registered) {
            ServerWire.sendNotice(
                    player,
                    "Сначала завершите регистрацию в Системе."
            );

            sendFullNow(player);

            return null;
        }

        return p;
    }

    private static Role role(
            ServerPlayer player
    ) {
        if (isOpLike(player))
            return Role.OWNER;

        ProfileData p =
                STORE.getOrCreate(
                        player.getUUID(),
                        player.getName().getString()
                );

        return p.role == null
                ? Role.PLAYER
                : p.role;
    }

    private static boolean isOpLike(
            ServerPlayer player
    ) {
        return player
                .createCommandSourceStack()
                .permissions()
                .hasPermission(
                        Permissions.COMMANDS_MODERATOR
                );
    }

    private static void sendFullNow(
            ServerPlayer player
    ) {
        ServerWire.sendFull(
                player,
                STORE,
                role(player)
        );
    }

    private static void persistAndSync(
            MinecraftServer server
    ) {
        STORE.save();

        if (server == null)
            return;

        for (ServerPlayer player :
                server.getPlayerList().getPlayers()) {

            sendFullNow(player);
        }
    }

    private static String nonEmpty(
            String value,
            String fallback
    ) {
        return value == null
                || value.isBlank()
                ? fallback
                : value;
    }
}

package ru.okeygoogle.respawnsystem;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
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

    private static final Set<String> THEMES = Set.of(
            "SYSTEM", "CRIMSON", "VIOLET", "MONO",
            "FOX_RED", "FOX_WHITE", "FOX_ORANGE",
            "SHULKER_PURPLE", "SHULKER_DARK", "SHULKER_CHORUS",
            "SNOW_CLASSIC", "SNOW_ICE", "SNOW_NIGHT"
    );
    private static final Set<String> FRAMES = Set.of("SYSTEM", "FOX", "SHULKER", "SNOWMAN", "MINIMAL");
    private static final Set<String> DECORS = Set.of("NONE", "FOX", "SHULKER", "SNOWMAN");

    private static final DataStore STORE = new DataStore();

    @Override
    public void onInitialize() {
        RespawnSounds.initialize();
        PayloadTypeRegistry.clientboundPlay().register(SystemPayload.TYPE, SystemPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SystemPayload.TYPE, SystemPayload.CODEC);
        ServerLifecycleEvents.SERVER_STARTING.register(server -> STORE.load());

        ServerPlayNetworking.registerGlobalReceiver(SystemPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            try {
                ServerWire.Incoming packet = ServerWire.incoming(payload.data());
                if (packet.protocol() != ServerWire.PROTOCOL) {
                    ServerWire.sendNotice(player, "Версия Системы клиента несовместима с сервером.");
                    return;
                }
                handle(player, packet.action(), packet.in());
            } catch (Exception e) {
                System.err.println("[Respawn System] Ошибка пакета от " + player.getName().getString() + ": " + e.getMessage());
                ServerWire.sendNotice(player, "Система отклонила некорректный запрос.");
            }
        });

        ServerPlayerEvents.JOIN.register(RespawnSystemMod::sendFullNow);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> STORE.save());
        System.out.println("[Respawn System] Загружена. Автор: okeygoogle");
    }

    private static void handle(ServerPlayer player, String action, DataInputStream in) throws Exception {
        switch (action) {
            case "REQUEST_STATE" -> sendFullNow(player);
            case "INITIAL_NICK" -> initialNick(player, ServerWire.clean(in.readUTF(), 32));
            case "COSMETICS" -> cosmetics(
                    player,
                    ServerWire.clean(in.readUTF(), 32),
                    ServerWire.clean(in.readUTF(), 9),
                    ServerWire.clean(in.readUTF(), 60),
                    ServerWire.clean(in.readUTF(), 16),
                    ServerWire.clean(in.readUTF(), 16)
            );
            case "CHAT_SEND" -> chat(player, ServerWire.clean(in.readUTF(), 280));
            case "SYSTEM_CHAT_SEND" -> systemChat(player, ServerWire.clean(in.readUTF(), 280));
            case "DM_SEND" -> directMessage(player, UUID.fromString(in.readUTF()), ServerWire.clean(in.readUTF(), 500));
            case "ADMIN_CHAT_DELETE" -> adminDeleteChat(player, in.readLong(), false);
            case "ADMIN_CHAT_CLEAR" -> adminClearChat(player, false);
            case "ADMIN_SYSTEM_CHAT_DELETE" -> adminDeleteChat(player, in.readLong(), true);
            case "ADMIN_SYSTEM_CHAT_CLEAR" -> adminClearChat(player, true);
            case "MARKER_CREATE" -> {
                String title = ServerWire.clean(in.readUTF(), 48);
                String type = ServerWire.clean(in.readUTF(), 24);
                in.readUTF(); in.readInt(); in.readInt(); in.readInt();
                markerCreate(player, title, type);
            }
            case "MARKER_DELETE" -> markerDelete(player, in.readInt());
            case "TICKET_CREATE" -> ticketCreate(player, ServerWire.clean(in.readUTF(), 40), ServerWire.clean(in.readUTF(), 500));
            case "TICKET_MESSAGE" -> ticketMessage(player, in.readInt(), ServerWire.clean(in.readUTF(), 500), false);
            case "TICKET_INTERNAL" -> ticketMessage(player, in.readInt(), ServerWire.clean(in.readUTF(), 500), true);
            case "TICKET_TAKE" -> ticketTake(player, in.readInt());
            case "TICKET_CLOSE" -> ticketClose(player, in.readInt());
            case "ADMIN_TICKET_DELETE" -> adminDeleteTicket(player, in.readInt());
            case "ADMIN_TICKETS_CLEAR" -> adminClearTickets(player);
            case "ADMIN_SET_ROLE" -> adminSetRole(player, UUID.fromString(in.readUTF()), Role.safe(in.readUTF()));
            case "ADMIN_PROFILE_UPDATE" -> adminProfileUpdate(player, in);
            default -> ServerWire.sendNotice(player, "Неизвестный запрос Системы: " + action);
        }
    }

    private static void initialNick(ServerPlayer player, String nick) {
        ProfileData p = STORE.getOrCreate(player.getUUID(), player.getName().getString());
        if (p.registered) {
            ServerWire.sendNotice(player, "Никнейм уже зарегистрирован. Его может изменить администратор.");
            return;
        }
        if (nick.length() < 2) {
            ServerWire.sendNotice(player, "Никнейм слишком короткий.");
            return;
        }
        if (STORE.systemNameTaken(nick, player.getUUID())) {
            ServerWire.sendNotice(player, "Этот системный никнейм уже занят.");
            return;
        }
        p.minecraftName = player.getName().getString();
        p.systemName = nick;
        p.registered = true;
        p.status = "АКТИВЕН";
        STORE.addChat(new ChatEntry(System.currentTimeMillis(), "СИСТЕМА", "Новый профиль зарегистрирован: " + nick, true));
        persistAndSync(player.level().getServer());
    }

    private static void cosmetics(ServerPlayer player, String theme, String accent, String subtitle, String frame, String decor) {
        ProfileData p = registered(player);
        if (p == null) return;
        String normalizedTheme = theme.toUpperCase(Locale.ROOT);
        String normalizedFrame = frame.toUpperCase(Locale.ROOT);
        String normalizedDecor = decor.toUpperCase(Locale.ROOT);
        p.theme = THEMES.contains(normalizedTheme) ? normalizedTheme : "SYSTEM";
        p.accent = accent.matches("#[0-9A-Fa-f]{6}") ? accent.toUpperCase(Locale.ROOT) : "#57D7FF";
        p.subtitle = subtitle;
        p.frame = FRAMES.contains(normalizedFrame) ? normalizedFrame : "SYSTEM";
        p.decor = DECORS.contains(normalizedDecor) ? normalizedDecor : "NONE";
        persistAndSync(player.level().getServer());
    }

    private static void chat(ServerPlayer player, String message) {
        ProfileData p = registered(player);
        if (p == null || message.isBlank()) return;
        STORE.addChat(new ChatEntry(System.currentTimeMillis(), p.systemName, message, false));
        persistAndSync(player.level().getServer());
    }

    private static void systemChat(ServerPlayer player, String message) {
        if (!role(player).canAdmin()) {
            ServerWire.sendNotice(player, "Нет доступа к системному каналу.");
            return;
        }
        if (message.isBlank()) return;
        ProfileData p = STORE.getOrCreate(player.getUUID(), player.getName().getString());
        String sender = p.registered ? p.systemName : player.getName().getString();
        STORE.addSystemChat(new ChatEntry(System.currentTimeMillis(), sender, message, true));
        persistAndSync(player.level().getServer());
    }

    private static void directMessage(ServerPlayer player, UUID recipientId, String text) {
        ProfileData senderProfile = registered(player);
        if (senderProfile == null || text.isBlank()) return;
        if (role(player) == Role.OWNER) {
            ServerWire.sendNotice(player, "Владелец может просматривать личные сообщения, но не писать в них.");
            return;
        }
        if (recipientId.equals(player.getUUID())) {
            ServerWire.sendNotice(player, "Нельзя отправить личное сообщение самому себе.");
            return;
        }
        ProfileData recipient = STORE.profile(recipientId);
        if (recipient == null || !recipient.registered) {
            ServerWire.sendNotice(player, "Получатель не найден.");
            return;
        }
        STORE.addDirectMessage(new DirectMessage(
                System.currentTimeMillis(), player.getUUID(), senderProfile.systemName,
                recipient.uuid, recipient.systemName, text
        ));
        persistAndSync(player.level().getServer());
    }

    private static void adminDeleteChat(ServerPlayer player, long time, boolean system) {
        if (!role(player).canAdmin()) {
            ServerWire.sendNotice(player, "Недостаточный уровень доступа.");
            return;
        }
        boolean removed = system ? STORE.removeSystemChat(time) : STORE.removeChat(time);
        if (!removed) ServerWire.sendNotice(player, "Сообщение уже отсутствует.");
        persistAndSync(player.level().getServer());
    }

    private static void adminClearChat(ServerPlayer player, boolean system) {
        if (!role(player).canAdmin()) {
            ServerWire.sendNotice(player, "Недостаточный уровень доступа.");
            return;
        }
        if (system) STORE.clearSystemChat(); else STORE.clearChat();
        persistAndSync(player.level().getServer());
    }

    private static void markerCreate(ServerPlayer player, String title, String type) {
        ProfileData p = registered(player);
        if (p == null) return;
        var pos = player.blockPosition();
        String dimension = player.level().dimension().identifier().toString();
        STORE.addMarker(player.getUUID(), p.systemName,
                title.isBlank() ? "Метка" : title,
                type.isBlank() ? "ОБЩАЯ" : type,
                dimension, pos.getX(), pos.getY(), pos.getZ());
        persistAndSync(player.level().getServer());
    }

    private static void markerDelete(ServerPlayer player, int id) {
        if (!STORE.removeMarker(id, player.getUUID(), role(player).canAdmin())) {
            ServerWire.sendNotice(player, "У вас нет права удалить эту метку.");
            return;
        }
        persistAndSync(player.level().getServer());
    }

    private static void ticketCreate(ServerPlayer player, String category, String message) {
        ProfileData p = registered(player);
        if (p == null || message.isBlank()) return;
        STORE.createTicket(player.getUUID(), p.systemName, category.isBlank() ? "Помощь" : category, message);
        persistAndSync(player.level().getServer());
    }

    private static void ticketMessage(ServerPlayer player, int id, String message, boolean internal) {
        if (message.isBlank()) return;
        TicketData ticket = STORE.ticket(id);
        if (ticket == null) {
            ServerWire.sendNotice(player, "Обращение не найдено.");
            return;
        }
        Role currentRole = role(player);
        boolean owner = ticket.owner.equals(player.getUUID());
        if (!owner && !currentRole.canSupport()) {
            ServerWire.sendNotice(player, "Нет доступа к этому обращению.");
            return;
        }
        if (internal && !currentRole.canSupport()) {
            ServerWire.sendNotice(player, "Внутренние заметки доступны только поддержке.");
            return;
        }
        if ("ЗАКРЫТО".equals(ticket.status) && !currentRole.canSupport()) {
            ServerWire.sendNotice(player, "Обращение уже закрыто.");
            return;
        }
        ProfileData p = STORE.getOrCreate(player.getUUID(), player.getName().getString());
        String sender = p.registered ? p.systemName : player.getName().getString();
        ticket.messages.add(new TicketMessage(System.currentTimeMillis(), sender, currentRole.name(), message, internal));
        persistAndSync(player.level().getServer());
    }

    private static void ticketTake(ServerPlayer player, int id) {
        if (!role(player).canSupport()) {
            ServerWire.sendNotice(player, "Нет доступа к центру поддержки.");
            return;
        }
        TicketData ticket = STORE.ticket(id);
        if (ticket == null) return;
        ProfileData p = STORE.getOrCreate(player.getUUID(), player.getName().getString());
        ticket.assignedTo = p.registered ? p.systemName : player.getName().getString();
        ticket.status = "В РАБОТЕ";
        persistAndSync(player.level().getServer());
    }

    private static void ticketClose(ServerPlayer player, int id) {
        if (!role(player).canSupport()) {
            ServerWire.sendNotice(player, "Нет доступа к центру поддержки.");
            return;
        }
        TicketData ticket = STORE.ticket(id);
        if (ticket == null) return;
        ticket.status = "ЗАКРЫТО";
        persistAndSync(player.level().getServer());
    }

    private static void adminDeleteTicket(ServerPlayer player, int id) {
        if (!role(player).canAdmin()) {
            ServerWire.sendNotice(player, "Недостаточный уровень доступа.");
            return;
        }
        if (!STORE.deleteTicket(id)) ServerWire.sendNotice(player, "Обращение не найдено.");
        persistAndSync(player.level().getServer());
    }

    private static void adminClearTickets(ServerPlayer player) {
        if (!role(player).canAdmin()) {
            ServerWire.sendNotice(player, "Недостаточный уровень доступа.");
            return;
        }
        STORE.clearTickets();
        persistAndSync(player.level().getServer());
    }

    private static void adminSetRole(ServerPlayer actor, UUID targetId, Role requestedRole) {
        Role actorRole = role(actor);
        if (!actorRole.canAdmin()) {
            ServerWire.sendNotice(actor, "Недостаточный уровень доступа.");
            return;
        }
        ProfileData target = STORE.profile(targetId);
        if (target == null || !target.registered) {
            ServerWire.sendNotice(actor, "Профиль не найден.");
            return;
        }
        if (actorRole == Role.ADMIN && requestedRole.rank() > Role.MODERATOR.rank()) {
            ServerWire.sendNotice(actor, "Администратор может назначать роли только до Модератора.");
            return;
        }
        target.role = requestedRole;
        persistAndSync(actor.level().getServer());
    }

    private static void adminProfileUpdate(ServerPlayer actor, DataInputStream in) throws Exception {
        Role actorRole = role(actor);
        if (!actorRole.canAdmin()) {
            ServerWire.sendNotice(actor, "Недостаточный уровень доступа.");
            return;
        }

        UUID targetId = UUID.fromString(in.readUTF());
        ProfileData p = STORE.profile(targetId);
        if (p == null || !p.registered) {
            ServerWire.sendNotice(actor, "Профиль не найден.");
            return;
        }

        String requestedSystemName = nonEmpty(ServerWire.clean(in.readUTF(), 32), p.systemName);
        if (STORE.systemNameTaken(requestedSystemName, targetId)) {
            ServerWire.sendNotice(actor, "Этот системный никнейм уже занят.");
            return;
        }

        p.systemName = requestedSystemName;
        p.race = nonEmpty(ServerWire.clean(in.readUTF(), 48), "НЕ ОПРЕДЕЛЕНА");
        p.origin = nonEmpty(ServerWire.clean(in.readUTF(), 64), "—");
        p.status = nonEmpty(ServerWire.clean(in.readUTF(), 48), "НЕОПОЗНАННЫЙ");
        p.faction = nonEmpty(ServerWire.clean(in.readUTF(), 64), "—");

        Role requestedRole = Role.safe(ServerWire.clean(in.readUTF(), 16));
        if (actorRole == Role.ADMIN && requestedRole.rank() > Role.MODERATOR.rank()) {
            ServerWire.sendNotice(actor, "Администратор может назначать роли только до Модератора.");
        } else {
            p.role = requestedRole;
        }

        p.abilities = new ArrayList<>(ServerWire.readList(in, 32, 80));
        p.traits = new ArrayList<>(ServerWire.readList(in, 32, 80));
        p.adminNote = ServerWire.clean(in.readUTF(), 500);
        persistAndSync(actor.level().getServer());
    }

    private static ProfileData registered(ServerPlayer player) {
        ProfileData p = STORE.getOrCreate(player.getUUID(), player.getName().getString());
        if (!p.registered) {
            ServerWire.sendNotice(player, "Сначала завершите регистрацию в Системе.");
            sendFullNow(player);
            return null;
        }
        return p;
    }

    private static Role role(ServerPlayer player) {
        if (isOpLike(player)) return Role.OWNER;
        ProfileData p = STORE.getOrCreate(player.getUUID(), player.getName().getString());
        return p.role == null ? Role.PLAYER : p.role;
    }

    private static boolean isOpLike(ServerPlayer player) {
        return player.createCommandSourceStack().permissions().hasPermission(Permissions.COMMANDS_MODERATOR);
    }

    private static void sendFullNow(ServerPlayer player) {
        ServerWire.sendFull(player, STORE, role(player));
    }

    private static void persistAndSync(MinecraftServer server) {
        STORE.save();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sendFullNow(player);
    }

    private static String nonEmpty(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

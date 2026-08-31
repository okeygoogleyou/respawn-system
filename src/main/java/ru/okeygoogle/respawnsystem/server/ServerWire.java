package ru.okeygoogle.respawnsystem.server;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import ru.okeygoogle.respawnsystem.net.SystemPayload;
import ru.okeygoogle.respawnsystem.server.data.DataStore;
import ru.okeygoogle.respawnsystem.server.model.*;

import java.io.*;
import java.util.*;

public final class ServerWire {
    public static final int PROTOCOL = 3;

    private ServerWire() {}

    public record Incoming(int protocol, String action, DataInputStream in) {}

    public static Incoming incoming(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        return new Incoming(in.readInt(), in.readUTF(), in);
    }

    public static void sendNotice(ServerPlayer player, String text) {
        send(player, packet("NOTICE", out -> out.writeUTF(text == null ? "" : text)));
    }

    public static void sendFull(ServerPlayer player, DataStore store, Role effectiveRole) {
        send(player, full(store, player, effectiveRole));
    }

    public static SystemPayload full(DataStore store, ServerPlayer viewer, Role effectiveRole) {
        return packet("FULL_SYNC", out -> {
            ProfileData rawSelf = store.getOrCreate(viewer.getUUID(), viewer.getName().getString());
            ProfileData self = rawSelf.copy(effectiveRole.canAdmin());
            self.role = effectiveRole;
            out.writeBoolean(rawSelf.registered);
            out.writeUTF(effectiveRole.name());
            writeProfile(out, self);

            List<ProfileData> profiles = store.profiles().stream()
                    .filter(p -> p.registered)
                    .limit(DataStore.MAX_PROFILES_IN_SYNC)
                    .map(p -> p.copy(effectiveRole.canAdmin()))
                    .toList();
            out.writeInt(profiles.size());
            for (ProfileData p : profiles) writeProfile(out, p);

            writeChatList(out, store.chat());
            writeChatList(out, store.systemChat());

            List<DirectMessage> dms = store.directMessages().stream()
                    .filter(m -> effectiveRole == Role.OWNER
                            || m.sender().equals(viewer.getUUID())
                            || m.recipient().equals(viewer.getUUID()))
                    .toList();
            out.writeInt(dms.size());
            for (DirectMessage m : dms) {
                out.writeLong(m.time());
                out.writeUTF(m.sender().toString());
                out.writeUTF(n(m.senderName()));
                out.writeUTF(m.recipient().toString());
                out.writeUTF(n(m.recipientName()));
                out.writeUTF(n(m.text()));
            }

            List<MarkerData> markers = store.markers();
            out.writeInt(markers.size());
            for (MarkerData m : markers) {
                out.writeInt(m.id());
                out.writeUTF(m.owner().toString());
                out.writeUTF(n(m.ownerName()));
                out.writeUTF(n(m.title()));
                out.writeUTF(n(m.type()));
                out.writeUTF(n(m.dimension()));
                out.writeInt(m.x());
                out.writeInt(m.y());
                out.writeInt(m.z());
            }

            List<TicketData> tickets = store.tickets().stream()
                    .filter(t -> effectiveRole.canSupport() || t.owner.equals(viewer.getUUID()))
                    .map(t -> t.visibleCopy(effectiveRole.canSupport()))
                    .toList();
            out.writeInt(tickets.size());
            for (TicketData ticket : tickets) writeTicket(out, ticket);
            out.writeUTF("");
        });
    }

    private static void writeChatList(DataOutputStream out, List<ChatEntry> list) throws IOException {
        out.writeInt(list.size());
        for (ChatEntry e : list) {
            out.writeLong(e.time());
            out.writeUTF(n(e.sender()));
            out.writeUTF(n(e.message()));
            out.writeBoolean(e.system());
        }
    }

    private static void writeProfile(DataOutputStream out, ProfileData p) throws IOException {
        out.writeUTF(p.uuid.toString());
        out.writeUTF(n(p.minecraftName));
        out.writeUTF(n(p.systemName));
        out.writeUTF(n(p.race));
        out.writeUTF(n(p.origin));
        out.writeUTF(n(p.status));
        out.writeUTF(n(p.faction));
        out.writeUTF(p.role == null ? Role.PLAYER.name() : p.role.name());
        writeList(out, p.abilities);
        writeList(out, p.traits);
        out.writeUTF(n(p.subtitle));
        out.writeUTF(n(p.theme));
        out.writeUTF(n(p.accent));
        out.writeUTF(n(p.frame));
        out.writeUTF(n(p.decor));
        out.writeUTF(n(p.adminNote));
    }

    private static void writeTicket(DataOutputStream out, TicketData t) throws IOException {
        out.writeInt(t.id);
        out.writeUTF(t.owner.toString());
        out.writeUTF(n(t.ownerName));
        out.writeUTF(n(t.category));
        out.writeUTF(n(t.status));
        out.writeUTF(n(t.assignedTo));
        out.writeInt(t.messages.size());
        for (TicketMessage m : t.messages) {
            out.writeLong(m.time());
            out.writeUTF(n(m.sender()));
            out.writeUTF(n(m.role()));
            out.writeUTF(n(m.text()));
            out.writeBoolean(m.internal());
        }
    }

    public static List<String> readList(DataInputStream in, int maxItems, int maxLength) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > maxItems) throw new IOException("Некорректный размер списка: " + count);
        List<String> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(clean(in.readUTF(), maxLength));
        return result;
    }

    public static String clean(String text, int max) {
        if (text == null) return "";
        String sanitized = text.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
        return sanitized.length() <= max ? sanitized : sanitized.substring(0, max);
    }

    private static void writeList(DataOutputStream out, List<String> list) throws IOException {
        List<String> safe = list == null ? List.of() : list;
        out.writeInt(safe.size());
        for (String value : safe) out.writeUTF(n(value));
    }

    private static SystemPayload packet(String action, Writer writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(PROTOCOL);
            out.writeUTF(action);
            writer.write(out);
            out.flush();
            return new SystemPayload(bytes.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось сформировать пакет Системы", e);
        }
    }

    private static void send(ServerPlayer player, SystemPayload payload) {
        if (ServerPlayNetworking.canSend(player, SystemPayload.TYPE)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private static String n(String s) { return s == null ? "" : s; }

    @FunctionalInterface
    private interface Writer { void write(DataOutputStream out) throws IOException; }
}

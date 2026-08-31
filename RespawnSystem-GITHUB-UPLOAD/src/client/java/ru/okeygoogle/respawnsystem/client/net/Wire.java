package ru.okeygoogle.respawnsystem.client.net;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import ru.okeygoogle.respawnsystem.net.SystemPayload;
import ru.okeygoogle.respawnsystem.client.model.ProfileData;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public final class Wire {
    public static final int PROTOCOL = 2;
    private Wire() {}

    @FunctionalInterface
    private interface Writer { void write(DataOutputStream out) throws IOException; }

    private static void send(String action, Writer writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(PROTOCOL);
            out.writeUTF(action);
            writer.write(out);
            out.flush();
            ClientPlayNetworking.send(new SystemPayload(bytes.toByteArray()));
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось сформировать пакет Системы", e);
        }
    }

    public static void requestState() { send("REQUEST_STATE", out -> {}); }
    public static void initialNick(String nick) { send("INITIAL_NICK", out -> out.writeUTF(nick)); }
    public static void cosmetics(String theme, String accent, String subtitle) {
        send("COSMETICS", out -> { out.writeUTF(theme); out.writeUTF(accent); out.writeUTF(subtitle); });
    }
    public static void chat(String message) { send("CHAT_SEND", out -> out.writeUTF(message)); }
    public static void markerCreate(String title, String type, String dimension, int x, int y, int z) {
        send("MARKER_CREATE", out -> { out.writeUTF(title); out.writeUTF(type); out.writeUTF(dimension); out.writeInt(x); out.writeInt(y); out.writeInt(z); });
    }
    public static void markerDelete(int id) { send("MARKER_DELETE", out -> out.writeInt(id)); }
    public static void ticketCreate(String category, String message) {
        send("TICKET_CREATE", out -> { out.writeUTF(category); out.writeUTF(message); });
    }
    public static void ticketMessage(int id, String message) {
        send("TICKET_MESSAGE", out -> { out.writeInt(id); out.writeUTF(message); });
    }
    public static void ticketInternal(int id, String message) {
        send("TICKET_INTERNAL", out -> { out.writeInt(id); out.writeUTF(message); });
    }
    public static void ticketTake(int id) { send("TICKET_TAKE", out -> out.writeInt(id)); }
    public static void ticketClose(int id) { send("TICKET_CLOSE", out -> out.writeInt(id)); }
    public static void systemAnnouncement(String message) { send("SYSTEM_ANNOUNCE", out -> out.writeUTF(message)); }

    public static void adminProfileUpdate(ProfileData p) {
        send("ADMIN_PROFILE_UPDATE", out -> {
            out.writeUTF(p.uuid().toString());
            out.writeUTF(p.systemName());
            out.writeUTF(p.race());
            out.writeUTF(p.origin());
            out.writeUTF(p.status());
            out.writeUTF(p.faction());
            out.writeUTF(p.accessRole());
            writeList(out, p.abilities());
            writeList(out, p.traits());
            out.writeUTF(p.adminNote());
        });
    }

    public static DataInputStream input(byte[] bytes) {
        return new DataInputStream(new ByteArrayInputStream(bytes));
    }

    public static void writeList(DataOutputStream out, List<String> list) throws IOException {
        out.writeInt(list.size());
        for (String value : list) out.writeUTF(value);
    }

    public static List<String> readList(DataInputStream in) throws IOException {
        int count = Math.max(0, Math.min(in.readInt(), 256));
        java.util.ArrayList<String> result = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(in.readUTF());
        return List.copyOf(result);
    }

    public static UUID readUuid(DataInputStream in) throws IOException {
        return UUID.fromString(in.readUTF());
    }
}

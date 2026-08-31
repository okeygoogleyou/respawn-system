package ru.okeygoogle.respawnsystem.client;

import net.minecraft.client.Minecraft;
import ru.okeygoogle.respawnsystem.client.gui.WelcomeScreen;
import ru.okeygoogle.respawnsystem.client.model.*;
import ru.okeygoogle.respawnsystem.client.net.Wire;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.*;

public final class ClientSystemState {
    public static final ClientSystemState INSTANCE = new ClientSystemState();

    private boolean connected;
    private boolean registered;
    private Role role = Role.PLAYER;
    private ProfileData self;
    private List<ProfileData> profiles = List.of();
    private List<ChatEntry> chat = List.of();
    private List<ChatEntry> systemChat = List.of();
    private List<DirectMessage> directMessages = List.of();
    private List<MarkerData> markers = List.of();
    private List<TicketData> tickets = List.of();
    private String notice = "";
    private long noticeUntil;
    private long revision;

    private ClientSystemState() {}

    public synchronized void reset() {
        connected = false;
        registered = false;
        role = Role.PLAYER;
        self = null;
        profiles = List.of();
        chat = List.of();
        systemChat = List.of();
        directMessages = List.of();
        markers = List.of();
        tickets = List.of();
        notice = "";
        noticeUntil = 0L;
        revision++;
    }

    public void accept(byte[] bytes) {
        try (DataInputStream in = Wire.input(bytes)) {
            int protocol = in.readInt();
            if (protocol != Wire.PROTOCOL) {
                setNotice("Несовместимая версия протокола Системы: " + protocol);
                return;
            }
            String action = in.readUTF();
            if ("FULL_SYNC".equals(action)) readFull(in);
            else if ("NOTICE".equals(action)) setNotice(in.readUTF());
        } catch (Exception e) {
            setNotice("Ошибка синхронизации Системы: " + e.getMessage());
        }
    }

    private synchronized void readFull(DataInputStream in) throws IOException {
        connected = true;
        registered = in.readBoolean();
        role = Role.safe(in.readUTF());
        self = readProfile(in);

        int profileCount = bounded(in.readInt(), 256);
        List<ProfileData> profileList = new ArrayList<>(profileCount);
        for (int i = 0; i < profileCount; i++) profileList.add(readProfile(in));
        profiles = List.copyOf(profileList);

        chat = readChatList(in, 200);
        systemChat = readChatList(in, 200);

        int dmCount = bounded(in.readInt(), 500);
        List<DirectMessage> dmList = new ArrayList<>(dmCount);
        for (int i = 0; i < dmCount; i++) {
            dmList.add(new DirectMessage(
                    in.readLong(), Wire.readUuid(in), in.readUTF(), Wire.readUuid(in), in.readUTF(), in.readUTF()
            ));
        }
        directMessages = List.copyOf(dmList);

        int markerCount = bounded(in.readInt(), 500);
        List<MarkerData> markerList = new ArrayList<>(markerCount);
        for (int i = 0; i < markerCount; i++) {
            markerList.add(new MarkerData(in.readInt(), Wire.readUuid(in), in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF(), in.readInt(), in.readInt(), in.readInt()));
        }
        markers = List.copyOf(markerList);

        int ticketCount = bounded(in.readInt(), 300);
        List<TicketData> ticketList = new ArrayList<>(ticketCount);
        for (int i = 0; i < ticketCount; i++) ticketList.add(readTicket(in));
        tickets = List.copyOf(ticketList);

        String syncNotice = in.readUTF();
        if (!syncNotice.isBlank()) setNoticeInternal(syncNotice);
        revision++;

        Minecraft client = Minecraft.getInstance();
        if (!registered && client.player != null && !(client.gui.screen() instanceof WelcomeScreen)) {
            client.execute(() -> client.gui.setScreen(new WelcomeScreen()));
        } else if (registered && client.gui.screen() instanceof WelcomeScreen) {
            client.execute(() -> client.gui.setScreen(new ru.okeygoogle.respawnsystem.client.gui.SystemScreen()));
        }
    }

    private static List<ChatEntry> readChatList(DataInputStream in, int max) throws IOException {
        int count = bounded(in.readInt(), max);
        List<ChatEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new ChatEntry(in.readLong(), in.readUTF(), in.readUTF(), in.readBoolean()));
        }
        return List.copyOf(list);
    }

    private static int bounded(int value, int max) throws IOException {
        if (value < 0 || value > max) throw new IOException("Некорректный размер массива: " + value);
        return value;
    }

    private static ProfileData readProfile(DataInputStream in) throws IOException {
        UUID uuid = Wire.readUuid(in);
        return new ProfileData(
                uuid, in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF(),
                Wire.readList(in), Wire.readList(in), in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF()
        );
    }

    private static TicketData readTicket(DataInputStream in) throws IOException {
        int id = in.readInt();
        UUID owner = Wire.readUuid(in);
        String ownerName = in.readUTF();
        String category = in.readUTF();
        String status = in.readUTF();
        String assigned = in.readUTF();
        int count = bounded(in.readInt(), 300);
        List<TicketMessage> messages = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            messages.add(new TicketMessage(in.readLong(), in.readUTF(), in.readUTF(), in.readUTF(), in.readBoolean()));
        }
        return new TicketData(id, owner, ownerName, category, status, assigned, List.copyOf(messages));
    }

    private void setNoticeInternal(String message) {
        notice = message == null ? "" : message;
        noticeUntil = System.currentTimeMillis() + 2000L;
    }

    public synchronized void setNotice(String message) {
        setNoticeInternal(message);
        revision++;
    }

    public synchronized boolean connected() { return connected; }
    public synchronized boolean registered() { return registered; }
    public synchronized Role role() { return role; }
    public synchronized ProfileData self() { return self; }
    public synchronized List<ProfileData> profiles() { return profiles; }
    public synchronized List<ChatEntry> chat() { return chat; }
    public synchronized List<ChatEntry> systemChat() { return systemChat; }
    public synchronized List<DirectMessage> directMessages() { return directMessages; }
    public synchronized List<MarkerData> markers() { return markers; }
    public synchronized List<TicketData> tickets() { return tickets; }

    public synchronized String notice() {
        if (!notice.isBlank() && System.currentTimeMillis() > noticeUntil) {
            notice = "";
            noticeUntil = 0L;
        }
        return notice;
    }

    public synchronized void clearNotice() { notice = ""; noticeUntil = 0L; revision++; }
    public synchronized long revision() { return revision; }

    public synchronized Optional<ProfileData> profile(UUID id) {
        return profiles.stream().filter(p -> p.uuid().equals(id)).findFirst();
    }
}

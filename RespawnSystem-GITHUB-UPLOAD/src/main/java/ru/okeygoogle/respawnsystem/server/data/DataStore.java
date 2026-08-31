package ru.okeygoogle.respawnsystem.server.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import ru.okeygoogle.respawnsystem.server.model.*;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

public final class DataStore {
    public static final int MAX_CHAT = 120;
    public static final int MAX_MARKERS = 200;
    public static final int MAX_TICKETS = 300;
    public static final int MAX_PROFILES_IN_SYNC = 96;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path directory = FabricLoader.getInstance().getConfigDir().resolve("respawn-system");
    private final Path file = directory.resolve("data.json");
    private final Path backup = directory.resolve("data.backup.json");

    private final Map<UUID, ProfileData> profiles = new LinkedHashMap<>();
    private final List<ChatEntry> chat = new ArrayList<>();
    private final List<MarkerData> markers = new ArrayList<>();
    private final List<TicketData> tickets = new ArrayList<>();
    private int nextMarkerId = 1;
    private int nextTicketId = 1;

    public synchronized void load() {
        profiles.clear();
        chat.clear();
        markers.clear();
        tickets.clear();
        nextMarkerId = 1;
        nextTicketId = 1;

        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file)) {
            DiskData data = gson.fromJson(reader, DiskData.class);
            if (data == null) return;
            nextMarkerId = Math.max(1, data.nextMarkerId);
            nextTicketId = Math.max(1, data.nextTicketId);

            if (data.profiles != null) for (Map.Entry<String, DiskProfile> e : data.profiles.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(e.getKey());
                    DiskProfile d = e.getValue();
                    if (d == null) continue;
                    ProfileData p = new ProfileData(uuid, nz(d.minecraftName, "unknown"));
                    p.systemName = nz(d.systemName, p.minecraftName);
                    p.race = nz(d.race, "НЕ ОПРЕДЕЛЕНА");
                    p.origin = nz(d.origin, "—");
                    p.status = nz(d.status, "НЕОПОЗНАННЫЙ");
                    p.faction = nz(d.faction, "—");
                    p.role = Role.safe(d.role);
                    p.abilities = cleanList(d.abilities, 32, 80);
                    p.traits = cleanList(d.traits, 32, 80);
                    p.subtitle = nz(d.subtitle, "");
                    p.theme = nz(d.theme, "SYSTEM");
                    p.accent = nz(d.accent, "#57D7FF");
                    p.adminNote = nz(d.adminNote, "");
                    p.registered = d.registered;
                    profiles.put(uuid, p);
                } catch (Exception ignored) {}
            }

            if (data.chat != null) for (DiskChat d : data.chat) {
                if (d != null) chat.add(new ChatEntry(d.time, nz(d.sender, ""), nz(d.message, ""), d.system));
            }
            if (data.markers != null) for (DiskMarker d : data.markers) {
                try {
                    markers.add(new MarkerData(d.id, UUID.fromString(d.owner), nz(d.ownerName, ""), nz(d.title, "Метка"),
                            nz(d.type, "ОБЩАЯ"), nz(d.dimension, "minecraft:overworld"), d.x, d.y, d.z));
                } catch (Exception ignored) {}
            }
            if (data.tickets != null) for (DiskTicket d : data.tickets) {
                try {
                    TicketData t = new TicketData(d.id, UUID.fromString(d.owner), nz(d.ownerName, ""), nz(d.category, "Помощь"));
                    t.status = nz(d.status, "ОТКРЫТО");
                    t.assignedTo = nz(d.assignedTo, "");
                    if (d.messages != null) for (DiskMessage m : d.messages) if (m != null) {
                        t.messages.add(new TicketMessage(m.time, nz(m.sender, ""), nz(m.role, "PLAYER"), nz(m.text, ""), m.internal));
                    }
                    tickets.add(t);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            System.err.println("[Respawn System] Не удалось загрузить data.json: " + e.getMessage());
        }
    }

    public synchronized void save() {
        DiskData data = new DiskData();
        data.nextMarkerId = nextMarkerId;
        data.nextTicketId = nextTicketId;
        data.profiles = new LinkedHashMap<>();
        for (ProfileData p : profiles.values()) {
            DiskProfile d = new DiskProfile();
            d.minecraftName = p.minecraftName;
            d.systemName = p.systemName;
            d.race = p.race;
            d.origin = p.origin;
            d.status = p.status;
            d.faction = p.faction;
            d.role = p.role.name();
            d.abilities = new ArrayList<>(p.abilities);
            d.traits = new ArrayList<>(p.traits);
            d.subtitle = p.subtitle;
            d.theme = p.theme;
            d.accent = p.accent;
            d.adminNote = p.adminNote;
            d.registered = p.registered;
            data.profiles.put(p.uuid.toString(), d);
        }
        data.chat = chat.stream().map(e -> new DiskChat(e.time(), e.sender(), e.message(), e.system())).toList();
        data.markers = markers.stream().map(m -> new DiskMarker(m.id(), m.owner().toString(), m.ownerName(), m.title(), m.type(), m.dimension(), m.x(), m.y(), m.z())).toList();
        data.tickets = tickets.stream().map(t -> {
            DiskTicket d = new DiskTicket();
            d.id = t.id;
            d.owner = t.owner.toString();
            d.ownerName = t.ownerName;
            d.category = t.category;
            d.status = t.status;
            d.assignedTo = t.assignedTo;
            d.messages = t.messages.stream().map(m -> new DiskMessage(m.time(), m.sender(), m.role(), m.text(), m.internal())).toList();
            return d;
        }).toList();

        try {
            Files.createDirectories(directory);
            if (Files.exists(file)) Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            Path temp = directory.resolve("data.tmp.json");
            try (Writer writer = Files.newBufferedWriter(temp)) { gson.toJson(data, writer); }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            try {
                Files.createDirectories(directory);
                try (Writer writer = Files.newBufferedWriter(file)) { gson.toJson(data, writer); }
            } catch (IOException e) {
                System.err.println("[Respawn System] Не удалось сохранить data.json: " + e.getMessage());
            }
        }
    }

    public synchronized ProfileData profile(UUID uuid) { return profiles.get(uuid); }

    public synchronized ProfileData getOrCreate(UUID uuid, String minecraftName) {
        ProfileData p = profiles.computeIfAbsent(uuid, id -> new ProfileData(id, minecraftName));
        p.minecraftName = minecraftName;
        return p;
    }

    public synchronized boolean systemNameTaken(String name, UUID except) {
        if (name == null || name.isBlank()) return false;
        String normalized = name.trim();
        return profiles.values().stream()
                .filter(p -> p.registered && (except == null || !p.uuid.equals(except)))
                .anyMatch(p -> normalized.equalsIgnoreCase(p.systemName));
    }

    public synchronized Collection<ProfileData> profiles() { return new ArrayList<>(profiles.values()); }
    public synchronized List<ChatEntry> chat() { return new ArrayList<>(chat); }
    public synchronized List<MarkerData> markers() { return new ArrayList<>(markers); }
    public synchronized List<TicketData> tickets() { return new ArrayList<>(tickets); }

    public synchronized void addChat(ChatEntry entry) {
        chat.add(entry);
        while (chat.size() > MAX_CHAT) chat.removeFirst();
    }

    public synchronized MarkerData addMarker(UUID owner, String ownerName, String title, String type, String dimension, int x, int y, int z) {
        MarkerData marker = new MarkerData(nextMarkerId++, owner, ownerName, title, type, dimension, x, y, z);
        markers.add(marker);
        while (markers.size() > MAX_MARKERS) markers.removeFirst();
        return marker;
    }

    public synchronized boolean removeMarker(int id, UUID requester, boolean admin) {
        return markers.removeIf(m -> m.id() == id && (admin || m.owner().equals(requester)));
    }

    public synchronized TicketData createTicket(UUID owner, String ownerName, String category, String text) {
        TicketData ticket = new TicketData(nextTicketId++, owner, ownerName, category);
        ticket.messages.add(new TicketMessage(System.currentTimeMillis(), ownerName, "PLAYER", text, false));
        tickets.add(ticket);
        while (tickets.size() > MAX_TICKETS) tickets.removeFirst();
        return ticket;
    }

    public synchronized TicketData ticket(int id) {
        return tickets.stream().filter(t -> t.id == id).findFirst().orElse(null);
    }

    private static String nz(String value, String fallback) { return value == null ? fallback : value; }

    private static ArrayList<String> cleanList(List<String> input, int maxItems, int maxLength) {
        ArrayList<String> out = new ArrayList<>();
        if (input == null) return out;
        for (String value : input) {
            if (out.size() >= maxItems) break;
            if (value == null) continue;
            String clean = value.trim();
            if (clean.length() > maxLength) clean = clean.substring(0, maxLength);
            if (!clean.isBlank()) out.add(clean);
        }
        return out;
    }

    private static final class DiskData {
        int nextMarkerId = 1;
        int nextTicketId = 1;
        Map<String, DiskProfile> profiles = new LinkedHashMap<>();
        List<DiskChat> chat = new ArrayList<>();
        List<DiskMarker> markers = new ArrayList<>();
        List<DiskTicket> tickets = new ArrayList<>();
    }

    private static final class DiskProfile {
        String minecraftName;
        String systemName;
        String race;
        String origin;
        String status;
        String faction;
        String role = "PLAYER";
        List<String> abilities = new ArrayList<>();
        List<String> traits = new ArrayList<>();
        String subtitle;
        String theme;
        String accent;
        String adminNote;
        boolean registered;
    }

    private record DiskChat(long time, String sender, String message, boolean system) {}
    private record DiskMarker(int id, String owner, String ownerName, String title, String type, String dimension, int x, int y, int z) {}

    private static final class DiskTicket {
        int id;
        String owner;
        String ownerName;
        String category;
        String status;
        String assignedTo;
        List<DiskMessage> messages = new ArrayList<>();
    }

    private record DiskMessage(long time, String sender, String role, String text, boolean internal) {}
}

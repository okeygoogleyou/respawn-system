package ru.okeygoogle.respawnsystem.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.component.ResolvableProfile;
import ru.okeygoogle.respawnsystem.client.ClientSystemState;
import ru.okeygoogle.respawnsystem.client.model.*;
import ru.okeygoogle.respawnsystem.client.net.Wire;
import ru.okeygoogle.respawnsystem.client.ui.*;

import java.util.*;
import java.util.stream.Collectors;

public final class SystemScreen extends Screen {
    private enum Tab { PROFILE, PLAYERS, CHAT, MARKERS, SUPPORT, COSMETICS, ADMIN, SUPPORT_CENTER }

    private final ClientSystemState state = ClientSystemState.INSTANCE;
    private Tab tab = Tab.PROFILE;
    private UUID selectedProfile;
    private Integer selectedTicket;
    private long knownRevision = -1;

    private int left, top, panelW, panelH, sideW, contentX, contentW;
    private EditBox inputA, inputB, inputC, inputD, inputE, inputF;

    public SystemScreen() {
        super(Component.literal("Система"));
        ProfileData self = state.self();
        selectedProfile = self == null ? null : self.uuid();
    }

    @Override
    protected void init() {
        super.init();
        knownRevision = state.revision();
        panelW = Math.min(760, width - 28);
        panelH = Math.min(430, height - 28);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        sideW = 142;
        contentX = left + sideW + 18;
        contentW = panelW - sideW - 30;

        addTabs();
        switch (tab) {
            case PROFILE -> initProfile();
            case PLAYERS -> initPlayers();
            case CHAT -> initChat();
            case MARKERS -> initMarkers();
            case SUPPORT -> initSupport(false);
            case COSMETICS -> initCosmetics();
            case ADMIN -> initAdmin();
            case SUPPORT_CENTER -> initSupport(true);
        }
    }

    private void addTabs() {
        int y = top + 58;
        addTab("ПРОФИЛЬ", Tab.PROFILE, y); y += 25;
        addTab("ИГРОКИ", Tab.PLAYERS, y); y += 25;
        addTab("СВЯЗЬ", Tab.CHAT, y); y += 25;
        addTab("МЕТКИ", Tab.MARKERS, y); y += 25;
        addTab("ПОДДЕРЖКА", Tab.SUPPORT, y); y += 25;
        addTab("ОФОРМЛЕНИЕ", Tab.COSMETICS, y); y += 31;
        if (state.role().canAdmin()) { addTab("УПРАВЛЕНИЕ", Tab.ADMIN, y); y += 25; }
        if (state.role().canSupport()) addTab("ЦЕНТР ПОДДЕРЖКИ", Tab.SUPPORT_CENTER, y);
    }

    private void addTab(String name, Tab target, int y) {
        Button button = Button.builder(Component.literal((tab == target ? "◆ " : "  ") + name), b -> {
                    tab = target;
                    SoundFx.click();
                    rebuildWidgets();
                })
                .bounds(left + 13, y, sideW - 26, 20).build();
        this.addRenderableWidget(button);
    }

    private ProfileData selectedProfile() {
        ProfileData self = state.self();
        if (selectedProfile != null) {
            if (self != null && self.uuid().equals(selectedProfile)) return self;
            Optional<ProfileData> found = state.profile(selectedProfile);
            if (found.isPresent()) return found.get();
        }
        return self;
    }

    private void initProfile() {
        ProfileData p = selectedProfile();
        if (p == null) return;
        PlayerFaceWidget face = new PlayerFaceWidget(62, ResolvableProfile.createUnresolved(p.minecraftName()));
        face.setPosition(contentX + 14, top + 74);
        this.addRenderableWidget(face);
    }

    private void initPlayers() {
        int y = top + 72;
        List<ProfileData> list = state.profiles();
        for (int i = 0; i < Math.min(list.size(), 11); i++) {
            ProfileData p = list.get(i);
            Button b = Button.builder(Component.literal(p.systemName() + "  //  " + p.race()), btn -> {
                selectedProfile = p.uuid();
                tab = Tab.PROFILE;
                SoundFx.click();
                rebuildWidgets();
            }).bounds(contentX, y, Math.min(360, contentW - 8), 20).build();
            addRenderableWidget(b);
            y += 25;
        }
    }

    private void initChat() {
        inputA = field(contentX, top + panelH - 47, contentW - 76, 20, 280, "Сообщение");
        addRenderableWidget(Button.builder(Component.literal("ОТПРАВИТЬ"), b -> {
            String text = inputA.getValue().trim();
            if (!text.isBlank()) { Wire.chat(text); inputA.setValue(""); SoundFx.confirm(); }
        }).bounds(contentX + contentW - 70, top + panelH - 47, 70, 20).build());

        if (state.role().canAdmin()) {
            addRenderableWidget(Button.builder(Component.literal("СИСТЕМНОЕ ОБЪЯВЛЕНИЕ"), b -> {
                String text = inputA.getValue().trim();
                if (!text.isBlank()) { Wire.systemAnnouncement(text); inputA.setValue(""); SoundFx.notification(); }
            }).bounds(contentX, top + panelH - 72, 170, 20).build());
        }
    }

    private void initMarkers() {
        inputA = field(contentX, top + panelH - 47, Math.min(260, contentW - 160), 20, 48, "Название метки");
        addRenderableWidget(Button.builder(Component.literal("ПОСТАВИТЬ ЗДЕСЬ"), b -> createMarker())
                .bounds(contentX + Math.min(268, contentW - 152), top + panelH - 47, 145, 20).build());

        List<MarkerData> markers = state.markers();
        int y = top + 72;
        for (int i = Math.max(0, markers.size() - 9); i < markers.size(); i++) {
            MarkerData m = markers.get(i);
            if (canDeleteMarker(m)) {
                addRenderableWidget(Button.builder(Component.literal("×"), b -> { Wire.markerDelete(m.id()); SoundFx.click(); })
                        .bounds(contentX + contentW - 24, y - 3, 20, 18).build());
            }
            y += 29;
        }
    }

    private boolean canDeleteMarker(MarkerData m) {
        ProfileData self = state.self();
        return state.role().canAdmin() || (self != null && self.uuid().equals(m.owner()));
    }

    private void createMarker() {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) return;
        String title = inputA.getValue().trim();
        if (title.isBlank()) title = "Метка";
        var pos = minecraft.player.blockPosition();
        Wire.markerCreate(title, "ОБЩАЯ", "", pos.getX(), pos.getY(), pos.getZ());
        inputA.setValue("");
        SoundFx.confirm();
    }

    private void initCosmetics() {
        ProfileData self = state.self();
        if (self == null) return;
        inputA = field(contentX, top + 127, Math.min(300, contentW), 20, 60, "Подпись");
        inputA.setValue(self.subtitle());
        inputB = field(contentX, top + 177, 130, 20, 9, "#57D7FF");
        inputB.setValue(self.accent());

        addRenderableWidget(Button.builder(Component.literal("ТЕМА: " + ThemeCatalog.byId(self.theme()).title()), b -> {
            String next = ThemeCatalog.next(state.self().theme());
            Wire.cosmetics(next, safeAccent(inputB.getValue()), inputA.getValue().trim());
            SoundFx.click();
        }).bounds(contentX, top + 76, Math.min(280, contentW), 20).build());

        addRenderableWidget(Button.builder(Component.literal("СОХРАНИТЬ ОФОРМЛЕНИЕ"), b -> {
            Wire.cosmetics(state.self().theme(), safeAccent(inputB.getValue()), inputA.getValue().trim());
            SoundFx.confirm();
        }).bounds(contentX, top + 222, 190, 20).build());
    }

    private static String safeAccent(String value) {
        String v = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return v.matches("#[0-9A-F]{6}") ? v : "#57D7FF";
    }

    private void initSupport(boolean staff) {
        List<TicketData> tickets = ticketsFor(staff);
        if (selectedTicket == null) {
            selectedTicket = tickets.isEmpty() ? null : tickets.getLast().id();
        } else if (selectedTicket != -1 && tickets.stream().noneMatch(t -> t.id() == selectedTicket)) {
            selectedTicket = tickets.isEmpty() ? null : tickets.getLast().id();
        }

        int listW = Math.min(190, contentW / 3);
        int y = top + 72;
        int shown = 0;
        for (int i = tickets.size() - 1; i >= 0 && shown < 9; i--, shown++) {
            TicketData t = tickets.get(i);
            String prefix = Objects.equals(selectedTicket, t.id()) ? "◆ " : "";
            addRenderableWidget(Button.builder(Component.literal(prefix + "#" + t.id() + " " + t.ownerName()), b -> {
                selectedTicket = t.id();
                SoundFx.click();
                rebuildWidgets();
            }).bounds(contentX, y, listW, 20).build());
            y += 24;
        }

        if (!staff) {
            addRenderableWidget(Button.builder(Component.literal("+ НОВОЕ ОБРАЩЕНИЕ"), b -> {
                selectedTicket = -1;
                SoundFx.click();
                rebuildWidgets();
            }).bounds(contentX, top + panelH - 47, listW, 20).build());
        }

        TicketData selected = findTicket(selectedTicket, staff);
        if (selected != null) {
            int chatX = contentX + listW + 12;
            int chatW = contentW - listW - 12;
            inputA = field(chatX, top + panelH - 47, Math.max(80, chatW - 72), 20, 500, "Ответ");
            addRenderableWidget(Button.builder(Component.literal("➤"), b -> {
                String text = inputA.getValue().trim();
                if (!text.isBlank()) { Wire.ticketMessage(selected.id(), text); inputA.setValue(""); SoundFx.confirm(); }
            }).bounds(chatX + chatW - 66, top + panelH - 47, 62, 20).build());

            if (staff) {
                addRenderableWidget(Button.builder(Component.literal("ВЗЯТЬ"), b -> Wire.ticketTake(selected.id()))
                        .bounds(chatX, top + panelH - 72, 65, 20).build());
                addRenderableWidget(Button.builder(Component.literal("ЗАКРЫТЬ"), b -> Wire.ticketClose(selected.id()))
                        .bounds(chatX + 70, top + panelH - 72, 75, 20).build());
                addRenderableWidget(Button.builder(Component.literal("ВНУТР. ЗАМЕТКА"), b -> {
                    String text = inputA.getValue().trim();
                    if (!text.isBlank()) { Wire.ticketInternal(selected.id(), text); inputA.setValue(""); SoundFx.click(); }
                }).bounds(chatX + 150, top + panelH - 72, 130, 20).build());
            }
        } else if (!staff) {
            int formX = contentX + listW + 12;
            int formW = contentW - listW - 12;
            inputA = field(formX, top + 118, Math.min(320, formW), 20, 40, "Категория");
            inputA.setValue("Помощь");
            inputB = field(formX, top + 166, Math.max(120, Math.min(440, formW)), 20, 500, "Опишите проблему");
            addRenderableWidget(Button.builder(Component.literal("СОЗДАТЬ ОБРАЩЕНИЕ"), b -> {
                String message = inputB.getValue().trim();
                if (!message.isBlank()) { Wire.ticketCreate(inputA.getValue().trim(), message); SoundFx.confirm(); }
            }).bounds(formX, top + 211, Math.min(180, formW), 20).build());
        }
    }

    private List<TicketData> ticketsFor(boolean staff) {
        if (staff) return state.tickets();
        ProfileData self = state.self();
        if (self == null) return List.of();
        return state.tickets().stream().filter(t -> t.owner().equals(self.uuid())).toList();
    }

    private TicketData findTicket(Integer id, boolean staff) {
        if (id == null) return null;
        return ticketsFor(staff).stream().filter(t -> t.id() == id).findFirst().orElse(null);
    }

    private void initAdmin() {
        if (!state.role().canAdmin()) { tab = Tab.PROFILE; rebuildWidgets(); return; }
        ProfileData p = selectedProfile();
        if (p == null && !state.profiles().isEmpty()) { selectedProfile = state.profiles().getFirst().uuid(); p = state.profiles().getFirst(); }
        if (p == null) return;

        int labelW = 92;
        int x = contentX + labelW;
        int w = Math.min(330, contentW - labelW);
        int y = top + 76;
        inputA = field(x, y, w, 20, 32, "Системное имя"); inputA.setValue(p.systemName()); y += 31;
        inputB = field(x, y, w, 20, 48, "Раса"); inputB.setValue(p.race()); y += 31;
        inputC = field(x, y, w, 20, 64, "Происхождение"); inputC.setValue(p.origin()); y += 31;
        inputD = field(x, y, w, 20, 48, "Статус"); inputD.setValue(p.status()); y += 31;
        inputE = field(x, y, w, 20, 64, "Фракция"); inputE.setValue(p.faction()); y += 31;
        inputF = field(x, y, w, 20, 500, "Способности через ;"); inputF.setValue(String.join("; ", p.abilities())); y += 31;

        EditBox traits = field(x, y, w, 20, 500, "Особенности через ;"); traits.setValue(String.join("; ", p.traits())); y += 31;
        EditBox note = field(x, y, w, 20, 500, "Скрытая заметка"); note.setValue(p.adminNote()); y += 31;
        EditBox roleBox = field(x, y, w, 20, 16, "Роль доступа"); roleBox.setValue(p.accessRole()); y += 34;

        ProfileData base = p;
        addRenderableWidget(Button.builder(Component.literal("СОХРАНИТЬ СИСТЕМНЫЕ ДАННЫЕ"), b -> {
            ProfileData updated = new ProfileData(base.uuid(), base.minecraftName(), inputA.getValue().trim(), inputB.getValue().trim(),
                    inputC.getValue().trim(), inputD.getValue().trim(), inputE.getValue().trim(), roleBox.getValue().trim().toUpperCase(Locale.ROOT), splitSemi(inputF.getValue()),
                    splitSemi(traits.getValue()), base.subtitle(), base.theme(), base.accent(), note.getValue().trim());
            Wire.adminProfileUpdate(updated);
            SoundFx.confirm();
        }).bounds(x, y, Math.min(230, w), 20).build());

        int playerY = top + 76;
        int playerX = contentX + Math.min(445, contentW - 135);
        for (ProfileData player : state.profiles().stream().limit(10).toList()) {
            addRenderableWidget(Button.builder(Component.literal(player.systemName()), b -> {
                selectedProfile = player.uuid();
                rebuildWidgets();
            }).bounds(playerX, playerY, Math.max(110, contentX + contentW - playerX), 18).build());
            playerY += 21;
        }
    }

    private static List<String> splitSemi(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.split(";")).map(String::trim).filter(s -> !s.isBlank()).limit(32).toList();
    }

    private EditBox field(int x, int y, int w, int h, int max, String label) {
        EditBox box = new EditBox(font, x, y, Math.max(40, w), h, Component.literal(label));
        box.setMaxLength(max);
        addRenderableWidget(box);
        return box;
    }

    @Override
    public void tick() {
        super.tick();
        if (minecraft != null && minecraft.gui.screen() == this && knownRevision != state.revision()) {
            knownRevision = state.revision();
            rebuildWidgets();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Theme theme = theme();
        g.fill(0, 0, width, height, 0xA8000000);
        g.fill(left, top, left + panelW, top + panelH, theme.background());
        g.outline(left, top, panelW, panelH, withAccent(theme, 0xFF));
        g.fill(left, top, left + 4, top + panelH, withAccent(theme, 0xFF));
        g.fill(left + sideW, top + 44, left + sideW + 1, top + panelH - 14, 0x664C6470);
        g.fill(left + 4, top + 43, left + panelW, top + 44, 0x554C6470);
        g.fill(contentX - 8, top + 58, left + panelW - 12, top + panelH - 14, theme.panel());
        g.outline(contentX - 8, top + 58, contentW + 8, panelH - 72, 0x553C6574);

        if (UiConfig.INSTANCE.showGrid) {
            int spacing = UiConfig.INSTANCE.gridSpacing;
            for (int x = left + sideW; x < left + panelW; x += spacing) g.fill(x, top + 44, x + 1, top + panelH, 0x0DFFFFFF);
            for (int y = top + 44; y < top + panelH; y += spacing) g.fill(left + sideW, y, left + panelW, y + 1, 0x0DFFFFFF);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        Theme theme = theme();
        g.text(font, Component.literal(UiConfig.INSTANCE.brandText), left + 14, top + 15, theme.text(), false);
        g.text(font, Component.literal("СОСТОЯНИЕ: " + (state.connected() ? "ONLINE" : "OFFLINE")), left + panelW - 128, top + 15,
                state.connected() ? 0xFF67E69B : theme.danger(), false);
        g.text(font, Component.literal("ДОСТУП: " + roleName(state.role())), left + 14, top + panelH - 24, theme.muted(), false);

        switch (tab) {
            case PROFILE -> renderProfile(g, theme);
            case PLAYERS -> renderPlayers(g, theme);
            case CHAT -> renderChat(g, theme);
            case MARKERS -> renderMarkers(g, theme);
            case SUPPORT -> renderSupport(g, theme, false);
            case COSMETICS -> renderCosmetics(g, theme);
            case ADMIN -> renderAdmin(g, theme);
            case SUPPORT_CENTER -> renderSupport(g, theme, true);
        }

        String notice = state.notice();
        if (!notice.isBlank()) {
            g.fill(contentX, top + panelH - 26, contentX + contentW, top + panelH - 13, 0xCC24151A);
            g.text(font, Component.literal(notice), contentX + 5, top + panelH - 24, 0xFFFFA6AE, false);
        }
    }

    private void renderProfile(GuiGraphicsExtractor g, Theme theme) {
        ProfileData p = selectedProfile();
        if (p == null) { title(g, "ПРОФИЛЬ НЕДОСТУПЕН", theme); return; }
        StatusStyle status = StatusStyle.of(p.status(), withAccent(theme, 0xFF));
        title(g, "ПРОФИЛЬ // " + p.systemName(), theme);
        String statusKey = p.status() == null ? "" : p.status().toUpperCase(Locale.ROOT);
        boolean unidentified = statusKey.contains("НЕОПОЗН");
        boolean lost = statusKey.contains("ПОТЕР");
        boolean archived = statusKey.contains("МЕРТВ") || statusKey.contains("НЕАКТИВ");
        String unavailable = "[ НЕТ ДАННЫХ ]";

        int x = contentX + 92;
        int y = top + 79;
        labelValue(g, x, y, "НИКНЕЙМ", p.systemName(), theme); y += 22;
        labelValue(g, x, y, "РАСА", unidentified ? unavailable : p.race(), theme); y += 22;
        labelValue(g, x, y, "ПРОИСХОЖДЕНИЕ", unidentified ? unavailable : p.origin(), theme); y += 22;
        labelValue(g, x, y, "ФРАКЦИЯ", unidentified ? unavailable : p.faction(), theme); y += 22;
        labelValue(g, x, y, "ДОСТУП", roleName(Role.safe(p.accessRole())), theme); y += 22;
        g.text(font, Component.literal("СТАТУС"), x, y, theme.muted(), false);
        g.text(font, Component.literal(p.status()), x + 88, y, status.color(), false);

        if (!status.warning().isBlank()) {
            g.fill(contentX + 12, top + 181, contentX + contentW - 12, top + 202, 0x991C1514);
            g.text(font, Component.literal(status.warning()), contentX + 19, top + 188, status.color(), false);
        }

        int columnY = top + 225;
        g.text(font, Component.literal(archived ? "АРХИВ СПОСОБНОСТЕЙ" : "СПОСОБНОСТИ"), contentX + 14, columnY, archived ? status.color() : withAccent(theme, 0xFF), false);
        int yy = columnY + 17;
        if (unidentified) {
            g.text(font, Component.literal("[ ДОСТУП К ДАННЫМ ОГРАНИЧЕН ]"), contentX + 20, yy, status.color(), false);
        } else if (lost) {
            g.text(font, Component.literal("[ СИГНАЛ ПОТЕРЯН ]"), contentX + 20, yy, status.color(), false);
        } else {
            for (String ability : p.abilities().stream().limit(7).toList()) {
                g.text(font, Component.literal("◆ " + ability), contentX + 20, yy, archived ? theme.muted() : theme.text(), false); yy += 14;
            }
            if (p.abilities().isEmpty()) g.text(font, Component.literal("[ ДАННЫЕ НЕ ОБНАРУЖЕНЫ ]"), contentX + 20, yy, theme.muted(), false);
        }

        int tx = contentX + Math.max(260, contentW / 2);
        g.text(font, Component.literal("ОСОБЕННОСТИ"), tx, columnY, withAccent(theme, 0xFF), false);
        yy = columnY + 17;
        for (String trait : p.traits().stream().limit(7).toList()) {
            g.text(font, Component.literal("[ " + trait + " ]"), tx, yy, theme.text(), false); yy += 14;
        }
        if (p.traits().isEmpty()) g.text(font, Component.literal("—"), tx, yy, theme.muted(), false);

        if (!p.subtitle().isBlank()) g.text(font, Component.literal("“" + p.subtitle() + "”"), contentX + 14, top + panelH - 52, theme.muted(), false);
        if (state.role().canAdmin() && !p.adminNote().isBlank()) {
            g.text(font, Component.literal("СКРЫТАЯ ЗАМЕТКА: " + p.adminNote()), contentX + 14, top + panelH - 66, 0xFFFFA967, false);
        }
    }

    private void renderPlayers(GuiGraphicsExtractor g, Theme theme) {
        title(g, "ПОДКЛЮЧЕННЫЕ / ЗАРЕГИСТРИРОВАННЫЕ ПРОФИЛИ", theme);
        if (state.profiles().isEmpty()) g.text(font, Component.literal("Профили пока не синхронизированы."), contentX, top + 78, theme.muted(), false);
    }

    private void renderChat(GuiGraphicsExtractor g, Theme theme) {
        title(g, "СВЯЗЬ // ОБЩИЙ КАНАЛ", theme);
        List<ChatEntry> list = state.chat();
        int y = top + 83;
        int start = Math.max(0, list.size() - 15);
        for (int i = start; i < list.size(); i++) {
            ChatEntry e = list.get(i);
            int color = e.system() ? withAccent(theme, 0xFF) : theme.text();
            String prefix = e.system() ? "[СИСТЕМА] " : e.sender() + ": ";
            drawWrapped(g, prefix + e.message(), contentX + 8, y, contentW - 20, color, 2);
            y += 18;
        }
    }

    private void renderMarkers(GuiGraphicsExtractor g, Theme theme) {
        title(g, "ОБЩИЕ МЕТКИ", theme);
        List<MarkerData> list = state.markers();
        int y = top + 75;
        int start = Math.max(0, list.size() - 9);
        for (int i = start; i < list.size(); i++) {
            MarkerData m = list.get(i);
            g.text(font, Component.literal("◆ " + m.title()), contentX + 8, y, withAccent(theme, 0xFF), false);
            g.text(font, Component.literal(m.dimension() + "  " + m.x() + " / " + m.y() + " / " + m.z() + "  • " + m.ownerName()),
                    contentX + 20, y + 12, theme.muted(), false);
            y += 29;
        }
        if (list.isEmpty()) g.text(font, Component.literal("Пока никто не оставил меток."), contentX + 8, y, theme.muted(), false);
    }

    private void renderCosmetics(GuiGraphicsExtractor g, Theme theme) {
        title(g, "ОФОРМЛЕНИЕ ПРОФИЛЯ", theme);
        g.text(font, Component.literal("Тема интерфейса / карточки"), contentX, top + 64, theme.muted(), false);
        g.text(font, Component.literal("Декоративная подпись"), contentX, top + 113, theme.muted(), false);
        g.text(font, Component.literal("Акцент HEX"), contentX, top + 163, theme.muted(), false);
        g.text(font, Component.literal("Игрок может менять только эти косметические параметры."), contentX, top + 260, theme.muted(), false);
    }

    private void renderSupport(GuiGraphicsExtractor g, Theme theme, boolean staff) {
        title(g, staff ? "ЦЕНТР ПОДДЕРЖКИ" : "ПОДДЕРЖКА", theme);
        TicketData t = findTicket(selectedTicket, staff);
        if (t == null) {
            if (staff) g.text(font, Component.literal("Нет доступных обращений."), contentX, top + 82, theme.muted(), false);
            else {
                int listW = Math.min(190, contentW / 3);
                int formX = contentX + listW + 12;
                g.text(font, Component.literal("Создайте обращение — его увидят только модераторы и OP."), formX, top + 84, theme.muted(), false);
                g.text(font, Component.literal("КАТЕГОРИЯ"), formX, top + 105, withAccent(theme, 0xFF), false);
                g.text(font, Component.literal("СООБЩЕНИЕ"), formX, top + 153, withAccent(theme, 0xFF), false);
            }
            return;
        }

        int listW = Math.min(190, contentW / 3);
        int chatX = contentX + listW + 12;
        int chatW = contentW - listW - 12;
        g.text(font, Component.literal("#" + t.id() + " // " + t.category()), chatX, top + 70, withAccent(theme, 0xFF), false);
        g.text(font, Component.literal("Статус: " + t.status() + "   Ответственный: " + (t.assignedTo().isBlank() ? "—" : t.assignedTo())), chatX, top + 84, theme.muted(), false);

        int y = top + 105;
        List<TicketMessage> messages = t.messages();
        int start = Math.max(0, messages.size() - 12);
        for (int i = start; i < messages.size(); i++) {
            TicketMessage m = messages.get(i);
            int color = m.internal() ? 0xFFFFB565 : ("PLAYER".equals(m.role()) ? theme.text() : withAccent(theme, 0xFF));
            String prefix = m.internal() ? "[ВНУТРЕННЕ] " : m.sender() + " • ";
            drawWrapped(g, prefix + m.text(), chatX, y, chatW - 6, color, 2);
            y += 20;
        }
    }

    private void renderAdmin(GuiGraphicsExtractor g, Theme theme) {
        title(g, "УПРАВЛЕНИЕ СИСТЕМНЫМИ ПРОФИЛЯМИ", theme);
        ProfileData p = selectedProfile();
        if (p == null) return;
        String[] labels = {"НИКНЕЙМ", "РАСА", "ПРОИСХОЖД.", "СТАТУС", "ФРАКЦИЯ", "СПОСОБНОСТИ", "ОСОБЕННОСТИ", "СКРЫТАЯ ЗАМЕТКА", "РОЛЬ ДОСТУПА"};
        int y = top + 82;
        for (String label : labels) { g.text(font, Component.literal(label), contentX, y, theme.muted(), false); y += 31; }
        g.text(font, Component.literal("Игрок: " + p.minecraftName() + " // UUID " + p.uuid().toString().substring(0, 8)), contentX, top + 62, withAccent(theme, 0xFF), false);
    }

    private void title(GuiGraphicsExtractor g, String value, Theme theme) {
        g.text(font, Component.literal(value), contentX, top + 47, withAccent(theme, 0xFF), false);
    }

    private void labelValue(GuiGraphicsExtractor g, int x, int y, String label, String value, Theme theme) {
        g.text(font, Component.literal(label), x, y, theme.muted(), false);
        g.text(font, Component.literal(value == null || value.isBlank() ? "—" : value), x + 88, y, theme.text(), false);
    }

    private void drawWrapped(GuiGraphicsExtractor g, String text, int x, int y, int width, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), Math.max(40, width));
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) g.text(font, lines.get(i), x, y + i * 10, color, false);
    }

    private Theme theme() {
        ProfileData self = state.self();
        Theme base = ThemeCatalog.byId(self == null ? "SYSTEM" : self.theme());
        if (self == null) return base;
        try {
            int rgb = Integer.parseInt(safeAccent(self.accent()).substring(1), 16);
            return new Theme(base.id(), base.title(), 0xFF000000 | rgb, base.background(), base.panel(), base.panelAlt(), base.text(), base.muted(), base.danger());
        } catch (Exception ignored) { return base; }
    }

    private static int withAccent(Theme theme, int alpha) {
        return (alpha << 24) | (theme.accent() & 0x00FFFFFF);
    }

    private static String roleName(Role role) {
        return switch (role) {
            case PLAYER -> "ПОЛЬЗОВАТЕЛЬ";
            case HELPER -> "ПОМОЩНИК";
            case MODERATOR -> "МОДЕРАТОР";
            case ADMIN -> "АДМИНИСТРАТОР";
            case OWNER -> "ВЛАДЕЛЕЦ";
        };
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

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
        panelW = Math.min(820, width - 24);
        panelH = Math.min(470, height - 24);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        sideW = 148;
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
        int y = top + 66;
        addTab("ПРОФИЛЬ", Tab.PROFILE, y); y += 26;
        addTab("ИГРОКИ", Tab.PLAYERS, y); y += 26;
        addTab("СВЯЗЬ", Tab.CHAT, y); y += 26;
        addTab("МЕТКИ", Tab.MARKERS, y); y += 26;
        addTab("ПОДДЕРЖКА", Tab.SUPPORT, y); y += 26;
        addTab("ОФОРМЛЕНИЕ", Tab.COSMETICS, y); y += 34;
        if (state.role().canAdmin()) { addTab("УПРАВЛЕНИЕ", Tab.ADMIN, y); y += 26; }
        if (state.role().canSupport()) addTab("ЦЕНТР ПОДДЕРЖКИ", Tab.SUPPORT_CENTER, y);
    }

    private void addTab(String name, Tab target, int y) {
        addRenderableWidget(Button.builder(Component.literal((tab == target ? "◆ " : "  ") + name), b -> {
            tab = target;
            SoundFx.click();
            rebuildWidgets();
        }).bounds(left + 14, y, sideW - 28, 20).build());
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
        PlayerFaceWidget face = new PlayerFaceWidget(70, ResolvableProfile.createUnresolved(p.minecraftName()));
        face.setPosition(contentX + 18, top + 86);
        addRenderableWidget(face);
    }

    private void initPlayers() {
        int y = top + 78;
        List<ProfileData> list = state.profiles();
        for (int i = 0; i < Math.min(list.size(), 12); i++) {
            ProfileData p = list.get(i);
            addRenderableWidget(Button.builder(Component.literal(p.systemName() + "  //  " + p.race()), b -> {
                selectedProfile = p.uuid();
                tab = Tab.PROFILE;
                SoundFx.click();
                rebuildWidgets();
            }).bounds(contentX + 8, y, Math.min(380, contentW - 16), 20).build());
            y += 25;
        }
    }

    private void initChat() {
        inputA = field(contentX + 8, top + panelH - 52, contentW - 92, 20, 280, "Сообщение");
        addRenderableWidget(Button.builder(Component.literal("➤"), b -> {
            String text = inputA.getValue().trim();
            if (!text.isBlank()) { Wire.chat(text); inputA.setValue(""); SoundFx.confirm(); }
        }).bounds(contentX + contentW - 76, top + panelH - 52, 68, 20).build());

        if (state.role().canAdmin()) {
            addRenderableWidget(Button.builder(Component.literal("СИСТЕМНОЕ ОБЪЯВЛЕНИЕ"), b -> {
                String text = inputA.getValue().trim();
                if (!text.isBlank()) { Wire.systemAnnouncement(text); inputA.setValue(""); SoundFx.notification(); }
            }).bounds(contentX + 8, top + panelH - 78, 176, 20).build());
        }
    }

    private void initMarkers() {
        inputA = field(contentX + 8, top + panelH - 52, Math.min(280, contentW - 180), 20, 48, "Название метки");
        addRenderableWidget(Button.builder(Component.literal("ПОСТАВИТЬ ЗДЕСЬ"), b -> createMarker())
                .bounds(contentX + Math.min(296, contentW - 164), top + panelH - 52, 150, 20).build());

        List<MarkerData> markers = state.markers();
        int y = top + 80;
        for (int i = Math.max(0, markers.size() - 9); i < markers.size(); i++) {
            MarkerData m = markers.get(i);
            if (canDeleteMarker(m)) {
                addRenderableWidget(Button.builder(Component.literal("×"), b -> { Wire.markerDelete(m.id()); SoundFx.click(); })
                        .bounds(contentX + contentW - 30, y - 3, 20, 18).build());
            }
            y += 31;
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

        inputA = field(contentX + 12, top + 142, Math.min(330, contentW - 24), 20, 60, "Подпись");
        inputA.setValue(self.subtitle());
        inputB = field(contentX + 12, top + 198, 145, 20, 9, "#57D7FF");
        inputB.setValue(self.accent());

        addRenderableWidget(Button.builder(Component.literal("ТЕМА: " + ThemeCatalog.byId(self.theme()).title()), b -> {
            String next = ThemeCatalog.next(state.self().theme());
            Wire.cosmetics(next, safeAccent(inputB.getValue()), inputA.getValue().trim());
            SoundFx.click();
        }).bounds(contentX + 12, top + 88, Math.min(300, contentW - 24), 20).build());

        addRenderableWidget(Button.builder(Component.literal("RGB: " + (UiConfig.INSTANCE.rgbCycle ? "ВКЛ" : "ВЫКЛ")), b -> {
            UiConfig.INSTANCE.rgbCycle = !UiConfig.INSTANCE.rgbCycle;
            UiConfig.INSTANCE.save();
            SoundFx.click();
            rebuildWidgets();
        }).bounds(contentX + 172, top + 198, 104, 20).build());

        addRenderableWidget(Button.builder(Component.literal("СКОРОСТЬ RGB: " + UiConfig.INSTANCE.rgbSpeed), b -> {
            UiConfig.INSTANCE.rgbSpeed++;
            if (UiConfig.INSTANCE.rgbSpeed > 5) UiConfig.INSTANCE.rgbSpeed = 1;
            UiConfig.INSTANCE.save();
            SoundFx.click();
            rebuildWidgets();
        }).bounds(contentX + 286, top + 198, 140, 20).build());

        addRenderableWidget(Button.builder(Component.literal("СОХРАНИТЬ ОФОРМЛЕНИЕ"), b -> {
            Wire.cosmetics(state.self().theme(), safeAccent(inputB.getValue()), inputA.getValue().trim());
            UiConfig.INSTANCE.save();
            SoundFx.confirm();
        }).bounds(contentX + 12, top + 250, 204, 20).build());
    }

    private static String safeAccent(String value) {
        String v = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return v.matches("#[0-9A-F]{6}") ? v : "#57D7FF";
    }

    private void initSupport(boolean staff) {
        List<TicketData> tickets = ticketsFor(staff);
        if (selectedTicket == null) selectedTicket = tickets.isEmpty() ? null : tickets.getLast().id();
        else if (selectedTicket != -1 && tickets.stream().noneMatch(t -> t.id() == selectedTicket))
            selectedTicket = tickets.isEmpty() ? null : tickets.getLast().id();

        int listW = Math.min(200, contentW / 3);
        int y = top + 80;
        int shown = 0;
        for (int i = tickets.size() - 1; i >= 0 && shown < 10; i--, shown++) {
            TicketData t = tickets.get(i);
            String prefix = Objects.equals(selectedTicket, t.id()) ? "◆ " : "";
            addRenderableWidget(Button.builder(Component.literal(prefix + "#" + t.id() + " " + t.ownerName()), b -> {
                selectedTicket = t.id();
                SoundFx.click();
                rebuildWidgets();
            }).bounds(contentX + 8, y, listW, 20).build());
            y += 24;
        }

        if (!staff) {
            addRenderableWidget(Button.builder(Component.literal("+ НОВОЕ ОБРАЩЕНИЕ"), b -> {
                selectedTicket = -1;
                SoundFx.click();
                rebuildWidgets();
            }).bounds(contentX + 8, top + panelH - 52, listW, 20).build());
        }

        TicketData selected = findTicket(selectedTicket, staff);
        if (selected != null) {
            int chatX = contentX + listW + 20;
            int chatW = contentW - listW - 28;
            inputA = field(chatX, top + panelH - 52, Math.max(90, chatW - 72), 20, 500, "Ответ");
            addRenderableWidget(Button.builder(Component.literal("➤"), b -> {
                String text = inputA.getValue().trim();
                if (!text.isBlank()) { Wire.ticketMessage(selected.id(), text); inputA.setValue(""); SoundFx.confirm(); }
            }).bounds(chatX + chatW - 66, top + panelH - 52, 62, 20).build());

            if (staff) {
                addRenderableWidget(Button.builder(Component.literal("ВЗЯТЬ"), b -> Wire.ticketTake(selected.id()))
                        .bounds(chatX, top + panelH - 78, 65, 20).build());
                addRenderableWidget(Button.builder(Component.literal("ЗАКРЫТЬ"), b -> Wire.ticketClose(selected.id()))
                        .bounds(chatX + 70, top + panelH - 78, 75, 20).build());
                addRenderableWidget(Button.builder(Component.literal("ВНУТР. ЗАМЕТКА"), b -> {
                    String text = inputA.getValue().trim();
                    if (!text.isBlank()) { Wire.ticketInternal(selected.id(), text); inputA.setValue(""); SoundFx.click(); }
                }).bounds(chatX + 150, top + panelH - 78, 132, 20).build());
            }
        } else if (!staff) {
            int formX = contentX + listW + 20;
            int formW = contentW - listW - 28;
            inputA = field(formX, top + 132, Math.min(330, formW), 20, 40, "Категория");
            inputA.setValue("Помощь");
            inputB = field(formX, top + 188, Math.max(120, Math.min(460, formW)), 20, 500, "Опишите проблему");
            addRenderableWidget(Button.builder(Component.literal("СОЗДАТЬ ОБРАЩЕНИЕ"), b -> {
                String message = inputB.getValue().trim();
                if (!message.isBlank()) { Wire.ticketCreate(inputA.getValue().trim(), message); SoundFx.confirm(); }
            }).bounds(formX, top + 238, Math.min(190, formW), 20).build());
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
        if (p == null && !state.profiles().isEmpty()) {
            selectedProfile = state.profiles().getFirst().uuid();
            p = state.profiles().getFirst();
        }
        if (p == null) return;

        int labelW = 104;
        int x = contentX + labelW;
        int w = Math.min(340, contentW - labelW - 150);
        int y = top + 84;
        inputA = field(x, y, w, 20, 32, "Системное имя"); inputA.setValue(p.systemName()); y += 32;
        inputB = field(x, y, w, 20, 48, "Раса"); inputB.setValue(p.race()); y += 32;
        inputC = field(x, y, w, 20, 64, "Происхождение"); inputC.setValue(p.origin()); y += 32;
        inputD = field(x, y, w, 20, 48, "Статус"); inputD.setValue(p.status()); y += 32;
        inputE = field(x, y, w, 20, 64, "Фракция"); inputE.setValue(p.faction()); y += 32;
        inputF = field(x, y, w, 20, 500, "Способности через ;"); inputF.setValue(String.join("; ", p.abilities())); y += 32;

        EditBox traits = field(x, y, w, 20, 500, "Особенности через ;"); traits.setValue(String.join("; ", p.traits())); y += 32;
        EditBox note = field(x, y, w, 20, 500, "Скрытая заметка"); note.setValue(p.adminNote()); y += 32;
        EditBox roleBox = field(x, y, w, 20, 16, "Роль доступа"); roleBox.setValue(p.accessRole()); y += 34;

        ProfileData base = p;
        addRenderableWidget(Button.builder(Component.literal("СОХРАНИТЬ СИСТЕМНЫЕ ДАННЫЕ"), b -> {
            ProfileData updated = new ProfileData(base.uuid(), base.minecraftName(), inputA.getValue().trim(), inputB.getValue().trim(),
                    inputC.getValue().trim(), inputD.getValue().trim(), inputE.getValue().trim(), roleBox.getValue().trim().toUpperCase(Locale.ROOT),
                    splitSemi(inputF.getValue()), splitSemi(traits.getValue()), base.subtitle(), base.theme(), base.accent(), note.getValue().trim());
            Wire.adminProfileUpdate(updated);
            SoundFx.confirm();
        }).bounds(x, y, Math.min(240, w), 20).build());

        int playerY = top + 84;
        int playerX = contentX + contentW - 132;
        for (ProfileData player : state.profiles().stream().limit(12).toList()) {
            addRenderableWidget(Button.builder(Component.literal(player.systemName()), b -> {
                selectedProfile = player.uuid();
                rebuildWidgets();
            }).bounds(playerX, playerY, 120, 18).build());
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
        int accent = withAccent(theme, 0xFF);
        int dimAccent = withAccent(theme, 0x55);

        g.fill(0, 0, width, height, 0xB4000000);
        g.fill(left, top, left + panelW, top + panelH, theme.background());

        if (UiConfig.INSTANCE.showGrid) {
            int spacing = UiConfig.INSTANCE.gridSpacing;
            for (int x = left; x < left + panelW; x += spacing) g.fill(x, top, x + 1, top + panelH, withAccent(theme, 0x10));
            for (int y = top; y < top + panelH; y += spacing) g.fill(left, y, left + panelW, y + 1, withAccent(theme, 0x10));
        }

        for (int y = top + 48; y < top + panelH - 12; y += 4)
            g.fill(left + sideW, y, left + panelW - 10, y + 1, 0x06000000);

        g.outline(left, top, panelW, panelH, accent);
        g.outline(left + 2, top + 2, panelW - 4, panelH - 4, dimAccent);
        drawCorners(g, left, top, panelW, panelH, accent);

        g.fill(left + 5, top + 48, left + panelW - 5, top + 49, dimAccent);
        g.fill(left + sideW, top + 49, left + sideW + 1, top + panelH - 16, dimAccent);

        g.fill(contentX - 10, top + 64, left + panelW - 12, top + panelH - 16, theme.panel());
        g.outline(contentX - 10, top + 64, contentW + 10, panelH - 80, withAccent(theme, 0x50));
        drawCorners(g, contentX - 10, top + 64, contentW + 10, panelH - 80, withAccent(theme, 0xA0));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        Theme theme = theme();
        int accent = withAccent(theme, 0xFF);

        g.text(font, Component.literal("◈ " + UiConfig.INSTANCE.brandText), left + 14, top + 17, accent, false);
        g.text(font, Component.literal("OKG // CORE"), left + 14, top + 31, theme.muted(), false);
        g.text(font, Component.literal("СОСТОЯНИЕ: " + (state.connected() ? "ONLINE" : "OFFLINE")), left + panelW - 136, top + 17,
                state.connected() ? 0xFF67E69B : theme.danger(), false);
        g.text(font, Component.literal(UiConfig.INSTANCE.rgbCycle ? "RGB LINK ACTIVE" : "STATIC LINK"), left + panelW - 136, top + 31,
                UiConfig.INSTANCE.rgbCycle ? accent : theme.muted(), false);
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
            g.fill(contentX, top + panelH - 31, contentX + contentW, top + panelH - 17, 0xCC24151A);
            g.outline(contentX, top + panelH - 31, contentW, 14, theme.danger());
            g.text(font, Component.literal(notice), contentX + 6, top + panelH - 28, 0xFFFFA6AE, false);
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

        int cardX = contentX + 8;
        int cardY = top + 78;
        int cardW = contentW - 16;
        int accent = withAccent(theme, 0xFF);

        neonPanel(g, cardX, cardY, cardW, 122, theme);
        g.outline(contentX + 12, top + 82, 82, 82, withAccent(theme, 0x90));
        drawCorners(g, contentX + 12, top + 82, 82, 82, accent);
        g.text(font, Component.literal("ID // " + p.uuid().toString().substring(0, 8).toUpperCase(Locale.ROOT)), contentX + 18, top + 171, theme.muted(), false);

        int x = contentX + 110;
        int y = top + 88;
        dataLine(g, x, y, "НИКНЕЙМ", p.systemName(), theme, accent); y += 19;
        dataLine(g, x, y, "РАСА", unidentified ? unavailable : p.race(), theme, accent); y += 19;
        dataLine(g, x, y, "ПРОИСХОЖДЕНИЕ", unidentified ? unavailable : p.origin(), theme, accent); y += 19;
        dataLine(g, x, y, "ФРАКЦИЯ", unidentified ? unavailable : p.faction(), theme, accent); y += 19;
        dataLine(g, x, y, "ДОСТУП", roleName(Role.safe(p.accessRole())), theme, accent); y += 19;
        g.text(font, Component.literal("СТАТУС"), x, y, theme.muted(), false);
        g.text(font, Component.literal("[ " + safeText(p.status()) + " ]"), x + 104, y, status.color(), false);

        if (!status.warning().isBlank()) {
            g.fill(cardX + 8, top + 205, cardX + cardW - 8, top + 225, 0x991C1514);
            g.outline(cardX + 8, top + 205, cardW - 16, 20, status.color());
            g.text(font, Component.literal("⚠ " + status.warning()), cardX + 16, top + 211, status.color(), false);
        }

        int blocksY = top + 236;
        int gap = 10;
        int half = (cardW - gap) / 2;
        neonPanel(g, cardX, blocksY, half, 142, theme);
        neonPanel(g, cardX + half + gap, blocksY, cardW - half - gap, 142, theme);

        g.text(font, Component.literal(archived ? "◆ АРХИВ СПОСОБНОСТЕЙ" : "◆ СПОСОБНОСТИ"), cardX + 12, blocksY + 10,
                archived ? status.color() : accent, false);
        int yy = blocksY + 30;
        if (unidentified) {
            g.text(font, Component.literal("[ ДОСТУП ОГРАНИЧЕН ]"), cardX + 14, yy, status.color(), false);
        } else if (lost) {
            g.text(font, Component.literal("[ СИГНАЛ ПОТЕРЯН ]"), cardX + 14, yy, status.color(), false);
        } else {
            for (String ability : p.abilities().stream().limit(7).toList()) {
                g.text(font, Component.literal("◈ " + ability), cardX + 14, yy, archived ? theme.muted() : theme.text(), false);
                yy += 14;
            }
            if (p.abilities().isEmpty()) g.text(font, Component.literal("[ ДАННЫЕ НЕ ОБНАРУЖЕНЫ ]"), cardX + 14, yy, theme.muted(), false);
        }

        int tx = cardX + half + gap + 12;
        g.text(font, Component.literal("◆ ОСОБЕННОСТИ"), tx, blocksY + 10, accent, false);
        yy = blocksY + 30;
        for (String trait : p.traits().stream().limit(7).toList()) {
            g.text(font, Component.literal("[ " + trait + " ]"), tx + 2, yy, theme.text(), false);
            yy += 14;
        }
        if (p.traits().isEmpty()) g.text(font, Component.literal("[ ПУСТО ]"), tx + 2, yy, theme.muted(), false);

        if (!p.subtitle().isBlank())
            g.text(font, Component.literal("“" + p.subtitle() + "”"), cardX + 12, top + panelH - 47, theme.muted(), false);
        if (state.role().canAdmin() && !p.adminNote().isBlank())
            g.text(font, Component.literal("ADMIN NOTE // " + p.adminNote()), cardX + 12, top + panelH - 62, 0xFFFFA967, false);
    }

    private void renderPlayers(GuiGraphicsExtractor g, Theme theme) {
        title(g, "ПРОФИЛИ // БАЗА ПОЛЬЗОВАТЕЛЕЙ", theme);
        g.text(font, Component.literal("Выберите профиль, чтобы открыть карточку."), contentX + 8, top + 62, theme.muted(), false);
        if (state.profiles().isEmpty()) g.text(font, Component.literal("[ НЕТ СИНХРОНИЗИРОВАННЫХ ПРОФИЛЕЙ ]"), contentX + 8, top + 86, theme.muted(), false);
    }

    private void renderChat(GuiGraphicsExtractor g, Theme theme) {
        title(g, "СВЯЗЬ // ОБЩИЙ КАНАЛ", theme);
        int boxX = contentX + 8, boxY = top + 78, boxW = contentW - 16, boxH = panelH - 155;
        neonPanel(g, boxX, boxY, boxW, boxH, theme);
        List<ChatEntry> list = state.chat();
        int y = boxY + 12;
        int start = Math.max(0, list.size() - 15);
        for (int i = start; i < list.size(); i++) {
            ChatEntry e = list.get(i);
            int color = e.system() ? withAccent(theme, 0xFF) : theme.text();
            String prefix = e.system() ? "[СИСТЕМА] " : e.sender() + " // ";
            drawWrapped(g, prefix + e.message(), boxX + 10, y, boxW - 20, color, 2);
            y += 19;
        }
    }

    private void renderMarkers(GuiGraphicsExtractor g, Theme theme) {
        title(g, "МЕТКИ // ОБЩАЯ КАРТА", theme);
        List<MarkerData> list = state.markers();
        int y = top + 82;
        int start = Math.max(0, list.size() - 9);
        for (int i = start; i < list.size(); i++) {
            MarkerData m = list.get(i);
            g.fill(contentX + 8, y - 5, contentX + contentW - 8, y + 22, theme.panelAlt());
            g.outline(contentX + 8, y - 5, contentW - 16, 27, withAccent(theme, 0x45));
            g.text(font, Component.literal("◆ " + m.title()), contentX + 16, y, withAccent(theme, 0xFF), false);
            g.text(font, Component.literal(m.dimension() + "  //  " + m.x() + " / " + m.y() + " / " + m.z() + "  //  " + m.ownerName()),
                    contentX + 28, y + 12, theme.muted(), false);
            y += 31;
        }
        if (list.isEmpty()) g.text(font, Component.literal("[ МЕТОК НЕТ ]"), contentX + 12, y, theme.muted(), false);
    }

    private void renderCosmetics(GuiGraphicsExtractor g, Theme theme) {
        title(g, "ОФОРМЛЕНИЕ // ВИЗУАЛЬНЫЙ ПРОТОКОЛ", theme);
        neonPanel(g, contentX + 8, top + 78, contentW - 16, 222, theme);
        g.text(font, Component.literal("ТЕМА ИНТЕРФЕЙСА"), contentX + 20, top + 112, theme.muted(), false);
        g.text(font, Component.literal("ДЕКОРАТИВНАЯ ПОДПИСЬ"), contentX + 20, top + 132, theme.muted(), false);
        g.text(font, Component.literal("АКЦЕНТНЫЙ ЦВЕТ HEX"), contentX + 20, top + 188, theme.muted(), false);
        g.text(font, Component.literal("RGB-ЦИКЛ плавно меняет цвет рамок, заголовков и системных линий."), contentX + 20, top + 228, theme.text(), false);
        g.text(font, Component.literal("HEX остаётся запасным статичным цветом, когда RGB выключен."), contentX + 20, top + 242, theme.muted(), false);
        g.text(font, Component.literal("Игрок меняет только косметику. Системные данные остаются под контролем администрации."), contentX + 20, top + 282, theme.muted(), false);
    }

    private void renderSupport(GuiGraphicsExtractor g, Theme theme, boolean staff) {
        title(g, staff ? "ЦЕНТР ПОДДЕРЖКИ // STAFF" : "ПОДДЕРЖКА // PRIVATE CHANNEL", theme);
        TicketData t = findTicket(selectedTicket, staff);
        if (t == null) {
            if (staff) g.text(font, Component.literal("[ НЕТ ДОСТУПНЫХ ОБРАЩЕНИЙ ]"), contentX + 8, top + 88, theme.muted(), false);
            else {
                int listW = Math.min(200, contentW / 3);
                int formX = contentX + listW + 20;
                neonPanel(g, formX - 8, top + 82, contentW - listW - 20, 190, theme);
                g.text(font, Component.literal("КАНАЛ ВИДЯТ ТОЛЬКО ВЫ И ПЕРСОНАЛ С ДОСТУПОМ."), formX, top + 98, withAccent(theme, 0xFF), false);
                g.text(font, Component.literal("КАТЕГОРИЯ"), formX, top + 120, theme.muted(), false);
                g.text(font, Component.literal("СООБЩЕНИЕ"), formX, top + 176, theme.muted(), false);
            }
            return;
        }

        int listW = Math.min(200, contentW / 3);
        int chatX = contentX + listW + 20;
        int chatW = contentW - listW - 28;
        neonPanel(g, chatX - 8, top + 78, chatW + 8, panelH - 160, theme);
        g.text(font, Component.literal("#" + t.id() + " // " + t.category()), chatX, top + 88, withAccent(theme, 0xFF), false);
        g.text(font, Component.literal("СТАТУС: " + t.status() + "   //   STAFF: " + (t.assignedTo().isBlank() ? "—" : t.assignedTo())), chatX, top + 102, theme.muted(), false);

        int y = top + 126;
        List<TicketMessage> messages = t.messages();
        int start = Math.max(0, messages.size() - 12);
        for (int i = start; i < messages.size(); i++) {
            TicketMessage m = messages.get(i);
            int color = m.internal() ? 0xFFFFB565 : ("PLAYER".equals(m.role()) ? theme.text() : withAccent(theme, 0xFF));
            String prefix = m.internal() ? "[ВНУТРЕННЕ] " : m.sender() + " // ";
            drawWrapped(g, prefix + m.text(), chatX, y, chatW - 12, color, 2);
            y += 20;
        }
    }

    private void renderAdmin(GuiGraphicsExtractor g, Theme theme) {
        title(g, "УПРАВЛЕНИЕ // СИСТЕМНЫЕ ПРОФИЛИ", theme);
        ProfileData p = selectedProfile();
        if (p == null) return;
        String[] labels = {"НИКНЕЙМ", "РАСА", "ПРОИСХОЖД.", "СТАТУС", "ФРАКЦИЯ", "СПОСОБНОСТИ", "ОСОБЕННОСТИ", "СКРЫТАЯ ЗАМЕТКА", "РОЛЬ ДОСТУПА"};
        int y = top + 90;
        for (String label : labels) { g.text(font, Component.literal(label), contentX + 8, y, theme.muted(), false); y += 32; }
        g.text(font, Component.literal("TARGET // " + p.minecraftName() + " // UUID " + p.uuid().toString().substring(0, 8).toUpperCase(Locale.ROOT)),
                contentX + 8, top + 66, withAccent(theme, 0xFF), false);
    }

    private void title(GuiGraphicsExtractor g, String value, Theme theme) {
        g.text(font, Component.literal("◈ " + value), contentX, top + 52, withAccent(theme, 0xFF), false);
        g.fill(contentX, top + 66, contentX + Math.min(contentW, 320), top + 67, withAccent(theme, 0x65));
    }

    private void dataLine(GuiGraphicsExtractor g, int x, int y, String label, String value, Theme theme, int accent) {
        g.text(font, Component.literal(label), x, y, theme.muted(), false);
        String shown = safeText(value);
        g.text(font, Component.literal(shown), x + 104, y, theme.text(), false);
        g.fill(x + 100, y + 11, x + Math.min(contentW - 130, 360), y + 12, withAlpha(accent, 0x22));
    }

    private static String safeText(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private void neonPanel(GuiGraphicsExtractor g, int x, int y, int w, int h, Theme theme) {
        if (w <= 4 || h <= 4) return;
        g.fill(x, y, x + w, y + h, theme.panelAlt());
        g.outline(x, y, w, h, withAccent(theme, 0x40));
        g.outline(x + 2, y + 2, w - 4, h - 4, 0x322A3946);
        drawCorners(g, x, y, w, h, withAccent(theme, 0xB5));
    }

    private static void drawCorners(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        int s = 10;
        g.fill(x, y, x + s, y + 2, color); g.fill(x, y, x + 2, y + s, color);
        g.fill(x + w - s, y, x + w, y + 2, color); g.fill(x + w - 2, y, x + w, y + s, color);
        g.fill(x, y + h - 2, x + s, y + h, color); g.fill(x, y + h - s, x + 2, y + h, color);
        g.fill(x + w - s, y + h - 2, x + w, y + h, color); g.fill(x + w - 2, y + h - s, x + w, y + h, color);
    }

    private void drawWrapped(GuiGraphicsExtractor g, String text, int x, int y, int width, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), Math.max(40, width));
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++)
            g.text(font, lines.get(i), x, y + i * 10, color, false);
    }

    private Theme theme() {
        ProfileData self = state.self();
        Theme base = ThemeCatalog.byId(self == null ? "SYSTEM" : self.theme());
        int rgb;

        if (UiConfig.INSTANCE.rgbCycle) {
            rgb = rainbowRgb(UiConfig.INSTANCE.rgbSpeed);
        } else if (self != null) {
            try { rgb = Integer.parseInt(safeAccent(self.accent()).substring(1), 16); }
            catch (Exception ignored) { rgb = base.accent() & 0x00FFFFFF; }
        } else {
            rgb = base.accent() & 0x00FFFFFF;
        }

        return new Theme(base.id(), base.title(), 0xFF000000 | rgb,
                base.background(), base.panel(), base.panelAlt(), base.text(), base.muted(), base.danger());
    }

    private static int rainbowRgb(int speed) {
        long period = Math.max(1800L, 11000L - Math.max(1, Math.min(5, speed)) * 1700L);
        float hue = (System.currentTimeMillis() % period) / (float) period;
        return hsvToRgb(hue, 0.72f, 1.0f);
    }

    private static int hsvToRgb(float h, float s, float v) {
        float hh = (h - (float)Math.floor(h)) * 6.0f;
        int i = (int)Math.floor(hh);
        float f = hh - i;
        float p = v * (1.0f - s);
        float q = v * (1.0f - s * f);
        float t = v * (1.0f - s * (1.0f - f));
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        int ri = Math.min(255, Math.max(0, Math.round(r * 255)));
        int gi = Math.min(255, Math.max(0, Math.round(g * 255)));
        int bi = Math.min(255, Math.max(0, Math.round(b * 255)));
        return (ri << 16) | (gi << 8) | bi;
    }

    private static int withAccent(Theme theme, int alpha) {
        return (alpha << 24) | (theme.accent() & 0x00FFFFFF);
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
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

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
    private enum Tab { PROFILE, PLAYERS, CHAT, SYSTEM_CHAT, DM, MARKERS, SUPPORT, COSMETICS, ADMIN, SUPPORT_CENTER }

    private static final List<String> FRAMES = List.of("SYSTEM", "FOX", "SHULKER", "SNOWMAN", "MINIMAL");
    private static final List<String> DECORS = List.of("NONE", "FOX", "SHULKER", "SNOWMAN");

    private final ClientSystemState state = ClientSystemState.INSTANCE;
    private Tab tab = Tab.PROFILE;
    private UUID selectedProfile;
    private UUID selectedDmPeer;
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
        panelW = Math.min(860, width - 24);
        panelH = Math.min(500, height - 24);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        sideW = 154;
        contentX = left + sideW + 16;
        contentW = panelW - sideW - 28;

        addTabs();
        switch (tab) {
            case PROFILE -> initProfile();
            case PLAYERS -> initPlayers();
            case CHAT -> initChat(false);
            case SYSTEM_CHAT -> initChat(true);
            case DM -> initDm();
            case MARKERS -> initMarkers();
            case SUPPORT -> initSupport(false);
            case COSMETICS -> initCosmetics();
            case ADMIN -> initAdmin();
            case SUPPORT_CENTER -> initSupport(true);
        }
    }

    private void addTabs() {
        int y = top + 62;
        addTab("ПРОФИЛЬ", Tab.PROFILE, y); y += 24;
        addTab("ИГРОКИ", Tab.PLAYERS, y); y += 24;
        addTab("ОБЩИЙ ЧАТ", Tab.CHAT, y); y += 24;
        addTab("СИСТЕМНЫЙ КАНАЛ", Tab.SYSTEM_CHAT, y); y += 24;
        addTab("ЛИЧНЫЕ", Tab.DM, y); y += 24;
        addTab("МЕТКИ", Tab.MARKERS, y); y += 24;
        addTab("ПОДДЕРЖКА", Tab.SUPPORT, y); y += 24;
        addTab("ОФОРМЛЕНИЕ", Tab.COSMETICS, y); y += 32;
        if (state.role().canAdmin()) { addTab("УПРАВЛЕНИЕ", Tab.ADMIN, y); y += 24; }
        if (state.role().canSupport()) addTab("ЦЕНТР ПОДДЕРЖКИ", Tab.SUPPORT_CENTER, y);
    }

    private void addTab(String name, Tab target, int y) {
        addRenderableWidget(Button.builder(Component.literal((tab == target ? "◆ " : "◇ ") + name), b -> {
            tab = target;
            SoundFx.click();
            rebuildWidgets();
        }).bounds(left + 12, y, sideW - 24, 19).build());
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
        PlayerFaceWidget face = new PlayerFaceWidget(76, ResolvableProfile.createUnresolved(p.minecraftName()));
        face.setPosition(contentX + 22, top + 88);
        addRenderableWidget(face);
    }

    private void initPlayers() {
        int y = top + 82;
        for (ProfileData p : state.profiles().stream().limit(14).toList()) {
            addRenderableWidget(Button.builder(Component.literal(p.systemName() + "  //  " + p.race()), b -> {
                selectedProfile = p.uuid();
                tab = Tab.PROFILE;
                SoundFx.click();
                rebuildWidgets();
            }).bounds(contentX + 10, y, Math.min(410, contentW - 20), 20).build());
            y += 25;
        }
    }

    private void initChat(boolean systemChannel) {
        List<ChatEntry> list = systemChannel ? state.systemChat() : state.chat();
        int boxY = top + 82;
        int start = Math.max(0, list.size() - 13);
        int y = boxY + 9;
        if (state.role().canAdmin()) {
            for (int i = start; i < list.size(); i++) {
                ChatEntry e = list.get(i);
                addRenderableWidget(Button.builder(Component.literal("×"), b -> {
                    if (systemChannel) Wire.adminDeleteSystemMessage(e.time());
                    else Wire.adminDeleteChatMessage(e.time());
                    SoundFx.click();
                }).bounds(contentX + contentW - 34, y - 4, 20, 18).build());
                y += 23;
            }
            addRenderableWidget(Button.builder(Component.literal("ОЧИСТИТЬ КАНАЛ"), b -> {
                if (systemChannel) Wire.adminClearSystemChat(); else Wire.adminClearChat();
                SoundFx.error();
            }).bounds(contentX + 10, top + panelH - 77, 142, 20).build());
        }

        if (!systemChannel || state.role().canAdmin()) {
            inputA = field(contentX + 10, top + panelH - 50, contentW - 92, 20, 280,
                    systemChannel ? "Системное сообщение" : "Сообщение");
            addRenderableWidget(Button.builder(Component.literal("ОТПРАВИТЬ"), b -> {
                String text = inputA.getValue().trim();
                if (!text.isBlank()) {
                    if (systemChannel) Wire.systemChat(text); else Wire.chat(text);
                    inputA.setValue("");
                    SoundFx.confirm();
                }
            }).bounds(contentX + contentW - 76, top + panelH - 50, 66, 20).build());
        }
    }

    private void initDm() {
        ProfileData self = state.self();
        if (self == null) return;
        if (state.role() == Role.OWNER) return;

        List<ProfileData> peers = state.profiles().stream().filter(p -> !p.uuid().equals(self.uuid())).toList();
        if (selectedDmPeer == null || peers.stream().noneMatch(p -> p.uuid().equals(selectedDmPeer))) {
            selectedDmPeer = peers.isEmpty() ? null : peers.getFirst().uuid();
        }
        int y = top + 82;
        for (ProfileData p : peers.stream().limit(12).toList()) {
            boolean selected = p.uuid().equals(selectedDmPeer);
            addRenderableWidget(Button.builder(Component.literal((selected ? "◆ " : "◇ ") + p.systemName()), b -> {
                selectedDmPeer = p.uuid();
                SoundFx.click();
                rebuildWidgets();
            }).bounds(contentX + 10, y, 146, 20).build());
            y += 24;
        }
        if (selectedDmPeer != null) {
            inputA = field(contentX + 170, top + panelH - 50, contentW - 252, 20, 500, "Личное сообщение");
            addRenderableWidget(Button.builder(Component.literal("ОТПРАВИТЬ"), b -> {
                String text = inputA.getValue().trim();
                if (!text.isBlank() && selectedDmPeer != null) {
                    Wire.directMessage(selectedDmPeer, text);
                    inputA.setValue("");
                    SoundFx.confirm();
                }
            }).bounds(contentX + contentW - 76, top + panelH - 50, 66, 20).build());
        }
    }

    private void initMarkers() {
        inputA = field(contentX + 10, top + panelH - 50, Math.min(280, contentW - 180), 20, 48, "Название метки");
        addRenderableWidget(Button.builder(Component.literal("ПОСТАВИТЬ ЗДЕСЬ"), b -> createMarker())
                .bounds(contentX + Math.min(302, contentW - 164), top + panelH - 50, 150, 20).build());

        List<MarkerData> markers = state.markers();
        int y = top + 84;
        for (int i = Math.max(0, markers.size() - 10); i < markers.size(); i++) {
            MarkerData m = markers.get(i);
            if (canDeleteMarker(m)) {
                addRenderableWidget(Button.builder(Component.literal("×"), b -> { Wire.markerDelete(m.id()); SoundFx.click(); })
                        .bounds(contentX + contentW - 32, y - 4, 20, 18).build());
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
        String markerTitle = inputA.getValue().trim();
        if (markerTitle.isBlank()) markerTitle = "Метка";
        var pos = minecraft.player.blockPosition();
        Wire.markerCreate(markerTitle, "ОБЩАЯ", "", pos.getX(), pos.getY(), pos.getZ());
        inputA.setValue("");
        SoundFx.confirm();
    }

    private void initCosmetics() {
        ProfileData self = state.self();
        if (self == null) return;

        inputA = field(contentX + 18, top + 160, Math.min(360, contentW - 36), 20, 60, "Подпись");
        inputA.setValue(self.subtitle());
        inputB = field(contentX + 18, top + 220, 142, 20, 9, "#57D7FF");
        inputB.setValue(self.accent());

        addRenderableWidget(Button.builder(Component.literal("ТЕМА: " + ThemeCatalog.byId(self.theme()).title()), b -> {
            String next = ThemeCatalog.next(state.self().theme());
            Theme nextTheme = ThemeCatalog.byId(next);
            Wire.cosmetics(next, hex(nextTheme.accent()), inputA.getValue().trim(), state.self().frame(), state.self().decor());
            SoundFx.click();
        }).bounds(contentX + 18, top + 92, Math.min(350, contentW - 36), 20).build());

        addRenderableWidget(Button.builder(Component.literal("РАМКА: " + frameTitle(self.frame())), b -> {
            String next = nextOf(FRAMES, state.self().frame());
            Wire.cosmetics(state.self().theme(), safeAccent(inputB.getValue()), inputA.getValue().trim(), next, state.self().decor());
            SoundFx.click();
        }).bounds(contentX + 18, top + 118, 230, 20).build());

        addRenderableWidget(Button.builder(Component.literal("ДЕКОР: " + decorTitle(self.decor())), b -> {
            String next = nextOf(DECORS, state.self().decor());
            Wire.cosmetics(state.self().theme(), safeAccent(inputB.getValue()), inputA.getValue().trim(), state.self().frame(), next);
            SoundFx.click();
        }).bounds(contentX + 258, top + 118, 210, 20).build());

        addRenderableWidget(Button.builder(Component.literal("RGB: " + (UiConfig.INSTANCE.rgbCycle ? "ВКЛ" : "ВЫКЛ")), b -> {
            UiConfig.INSTANCE.rgbCycle = !UiConfig.INSTANCE.rgbCycle;
            UiConfig.INSTANCE.save();
            SoundFx.click();
            rebuildWidgets();
        }).bounds(contentX + 174, top + 220, 104, 20).build());

        addRenderableWidget(Button.builder(Component.literal("СКОРОСТЬ RGB: " + UiConfig.INSTANCE.rgbSpeed), b -> {
            UiConfig.INSTANCE.rgbSpeed++;
            if (UiConfig.INSTANCE.rgbSpeed > 5) UiConfig.INSTANCE.rgbSpeed = 1;
            UiConfig.INSTANCE.save();
            SoundFx.click();
            rebuildWidgets();
        }).bounds(contentX + 288, top + 220, 145, 20).build());

        addRenderableWidget(Button.builder(Component.literal("СОХРАНИТЬ"), b -> {
            Wire.cosmetics(state.self().theme(), safeAccent(inputB.getValue()), inputA.getValue().trim(), state.self().frame(), state.self().decor());
            UiConfig.INSTANCE.save();
            SoundFx.confirm();
        }).bounds(contentX + 18, top + 270, 150, 20).build());
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

        int listW = Math.min(205, contentW / 3);
        int y = top + 82;
        int shown = 0;
        for (int i = tickets.size() - 1; i >= 0 && shown < 11; i--, shown++) {
            TicketData t = tickets.get(i);
            String prefix = Objects.equals(selectedTicket, t.id()) ? "◆ " : "◇ ";
            addRenderableWidget(Button.builder(Component.literal(prefix + "#" + t.id() + " " + t.ownerName()), b -> {
                selectedTicket = t.id();
                SoundFx.click();
                rebuildWidgets();
            }).bounds(contentX + 10, y, listW, 20).build());
            y += 24;
        }

        if (!staff) {
            addRenderableWidget(Button.builder(Component.literal("+ НОВОЕ ОБРАЩЕНИЕ"), b -> {
                selectedTicket = -1;
                SoundFx.click();
                rebuildWidgets();
            }).bounds(contentX + 10, top + panelH - 50, listW, 20).build());
        }

        TicketData selected = findTicket(selectedTicket, staff);
        if (selected != null) {
            int chatX = contentX + listW + 22;
            int chatW = contentW - listW - 32;
            inputA = field(chatX, top + panelH - 50, Math.max(90, chatW - 78), 20, 500, "Ответ");
            addRenderableWidget(Button.builder(Component.literal("ОТПР."), b -> {
                String text = inputA.getValue().trim();
                if (!text.isBlank()) { Wire.ticketMessage(selected.id(), text); inputA.setValue(""); SoundFx.confirm(); }
            }).bounds(chatX + chatW - 70, top + panelH - 50, 66, 20).build());

            if (staff) {
                int controlY = top + panelH - 78;
                addRenderableWidget(Button.builder(Component.literal("ВЗЯТЬ"), b -> Wire.ticketTake(selected.id()))
                        .bounds(chatX, controlY, 62, 20).build());
                addRenderableWidget(Button.builder(Component.literal("ЗАКРЫТЬ"), b -> Wire.ticketClose(selected.id()))
                        .bounds(chatX + 68, controlY, 70, 20).build());
                addRenderableWidget(Button.builder(Component.literal("ВНУТР. ЗАМЕТКА"), b -> {
                    String text = inputA.getValue().trim();
                    if (!text.isBlank()) { Wire.ticketInternal(selected.id(), text); inputA.setValue(""); SoundFx.click(); }
                }).bounds(chatX + 144, controlY, 122, 20).build());
                if (state.role().canAdmin()) {
                    addRenderableWidget(Button.builder(Component.literal("УДАЛИТЬ"), b -> {
                        Wire.adminDeleteTicket(selected.id());
                        selectedTicket = null;
                        SoundFx.error();
                    }).bounds(chatX + 272, controlY, 78, 20).build());
                }
            }
        } else if (!staff) {
            int formX = contentX + listW + 22;
            int formW = contentW - listW - 32;
            inputA = field(formX, top + 142, Math.min(340, formW), 20, 40, "Категория");
            inputA.setValue("Помощь");
            inputB = field(formX, top + 202, Math.max(120, Math.min(470, formW)), 20, 500, "Опишите проблему");
            addRenderableWidget(Button.builder(Component.literal("СОЗДАТЬ ОБРАЩЕНИЕ"), b -> {
                String message = inputB.getValue().trim();
                if (!message.isBlank()) { Wire.ticketCreate(inputA.getValue().trim(), message); SoundFx.confirm(); }
            }).bounds(formX, top + 250, Math.min(190, formW), 20).build());
        }

        if (staff && state.role().canAdmin()) {
            addRenderableWidget(Button.builder(Component.literal("ОЧИСТИТЬ ВСЕ ТИКЕТЫ"), b -> {
                Wire.adminClearTickets(); selectedTicket = null; SoundFx.error();
            }).bounds(contentX + 10, top + panelH - 76, listW, 20).build());
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

        int labelW = 112;
        int x = contentX + labelW;
        int w = Math.min(350, contentW - labelW - 150);
        int y = top + 92;
        inputA = field(x, y, w, 20, 32, "Системное имя"); inputA.setValue(p.systemName()); y += 31;
        inputB = field(x, y, w, 20, 48, "Раса"); inputB.setValue(p.race()); y += 31;
        inputC = field(x, y, w, 20, 64, "Происхождение"); inputC.setValue(p.origin()); y += 31;
        inputD = field(x, y, w, 20, 48, "Статус"); inputD.setValue(p.status()); y += 31;
        inputE = field(x, y, w, 20, 64, "Фракция"); inputE.setValue(p.faction()); y += 31;
        inputF = field(x, y, w, 20, 500, "Способности через ;"); inputF.setValue(String.join("; ", p.abilities())); y += 31;

        EditBox traits = field(x, y, w, 20, 500, "Особенности через ;"); traits.setValue(String.join("; ", p.traits())); y += 31;
        EditBox note = field(x, y, w, 20, 500, "Скрытая заметка"); note.setValue(p.adminNote()); y += 34;

        ProfileData base = p;
        addRenderableWidget(Button.builder(Component.literal("СОХРАНИТЬ ДАННЫЕ"), b -> {
            ProfileData updated = new ProfileData(
                    base.uuid(), base.minecraftName(), inputA.getValue().trim(), inputB.getValue().trim(), inputC.getValue().trim(),
                    inputD.getValue().trim(), inputE.getValue().trim(), base.accessRole(), splitSemi(inputF.getValue()), splitSemi(traits.getValue()),
                    base.subtitle(), base.theme(), base.accent(), base.frame(), base.decor(), note.getValue().trim()
            );
            Wire.adminProfileUpdate(updated);
            SoundFx.confirm();
        }).bounds(x, y, 180, 20).build());

        addRenderableWidget(Button.builder(Component.literal("РОЛЬ: " + roleName(Role.safe(p.accessRole()))), b -> {
            Role next = nextRole(Role.safe(base.accessRole()), state.role() == Role.OWNER);
            Wire.adminSetRole(base.uuid(), next.name());
            SoundFx.click();
        }).bounds(x + 188, y, 170, 20).build());

        int playerY = top + 92;
        int playerX = contentX + contentW - 136;
        for (ProfileData player : state.profiles().stream().limit(13).toList()) {
            addRenderableWidget(Button.builder(Component.literal(player.systemName()), b -> {
                selectedProfile = player.uuid();
                rebuildWidgets();
            }).bounds(playerX, playerY, 126, 18).build());
            playerY += 21;
        }

        if (state.role() == Role.OWNER) {
            int roleY = top + panelH - 50;
            int roleX = contentX + 8;
            String[] names = {"ПОЛЬЗОВАТЕЛЬ", "ПОМОЩНИК", "МОДЕРАТОР", "АДМИНИСТРАТОР", "ВЛАДЕЛЕЦ"};
            Role[] roles = {Role.PLAYER, Role.HELPER, Role.MODERATOR, Role.ADMIN, Role.OWNER};
            for (int i = 0; i < roles.length; i++) {
                Role r = roles[i];
                addRenderableWidget(Button.builder(Component.literal(names[i]), b -> {
                    Wire.adminSetRole(base.uuid(), r.name()); SoundFx.confirm();
                }).bounds(roleX + i * 112, roleY, 106, 20).build());
            }
        }
    }

    private static Role nextRole(Role current, boolean owner) {
        Role[] allowed = owner
                ? new Role[]{Role.PLAYER, Role.HELPER, Role.MODERATOR, Role.ADMIN, Role.OWNER}
                : new Role[]{Role.PLAYER, Role.HELPER, Role.MODERATOR};
        for (int i = 0; i < allowed.length; i++) if (allowed[i] == current) return allowed[(i + 1) % allowed.length];
        return allowed[0];
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
        int dim = withAccent(theme, 0x4A);
        g.fill(0, 0, width, height, 0xB8000000);
        g.fill(left, top, left + panelW, top + panelH, theme.background());

        if (UiConfig.INSTANCE.showGrid) {
            int spacing = UiConfig.INSTANCE.gridSpacing;
            for (int x = left; x < left + panelW; x += spacing) g.fill(x, top, x + 1, top + panelH, withAccent(theme, 0x0E));
            for (int y = top; y < top + panelH; y += spacing) g.fill(left, y, left + panelW, y + 1, withAccent(theme, 0x0E));
        }

        g.outline(left, top, panelW, panelH, accent);
        g.outline(left + 2, top + 2, panelW - 4, panelH - 4, dim);
        drawFrame(g, left, top, panelW, panelH, accent, currentFrame());
        g.fill(left + 5, top + 45, left + panelW - 5, top + 46, dim);
        g.fill(left + sideW, top + 46, left + sideW + 1, top + panelH - 14, dim);
        g.fill(contentX - 8, top + 55, left + panelW - 10, top + panelH - 12, theme.panel());
        g.outline(contentX - 8, top + 55, contentW + 8, panelH - 67, withAccent(theme, 0x48));
        drawFrame(g, contentX - 8, top + 55, contentW + 8, panelH - 67, withAccent(theme, 0xA0), currentFrame());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Theme theme = theme();
        int accent = withAccent(theme, 0xFF);

        g.text(font, Component.literal("◈ " + UiConfig.INSTANCE.brandText), left + 13, top + 15, accent, false);
        g.text(font, Component.literal("OKG // CORE 0.4"), left + 13, top + 29, theme.muted(), false);
        g.text(font, Component.literal("СОСТОЯНИЕ: " + (state.connected() ? "ONLINE" : "OFFLINE")), left + panelW - 150, top + 15,
                state.connected() ? 0xFF67E69B : theme.danger(), false);
        g.text(font, Component.literal(UiConfig.INSTANCE.rgbCycle ? "RGB LINK ACTIVE" : "STATIC LINK"), left + panelW - 150, top + 29,
                UiConfig.INSTANCE.rgbCycle ? accent : theme.muted(), false);
        g.text(font, Component.literal("ДОСТУП: " + roleName(state.role())), left + 13, top + panelH - 22, theme.muted(), false);
        drawMascot(g, left + sideW - 42, top + 12, currentDecor(), accent, theme.text());

        switch (tab) {
            case PROFILE -> renderProfile(g, theme);
            case PLAYERS -> renderPlayers(g, theme);
            case CHAT -> renderChat(g, theme, false);
            case SYSTEM_CHAT -> renderChat(g, theme, true);
            case DM -> renderDm(g, theme);
            case MARKERS -> renderMarkers(g, theme);
            case SUPPORT -> renderSupport(g, theme, false);
            case COSMETICS -> renderCosmetics(g, theme);
            case ADMIN -> renderAdmin(g, theme);
            case SUPPORT_CENTER -> renderSupport(g, theme, true);
        }

        String notice = state.notice();
        if (!notice.isBlank()) {
            int nw = Math.min(contentW - 20, Math.max(180, font.width(notice) + 18));
            int nx = contentX + (contentW - nw) / 2;
            int ny = top + panelH - 31;
            g.fill(nx, ny, nx + nw, ny + 16, 0xE51B1115);
            g.outline(nx, ny, nw, 16, theme.danger());
            g.text(font, Component.literal(notice), nx + 8, ny + 4, 0xFFFFD9DD, false);
        }

        // ВАЖНО: виджеты и PlayerFaceWidget рисуются ПОСЛЕ цветных панелей.
        // Поэтому тема/RGB больше не тонируют аватар и не прячут кнопки.
        super.extractRenderState(g, mouseX, mouseY, delta);

        // Тонкая неоновая окантовка поверх ванильных кнопок: они остаются
        // совместимыми с Minecraft, но визуально вписываются в Систему.
        for (var child : children()) {
            if (child instanceof Button b) {
                g.outline(b.getX(), b.getY(), b.getWidth(), b.getHeight(), withAccent(theme, 0xA8));
            }
        }
    }

    private void renderProfile(GuiGraphicsExtractor g, Theme theme) {
        ProfileData p = selectedProfile();
        if (p == null) { title(g, "ПРОФИЛЬ НЕДОСТУПЕН", theme); return; }
        title(g, "ПРОФИЛЬ // " + p.systemName(), theme);
        int accent = withAccent(theme, 0xFF);
        int cardX = contentX + 9, cardY = top + 77, cardW = contentW - 18;
        neonPanel(g, cardX, cardY, cardW, 130, theme);
        g.outline(contentX + 17, top + 83, 88, 88, withAccent(theme, 0x70));
        drawFrame(g, contentX + 17, top + 83, 88, 88, accent, currentFrame());
        g.text(font, Component.literal("ID // " + p.uuid().toString().substring(0, 8).toUpperCase(Locale.ROOT)), contentX + 20, top + 179, theme.muted(), false);

        int x = contentX + 124, y = top + 88;
        dataLine(g, x, y, "НИКНЕЙМ", p.systemName(), theme); y += 19;
        dataLine(g, x, y, "РАСА", p.race(), theme); y += 19;
        dataLine(g, x, y, "ПРОИСХОЖДЕНИЕ", p.origin(), theme); y += 19;
        dataLine(g, x, y, "ФРАКЦИЯ", p.faction(), theme); y += 19;
        dataLine(g, x, y, "ДОСТУП", roleName(Role.safe(p.accessRole())), theme); y += 19;
        g.text(font, Component.literal("СТАТУС"), x, y, theme.muted(), false);
        g.text(font, Component.literal("[ " + safeText(p.status()) + " ]"), x + 112, y, statusColor(p.status(), accent), false);

        int blocksY = top + 222, gap = 10, half = (cardW - gap) / 2;
        neonPanel(g, cardX, blocksY, half, 148, theme);
        neonPanel(g, cardX + half + gap, blocksY, cardW - half - gap, 148, theme);
        g.text(font, Component.literal("◆ СПОСОБНОСТИ"), cardX + 12, blocksY + 10, accent, false);
        int yy = blocksY + 31;
        for (String ability : p.abilities().stream().limit(8).toList()) {
            g.text(font, Component.literal("◈ " + ability), cardX + 14, yy, theme.text(), false); yy += 14;
        }
        if (p.abilities().isEmpty()) g.text(font, Component.literal("[ ДАННЫЕ НЕ ОБНАРУЖЕНЫ ]"), cardX + 14, yy, theme.muted(), false);

        int tx = cardX + half + gap + 12;
        g.text(font, Component.literal("◆ ОСОБЕННОСТИ"), tx, blocksY + 10, accent, false);
        yy = blocksY + 31;
        for (String trait : p.traits().stream().limit(8).toList()) {
            g.text(font, Component.literal("[ " + trait + " ]"), tx + 2, yy, theme.text(), false); yy += 14;
        }
        if (p.traits().isEmpty()) g.text(font, Component.literal("[ ПУСТО ]"), tx + 2, yy, theme.muted(), false);

        if (!p.subtitle().isBlank()) g.text(font, Component.literal("“" + p.subtitle() + "”"), cardX + 12, top + panelH - 48, theme.muted(), false);
        if (state.role().canAdmin() && !p.adminNote().isBlank())
            g.text(font, Component.literal("ADMIN NOTE // " + p.adminNote()), cardX + 12, top + panelH - 64, 0xFFFFB067, false);
    }

    private void renderPlayers(GuiGraphicsExtractor g, Theme theme) {
        title(g, "ПРОФИЛИ // БАЗА ПОЛЬЗОВАТЕЛЕЙ", theme);
        g.text(font, Component.literal("Выберите профиль для просмотра."), contentX + 10, top + 67, theme.muted(), false);
        if (state.profiles().isEmpty()) g.text(font, Component.literal("[ НЕТ ПРОФИЛЕЙ ]"), contentX + 10, top + 92, theme.muted(), false);
    }

    private void renderChat(GuiGraphicsExtractor g, Theme theme, boolean systemChannel) {
        title(g, systemChannel ? "СИСТЕМНЫЙ КАНАЛ // READ ALL" : "ОБЩИЙ ЧАТ // PUBLIC", theme);
        int boxX = contentX + 9, boxY = top + 77, boxW = contentW - 18, boxH = panelH - 150;
        neonPanel(g, boxX, boxY, boxW, boxH, theme);
        if (systemChannel && !state.role().canAdmin())
            g.text(font, Component.literal("Писать могут только администраторы. Читать могут все."), boxX + 10, boxY + 8, theme.muted(), false);
        List<ChatEntry> list = systemChannel ? state.systemChat() : state.chat();
        int start = Math.max(0, list.size() - 13);
        int y = boxY + (systemChannel && !state.role().canAdmin() ? 27 : 10);
        for (int i = start; i < list.size(); i++) {
            ChatEntry e = list.get(i);
            String prefix = systemChannel ? "[SYSTEM] " + e.sender() + " // " : e.sender() + " // ";
            drawWrapped(g, prefix + e.message(), boxX + 10, y, boxW - (state.role().canAdmin() ? 46 : 20),
                    systemChannel ? withAccent(theme, 0xFF) : (e.system() ? withAccent(theme, 0xFF) : theme.text()), 2);
            y += 23;
        }
        if (list.isEmpty()) g.text(font, Component.literal("[ СООБЩЕНИЙ НЕТ ]"), boxX + 10, boxY + 30, theme.muted(), false);
    }

    private void renderDm(GuiGraphicsExtractor g, Theme theme) {
        title(g, "ЛИЧНЫЕ СООБЩЕНИЯ // PRIVATE", theme);
        ProfileData self = state.self();
        if (self == null) return;
        if (state.role() == Role.OWNER) {
            g.text(font, Component.literal("РЕЖИМ ВЛАДЕЛЬЦА: только просмотр, отправка отключена."), contentX + 10, top + 68, 0xFFFFB56B, false);
            int y = top + 88;
            List<DirectMessage> list = state.directMessages();
            int start = Math.max(0, list.size() - 17);
            for (int i = start; i < list.size(); i++) {
                DirectMessage m = list.get(i);
                drawWrapped(g, m.senderName() + " → " + m.recipientName() + " // " + m.text(), contentX + 12, y, contentW - 24, theme.text(), 2);
                y += 20;
            }
            if (list.isEmpty()) g.text(font, Component.literal("[ ЛИЧНЫХ СООБЩЕНИЙ НЕТ ]"), contentX + 12, y, theme.muted(), false);
            return;
        }

        g.text(font, Component.literal("Выберите игрока слева."), contentX + 10, top + 68, theme.muted(), false);
        if (selectedDmPeer == null) return;
        ProfileData peer = state.profile(selectedDmPeer).orElse(null);
        if (peer == null) return;
        int x = contentX + 170, y = top + 88;
        g.text(font, Component.literal("ДИАЛОГ // " + peer.systemName()), x, top + 68, withAccent(theme, 0xFF), false);
        List<DirectMessage> conversation = state.directMessages().stream().filter(m ->
                (m.sender().equals(self.uuid()) && m.recipient().equals(peer.uuid()))
                        || (m.sender().equals(peer.uuid()) && m.recipient().equals(self.uuid()))).toList();
        int start = Math.max(0, conversation.size() - 17);
        for (int i = start; i < conversation.size(); i++) {
            DirectMessage m = conversation.get(i);
            int color = m.sender().equals(self.uuid()) ? withAccent(theme, 0xFF) : theme.text();
            drawWrapped(g, m.senderName() + " // " + m.text(), x, y, contentW - 190, color, 2);
            y += 20;
        }
    }

    private void renderMarkers(GuiGraphicsExtractor g, Theme theme) {
        title(g, "МЕТКИ // ОБЩАЯ КАРТА", theme);
        List<MarkerData> list = state.markers();
        int y = top + 86;
        int start = Math.max(0, list.size() - 10);
        for (int i = start; i < list.size(); i++) {
            MarkerData m = list.get(i);
            g.fill(contentX + 9, y - 5, contentX + contentW - 9, y + 22, theme.panelAlt());
            g.outline(contentX + 9, y - 5, contentW - 18, 27, withAccent(theme, 0x40));
            g.text(font, Component.literal("◆ " + m.title()), contentX + 17, y, withAccent(theme, 0xFF), false);
            g.text(font, Component.literal(m.dimension() + " // " + m.x() + " / " + m.y() + " / " + m.z() + " // " + m.ownerName()),
                    contentX + 28, y + 12, theme.muted(), false);
            y += 31;
        }
        if (list.isEmpty()) g.text(font, Component.literal("[ МЕТОК НЕТ ]"), contentX + 12, y, theme.muted(), false);
    }

    private void renderCosmetics(GuiGraphicsExtractor g, Theme theme) {
        title(g, "ОФОРМЛЕНИЕ // НЕЗАВИСИМЫЕ СЛОИ", theme);
        neonPanel(g, contentX + 9, top + 77, contentW - 18, 245, theme);
        g.text(font, Component.literal("ТЕМА — цветовая палитра интерфейса"), contentX + 20, top + 82, theme.muted(), false);
        g.text(font, Component.literal("РАМКА — системная / лиса / шалкер / снеговик / минимал"), contentX + 20, top + 145, theme.muted(), false);
        g.text(font, Component.literal("ДЕКОР — отдельная пиксельная иконка, не связана с рамкой"), contentX + 20, top + 185, theme.muted(), false);
        g.text(font, Component.literal("АКЦЕНТ HEX"), contentX + 20, top + 210, theme.muted(), false);
        g.text(font, Component.literal("RGB меняет только рамки, линии, заголовки и декор. Аватар игрока не тонируется."), contentX + 20, top + 252, theme.text(), false);
        drawMascot(g, contentX + contentW - 95, top + 142, currentDecor(), withAccent(theme, 0xFF), theme.text());
    }

    private void renderSupport(GuiGraphicsExtractor g, Theme theme, boolean staff) {
        title(g, staff ? "ЦЕНТР ПОДДЕРЖКИ // STAFF" : "ПОДДЕРЖКА // PRIVATE CHANNEL", theme);
        TicketData t = findTicket(selectedTicket, staff);
        if (t == null) {
            if (staff) g.text(font, Component.literal("[ НЕТ ДОСТУПНЫХ ОБРАЩЕНИЙ ]"), contentX + 10, top + 90, theme.muted(), false);
            else if (Objects.equals(selectedTicket, -1)) {
                g.text(font, Component.literal("Новое обращение видите только вы и персонал поддержки."), contentX + contentW / 3 + 25, top + 105, theme.muted(), false);
            }
            return;
        }
        int listW = Math.min(205, contentW / 3);
        int chatX = contentX + listW + 22;
        int chatW = contentW - listW - 32;
        neonPanel(g, chatX - 8, top + 78, chatW + 8, panelH - 155, theme);
        g.text(font, Component.literal("#" + t.id() + " // " + t.category()), chatX, top + 88, withAccent(theme, 0xFF), false);
        g.text(font, Component.literal("СТАТУС: " + t.status() + " // STAFF: " + (t.assignedTo().isBlank() ? "—" : t.assignedTo())), chatX, top + 102, theme.muted(), false);
        int y = top + 126;
        List<TicketMessage> messages = t.messages();
        int start = Math.max(0, messages.size() - 13);
        for (int i = start; i < messages.size(); i++) {
            TicketMessage m = messages.get(i);
            int color = m.internal() ? 0xFFFFB565 : ("PLAYER".equals(m.role()) ? theme.text() : withAccent(theme, 0xFF));
            String prefix = m.internal() ? "[ВНУТРЕННЕ] " : m.sender() + " // ";
            drawWrapped(g, prefix + m.text(), chatX, y, chatW - 12, color, 2);
            y += 20;
        }
    }

    private void renderAdmin(GuiGraphicsExtractor g, Theme theme) {
        title(g, "УПРАВЛЕНИЕ // ПРОФИЛИ И ДОСТУП", theme);
        ProfileData p = selectedProfile();
        if (p == null) return;
        String[] labels = {"НИКНЕЙМ", "РАСА", "ПРОИСХОЖД.", "СТАТУС", "ФРАКЦИЯ", "СПОСОБНОСТИ", "ОСОБЕННОСТИ", "СКРЫТАЯ ЗАМЕТКА"};
        int y = top + 98;
        for (String label : labels) { g.text(font, Component.literal(label), contentX + 8, y, theme.muted(), false); y += 31; }
        g.text(font, Component.literal("TARGET // " + p.minecraftName() + " // ROLE " + roleName(Role.safe(p.accessRole()))),
                contentX + 8, top + 70, withAccent(theme, 0xFF), false);
        if (state.role() == Role.OWNER)
            g.text(font, Component.literal("Быстрый доступ владельца: роли переключаются русскими кнопками внизу."), contentX + 8, top + panelH - 66, theme.muted(), false);
    }

    private void title(GuiGraphicsExtractor g, String value, Theme theme) {
        g.text(font, Component.literal("◈ " + value), contentX, top + 52, withAccent(theme, 0xFF), false);
        g.fill(contentX, top + 66, contentX + Math.min(contentW, 360), top + 67, withAccent(theme, 0x55));
    }

    private void dataLine(GuiGraphicsExtractor g, int x, int y, String label, String value, Theme theme) {
        g.text(font, Component.literal(label), x, y, theme.muted(), false);
        g.text(font, Component.literal(safeText(value)), x + 112, y, theme.text(), false);
        g.fill(x + 108, y + 11, x + Math.min(contentW - 135, 390), y + 12, withAccent(theme, 0x1F));
    }

    private static String safeText(String value) { return value == null || value.isBlank() ? "—" : value; }

    private void neonPanel(GuiGraphicsExtractor g, int x, int y, int w, int h, Theme theme) {
        if (w <= 4 || h <= 4) return;
        g.fill(x, y, x + w, y + h, theme.panelAlt());
        g.outline(x, y, w, h, withAccent(theme, 0x38));
        drawFrame(g, x, y, w, h, withAccent(theme, 0xA8), currentFrame());
    }

    private static void drawFrame(GuiGraphicsExtractor g, int x, int y, int w, int h, int color, String frame) {
        int s = "MINIMAL".equals(frame) ? 5 : 10;
        g.fill(x, y, x + s, y + 2, color); g.fill(x, y, x + 2, y + s, color);
        g.fill(x + w - s, y, x + w, y + 2, color); g.fill(x + w - 2, y, x + w, y + s, color);
        g.fill(x, y + h - 2, x + s, y + h, color); g.fill(x, y + h - s, x + 2, y + h, color);
        g.fill(x + w - s, y + h - 2, x + w, y + h, color); g.fill(x + w - 2, y + h - s, x + w, y + h, color);
        if ("FOX".equals(frame)) {
            g.fill(x + 5, y + 5, x + 9, y + 7, color); g.fill(x + w - 9, y + 5, x + w - 5, y + 7, color);
        } else if ("SHULKER".equals(frame)) {
            g.fill(x + w / 2 - 8, y, x + w / 2 + 8, y + 2, color);
            g.fill(x + w / 2 - 8, y + h - 2, x + w / 2 + 8, y + h, color);
        } else if ("SNOWMAN".equals(frame)) {
            g.fill(x + 6, y + 6, x + 8, y + 8, color); g.fill(x + w - 8, y + h - 8, x + w - 6, y + h - 6, color);
        }
    }

    private static void drawMascot(GuiGraphicsExtractor g, int x, int y, String decor, int accent, int light) {
        switch (decor) {
            case "FOX" -> drawFox(g, x, y, accent, light);
            case "SHULKER" -> drawShulker(g, x, y, accent, light);
            case "SNOWMAN" -> drawSnowman(g, x, y, accent, light);
            default -> {}
        }
    }

    private static void drawFox(GuiGraphicsExtractor g, int x, int y, int c, int light) {
        g.fill(x + 4, y, x + 10, y + 6, c); g.fill(x + 22, y, x + 28, y + 6, c);
        g.fill(x + 2, y + 5, x + 30, y + 22, c); g.fill(x + 7, y + 16, x + 25, y + 27, light);
        g.fill(x + 8, y + 10, x + 11, y + 13, 0xFF111111); g.fill(x + 21, y + 10, x + 24, y + 13, 0xFF111111);
        g.fill(x + 15, y + 20, x + 18, y + 23, 0xFF111111);
    }

    private static void drawShulker(GuiGraphicsExtractor g, int x, int y, int c, int light) {
        g.fill(x + 2, y + 3, x + 30, y + 11, c); g.fill(x, y + 11, x + 32, y + 27, c);
        g.fill(x + 5, y + 14, x + 27, y + 24, light); g.fill(x + 14, y + 17, x + 18, y + 21, 0xFF151018);
    }

    private static void drawSnowman(GuiGraphicsExtractor g, int x, int y, int c, int light) {
        g.fill(x + 9, y, x + 23, y + 13, light); g.fill(x + 5, y + 12, x + 27, y + 31, light);
        g.fill(x + 12, y + 5, x + 15, y + 8, 0xFF111111); g.fill(x + 19, y + 5, x + 22, y + 8, 0xFF111111);
        g.fill(x + 16, y + 9, x + 22, y + 11, c); g.fill(x + 15, y + 18, x + 18, y + 21, c); g.fill(x + 15, y + 24, x + 18, y + 27, c);
    }

    private void drawWrapped(GuiGraphicsExtractor g, String text, int x, int y, int width, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), Math.max(40, width));
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) g.text(font, lines.get(i), x, y + i * 10, color, false);
    }

    private Theme theme() {
        ProfileData self = state.self();
        Theme base = ThemeCatalog.byId(self == null ? "SYSTEM" : self.theme());
        int rgb;
        if (UiConfig.INSTANCE.rgbCycle) rgb = rainbowRgb(UiConfig.INSTANCE.rgbSpeed);
        else if (self != null) {
            try { rgb = Integer.parseInt(safeAccent(self.accent()).substring(1), 16); }
            catch (Exception ignored) { rgb = base.accent() & 0x00FFFFFF; }
        } else rgb = base.accent() & 0x00FFFFFF;
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
        int i = (int)Math.floor(hh); float f = hh - i;
        float p = v * (1.0f - s), q = v * (1.0f - s * f), t = v * (1.0f - s * (1.0f - f));
        float r, gg, b;
        switch (i % 6) {
            case 0 -> { r = v; gg = t; b = p; }
            case 1 -> { r = q; gg = v; b = p; }
            case 2 -> { r = p; gg = v; b = t; }
            case 3 -> { r = p; gg = q; b = v; }
            case 4 -> { r = t; gg = p; b = v; }
            default -> { r = v; gg = p; b = q; }
        }
        int ri = Math.min(255, Math.max(0, Math.round(r * 255)));
        int gi = Math.min(255, Math.max(0, Math.round(gg * 255)));
        int bi = Math.min(255, Math.max(0, Math.round(b * 255)));
        return (ri << 16) | (gi << 8) | bi;
    }

    private String currentFrame() {
        ProfileData self = state.self();
        return self == null || self.frame() == null || self.frame().isBlank() ? "SYSTEM" : self.frame().toUpperCase(Locale.ROOT);
    }

    private String currentDecor() {
        ProfileData self = state.self();
        return self == null || self.decor() == null || self.decor().isBlank() ? "NONE" : self.decor().toUpperCase(Locale.ROOT);
    }

    private static String nextOf(List<String> list, String current) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).equalsIgnoreCase(current)) return list.get((i + 1) % list.size());
        return list.getFirst();
    }

    private static String frameTitle(String id) {
        return switch (id == null ? "SYSTEM" : id.toUpperCase(Locale.ROOT)) {
            case "FOX" -> "ЛИСА"; case "SHULKER" -> "ШАЛКЕР"; case "SNOWMAN" -> "СНЕГОВИК";
            case "MINIMAL" -> "МИНИМАЛ"; default -> "СИСТЕМНАЯ";
        };
    }

    private static String decorTitle(String id) {
        return switch (id == null ? "NONE" : id.toUpperCase(Locale.ROOT)) {
            case "FOX" -> "ЛИСА"; case "SHULKER" -> "ШАЛКЕР"; case "SNOWMAN" -> "СНЕГОВИК"; default -> "НЕТ";
        };
    }

    private static String hex(int argb) { return String.format("#%06X", argb & 0x00FFFFFF); }
    private static int withAccent(Theme theme, int alpha) { return (alpha << 24) | (theme.accent() & 0x00FFFFFF); }

    private static int statusColor(String status, int accent) {
        String s = status == null ? "" : status.toUpperCase(Locale.ROOT);
        if (s.contains("АКТИВ")) return 0xFF67E69B;
        if (s.contains("ПОТЕР") || s.contains("МЕРТВ") || s.contains("НЕАКТИВ")) return 0xFFFF646F;
        return accent;
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

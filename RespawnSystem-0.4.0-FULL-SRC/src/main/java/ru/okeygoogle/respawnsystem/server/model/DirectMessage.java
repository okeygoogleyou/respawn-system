package ru.okeygoogle.respawnsystem.server.model;

import java.util.UUID;

public record DirectMessage(
        long time,
        UUID sender,
        String senderName,
        UUID recipient,
        String recipientName,
        String text
) {}

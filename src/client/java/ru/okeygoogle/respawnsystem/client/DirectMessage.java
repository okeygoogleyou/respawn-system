package ru.okeygoogle.respawnsystem.client;

import java.util.UUID;

public record DirectMessage(
        long time,
        UUID sender,
        String senderName,
        UUID receiver,
        String receiverName,
        String message
) {
}

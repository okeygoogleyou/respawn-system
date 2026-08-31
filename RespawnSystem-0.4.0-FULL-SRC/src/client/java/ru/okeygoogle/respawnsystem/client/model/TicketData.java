package ru.okeygoogle.respawnsystem.client.model;

import java.util.List;
import java.util.UUID;

public record TicketData(int id, UUID owner, String ownerName, String category, String status, String assignedTo, List<TicketMessage> messages) {}

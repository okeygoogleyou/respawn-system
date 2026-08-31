package ru.okeygoogle.respawnsystem.client.model;

public record TicketMessage(long time, String sender, String role, String text, boolean internal) {}

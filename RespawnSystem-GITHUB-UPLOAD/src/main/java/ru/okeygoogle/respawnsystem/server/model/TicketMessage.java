package ru.okeygoogle.respawnsystem.server.model;

public record TicketMessage(long time, String sender, String role, String text, boolean internal) {}

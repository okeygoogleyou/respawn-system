package ru.okeygoogle.respawnsystem.server.model;

public record ChatEntry(long time, String sender, String message, boolean system) {}

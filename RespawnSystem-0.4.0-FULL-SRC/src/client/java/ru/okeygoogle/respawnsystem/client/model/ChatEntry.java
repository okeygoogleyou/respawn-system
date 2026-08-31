package ru.okeygoogle.respawnsystem.client.model;

public record ChatEntry(long time, String sender, String message, boolean system) {}

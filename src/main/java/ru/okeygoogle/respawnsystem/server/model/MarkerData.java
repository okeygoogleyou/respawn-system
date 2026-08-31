package ru.okeygoogle.respawnsystem.server.model;

import java.util.UUID;

public record MarkerData(int id, UUID owner, String ownerName, String title, String type, String dimension, int x, int y, int z) {}

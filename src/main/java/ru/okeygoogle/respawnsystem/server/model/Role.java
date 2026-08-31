package ru.okeygoogle.respawnsystem.server.model;

public enum Role { PLAYER, HELPER, MODERATOR, ADMIN, OWNER; public boolean canSupport(){return this==MODERATOR||this==ADMIN||this==OWNER;} public boolean canAdmin(){return this==ADMIN||this==OWNER;} public int rank(){return ordinal();} public static Role safe(String value){try{return Role.valueOf(value==null?"PLAYER":value.trim().toUpperCase());}catch(Exception ignored){return PLAYER;}}}

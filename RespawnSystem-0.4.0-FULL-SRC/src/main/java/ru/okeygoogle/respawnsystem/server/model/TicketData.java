package ru.okeygoogle.respawnsystem.server.model;

import java.util.*;
public final class TicketData { public final int id; public final UUID owner; public String ownerName; public String category; public String status="ОТКРЫТО"; public String assignedTo=""; public final List<TicketMessage> messages=new ArrayList<>(); public TicketData(int id,UUID owner,String ownerName,String category){this.id=id;this.owner=owner;this.ownerName=ownerName;this.category=category;} public TicketData visibleCopy(boolean staff){TicketData copy=new TicketData(id,owner,ownerName,category);copy.status=status;copy.assignedTo=assignedTo;for(TicketMessage m:messages)if(staff||!m.internal())copy.messages.add(m);return copy;} }

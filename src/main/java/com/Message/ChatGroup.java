package com.Message;

import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

public class ChatGroup {
    public String groupName;
    public UUID owner;
    public Set<UUID> admins = new HashSet<>();
    public Set<UUID> members = new HashSet<>();

    public ChatGroup(String groupName, UUID owner) {
        this.groupName = groupName;
        this.owner = owner;
        this.members.add(owner);
    }

    public static ChatGroup get(String groupNameSetAdmin) {
        return null;
    }
}
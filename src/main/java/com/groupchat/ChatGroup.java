package com.groupchat;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ChatGroup {
    public String name;                 // 群名称（可变）
    public String groupName;
    public UUID owner;// 群主
    public Set<UUID> admins = new HashSet<>();
    public Set<UUID> members = new HashSet<>();
    public List<String> history = new ArrayList<>(); // 必须有这一行
    public String announcement; // 新增群公告字段
    public Map<UUID, GroupChat.GroupMuteInfo> mutedMembers; // 新增群聊禁言信息

    public Set<UUID> blacklist = new HashSet<>();

    public ChatGroup(String groupName, UUID owner) {
        this.groupName = groupName;
        this.owner = owner;
        this.members.add(owner);
        this.announcement = ""; // 默认空公告
        this.mutedMembers = new HashMap<>();
    }

    public static ChatGroup get(String groupNameSetAdmin) {
        return null;
    }

    public Object getOwner() {
        return null;
    }

    public boolean getMembers() {
        return false;
    }

    public void setOwner(@NotNull UUID uniqueId) {
    }
}
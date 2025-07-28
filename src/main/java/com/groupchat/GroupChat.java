package com.groupchat;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.Arrays;

public final class GroupChat extends
        JavaPlugin implements Listener {
    private final Map<UUID, MuteInfo> mutedPlayers = new HashMap<>();
    private final Map<String, ChatGroup> chatGroups = new HashMap<>();
    private final Map<UUID, String> playerGroup = new HashMap<>();
    private int groupHistoryLimit = 30; // 默认最多保存30条历史消息
    private final Set<UUID> groupChatMode = new HashSet<>();
    private final Set<UUID> deathReminded = new HashSet<>();
    Map<UUID, Boolean> notifyEnabled = new HashMap<>();
    Map<UUID, Long> lastNotifyTime = new HashMap<>();
    public long notifyCooldownMillis = 10000; // 默认10秒
    public void reloadNotifyConfig() {
        // 加载冷却时间
        notifyCooldownMillis = getConfig().getInt("notify-cooldown-seconds", 10) * 1000L;

        // 加载提醒开关状态
        ConfigurationSection section = getConfig().getConfigurationSection("notify-toggle");
        notifyEnabled.clear();
        if (section != null) {
            for (String uuidStr : section.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    boolean enabled = section.getBoolean(uuidStr, true);
                    notifyEnabled.put(uuid, enabled);
                } catch (IllegalArgumentException e) {
                    getLogger().warning("配置中的UUID无效: " + uuidStr);
                }
            }
        }
    }
    public void saveNotifyConfig() {
        ConfigurationSection section = getConfig().createSection("notify-toggle");
        for (Map.Entry<UUID, Boolean> entry : notifyEnabled.entrySet()) {
            section.set(entry.getKey().toString(), entry.getValue());
        }
        getConfig().set("notify-toggle", section);
        saveConfig();
    }


    public static class GroupMuteInfo {
        public long unmuteTime;
        public String reason;
        public UUID mutedBy;

        public GroupMuteInfo(long unmuteTime, String reason, UUID mutedBy) {
            this.unmuteTime = unmuteTime;
            this.reason = reason;
            this.mutedBy = mutedBy;
        }
    }

    public static class MuteInfo {
        public long unmuteTime;
        public String reason;

        public MuteInfo(long unmuteTime, String reason) {
            this.unmuteTime = unmuteTime;
            this.reason = reason;
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
                // 群聊聊天模式
                if (groupChatMode.contains(uuid)) {
                    String groupName = playerGroup.get(uuid);
                    if (groupName == null) {
                        player.sendMessage("§c你未加入任何群聊，自动退出群聊聊天模式。");
                        groupChatMode.remove(uuid);
                        return;
                    }
                    ChatGroup chatGroup = chatGroups.get(groupName);
                    if (chatGroup == null) {
                        player.sendMessage("§c群聊不存在，自动退出群聊聊天模式。");
                        groupChatMode.remove(uuid);
                        return;
                    }
                    // 检查群聊禁言状态
                    if (chatGroup.mutedMembers.containsKey(uuid)) {
                        GroupMuteInfo muteInfo = chatGroup.mutedMembers.get(uuid);
                        if (System.currentTimeMillis() < muteInfo.unmuteTime) {
                            long left = (muteInfo.unmuteTime - System.currentTimeMillis()) / 1000;
                            long min = left / 60;
                            long sec = left % 60;
                            String muterName = Bukkit.getOfflinePlayer(muteInfo.mutedBy).getName();
                            player.sendMessage("§c你在此群聊中已被 " + muterName + " 禁言，剩余时间：" + min + "分" + sec + "秒，原因：" + muteInfo.reason);
                            event.setCancelled(true);
                            return;
                        } else {
                            chatGroup.mutedMembers.remove(uuid); // 自动解禁
                        }
                    }
                    String msg = event.getMessage();
                    String historyMsg = "§6[群聊 " + groupName + "]§e" + player.getName() + "§f: " + msg;
                    chatGroup.history.add(historyMsg);
                    while (chatGroup.history.size() > groupHistoryLimit) {
                        chatGroup.history.remove(0);
                    }
                    for (UUID member : chatGroup.members) {
                        Player p = Bukkit.getPlayer(member);
                        if (p != null && p.isOnline()) {
                            p.sendMessage(historyMsg);
                        }
                    }
                    event.setCancelled(true); // 不在公屏显示
                }
            }


    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        int seconds = getConfig().getInt("notify-cooldown-seconds", 10); // 默认10秒
        saveDefaultConfig();
    }

    @Override
    public void onDisable() {
    }


    private static String getJsonValue(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return "";
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return "";
        return json.substring(start, end);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.setJoinMessage(null);
        String currentGroup = playerGroup.get(player.getUniqueId());
        ChatGroup group = null;
        deathReminded.remove(player.getUniqueId());
        if (currentGroup != null) {
            group = chatGroups.get(currentGroup);
        }
        if (group != null && group.history != null && !group.history.isEmpty()) {
            int start = Math.max(0, group.history.size() - groupHistoryLimit);
            player.sendMessage("§a========== 群聊历史记录 ==========");
            for (int i = start; i < group.history.size(); i++) {
                player.sendMessage(group.history.get(i));
            }
            player.sendMessage("§a=================================");
        }
        Set<UUID> notified = new HashSet<>();
        UUID playerId = player.getUniqueId();
        for (Map.Entry<String, ChatGroup> entry : chatGroups.entrySet()) {
            group = entry.getValue();
            if (group.members.contains(playerId)) {
                for (UUID memberId : group.members) {
                    if (!memberId.equals(playerId)) {
                        Player member = Bukkit.getPlayer(memberId);
                        if (member != null && member.isOnline()) {
                            member.sendMessage("§6[群聊] §e" + player.getName() + "§a上线啦！");//提示群聊里的玩家上线提醒
                            notified.add(memberId);
                        }
                    }
                }
            }
        }

        // 屏蔽原版加入提示
        event.setJoinMessage(null);

        // 给没收到群聊提醒的玩家单独发原版提示
        String joinMsg = "§e" + player.getDisplayName() + " joined the server";
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!notified.contains(p.getUniqueId()) && !p.getUniqueId().equals(playerId)) {
                p.sendMessage(joinMsg);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "creategroup":
                if (args.length < 1) {
                    sender.sendMessage("§c用法: /creategroup <群名>");
                    return true;
                }
                if (!(sender instanceof Player creator)) {
                    sender.sendMessage("§c只有玩家可以创建群聊！");
                    return true;
                }
                // 统计该玩家已创建的群聊数量
                long createdCount = chatGroups.values().stream()
                        .filter(g -> g.owner.equals(creator.getUniqueId()))
                        .count();
                if (createdCount >= 10) {
                    sender.sendMessage("§c你最多只能创建10个群聊！");
                    return true;
                }
                String groupName = args[0];
                if (chatGroups.containsKey(groupName)) {
                    sender.sendMessage("§c群聊已存在！");
                    return true;
                }
                ChatGroup group = new ChatGroup(groupName, creator.getUniqueId());
                chatGroups.put(groupName, group);
                playerGroup.put(creator.getUniqueId(), groupName);
                sender.sendMessage("§a群聊 " + groupName + " 创建成功并已加入！你是群主。");
                break;
            case "renamegroup":
                if (args.length < 2) {
                    sender.sendMessage("§c用法: /renamegroup <现群名> <新群名>");
                    return true;
                }
                if (!(sender instanceof Player playerRename)) {
                    sender.sendMessage("§c只有玩家可以操作！");
                    return true;
                }
                String currentGroupName = playerGroup.get(playerRename.getUniqueId());
                if (currentGroupName == null) {
                    sender.sendMessage("§c你未加入任何群聊！");
                    return true;
                }
                ChatGroup toRename = chatGroups.get(currentGroupName);
                if (toRename == null) {
                    sender.sendMessage("§c群聊不存在！");
                    return true;
                }
                if (!toRename.owner.equals(playerRename.getUniqueId())) {
                    sender.sendMessage("§c只有群主可以修改群名称！");
                    return true;
                }
                String newGroupName = args[0];
                if (chatGroups.containsKey(newGroupName)) {
                    sender.sendMessage("§c新群名已存在！");
                    return true;
                }
                // 先从 Map 里移除旧key
                chatGroups.remove(currentGroupName);
                // 改名
                toRename.name = newGroupName;
                // 放入新key
                chatGroups.put(newGroupName, toRename);
                // 所有成员的 playerGroup 映射更新
                for (UUID m : toRename.members) {
                    if (playerGroup.get(m) != null && playerGroup.get(m).equals(currentGroupName)) {
                        playerGroup.put(m, newGroupName);
                    }
                }
                sender.sendMessage("§a群名称已修改为: " + newGroupName);
                break;
            case "invitegroup":
                if (args.length < 2) {
                    sender.sendMessage("§c用法: /invitegroup <群名> <玩家名>");
                    return true;
                }
                if (!(sender instanceof Player inviter)) {
                    sender.sendMessage("§c只有玩家可以邀请！");
                    return true;
                }
                String inviteGroup = args[0];
                Player invitee = Bukkit.getPlayer(args[1]);
                if (invitee == null) {
                    sender.sendMessage("§c未找到该玩家！");
                    return true;
                }
                ChatGroup groupToInvite = chatGroups.get(inviteGroup);
                if (groupToInvite == null) {
                    sender.sendMessage("§c群聊不存在！");
                    return true;
                }
                if (!groupToInvite.owner.equals(inviter.getUniqueId()) && !groupToInvite.admins.contains(inviter.getUniqueId())) {
                    sender.sendMessage("§c只有群主或管理员才能邀请成员！");
                    return true;
                }
                if (groupToInvite.members.contains(invitee.getUniqueId())) {
                    sender.sendMessage("§c该玩家已在群聊中！");
                    return true;
                }
                groupToInvite.members.add(invitee.getUniqueId());
                invitee.sendMessage("§a你已被邀请加入群聊 " + inviteGroup + "，输入 /joingroup " + inviteGroup + " 加入。");
                sender.sendMessage("§a已邀请 " + invitee.getName() + " 加入群聊 " + inviteGroup + "。");
                break;
            case "joingroup":
                Bukkit.getLogger().info("playerGroup: " + playerGroup);
                if (args.length < 1) {
                    sender.sendMessage("§c用法: /joingroup <群名>");
                    return true;
                }
                if (!(sender instanceof Player joiner)) {
                    sender.sendMessage("§c只有玩家可以加入群聊！");
                    return true;
                }
                String joinGroup = args[0];
                ChatGroup groupToJoin = chatGroups.get(joinGroup);
                if (groupToJoin == null) {
                    sender.sendMessage("§c群聊不存在！");
                    return true;
                }
                if (!groupToJoin.members.contains(joiner.getUniqueId())) {
                    sender.sendMessage("§c你没有被邀请加入该群聊！");
                    return true;
                }
                playerGroup.put(joiner.getUniqueId(), joinGroup);
                sender.sendMessage("§a你已加入群聊 " + joinGroup + "。");

                // 显示群公告
                if (groupToJoin.announcement != null && !groupToJoin.announcement.isEmpty()) {
                    sender.sendMessage("§e[群聊公告] §f" + groupToJoin.announcement);
                }
                group = chatGroups.get(joinGroup);
                if (group.blacklist.contains(joiner.getUniqueId())) {
                    joiner.sendMessage("§c你已被此群聊加入黑名单，无法加入！");
                    return true;
                }
                break;
            case "groupban":
                if (!(sender instanceof Player banner)) {
                    sender.sendMessage("§c只有玩家可以执行该操作！");
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage("§c用法: /groupban <群名> <玩家名>");
                    return true;
                }

                String banGroupName = args[0];
                String banTargetName = args[1];
                ChatGroup banGroup = chatGroups.get(banGroupName);
                if (banGroup == null) {
                    sender.sendMessage("§c群聊 " + banGroupName + " 不存在！");
                    return true;
                }

                // 权限检查
                if (!banGroup.owner.equals(banner.getUniqueId()) && !banGroup.admins.contains(banner.getUniqueId())) {
                    sender.sendMessage("§c你不是该群的群主或管理员，无法操作！");
                    return true;
                }

                OfflinePlayer target = Bukkit.getOfflinePlayer(banTargetName);
                if (!target.hasPlayedBefore() && !target.isOnline()) {
                    sender.sendMessage("§c目标玩家不存在！");
                    return true;
                }

                if (target.getUniqueId().equals(banner.getUniqueId())) {
                    sender.sendMessage("§c你不能将自己加入黑名单！");
                    return true;
                }

                banGroup.blacklist.add(target.getUniqueId());
                banGroup.members.remove(target.getUniqueId()); // 如果已在群中，移除

                sender.sendMessage("§a玩家 §e" + banTargetName + " §a已加入群聊黑名单！");
                return true;
            case "groupunban":
                if (!(sender instanceof Player unbanner)) {
                    sender.sendMessage("§c只有玩家可以执行该操作！");
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage("§c用法: /groupunban <群名> <玩家名>");
                    return true;
                }

                String unbanGroupName = args[0];
                String unbanTargetName = args[1];
                ChatGroup unbanGroup = chatGroups.get(unbanGroupName);
                if (unbanGroup == null) {
                    sender.sendMessage("§c群聊 " + unbanGroupName + " 不存在！");
                    return true;
                }

                if (!unbanGroup.owner.equals(unbanner.getUniqueId()) && !unbanGroup.admins.contains(unbanner.getUniqueId())) {
                    sender.sendMessage("§c你不是该群的群主或管理员，无法操作！");
                    return true;
                }

                OfflinePlayer unbanTarget = Bukkit.getOfflinePlayer(unbanTargetName);
                if (!unbanGroup.blacklist.contains(unbanTarget.getUniqueId())) {
                    sender.sendMessage("§e该玩家不在黑名单中！");
                    return true;
                }

                unbanGroup.blacklist.remove(unbanTarget.getUniqueId());
                sender.sendMessage("§a玩家 §e" + unbanTargetName + " §a已移出群聊黑名单！");
                return true;

            case "leavegroup":
                if (!(sender instanceof Player leaver)) {
                    sender.sendMessage("§c只有玩家可以退出群聊！");
                    return true;
                }
                String groupNameLeave = playerGroup.get(leaver.getUniqueId());
                if (groupNameLeave == null) {
                    sender.sendMessage("§c你未加入任何群聊！");
                    return true;
                }

                ChatGroup groupLeave = chatGroups.get(groupNameLeave);
                if (groupLeave == null) {
                    sender.sendMessage("§c群聊不存在！");
                    return true;
                }

                // 是群主，必须转让后才能退出
                if (groupLeave.owner.equals(leaver.getUniqueId())) {
                    if (groupLeave.members.size() <= 1) {
                        // 只剩群主一个人，允许解散
                        chatGroups.remove(groupNameLeave);
                        playerGroup.remove(leaver.getUniqueId());
                        sender.sendMessage("§a你已退出并解散群聊 " + groupNameLeave + "（无其他成员）");
                        return true;
                    }

                    sender.sendMessage("§c你是群主，退出前必须将群主转让给其他成员。可用以下命令转让群主：");
                    for (UUID memberId : groupLeave.members) {
                        if (!memberId.equals(leaver.getUniqueId())) {
                            OfflinePlayer member = Bukkit.getOfflinePlayer(memberId);
                            sender.sendMessage("§7- /transfergroup " + groupNameLeave + " " + member.getName());
                        }
                    }
                    return true;
                }
                // 普通成员退出
                groupLeave.members.remove(leaver.getUniqueId());
                playerGroup.remove(leaver.getUniqueId());
                sender.sendMessage("§a你已退出群聊 " + groupNameLeave + "。");
                break;
            case "transfergroup":
                if (args.length < 2) {
                    sender.sendMessage("§c用法: /transfergroup <群名> <新群主玩家名>");
                    return true;
                }
                if (!(sender instanceof Player ownerTransfer)) {
                    sender.sendMessage("§c只有玩家可以执行该命令！");
                    return true;
                }

                String transGroupName = args[0];
                Player newOwnerPlayer = Bukkit.getPlayer(args[1]);

                if (newOwnerPlayer == null) {
                    sender.sendMessage("§c无法找到玩家 " + args[1]);
                    return true;
                }

                ChatGroup transGroup = chatGroups.get(transGroupName);
                if (transGroup == null) {
                    sender.sendMessage("§c群聊不存在！");
                    return true;
                }

                if (!transGroup.owner.equals(ownerTransfer.getUniqueId())) {
                    sender.sendMessage("§c只有群主才能转让群聊！");
                    return true;
                }

                if (!transGroup.members.contains(newOwnerPlayer.getUniqueId())) {
                    sender.sendMessage("§c新群主必须是群聊成员！");
                    return true;
                }

                transGroup.owner = newOwnerPlayer.getUniqueId();
                sender.sendMessage("§a你已将群聊 " + transGroupName + " 的群主身份转让给 " + newOwnerPlayer.getName());
                newOwnerPlayer.sendMessage("§e你已成为群聊 " + transGroupName + " 的新群主！");
                break;
            case "disbandgroup":
                if (args.length < 1) {
                    sender.sendMessage("§c用法: /disbandgroup <群名>");
                    return true;
                }

                if (!(sender instanceof Player disbander)) {
                    sender.sendMessage("§c只有玩家可以执行该命令！");
                    return true;
                }

                String disbandGroupName = args[0];
                ChatGroup groupToDisband = chatGroups.get(disbandGroupName);

                if (groupToDisband == null) {
                    sender.sendMessage("§c群聊不存在！");
                    return true;
                }

                if (!groupToDisband.owner.equals(disbander.getUniqueId())) {
                    sender.sendMessage("§c只有群主才能解散群聊！");
                    return true;
                }

                // 通知所有成员
                for (UUID memberId : groupToDisband.members) {
                    Player member = Bukkit.getPlayer(memberId);
                    if (member != null && member.isOnline()) {
                        member.sendMessage("§c群聊 " + disbandGroupName + " 已被群主 " + disbander.getName() + " 解散。");
                    }
                    playerGroup.remove(memberId);
                }

                chatGroups.remove(disbandGroupName);
                sender.sendMessage("§a你已成功解散群聊 " + disbandGroupName + "。");
                break;
            case "kickgroup":
                if (args.length < 2) {
                    sender.sendMessage("§c用法: /kickgroup <群名> <玩家名>");
                    return true;
                }
                if (!(sender instanceof Player kicker)) {
                    sender.sendMessage("§c只有玩家可以踢人！");
                    return true;
                }
                String kickGroup = args[0];
                Player toKick = Bukkit.getPlayer(args[1]);
                if (toKick == null) {
                    sender.sendMessage("§c未找到该玩家！");
                    return true;
                }
                ChatGroup groupToKick = chatGroups.get(kickGroup);
                if (groupToKick == null) {
                    sender.sendMessage("§c群聊不存在！");
                    return true;
                }
                boolean isOwner = groupToKick.owner.equals(kicker.getUniqueId());
                boolean isAdmin = groupToKick.admins.contains(kicker.getUniqueId());
                if (!isOwner && !isAdmin) {
                    sender.sendMessage("§c只有群主或管理员才能踢人！");
                    return true;
                }
                if (!groupToKick.members.contains(toKick.getUniqueId())) {
                    sender.sendMessage("§c该玩家不在群聊中！");
                    return true;
                }
                // 管理员不能踢群主
                if (isAdmin && !isOwner && groupToKick.owner.equals(toKick.getUniqueId())) {
                    sender.sendMessage("§c管理员不能踢群主！");
                    return true;
                }
                groupToKick.members.remove(toKick.getUniqueId());
                playerGroup.remove(toKick.getUniqueId());
                toKick.sendMessage("§c你已被踢出群聊 " + kickGroup + "。");
                sender.sendMessage("§a已将 " + toKick.getName() + " 踢出群聊 " + kickGroup + "。");
                break;
            case "setadmin":
                if (args.length < 2) {
                    sender.sendMessage("§c用法: /setadmin <群名> <玩家名>");
                    return true;
                }
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§c只有玩家可以操作！");
                    return true;
                }
                String groupNameSetAdmin = args[0];
                Player adminTarget = Bukkit.getPlayer(args[1]);
                if (adminTarget == null) {
                    sender.sendMessage("§c未找到该玩家！");
                    return true;
                }
                ChatGroup groupSetAdmin = chatGroups.get(groupNameSetAdmin);
                if (groupSetAdmin == null) {
                    sender.sendMessage("§c群聊不存在！");
                    return true;
                }
                if (!groupSetAdmin.owner.equals(p.getUniqueId())) {
                    sender.sendMessage("§c只有群主可以任命管理员！");
                    return true;
                }
                if (!groupSetAdmin.members.contains(adminTarget.getUniqueId())) {
                    sender.sendMessage("§c该玩家不在群聊中！");
                    return true;
                }
                groupSetAdmin.admins.add(adminTarget.getUniqueId());
                adminTarget.sendMessage("§a你已被任命为群聊 " + groupNameSetAdmin + " 的管理员。");
                sender.sendMessage("§a已任命 " + adminTarget.getName() + " 为管理员。");
                break;
            case "unsetadmin":
                if (args.length < 2) {
                    sender.sendMessage("§c用法: /unsetadmin <群名> <玩家名>");
                    return true;
                }
                if (!(sender instanceof Player p2)) {
                    sender.sendMessage("§c只有玩家可以操作！");
                    return true;
                }
                String groupNameUnsetAdmin = args[0];
                Player adminTarget2 = Bukkit.getPlayer(args[1]);
                if (adminTarget2 == null) {
                    sender.sendMessage("§c未找到该玩家！");
                    return true;
                }
                ChatGroup groupUnsetAdmin = chatGroups.get(groupNameUnsetAdmin);
                if (groupUnsetAdmin == null) {
                    sender.sendMessage("§c群聊不存在！");
                    return true;
                }
                if (!groupUnsetAdmin.owner.equals(p2.getUniqueId())) {
                    sender.sendMessage("§c只有群主可以撤销管理员！");
                    return true;
                }
                if (!groupUnsetAdmin.admins.contains(adminTarget2.getUniqueId())) {
                    sender.sendMessage("§c该玩家不是管理员！");
                    return true;
                }
                groupUnsetAdmin.admins.remove(adminTarget2.getUniqueId());
                adminTarget2.sendMessage("§c你已被撤销群聊 " + groupNameUnsetAdmin + " 的管理员身份。");
                sender.sendMessage("§a已撤销 " + adminTarget2.getName() + " 的管理员身份。");
                break;
            case "grouplist":
                if (chatGroups.isEmpty()) {
                    sender.sendMessage("§e当前没有任何群聊。");
                    return true;
                }
                sender.sendMessage("§a当前群聊列表：");
                for (String gName : chatGroups.keySet()) {
                    ChatGroup g = chatGroups.get(gName);
                    String ownerName = Bukkit.getOfflinePlayer(g.owner).getName();
                    String adminNames = g.admins.stream()
                            .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                            .filter(Objects::nonNull)
                            .collect(Collectors.joining(", "));
                    sender.sendMessage("§6" + gName + " §7(群主: " + ownerName + ", 管理员: " + adminNames + ", 成员数: " + g.members.size() + ")");
                }
                break;
            case "groupmsg":
                Bukkit.getLogger().info("playerGroup: " + playerGroup);
                if (args.length < 1) {
                    sender.sendMessage("§c用法: /groupmsg <消息>");
                    return true;
                }
                if (!(sender instanceof Player talker)) {
                    sender.sendMessage("§c只有玩家可以群聊发言！");
                    return true;
                }

                groupName = playerGroup.get(talker.getUniqueId());
                if (groupName == null) {
                    sender.sendMessage("§c你未加入任何群聊！");
                    return true;
                }

                ChatGroup chatGroup = chatGroups.get(groupName);
                if (chatGroup == null) {
                    sender.sendMessage("§c群聊不存在！");
                    return true;
                }

                // 检查禁言状态
                if (chatGroup.mutedMembers.containsKey(talker.getUniqueId())) {
                    GroupMuteInfo muteInfo = chatGroup.mutedMembers.get(talker.getUniqueId());
                    if (System.currentTimeMillis() < muteInfo.unmuteTime) {
                        long left = (muteInfo.unmuteTime - System.currentTimeMillis()) / 1000;
                        long min = left / 60;
                        long sec = left % 60;
                        String muterName = Bukkit.getOfflinePlayer(muteInfo.mutedBy).getName();
                        sender.sendMessage("§c你在此群聊中已被 " + muterName + " 禁言，剩余时间：" + min + "分" + sec + "秒，原因：" + muteInfo.reason);
                        return true;
                    } else {
                        chatGroup.mutedMembers.remove(talker.getUniqueId());
                    }
                }

                String groupMsg = String.join(" ", args);
                String historyMsg = "§6[群聊 " + groupName + "]§e" + talker.getName() + "§f: " + groupMsg;
                chatGroup.history.add(historyMsg);
                while (chatGroup.history.size() > groupHistoryLimit) {
                    chatGroup.history.remove(0);
                }

                String lowerMsg = groupMsg.toLowerCase();
                boolean containsAtAll = lowerMsg.contains("@all");
                boolean isAdminOrOwner = chatGroup.owner.equals(talker.getUniqueId()) || chatGroup.admins.contains(talker.getUniqueId());

                long now = System.currentTimeMillis();
                long lastNotify = lastNotifyTime.getOrDefault(talker.getUniqueId(), 0L);
                boolean notifyCooldownPassed = now - lastNotify >= notifyCooldownMillis;

                for (UUID member : chatGroup.members) {
                    Player p = Bukkit.getPlayer(member);
                    if (p != null && p.isOnline()) {
                        p.sendMessage("§6[群聊 " + groupName + "]§e" + talker.getName() + "§f: " + groupMsg);

                        // 是否允许被提醒
                        boolean notifyOn = notifyEnabled.getOrDefault(p.getUniqueId(), true);
                        if (!notifyOn) continue;

                        String name = p.getName();
                        if (groupMsg.contains("@" + name) && notifyCooldownPassed) {
                            p.sendTitle("§e你被提及了", "§f在群聊 " + groupName + " 中", 10, 40, 10);
                            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                            p.sendMessage("§6[提醒] §e你在群聊中被 @" + talker.getName() + " 提及了！");
                            lastNotifyTime.put(talker.getUniqueId(), now);
                        }

                        if (containsAtAll && isAdminOrOwner && notifyCooldownPassed) {
                            p.sendTitle("§e全体提醒", "§f来自 " + talker.getName(), 10, 40, 10);
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
                            lastNotifyTime.put(talker.getUniqueId(), now);
                        }
                    }
                }
                break;
            case "groupnotify":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§c只有玩家可以设置提醒状态！");
                    return true;
                }
                if (args.length < 1) {
                    sender.sendMessage("§e用法: /groupnotify <on|off>");
                    return true;
                }
                String toggle = args[0].toLowerCase();
                if (toggle.equals("on")) {
                    notifyEnabled.put(player.getUniqueId(), true);
                    saveNotifyConfig();
                    sender.sendMessage("§a你已开启 @提醒功能。");
                } else if (toggle.equals("off")) {
                    notifyEnabled.put(player.getUniqueId(), false);
                    saveNotifyConfig();
                    sender.sendMessage("§c你已关闭 @提醒功能。");
                } else {
                    sender.sendMessage("§c用法错误，请使用 /groupnotify <on|off>");
                }
                break;

            case "switchgroup":
                if (!(sender instanceof Player switcher)) {
                    sender.sendMessage("§c只有玩家可以切换群聊！");
                    return true;
                }
                if (args.length < 1) {
                    sender.sendMessage("§c用法: /switchgroup <群名>");
                    return true;
                }
                String switchGroupName = args[0];
                ChatGroup switchGroup = chatGroups.get(switchGroupName);
                if (switchGroup == null) {
                    sender.sendMessage("§c群聊不存在！");
                    return true;
                }
                if (!switchGroup.members.contains(switcher.getUniqueId())) {
                    sender.sendMessage("§c你不是该群聊的成员！");
                    return true;
                }
                playerGroup.put(switcher.getUniqueId(), switchGroupName);
                sender.sendMessage("§a已切换到群聊 " + switchGroupName + "，现在你在该群聊中发言。");
                break;
            case "grouphistory":
                if (!(sender instanceof Player viewer)) {
                    sender.sendMessage("§c只有玩家可以查看群聊历史！");
                    return true;
                }
                String currentGroup = playerGroup.get(viewer.getUniqueId());
                if (currentGroup == null) {
                    sender.sendMessage("§c你未加入任何群聊！");
                    return true;
                }
                ChatGroup hisGroup = chatGroups.get(currentGroup);
                if (hisGroup == null) {
                    sender.sendMessage("§c群聊不存在！");
                    return true;
                }
                int pageSize = 20;
                int total = hisGroup.history.size();
                int totalPages = (total + pageSize - 1) / pageSize;
                int page = 1;
                if (args.length >= 1) {
                    try {
                        page = Integer.parseInt(args[0]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§c页码格式错误！");
                        return true;
                    }
                }
                if (page < 1) page = 1;
                if (page > totalPages) page = totalPages;
                int start = (page - 1) * pageSize;
                int end = Math.min(start + pageSize, total);

                sender.sendMessage("§a========== 群聊历史记录 第 " + page + "/" + totalPages + " 页 ==========");
                if (total == 0) {
                    sender.sendMessage("§7暂无历史消息。");
                } else {
                    for (int i = start; i < end; i++) {
                        sender.sendMessage(hisGroup.history.get(i));
                    }
                }
                sender.sendMessage("§a=================================");
                break;
            case "setgrouphistorylimit":
                if (!sender.hasPermission("message.admin")) {
                    sender.sendMessage("§c你没有权限执行此命令！");
                    return true;
                }
                if (args.length < 1) {
                    sender.sendMessage("§c用法: /setgrouphistorylimit <条数>");
                    return true;
                }
                try {
                    int limit = Integer.parseInt(args[0]);
                    if (limit < 1) {
                        sender.sendMessage("§c条数必须大于0！");
                        return true;
                    }
                    groupHistoryLimit = limit;
                    sender.sendMessage("§a历史消息最大保存条数已设置为: " + groupHistoryLimit);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c请输入有效的数字！");
                }
                break;
            case "groupchat":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§c只有玩家可以切换群聊聊天模式！");
                    return true;
                }
                groupName = playerGroup.get(player.getUniqueId());
                if (groupName == null) {
                    sender.sendMessage("§c你未加入任何群聊！");
                    return true;
                }
                groupChatMode.add(player.getUniqueId());
                sender.sendMessage("§a你已进入群聊聊天模式，直接输入内容即可发送到群聊。输入 /publicchat 退出。");
                break;
            case "publicchat":
                if (!(sender instanceof Player player2)) {
                    sender.sendMessage("§c只有玩家可以切换公屏聊天模式！");
                    return true;
                }
                groupChatMode.remove(player2.getUniqueId());
                sender.sendMessage("§a你已退出群聊聊天模式，现在发言会显示在公屏。");
                break;
            case "setannouncement":
                if (args.length < 1) {
                    sender.sendMessage("§c用法: /setannouncement <公告内容>");
                    return true;
                }
                if (!(sender instanceof Player announcer)) {
                    sender.sendMessage("§c只有玩家可以设置群公告！");
                    return true;
                }
                currentGroup = playerGroup.get(announcer.getUniqueId());
                if (currentGroup == null) {
                    sender.sendMessage("§c你未加入任何群聊！");
                    return true;
                }
                ChatGroup announcementGroup = chatGroups.get(currentGroup);
                if (announcementGroup == null) {
                    sender.sendMessage("§c群聊不存在！");
                    return true;
                }
                isOwner = announcementGroup.owner.equals(announcer.getUniqueId());
                isAdmin = announcementGroup.admins.contains(announcer.getUniqueId());
                if (!isOwner && !isAdmin) {
                    sender.sendMessage("§c只有群主或管理员才能设置群公告！");
                    return true;
                }
                String announcement = String.join(" ", args);
                announcementGroup.announcement = announcement;
                sender.sendMessage("§a群公告已设置为: " + announcement);
                // 通知所有在线群成员
                for (UUID memberId : announcementGroup.members) {
                    Player member = Bukkit.getPlayer(memberId);
                    if (member != null && member.isOnline() && !member.getUniqueId().equals(announcer.getUniqueId())) {
                        member.sendMessage("§6[群聊 " + currentGroup + "] §e公告已更新: §f" + announcement);
                    }
                }
                break;

            case "announcement":
                if (!(sender instanceof Player viewer)) {
                    sender.sendMessage("§c只有玩家可以查看群公告！");
                    return true;
                }
                String viewGroup = playerGroup.get(viewer.getUniqueId());
                if (viewGroup == null) {
                    sender.sendMessage("§c你未加入任何群聊！");
                    return true;
                }
                ChatGroup viewAnnouncementGroup = chatGroups.get(viewGroup);
                if (viewAnnouncementGroup == null) {
                    sender.sendMessage("§c群聊不存在！");
                    return true;
                }
                if (viewAnnouncementGroup.announcement == null || viewAnnouncementGroup.announcement.isEmpty()) {
                    sender.sendMessage("§6[群聊 " + viewGroup + "] §e当前没有群公告");
                } else {
                    sender.sendMessage("§6[群聊 " + viewGroup + "] §e公告: §f" + viewAnnouncementGroup.announcement);
                }
                break;
            case "groupmute":
                if (args.length < 3) {
                    sender.sendMessage("§c用法: /groupmute <群名> <玩家名> <时间(分钟)> [原因]");
                    return true;
                }
                if (!(sender instanceof Player muter)) {
                    sender.sendMessage("§c只有玩家可以执行此命令！");
                    return true;
                }
                String muteGroupName = args[0];
                Player mutedPlayer = Bukkit.getPlayer(args[1]);
                if (mutedPlayer == null) {
                    sender.sendMessage("§c未找到该玩家！");
                    return true;
                }
                ChatGroup muteGroup = chatGroups.get(muteGroupName);
                if (muteGroup == null) {
                    sender.sendMessage("§c群聊不存在！");
                    return true;
                }

                // 检查权限
                boolean isMuterOwner = muteGroup.owner.equals(muter.getUniqueId());
                boolean isMuterAdmin = muteGroup.admins.contains(muter.getUniqueId());
                boolean isMutedOwner = muteGroup.owner.equals(mutedPlayer.getUniqueId());
                boolean isMutedAdmin = muteGroup.admins.contains(mutedPlayer.getUniqueId());

                if (!isMuterOwner && !isMuterAdmin) {
                    sender.sendMessage("§c只有群主或管理员才能禁言成员！");
                    return true;
                }

                if (isMutedOwner) {
                    sender.sendMessage("§c不能禁言群主！");
                    return true;
                }

                if (isMutedAdmin && !isMuterOwner) {
                    sender.sendMessage("§c管理员不能禁言其他管理员，只有群主可以禁言管理员！");
                    return true;
                }

                if (!muteGroup.members.contains(mutedPlayer.getUniqueId())) {
                    sender.sendMessage("§c该玩家不在群聊中！");
                    return true;
                }

                int minutes;
                try {
                    minutes = Integer.parseInt(args[2]);
                    if (minutes <= 0) {
                        sender.sendMessage("§c禁言时间必须大于0分钟！");
                        return true;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c禁言时间必须是有效的数字！");
                    return true;
                }

                String muteReason = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "无理由";
                long unmuteTime = System.currentTimeMillis() + minutes * 60 * 1000;

                muteGroup.mutedMembers.put(mutedPlayer.getUniqueId(), new GroupMuteInfo(unmuteTime, muteReason, muter.getUniqueId()));

                mutedPlayer.sendMessage("§c你已被 " + muter.getName() + " 在群聊 " + muteGroupName + " 中禁言 " + minutes + " 分钟，原因: " + muteReason);
                sender.sendMessage("§a已禁言 " + mutedPlayer.getName() + " 在群聊 " + muteGroupName + " 中 " + minutes + " 分钟");

                // 通知群内其他成员
                String muteAnnouncement = "§6[群聊 " + muteGroupName + "] §c" + mutedPlayer.getName() + " 已被 " + muter.getName() + " 禁言 " + minutes + " 分钟";
                for (UUID memberId : muteGroup.members) {
                    if (!memberId.equals(muter.getUniqueId()) && !memberId.equals(mutedPlayer.getUniqueId())) {
                        Player member = Bukkit.getPlayer(memberId);
                        if (member != null && member.isOnline()) {
                            member.sendMessage(muteAnnouncement);
                        }
                    }
                }
                break;
            case "groupunmute":
                if (args.length < 2) {
                    sender.sendMessage("§c用法: /groupunmute <群名> <玩家名>");
                    return true;
                }
                if (!(sender instanceof Player unmuter)) {
                    sender.sendMessage("§c只有玩家可以执行此命令！");
                    return true;
                }
                String unmuteGroupName = args[0];
                Player unmutedPlayer = Bukkit.getPlayer(args[1]);
                if (unmutedPlayer == null) {
                    sender.sendMessage("§c未找到该玩家！");
                    return true;
                }
                ChatGroup unmuteGroup = chatGroups.get(unmuteGroupName);
                if (unmuteGroup == null) {
                    sender.sendMessage("§c群聊不存在！");
                    return true;
                }

                // 检查权限
                boolean isUnmuterOwner = unmuteGroup.owner.equals(unmuter.getUniqueId());
                boolean isUnmuterAdmin = unmuteGroup.admins.contains(unmuter.getUniqueId());

                if (!isUnmuterOwner && !isUnmuterAdmin) {
                    sender.sendMessage("§c只有群主或管理员才能解除禁言！");
                    return true;
                }

                if (!unmuteGroup.mutedMembers.containsKey(unmutedPlayer.getUniqueId())) {
                    sender.sendMessage("§c该玩家未被禁言！");
                    return true;
                }

                // 管理员只能解除自己设置的禁言，或者普通成员的禁言
                GroupMuteInfo muteInfo = unmuteGroup.mutedMembers.get(unmutedPlayer.getUniqueId());
                boolean isMutedByAdmin = unmuteGroup.admins.contains(muteInfo.mutedBy);

                if (!isUnmuterOwner && isMutedByAdmin && !muteInfo.mutedBy.equals(unmuter.getUniqueId())) {
                    sender.sendMessage("§c你只能解除自己设置的禁言！");
                    return true;
                }

                unmuteGroup.mutedMembers.remove(unmutedPlayer.getUniqueId());

                unmutedPlayer.sendMessage("§a你在群聊 " + unmuteGroupName + " 中的禁言已被 " + unmuter.getName() + " 解除");
                sender.sendMessage("§a已解除 " + unmutedPlayer.getName() + " 在群聊 " + unmuteGroupName + " 中的禁言");

                // 通知群内其他成员
                String unmuteAnnouncement = "§6[群聊 " + unmuteGroupName + "] §a" + unmutedPlayer.getName() + " 的禁言已被 " + unmuter.getName() + " 解除";
                for (UUID memberId : unmuteGroup.members) {
                    if (!memberId.equals(unmuter.getUniqueId()) && !memberId.equals(unmutedPlayer.getUniqueId())) {
                        Player member = Bukkit.getPlayer(memberId);
                        if (member != null && member.isOnline()) {
                            member.sendMessage(unmuteAnnouncement);
                        }
                    }
                }
                break;
            case "chathelp":
                sender.sendMessage("§a------- 群聊系统指令指南 -------");
                sender.sendMessage("§e/creategroup <群名> §7- 创建一个新的群聊");
                sender.sendMessage("§e/invitegroup <群名> <玩家名> §7- 邀请玩家加入群聊");
                sender.sendMessage("§e/joingroup <群名> §7- 接受邀请加入群聊");
                sender.sendMessage("§e/leavegroup §7- 退出当前群聊");
                sender.sendMessage("§e/switchgroup <群名> §7- 切换当前活跃群聊");
                sender.sendMessage("§e/kickgroup <群名> <玩家名> §7- 将玩家踢出群聊");
                sender.sendMessage("§e/groupban <群名> <玩家名> §7- 将玩家加入群聊黑名单");
                sender.sendMessage("§e/groupunban <群名> <玩家名> §7- 移除玩家的群聊黑名单");
                sender.sendMessage("§e/groupmsg <消息> §7- 在当前群聊中发送消息");
                sender.sendMessage("§e/groupchat §7- 进入群聊聊天模式（持续发送）");
                sender.sendMessage("§e/setadmin <群名> <玩家名> §7- 设置管理员");
                sender.sendMessage("§e/unsetadmin <群名> <玩家名> §7- 撤销管理员");
                sender.sendMessage("§e/groupmute <玩家名> <分钟> <原因> §7- 禁言群成员");
                sender.sendMessage("§e/groupunmute <玩家名> §7- 解除禁言");
                sender.sendMessage("§e/setannouncement <文本> §7- 设置群公告");
                sender.sendMessage("§e/announcement §7- 查看群公告");
                sender.sendMessage("§e/groupnotice §7- 查看当前群公告（新加入成员可随时查看）");
                sender.sendMessage("§e/grouphistory [页码] §7- 查看群聊历史消息");
                sender.sendMessage("§e/setgrouphistorylimit <条数> §7- 设置历史记录条数上限");
                sender.sendMessage("§e/renamegroup <旧群名> <新群名> §7- 修改群聊名称");
                sender.sendMessage("§e/transfergroup <群名> <玩家名> §7- 转让群主身份");
                sender.sendMessage("§e/disbandgroup <群名> §7- 解散群聊");
                sender.sendMessage("§eBy Bicd 本插件有问题找1315118858反馈");
                sender.sendMessage("§a------------------------------");
                break;
            default:
                return false;
        }
        return true;
    }
}


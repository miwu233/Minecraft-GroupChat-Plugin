package com.Message;

import com.Message.ChatGroup;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.Arrays;

public final class Message extends JavaPlugin implements Listener {

    private String joinMessage = "§d欢迎§e%player%§a加入Slient！";
    private String quitMessage = "§7人生自古谁无死 遗憾的 §e%player%§c离开了。";
    private String broadcastMessage = "§b服务器QQ群627489610 可以加入我们的大家庭聊天哦~";
    private BukkitTask broadcastTask = null;
    private String deathMessage = "§d你死啦！小心一点哦~ 冷知识 聊天栏输入“/back”可以回到死亡的位置";
    private final Set<UUID> diedPlayers = new HashSet<>();
    private boolean deathResetOnJoin = true;
    private final Map<UUID, MuteInfo> mutedPlayers = new HashMap<>();
    private final Map<String, ChatGroup> chatGroups = new HashMap<>();
    private final Map<UUID, String> playerGroup = new HashMap<>();


    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            player.sendMessage("§d你死啦！小心一点哦~ 冷知识 聊天栏输入“/back”可以回到死亡的位置");
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
        if (mutedPlayers.containsKey(uuid)) {
            MuteInfo info = mutedPlayers.get(uuid);
            if (System.currentTimeMillis() < info.unmuteTime) {
                long left = (info.unmuteTime - System.currentTimeMillis()) / 1000;
                long min = left / 60;
                long sec = left % 60;
                player.sendMessage("§c你已被禁言，剩余时间：" + min + "分" + sec + "秒，原因：" + info.reason);
                event.setCancelled(true);
                return;
            } else {
                mutedPlayers.remove(uuid); // 自动解禁
            }
        }
    }
    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        loadMessages();
    }

    @Override
    public void onDisable() {
        if (broadcastTask != null) broadcastTask.cancel();
    }

    private void loadMessages() {
        joinMessage = getConfig().getString("joinMessage", joinMessage);
        quitMessage = getConfig().getString("quitMessage", quitMessage);
        broadcastMessage = getConfig().getString("broadcastMessage", broadcastMessage);
    }

    private void saveMessages() {
        getConfig().set("joinMessage", joinMessage);
        getConfig().set("quitMessage", quitMessage);
        getConfig().set("broadcastMessage", broadcastMessage);
        saveConfig();
    }


    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 重置死亡提示
        if (deathResetOnJoin) {
            diedPlayers.remove(player.getUniqueId());
        }
        // 发送欢迎消息
        String msg = joinMessage.replace("%player%", player.getName());
        event.setJoinMessage(msg);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String msg = quitMessage.replace("%player%", event.getPlayer().getName());
        event.setQuitMessage(msg);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("message.admin")) {
            sender.sendMessage("§c你没有权限执行此命令！");
            return true;
        }
        switch (command.getName().toLowerCase()) {
            case "setjoinmsg":
                if (args.length == 0) {
                    sender.sendMessage("§c用法: /setjoinmsg <消息内容>");
                    return true;
                }
                joinMessage = String.join(" ", args);
                saveMessages();
                sender.sendMessage("§d欢迎消息已设置为: " + joinMessage);
                break;
            case "setquitmsg":
                if (args.length == 0) {
                    sender.sendMessage("§c用法: /setquitmsg <消息内容>");
                    return true;
                }
                quitMessage = String.join(" ", args);
                saveMessages();
                sender.sendMessage("§a离开消息已设置为: " + quitMessage);
                break;
            case "setbroadcast":
                if (args.length == 0) {
                    sender.sendMessage("§c用法: /setbroadcast <消息内容>");
                    return true;
                }
                broadcastMessage = String.join(" ", args);
                saveMessages();
                sender.sendMessage("§a广播内容已设置为: " + broadcastMessage);
                break;
            case "broadcaststart":
                if (args.length == 0) {
                    sender.sendMessage("§c用法: /broadcaststart <秒数>");
                    return true;
                }
                int seconds;
                try {
                    seconds = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c请输入有效的秒数！");
                    return true;
                }
                if (broadcastTask != null) broadcastTask.cancel();
                broadcastTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                    Bukkit.broadcastMessage(broadcastMessage);
                }, 0L, seconds * 20L);
                sender.sendMessage("§b定时广播已启动，每 " + seconds + " 秒发送一次。");
                break;
            case "broadcaststop":
                if (broadcastTask != null) {
                    broadcastTask.cancel();
                    broadcastTask = null;
                    sender.sendMessage("§c定时广播已停止。");
                } else {
                    sender.sendMessage("§c当前没有正在运行的定时广播。");
                }
                break;
            case "setdeathreset":
                if (args.length == 0) {
                    sender.sendMessage("§c用法: /setdeathreset <on|off>");
                    return true;
                }
                if (args[0].equalsIgnoreCase("on")) {
                    deathResetOnJoin = true;
                    sender.sendMessage("§a玩家重进后会重置死亡提示（再次死亡会收到提示）");
                } else if (args[0].equalsIgnoreCase("off")) {
                    deathResetOnJoin = false;
                    sender.sendMessage("§a玩家重进后不会重置死亡提示（只会收到一次提示）");
                } else {
                    sender.sendMessage("§c用法: /setdeathreset <on|off>");
                }
                break;
            case "mute":
                if (args.length < 3) {
                    sender.sendMessage("§c用法: /mute <玩家名> <时间> <原因>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    sender.sendMessage("§c未找到该玩家！");
                    return true;
                }
                String timeArg = args[1].toLowerCase();
                long duration = 0;
                try {
                    if (timeArg.endsWith("m")) {
                        duration = Long.parseLong(timeArg.replace("m", "")) * 60 * 1000;
                    } else if (timeArg.endsWith("h")) {
                        duration = Long.parseLong(timeArg.replace("h", "")) * 60 * 60 * 1000;
                    } else {
                        sender.sendMessage("§c时间格式错误，请用m（分钟）或h（小时）结尾！");
                        return true;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c时间格式错误！");
                    return true;
                }
                String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                mutedPlayers.put(target.getUniqueId(), new MuteInfo(System.currentTimeMillis() + duration, reason));
                sender.sendMessage("§c已禁言玩家 " + target.getName() + " " + args[1] + "，原因：" + reason);
                target.sendMessage("§c你已被禁言 " + args[1] + "，原因：" + reason);
                break;

            case "unmute":
                if (args.length < 1) {
                    sender.sendMessage("§c用法: /unmute <玩家名>");
                    return true;
                }
                Player unmuteTarget = Bukkit.getPlayer(args[0]);
                if (unmuteTarget == null) {
                    sender.sendMessage("§c未找到该玩家！");
                    return true;
                }
                mutedPlayers.remove(unmuteTarget.getUniqueId());
                sender.sendMessage("§c已解除玩家 " + unmuteTarget.getName() + " 的禁言。");
                unmuteTarget.sendMessage("§c你的禁言已被解除。");
                break;
            case "fakechat":
                if (args.length < 2) {
                    sender.sendMessage("§c用法: /fakechat <玩家名> <内容>");
                    return true;
                }
                Player fakePlayer = Bukkit.getPlayer(args[0]);
                if (fakePlayer == null) {
                    sender.sendMessage("§c未找到该玩家！");
                    return true;
                }
                String fakeMsg = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                AsyncPlayerChatEvent chatEvent = new AsyncPlayerChatEvent(
                        false, // async
                        fakePlayer,
                        fakeMsg,
                        new HashSet<>(Bukkit.getOnlinePlayers())
                );
                Bukkit.getPluginManager().callEvent(chatEvent);
                if (!chatEvent.isCancelled()) {
                    for (Player p : chatEvent.getRecipients()) {
                        p.sendMessage(String.format(chatEvent.getFormat(), fakePlayer.getDisplayName(), chatEvent.getMessage()));
                    }
                }
                sender.sendMessage("§a已以 " + fakePlayer.getName() + " 的名义发送消息。");
                break;
            case "creategroup":
                if (args.length < 1) {
                    sender.sendMessage("§c用法: /creategroup <群名>");
                    return true;
                }
                if (!(sender instanceof Player creator)) {
                    sender.sendMessage("§c只有玩家可以创建群聊！");
                    return true;
                }
                String groupName = args[0];
                if (chatGroups.containsKey(groupName)) {
                    sender.sendMessage("§c群聊已存在！");
                    return true;
                }
                String group;
                group = new ChatGroup(groupName, creator.getUniqueId()).toString();
                chatGroups.put(groupName, ChatGroup.get(group));
                playerGroup.put(creator.getUniqueId(), groupName);
                sender.sendMessage("§a群聊 " + groupName + " 创建成功并已加入！你是群主。");
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
                break;
            case "leavegroup":
                if (!(sender instanceof Player leaver)) {
                    sender.sendMessage("§c只有玩家可以退出群聊！");
                    return true;
                }
                String groupNameLeave = playerGroup.remove(leaver.getUniqueId());
                if (groupNameLeave == null) {
                    sender.sendMessage("§c你未加入任何群聊！");
                    return true;
                }
                ChatGroup groupLeave = chatGroups.get(groupNameLeave);
                if (groupLeave != null) {
                    groupLeave.members.remove(leaver.getUniqueId());
                    // 如果群主退出，自动转让群主给下一个成员
                    if (groupLeave.owner.equals(leaver.getUniqueId())) {
                        if (!groupLeave.members.isEmpty()) {
                            groupLeave.owner = groupLeave.members.iterator().next();
                            Player newOwner = Bukkit.getPlayer(groupLeave.owner);
                            if (newOwner != null) {
                                newOwner.sendMessage("§e你已成为群聊 " + groupNameLeave + " 的新群主。");
                            }
                        } else {
                            chatGroups.remove(groupNameLeave); // 群没人了，解散
                        }
                    }
                }
                sender.sendMessage("§a你已退出群聊 " + groupNameLeave + "。");
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
                if (!groupToKick.owner.equals(kicker.getUniqueId())) {
                    sender.sendMessage("§c只有群主才能踢人！");
                    return true;
                }
                if (!groupToKick.members.contains(toKick.getUniqueId())) {
                    sender.sendMessage("§c该玩家不在群聊中！");
                    return true;
                }
                groupToKick.members.remove(toKick.getUniqueId());
                playerGroup.remove(toKick.getUniqueId());
                toKick.sendMessage("§c你已被群主踢出群聊 " + kickGroup + "。");
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
                ChatGroup groupSetAdmin = ChatGroup.get(groupNameSetAdmin);
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
                if (args.length < 1) {
                    sender.sendMessage("§c用法: /groupmsg <消息>");
                    return true;
                }
                if (!(sender instanceof Player talker)) {
                    sender.sendMessage("§c只有玩家可以群聊发言！");
                    return true;
                }
                group = playerGroup.get(talker.getUniqueId());
                if (group == null) {
                    sender.sendMessage("§c你未加入任何群聊！");
                    return true;
                }
                ChatGroup chatGroup = chatGroups.get(group);
                if (chatGroup == null) {
                    sender.sendMessage("§c群聊不存在！");
                    return true;
                }
                String groupMsg = String.join(" ", args);
                for (UUID member : chatGroup.members) {
                    Player p = Bukkit.getPlayer(member);
                    if (p != null && p.isOnline()) {
                        p.sendMessage("§6[群聊 " + group + "] §e" + talker.getName() + "§f: " + groupMsg);
                    }
                }
                break;
            case "helpmsg":
                sender.sendMessage("§a========== 插件指令指南 ==========");
                sender.sendMessage("§e/creategroup <群名> §7- 创建一个新的群聊并成为群主");
                sender.sendMessage("§e/invitegroup <群名> <玩家名> §7- 邀请玩家加入群聊（群主/管理员）");
                sender.sendMessage("§e/joingroup <群名> §7- 加入已被邀请的群聊");
                sender.sendMessage("§e/leavegroup §7- 退出当前群聊");
                sender.sendMessage("§e/kickgroup <群名> <玩家名> §7- 踢出群聊成员（群主/管理员）");
                sender.sendMessage("§e/setadmin <群名> <玩家名> §7- 任命管理员（群主）");
                sender.sendMessage("§e/unsetadmin <群名> <玩家名> §7- 撤销管理员（群主）");
                sender.sendMessage("§e/grouplist §7- 查看所有群聊及成员");
                sender.sendMessage("§e/groupmsg <消息> §7- 在当前群聊中发言");
                sender.sendMessage("§a=====================================");
                break;
            case "message":
                sender.sendMessage("§a============ 插件指令指南 ============");
                sender.sendMessage("§e/setjoinmsg <消息内容> §7设置欢迎消息");
                sender.sendMessage("§e/setquitmsg <消息内容> §7设置离开消息");
                sender.sendMessage("§e/setbroadcast <消息内容> §7设置广播消息");
                sender.sendMessage("§e/broadcaststart <秒数> §7设置广播发送多少秒");
                sender.sendMessage("§e/broadcaststop §7停止广播");
                sender.sendMessage("§e/setdeathreset <on|off>玩家重进后是否重置死亡提示 §7玩家重进后是否重置死亡提示（再次死亡会收到提示）");
                sender.sendMessage("§e/mute <玩家名> <时间> <原因> §7禁言玩家（可精确到m）");
                sender.sendMessage("§e/unmute <玩家名> §7解除玩家禁言");
                sender.sendMessage("§e/fakechat <玩家名> <内容> §7伪装其他玩家发言（若有头衔依旧可用）");
                sender.sendMessage("§e/creategroup <群名> §7- 创建一个新的群聊并成为群主");
                sender.sendMessage("§e/invitegroup <群名> <玩家名> §7- 邀请玩家加入群聊（群主/管理员）");
                sender.sendMessage("§e/joingroup <群名> §7- 加入已被邀请的群聊");
                sender.sendMessage("§e/leavegroup §7- 退出当前群聊");
                sender.sendMessage("§e/kickgroup <群名> <玩家名> §7- 踢出群聊成员（群主/管理员）");
                sender.sendMessage("§e/setadmin <群名> <玩家名> §7- 任命管理员（群主）");
                sender.sendMessage("§e/unsetadmin <群名> <玩家名> §7- 撤销管理员（群主）");
                sender.sendMessage("§e/grouplist §7- 查看所有群聊及成员");
                sender.sendMessage("§e/groupmsg <消息> §7- 在当前群聊中发言");
                sender.sendMessage("§a====================================");
                break;
            default:
                return false;
        }
        return true;
    }
}


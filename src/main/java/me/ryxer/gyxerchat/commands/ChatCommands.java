package me.ryxer.gyxerchat.commands;

import io.papermc.paper.ban.BanListType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import me.ryxer.gyxerchat.GyxerChat;
import me.ryxer.gyxerchat.Keys;
import me.ryxer.gyxerchat.managers.TagManager;
import me.ryxer.gyxerchat.utils.Durations;
import me.ryxer.gyxerchat.utils.Lookup;
import me.ryxer.gyxerchat.utils.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.BanEntry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ChatCommands implements CommandExecutor, TabCompleter {
   public static final String[] COMMANDS = {
      "chatadmin", "mute", "tempmute", "unmute", "ban", "tempban", "unban", "warn", "announce", "chatcolor", "chatreload",
      "clearchat", "mutechat", "staffchat", "msg", "reply", "ignore", "socialspy", "tags", "createtag", "deletetag", "nick", "unnick", "settings"
   };
   private static final List<String> TIMES = List.of("10m", "30m", "1h", "12h", "1d", "7d", "30d");
   private final GyxerChat plugin;

   public ChatCommands(GyxerChat plugin) {
      this.plugin = plugin;
   }

   private static String rest(String[] args, int from) {
      return String.join(" ", Arrays.copyOfRange(args, from, args.length)).trim();
   }

   private void usage(CommandSender s, String usage) {
      this.plugin.send(s, "error-usage", "usage", usage);
   }

   private Player player(CommandSender s) {
      if (s instanceof Player p) {
         return p;
      }
      this.plugin.send(s, "players-only");
      return null;
   }

   private String timeText(long ms) {
      return ms < 0 ? this.plugin.rawMsg("time-forever") : Durations.format(ms);
   }

   @Override
   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
      String name = cmd.getName().toLowerCase(Locale.ROOT);
      if (cmd.getPermission() != null && !cmd.getPermission().isEmpty() && !sender.hasPermission(cmd.getPermission())) {
         this.plugin.send(sender, "no-permission");
         return true;
      }
      switch (name) {
         case "settings" -> {
            Player p = this.player(sender);
            if (p != null) {
               this.plugin.getMenuManager().openPlayerSettingsMenu(p);
            }
         }
         case "tags" -> {
            Player p = this.player(sender);
            if (p != null) {
               this.plugin.getMenuManager().openTagsMenu(p, 0);
            }
         }
         case "chatadmin" -> {
            Player p = this.player(sender);
            if (p != null) {
               this.plugin.getMenuManager().openMainMenu(p);
            }
         }
         case "chatcolor" -> {
            Player p = this.player(sender);
            if (p != null) {
               this.plugin.getMenuManager().openChatColorMenu(p, p, "none");
               this.plugin.playAudio(p, "click");
            }
         }
         case "createtag" -> this.createTag(sender, args);
         case "deletetag" -> this.deleteTag(sender, args);
         case "socialspy" -> {
            Player p = this.player(sender);
            if (p != null) {
               if (this.plugin.getSocialSpyPlayers().remove(p.getUniqueId())) {
                  this.plugin.send(p, "spy-disabled");
               } else {
                  this.plugin.getSocialSpyPlayers().add(p.getUniqueId());
                  this.plugin.send(p, "spy-enabled");
               }
            }
         }
         case "msg" -> {
            Player p = this.player(sender);
            if (p == null) {
               break;
            }
            if (args.length < 2) {
               this.usage(p, "/msg <player> <message>");
               break;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || target.equals(p)) {
               this.plugin.send(p, "player-not-found");
               break;
            }
            this.privateMessage(p, target, rest(args, 1));
         }
         case "reply" -> {
            Player p = this.player(sender);
            if (p == null) {
               break;
            }
            if (args.length == 0) {
               this.usage(p, "/r <message>");
               break;
            }
            UUID last = this.plugin.getRecentMessages().get(p.getUniqueId());
            if (last == null) {
               this.plugin.send(p, "no-reply");
               break;
            }
            Player target = Bukkit.getPlayer(last);
            if (target == null) {
               this.plugin.send(p, "player-not-found");
               break;
            }
            this.privateMessage(p, target, rest(args, 0));
         }
         case "ignore" -> this.ignore(sender, args);
         case "chatreload" -> {
            this.plugin.reloadAll();
            this.plugin.send(sender, "reload-success");
            if (sender instanceof Player p) {
               this.plugin.playAudio(p, "success");
            }
         }
         case "clearchat" -> {
            Component empty = Component.empty();
            for (Player p : Bukkit.getOnlinePlayers()) {
               if (!p.hasPermission("gyxerchat.clearchat.bypass")) {
                  for (int i = 0; i < 100; i++) {
                     p.sendMessage(empty);
                  }
               }
            }
            Bukkit.broadcast(this.plugin.msg("chat-cleared", "player", sender.getName()));
            for (Player p : Bukkit.getOnlinePlayers()) {
               this.plugin.playAudio(p, "clear-chat");
            }
         }
         case "mutechat" -> this.plugin.getMenuManager().setChatLock(sender.getName(), !this.plugin.isGlobalChatMuted());
         case "staffchat" -> {
            if (args.length > 0) {
               this.plugin.sendStaffChat(sender, rest(args, 0));
               break;
            }
            Player p = this.player(sender);
            if (p == null) {
               break;
            }
            if (this.plugin.getStaffChatPlayers().remove(p.getUniqueId())) {
               this.plugin.send(p, "staffchat-off");
            } else {
               this.plugin.getStaffChatPlayers().add(p.getUniqueId());
               this.plugin.send(p, "staffchat-on");
            }
            this.plugin.playAudio(p, "success");
         }
         case "announce" -> {
            if (args.length == 0) {
               this.usage(sender, "/announce <message>");
               break;
            }
            Bukkit.broadcast(this.plugin.getParsedMsg(false, "broadcast-prefix", "message", Text.parse(rest(args, 0))));
            for (Player p : Bukkit.getOnlinePlayers()) {
               this.plugin.playAudio(p, "success");
            }
         }
         case "mute", "tempmute" -> this.mute(sender, name.equals("tempmute"), args);
         case "unmute" -> this.unmute(sender, args);
         case "warn" -> this.warn(sender, args);
         case "ban", "tempban" -> this.ban(sender, name.equals("tempban"), args);
         case "unban" -> this.unban(sender, args);
         case "nick" -> this.nick(sender, args);
         case "unnick" -> {
            Player p = this.player(sender);
            if (p != null) {
               this.plugin.prefs().setNickname(p, null);
               this.plugin.applyNick(p);
               this.plugin.send(p, "nick-reset");
               this.plugin.playAudio(p, "success");
            }
         }
         default -> {
            return false;
         }
      }
      return true;
   }

   // ------------------------------------------------------------------ private messages

   private void privateMessage(Player from, Player to, String text) {
      var toPrefs = this.plugin.prefs().get(to.getUniqueId());
      boolean staff = from.hasPermission("everlynx.chat.staff");
      if (!staff && this.plugin.getIgnoreManager().isIgnoring(to.getUniqueId(), from.getUniqueId())) {
         this.plugin.send(from, "cannot-message");
         return;
      }
      if (!staff && toPrefs.has(Keys.PMS_DISABLED)) {
         this.plugin.send(from, "pms-disabled", "target", to.getName());
         return;
      }
      boolean codes = this.plugin.cfg().getBoolean("chat-settings.allow-color-codes", true) && from.hasPermission("gyxerchat.chat.colors");
      Component msg = Text.playerText(text, codes);
      from.sendMessage(this.plugin.getParsedMsg(false, "msg-format-send", "target", this.plugin.displayName(to), "message", msg));
      to.sendMessage(this.plugin.getParsedMsg(false, "msg-format-receive", "player", this.plugin.displayName(from), "message", msg));
      if (!toPrefs.has(Keys.MENTION_SOUND_DISABLED)) {
         this.plugin.playAudio(to, "mention");
      }
      this.plugin.getRecentMessages().put(from.getUniqueId(), to.getUniqueId());
      this.plugin.getRecentMessages().put(to.getUniqueId(), from.getUniqueId());
      Component spy = this.plugin.getParsedMsg(false, "socialspy-format", "sender", from.getName(), "target", to.getName(), "message", msg);
      for (UUID uuid : this.plugin.getSocialSpyPlayers()) {
         Player s = Bukkit.getPlayer(uuid);
         if (s != null && !s.equals(from) && !s.equals(to) && s.hasPermission("everlynx.spy")) {
            s.sendMessage(spy);
         }
      }
   }

   private void ignore(CommandSender sender, String[] args) {
      Player p = this.player(sender);
      if (p == null) {
         return;
      }
      if (args.length == 0) {
         this.usage(p, "/ignore <add|remove|list> [player]");
         return;
      }
      String sub = args[0].toLowerCase(Locale.ROOT);
      if (sub.equals("list")) {
         List<String> names = new ArrayList<>();
         for (UUID id : this.plugin.getIgnoreManager().getIgnored(p.getUniqueId())) {
            names.add(Lookup.name(Bukkit.getOfflinePlayer(id), "Unknown"));
         }
         if (names.isEmpty()) {
            this.plugin.send(p, "ignore-list-empty");
         } else {
            this.plugin.send(p, "ignore-list", "list", String.join(", ", names));
         }
         return;
      }
      // "/ignore <player>" works too and switches on/off
      boolean toggle = !sub.equals("add") && !sub.equals("remove");
      if (!toggle && args.length < 2) {
         this.usage(p, "/ignore " + sub + " <player>");
         return;
      }
      String targetName = toggle ? args[0] : args[1];
      Lookup.player(this.plugin, targetName, target -> {
         if (target == null) {
            this.plugin.send(p, "player-never-joined", "target", targetName);
            return;
         }
         String shown = Lookup.name(target, targetName);
         if (target.getUniqueId().equals(p.getUniqueId())) {
            this.plugin.send(p, "ignore-self");
            return;
         }
         boolean ignoring = this.plugin.getIgnoreManager().isIgnoring(p.getUniqueId(), target.getUniqueId());
         boolean wantAdd = toggle ? !ignoring : sub.equals("add");
         if (wantAdd == ignoring) {
            this.plugin.send(p, wantAdd ? "ignore-already" : "ignore-not", "target", shown);
            return;
         }
         this.plugin.getIgnoreManager().toggleIgnore(p.getUniqueId(), target.getUniqueId());
         this.plugin.send(p, wantAdd ? "ignore-add" : "ignore-remove", "target", shown);
         this.plugin.playAudio(p, "success");
      });
   }

   // ------------------------------------------------------------------ tags

   private void createTag(CommandSender sender, String[] args) {
      if (args.length < 2) {
         this.usage(sender, "/createtag <id> <display>");
         return;
      }
      if (!TagManager.validId(args[0])) {
         this.usage(sender, "/createtag <id: letters, numbers, _ or -> <display>");
         return;
      }
      String id = args[0].toLowerCase(Locale.ROOT);
      String display = rest(args, 1);
      this.plugin.getTagManager().createTag(id, display);
      this.plugin.send(sender, "tag-created", "id", id, "display", Text.parse(display));
   }

   private void deleteTag(CommandSender sender, String[] args) {
      if (args.length < 1) {
         this.usage(sender, "/deletetag <id>");
         return;
      }
      if (this.plugin.getTagManager().deleteTag(args[0])) {
         this.plugin.send(sender, "tag-deleted", "id", args[0].toLowerCase(Locale.ROOT));
      } else {
         this.plugin.send(sender, "tag-not-found", "id", args[0]);
      }
   }

   // ------------------------------------------------------------------ moderation

   private void mute(CommandSender sender, boolean temp, String[] args) {
      if (args.length < (temp ? 2 : 1)) {
         this.usage(sender, temp ? "/tempmute <player> <time> <reason>" : "/mute <player> <reason>");
         return;
      }
      long duration = temp ? Durations.parse(args[1]) : -1L;
      if (temp && duration < 0) {
         this.plugin.send(sender, "invalid-time");
         return;
      }
      String reason = rest(args, temp ? 2 : 1);
      String finalReason = reason.isEmpty() ? "No reason provided." : reason;
      Lookup.player(this.plugin, args[0], target -> {
         if (target == null) {
            this.plugin.send(sender, "player-never-joined", "target", args[0]);
            return;
         }
         String name = Lookup.name(target, args[0]);
         this.plugin.getMuteManager().setMute(target.getUniqueId(), name, finalReason, duration);
         String time = this.timeText(duration);
         if (temp) {
            this.plugin.send(sender, "tempmute-success", "target", name, "reason", finalReason, "time", time);
         } else {
            this.plugin.send(sender, "mute-success", "target", name, "reason", finalReason);
         }
         if (target.getPlayer() != null) {
            this.plugin.send(target.getPlayer(), "muted-notify", "time", time, "reason", finalReason);
         }
         this.plugin.getDiscordManager().sendLog("Chat Punishment",
            "Type: " + (temp ? "Tempmute (" + time + ")" : "Mute") + "\nAdmin: " + sender.getName() + "\nTarget: " + name + "\nReason: " + finalReason);
      });
   }

   private void unmute(CommandSender sender, String[] args) {
      if (args.length == 0) {
         this.usage(sender, "/unmute <player>");
         return;
      }
      Lookup.player(this.plugin, args[0], target -> {
         if (target == null) {
            this.plugin.send(sender, "player-never-joined", "target", args[0]);
            return;
         }
         String name = Lookup.name(target, args[0]);
         if (!this.plugin.getMuteManager().removeMute(target.getUniqueId())) {
            this.plugin.send(sender, "not-muted", "target", name);
            return;
         }
         this.plugin.send(sender, "unmute-success", "target", name);
         if (target.getPlayer() != null) {
            this.plugin.send(target.getPlayer(), "unmuted-notify");
         }
         this.plugin.getDiscordManager().sendLog("Chat Punishment", "Type: Unmute\nAdmin: " + sender.getName() + "\nTarget: " + name);
      });
   }

   private void warn(CommandSender sender, String[] args) {
      if (args.length < 2) {
         this.usage(sender, "/warn <player> <reason>");
         return;
      }
      Player target = Bukkit.getPlayer(args[0]);
      if (target == null) {
         this.plugin.send(sender, "player-not-found");
         return;
      }
      String reason = rest(args, 1);
      target.sendMessage(this.plugin.msg("warned", "reason", reason));
      this.plugin.playAudio(target, "warn");
      this.plugin.send(sender, "warn-success", "target", target.getName(), "reason", reason);
      this.plugin.getDiscordManager().sendLog("Player Warning", "Admin: " + sender.getName() + "\nTarget: " + target.getName() + "\nReason: " + reason);
   }

   private void ban(CommandSender sender, boolean temp, String[] args) {
      if (args.length < (temp ? 2 : 1)) {
         this.usage(sender, temp ? "/tempban <player> <time> <reason>" : "/ban <player> <reason>");
         return;
      }
      long duration = temp ? Durations.parse(args[1]) : -1L;
      if (temp && duration < 0) {
         this.plugin.send(sender, "invalid-time");
         return;
      }
      String reason = rest(args, temp ? 2 : 1);
      String finalReason = reason.isEmpty() ? "Banned by an operator." : reason;
      Lookup.player(this.plugin, args[0], target -> {
         if (target == null) {
            this.plugin.send(sender, "player-never-joined", "target", args[0]);
            return;
         }
         String name = Lookup.name(target, args[0]);
         Instant expires = duration < 0 ? null : Instant.now().plusMillis(duration);
         target.ban(finalReason, expires, sender.getName());
         String time = this.timeText(duration);
         Player online = target.getPlayer();
         if (online != null) {
            online.kick(this.plugin.getParsedMsg(false, "ban-screen", "reason", finalReason, "time", time));
         }
         this.plugin.send(sender, "ban-success", "target", name, "reason", finalReason, "time", time);
         this.plugin.getDiscordManager().sendLog("Server Ban", "Admin: " + sender.getName() + "\nTarget: " + name + "\nTime: " + time + "\nReason: " + finalReason);
      });
   }

   private void unban(CommandSender sender, String[] args) {
      if (args.length == 0) {
         this.usage(sender, "/unban <player>");
         return;
      }
      Lookup.player(this.plugin, args[0], target -> {
         if (target == null || !target.isBanned()) {
            this.plugin.send(sender, "not-banned", "target", args[0]);
            return;
         }
         Bukkit.getBanList(BanListType.PROFILE).pardon(target.getPlayerProfile());
         String name = Lookup.name(target, args[0]);
         this.plugin.send(sender, "unban-success", "target", name);
         this.plugin.getDiscordManager().sendLog("Server Unban", "Admin: " + sender.getName() + "\nTarget: " + name);
      });
   }

   // ------------------------------------------------------------------ nick

   private void nick(CommandSender sender, String[] args) {
      if (args.length < 1) {
         this.usage(sender, "/nick <name|off> [player]");
         return;
      }
      Player target;
      if (args.length >= 2) {
         if (!sender.hasPermission("everlynx.chat.admin")) {
            this.plugin.send(sender, "no-permission");
            return;
         }
         target = Bukkit.getPlayer(args[1]);
         if (target == null) {
            this.plugin.send(sender, "player-not-found");
            return;
         }
      } else {
         target = this.player(sender);
         if (target == null) {
            return;
         }
      }
      if (args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("reset")) {
         this.plugin.prefs().setNickname(target, null);
         this.plugin.applyNick(target);
         this.plugin.send(sender, "nick-reset");
         return;
      }
      String nick = args[0];
      if (!sender.hasPermission("gyxerchat.nick.colors")) {
         nick = Text.plain(Text.AMPERSAND.deserialize(nick));
      }
      String plainNick = Text.plain(Text.AMPERSAND.deserialize(nick));
      int maxLen = this.plugin.cfg().getInt("chat-settings.max-nick-length", 10);
      if (plainNick.isEmpty() || !plainNick.matches("[A-Za-z0-9_]+")) {
         this.plugin.send(sender, "nick-invalid");
         this.failSound(sender);
         return;
      }
      if (plainNick.length() > maxLen) {
         this.plugin.send(sender, "nick-too-long", "max", maxLen);
         this.failSound(sender);
         return;
      }
      if (this.nickTaken(target, plainNick)) {
         this.plugin.send(sender, "nick-taken");
         this.failSound(sender);
         return;
      }
      this.plugin.prefs().setNickname(target, nick);
      this.plugin.applyNick(target);
      this.plugin.send(sender, "nick-set", "nick", this.plugin.displayName(target));
      if (sender instanceof Player p) {
         this.plugin.playAudio(p, "success");
      }
   }

   private void failSound(CommandSender s) {
      if (s instanceof Player p) {
         this.plugin.playAudio(p, "fail");
      }
   }

   /** Someone else is called that, or uses it as a nickname. */
   private boolean nickTaken(Player self, String plainNick) {
      for (Player other : Bukkit.getOnlinePlayers()) {
         if (other.equals(self)) {
            continue;
         }
         if (other.getName().equalsIgnoreCase(plainNick) || this.plugin.getPlayerDisplayName(other).equalsIgnoreCase(plainNick)) {
            return true;
         }
      }
      if (self.getName().equalsIgnoreCase(plainNick)) {
         return false;
      }
      OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(plainNick);
      return cached != null && !cached.getUniqueId().equals(self.getUniqueId());
   }

   // ------------------------------------------------------------------ tab complete

   @Override
   public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, String[] args) {
      String name = cmd.getName().toLowerCase(Locale.ROOT);
      if (cmd.getPermission() != null && !cmd.getPermission().isEmpty() && !sender.hasPermission(cmd.getPermission())) {
         return List.of();
      }
      List<String> out = new ArrayList<>();
      if (args.length == 1) {
         switch (name) {
            case "ignore" -> {
               out.addAll(List.of("add", "remove", "list"));
               this.onlineNames(sender, out);
            }
            case "mute", "tempmute", "ban", "tempban", "warn", "msg" -> this.onlineNames(sender, out);
            case "unmute" -> {
               for (UUID id : this.plugin.getMuteManager().getAllMutedUUIDs()) {
                  var m = this.plugin.getMuteManager().get(id);
                  String n = Bukkit.getOfflinePlayer(id).getName();
                  if (n == null && m != null) {
                     n = m.name();
                  }
                  if (n != null) {
                     out.add(n);
                  }
               }
            }
            case "unban" -> {
               for (BanEntry<?> e : Bukkit.getBanList(BanListType.PROFILE).getEntries()) {
                  if (e.getBanTarget() instanceof com.destroystokyo.paper.profile.PlayerProfile pp && pp.getName() != null) {
                     out.add(pp.getName());
                  }
               }
            }
            case "deletetag" -> out.addAll(this.plugin.getTagManager().sorted().keySet());
            case "nick" -> out.add("off");
            default -> {
            }
         }
      } else if (args.length == 2) {
         switch (name) {
            case "tempmute", "tempban" -> out.addAll(TIMES);
            case "ignore" -> {
               if (args[0].equalsIgnoreCase("remove")) {
                  for (UUID id : this.plugin.getIgnoreManager().getIgnored(((sender instanceof Player p) ? p.getUniqueId() : new UUID(0, 0)))) {
                     String n = Bukkit.getOfflinePlayer(id).getName();
                     if (n != null) {
                        out.add(n);
                     }
                  }
               } else if (args[0].equalsIgnoreCase("add")) {
                  this.onlineNames(sender, out);
               }
            }
            case "nick" -> {
               if (sender.hasPermission("everlynx.chat.admin")) {
                  this.onlineNames(sender, out);
               }
            }
            default -> {
            }
         }
      }
      String current = args[args.length - 1].toLowerCase(Locale.ROOT);
      return out.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(current)).distinct().toList();
   }

   private void onlineNames(CommandSender sender, List<String> out) {
      for (Player p : Bukkit.getOnlinePlayers()) {
         if (!(sender instanceof Player self) || self.canSee(p)) {
            out.add(p.getName());
         }
      }
   }
}

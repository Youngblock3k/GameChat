package me.ryxer.gyxerchat.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import me.ryxer.gyxerchat.GyxerChat;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TpaCommands implements CommandExecutor, TabCompleter {
   public static final String[] COMMANDS = {"tpa", "tpahere", "tpaccept", "tpadeny", "tpacancel", "tpatoggle"};
   private final GyxerChat plugin;

   public TpaCommands(GyxerChat plugin) {
      this.plugin = plugin;
   }

   @Override
   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
      if (!(sender instanceof Player player)) {
         this.plugin.send(sender, "players-only");
         return true;
      }
      var tpa = this.plugin.getTpaManager();
      if (!tpa.enabled()) {
         this.plugin.send(player, "tpa-disabled");
         return true;
      }
      String name = cmd.getName().toLowerCase(Locale.ROOT);
      switch (name) {
         case "tpatoggle" -> tpa.toggleAutoAccept(player);
         case "tpacancel" -> {
            List<String> names = tpa.cancelFrom(player);
            if (names.isEmpty()) {
               this.plugin.send(player, "tpa-no-requests");
            } else {
               this.plugin.send(player, "tpa-cancelled", "target", String.join(", ", names));
            }
         }
         case "tpa", "tpahere" -> {
            if (args.length == 0) {
               this.plugin.send(player, "error-usage", "usage", "/" + name + " <player>");
               return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || !player.canSee(target)) {
               this.plugin.send(player, "player-not-found");
            } else if (target.equals(player)) {
               this.plugin.send(player, "tpa-self");
            } else {
               tpa.sendRequest(player, target, name.equals("tpahere"));
            }
         }
         case "tpaccept", "tpadeny" -> {
            boolean accept = name.equals("tpaccept");
            List<UUID> reqs = tpa.getRequests(player);
            if (reqs.isEmpty()) {
               this.plugin.send(player, "tpa-no-requests");
               return true;
            }
            Player from;
            if (args.length == 0) {
               // the newest request
               from = Bukkit.getPlayer(reqs.get(reqs.size() - 1));
            } else {
               from = Bukkit.getPlayer(args[0]);
            }
            if (from == null) {
               this.plugin.send(player, "player-not-found");
               return true;
            }
            boolean ok = accept ? tpa.acceptRequest(player, from) : tpa.denyRequest(player, from);
            if (!ok) {
               this.plugin.send(player, "tpa-no-requests");
            }
         }
         default -> {
            return false;
         }
      }
      return true;
   }

   @Override
   public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, String[] args) {
      if (!(sender instanceof Player player) || args.length != 1) {
         return List.of();
      }
      String name = cmd.getName().toLowerCase(Locale.ROOT);
      List<String> out = new ArrayList<>();
      if (name.equals("tpa") || name.equals("tpahere")) {
         for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(player) && player.canSee(p)) {
               out.add(p.getName());
            }
         }
      } else if (name.equals("tpaccept") || name.equals("tpadeny")) {
         for (UUID uuid : this.plugin.getTpaManager().getRequests(player)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
               out.add(p.getName());
            }
         }
      }
      String current = args[0].toLowerCase(Locale.ROOT);
      return out.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(current)).toList();
   }
}

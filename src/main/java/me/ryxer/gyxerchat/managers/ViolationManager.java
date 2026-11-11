package me.ryxer.gyxerchat.managers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.ryxer.gyxerchat.GyxerChat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Strikes for swearing/spam. Too many strikes run the punish command. Safe from the chat thread. */
public class ViolationManager {
   private record Strikes(int count, long last) {
   }

   private final GyxerChat plugin;
   private final Map<UUID, Strikes> strikes = new ConcurrentHashMap<>();

   public ViolationManager(GyxerChat plugin) {
      this.plugin = plugin;
   }

   public void addStrike(Player player, String reason) {
      var cfg = this.plugin.cfg();
      if (!cfg.getBoolean("auto-punish.enabled", true) || player.hasPermission("everlynx.chat.admin")) {
         return;
      }
      long now = System.currentTimeMillis();
      long resetMs = cfg.getLong("auto-punish.strike-reset-minutes", 30L) * 60_000L;
      int max = Math.max(1, cfg.getInt("auto-punish.max-strikes", 3));
      Strikes s = this.strikes.compute(player.getUniqueId(), (id, old) -> {
         int c = old == null || (resetMs > 0 && now - old.last() > resetMs) ? 1 : old.count() + 1;
         return new Strikes(c, now);
      });
      if (s.count() >= max) {
         this.strikes.remove(player.getUniqueId());
         String cmd = cfg.getString("auto-punish.punish-command", "tempmute {player} 10m Auto-Punish: Max Violations").replace("{player}", player.getName());
         this.plugin.runSync(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.startsWith("/") ? cmd.substring(1) : cmd));
         this.plugin.getDiscordManager().sendLog("Auto-Punish Triggered", "Player: " + player.getName() + "\nReason: Hit " + max + " strikes for " + reason);
      } else {
         player.sendMessage(this.plugin.msg("strike", "reason", reason, "strikes", s.count(), "max", max));
         this.plugin.playAudio(player, "fail");
      }
   }

   public void forget(UUID id) {
      this.strikes.remove(id);
   }
}

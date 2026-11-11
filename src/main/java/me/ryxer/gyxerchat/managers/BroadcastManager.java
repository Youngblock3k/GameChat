package me.ryxer.gyxerchat.managers;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import me.ryxer.gyxerchat.GyxerChat;
import me.ryxer.gyxerchat.Keys;
import me.ryxer.gyxerchat.utils.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** Timed announcements. Players can hide them in /settings. */
public class BroadcastManager {
   private final GyxerChat plugin;
   private BukkitTask task;
   private int currentIndex = 0;

   public BroadcastManager(GyxerChat plugin) {
      this.plugin = plugin;
   }

   public void start() {
      this.stop();
      var cfg = this.plugin.cfg();
      if (!cfg.getBoolean("auto-broadcaster.enabled", false)) {
         return;
      }
      List<String> messages = cfg.getStringList("auto-broadcaster.messages");
      if (messages.isEmpty()) {
         return;
      }
      long seconds = Math.max(5L, cfg.getLong("auto-broadcaster.interval-seconds", 180L));
      boolean random = cfg.getBoolean("auto-broadcaster.random-order", false);
      this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
         if (random) {
            this.currentIndex = ThreadLocalRandom.current().nextInt(messages.size());
         } else if (this.currentIndex >= messages.size()) {
            this.currentIndex = 0;
         }
         this.send(messages.get(this.currentIndex++));
      }, seconds * 20L, seconds * 20L);
      this.plugin.getLogger().info("Auto-Broadcaster started with an interval of " + seconds + "s.");
   }

   public void send(String raw) {
      Component msg = Text.parse(raw);
      for (Player p : Bukkit.getOnlinePlayers()) {
         if (!this.plugin.prefs().get(p.getUniqueId()).has(Keys.BROADCASTS_DISABLED)) {
            p.sendMessage(msg);
         }
      }
      Bukkit.getConsoleSender().sendMessage(msg);
      if (this.plugin.cfg().getBoolean("auto-broadcaster.discord-sync", false)) {
         this.plugin.getDiscordManager().sendChatMessage(Text.strip(raw));
      }
   }

   public void stop() {
      if (this.task != null) {
         this.task.cancel();
         this.task = null;
      }
   }

   public void reload() {
      this.currentIndex = 0;
      this.start();
   }
}

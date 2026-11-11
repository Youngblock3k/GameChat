package me.ryxer.gyxerchat.listeners;

import me.ryxer.gyxerchat.GyxerChat;
import me.ryxer.gyxerchat.Keys;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Join, quit and death messages, nicknames on join, cleanup on quit. */
public class PlayerListener implements Listener {
   private final GyxerChat plugin;

   public PlayerListener(GyxerChat plugin) {
      this.plugin = plugin;
   }

   private void announce(Component msg) {
      for (Player p : Bukkit.getOnlinePlayers()) {
         if (!this.plugin.prefs().get(p.getUniqueId()).has(Keys.JOINLEAVE_DISABLED)) {
            p.sendMessage(msg);
         }
      }
      Bukkit.getConsoleSender().sendMessage(msg);
   }

   private Component line(Player player, String path, Component reason) {
      String raw = this.plugin.papi(player, this.plugin.rawMsg(path));
      if (raw.isBlank()) {
         return null;
      }
      return reason == null
         ? this.plugin.styledTemplate(raw, "player", this.plugin.displayName(player))
         : this.plugin.styledTemplate(raw, "player", this.plugin.displayName(player), "reason", reason);
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onJoinEarly(PlayerJoinEvent event) {
      this.plugin.prefs().load(event.getPlayer());
      this.plugin.applyNick(event.getPlayer());
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      this.plugin.getDiscordManager().sendEvent(player, "discord.formats.join", "**+ {player}** joined the server.");
      if (this.plugin.cfg().getBoolean("custom-messages.join-leave-enabled", true)) {
         Component msg = this.line(player, "join", null);
         if (msg != null) {
            event.joinMessage(null);
            this.announce(msg);
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      this.plugin.getDiscordManager().sendEvent(player, "discord.formats.quit", "**- {player}** left the server.");
      if (this.plugin.cfg().getBoolean("custom-messages.join-leave-enabled", true)) {
         Component msg = this.line(player, "quit", null);
         if (msg != null) {
            event.quitMessage(null);
            this.announce(msg);
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onQuitCleanup(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      this.plugin.getTpaManager().clearRequests(player);
      this.plugin.getRecentMessages().remove(player.getUniqueId());
      this.plugin.getViolationManager().forget(player.getUniqueId());
      this.plugin.prefs().unload(player.getUniqueId());
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onDeath(PlayerDeathEvent event) {
      Player player = event.getEntity();
      if (event.deathMessage() == null) {
         return;
      }
      this.plugin.getDiscordManager().sendEvent(player, "discord.formats.death", "☠ **{player}** died.");
      if (this.plugin.cfg().getBoolean("custom-messages.death-enabled", true)) {
         Component msg = this.line(player, "death", event.deathMessage());
         if (msg != null) {
            event.deathMessage(null);
            this.announce(msg);
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onDamage(EntityDamageEvent event) {
      if (event.getEntity() instanceof Player p && this.plugin.getTpaManager().isCountingDown(p.getUniqueId())) {
         this.plugin.getTpaManager().onDamage(p);
      }
   }
}

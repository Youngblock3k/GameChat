package me.ryxer.gyxerchat.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.ryxer.gyxerchat.GyxerChat;
import me.ryxer.gyxerchat.Keys;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/** /tpa and /tpahere. Main thread only. */
public class TpaManager {
   private static final class Request {
      final boolean here;
      BukkitTask expiry;

      Request(boolean here) {
         this.here = here;
      }
   }

   private final GyxerChat plugin;
   /** target -> (sender -> request), oldest first */
   private final Map<UUID, LinkedHashMap<UUID, Request>> requests = new HashMap<>();
   /** player who is waiting to be teleported -> countdown */
   private final Map<UUID, BukkitTask> countdowns = new HashMap<>();

   public TpaManager(GyxerChat plugin) {
      this.plugin = plugin;
   }

   public boolean enabled() {
      return this.plugin.cfg().getBoolean("tpa-settings.enabled", true);
   }

   public void sendRequest(Player sender, Player target, boolean here) {
      var prefs = this.plugin.prefs().get(target.getUniqueId());
      if (!here && prefs.has(Keys.TPA_DISABLED) || here && prefs.has(Keys.TPAHERE_DISABLED)
         || this.plugin.getIgnoreManager().isIgnoring(target.getUniqueId(), sender.getUniqueId())) {
         this.plugin.send(sender, "tpa-target-disabled", "target", target.getName());
         return;
      }
      if (!this.worldAllowed(sender, target)) {
         this.plugin.send(sender, "tpa-other-world");
         return;
      }
      if (prefs.has(Keys.TPA_AUTO_ACCEPT)) {
         this.plugin.send(sender, "tpa-auto-accept", "target", target.getName());
         this.startTeleport(sender, target, here);
         return;
      }
      LinkedHashMap<UUID, Request> forTarget = this.requests.computeIfAbsent(target.getUniqueId(), k -> new LinkedHashMap<>());
      Request old = forTarget.remove(sender.getUniqueId());
      if (old != null && old.expiry != null) {
         old.expiry.cancel();
      }
      Request req = new Request(here);
      forTarget.put(sender.getUniqueId(), req);
      UUID senderId = sender.getUniqueId();
      UUID targetId = target.getUniqueId();
      String targetName = target.getName();
      long expire = Math.max(5L, this.plugin.cfg().getLong("tpa-settings.expire-seconds", 60L)) * 20L;
      req.expiry = Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         if (this.remove(targetId, senderId) != null) {
            Player s = Bukkit.getPlayer(senderId);
            if (s != null) {
               this.plugin.send(s, "tpa-expired", "target", targetName);
            }
         }
      }, expire);
      this.plugin.send(sender, "tpa-sent", "target", target.getName());
      Component type = this.plugin.getParsedMsg(false, here ? "tpa-type-here" : "tpa-type-to");
      this.plugin.send(target, "tpa-received", "sender", sender.getName(), "type", type);
      this.plugin.playAudio(target, "mention");
   }

   private boolean worldAllowed(Player a, Player b) {
      return a.getWorld().equals(b.getWorld()) || this.plugin.cfg().getBoolean("tpa-settings.allow-cross-world", true);
   }

   private Request remove(UUID target, UUID sender) {
      LinkedHashMap<UUID, Request> m = this.requests.get(target);
      if (m == null) {
         return null;
      }
      Request r = m.remove(sender);
      if (m.isEmpty()) {
         this.requests.remove(target);
      }
      if (r != null && r.expiry != null) {
         r.expiry.cancel();
      }
      return r;
   }

   public boolean acceptRequest(Player target, Player sender) {
      Request r = this.remove(target.getUniqueId(), sender.getUniqueId());
      if (r == null) {
         return false;
      }
      if (!this.worldAllowed(sender, target)) {
         this.plugin.send(target, "tpa-other-world");
         return true;
      }
      this.plugin.send(target, "tpa-accepted-target", "sender", sender.getName());
      this.startTeleport(sender, target, r.here);
      return true;
   }

   public boolean denyRequest(Player target, Player sender) {
      if (this.remove(target.getUniqueId(), sender.getUniqueId()) == null) {
         return false;
      }
      this.plugin.send(sender, "tpa-denied-sender", "target", target.getName());
      this.plugin.send(target, "tpa-denied-target", "sender", sender.getName());
      return true;
   }

   /** /tpacancel: the sender takes back their requests. Returns the names of the players it was sent to. */
   public List<String> cancelFrom(Player sender) {
      List<String> names = new ArrayList<>();
      for (UUID target : new ArrayList<>(this.requests.keySet())) {
         if (this.remove(target, sender.getUniqueId()) != null) {
            Player t = Bukkit.getPlayer(target);
            names.add(t != null ? t.getName() : "?");
         }
      }
      return names;
   }

   /** Senders who asked this player, oldest first, still online. */
   public List<UUID> getRequests(Player player) {
      LinkedHashMap<UUID, Request> m = this.requests.get(player.getUniqueId());
      List<UUID> out = new ArrayList<>();
      if (m != null) {
         for (UUID id : m.keySet()) {
            if (Bukkit.getPlayer(id) != null) {
               out.add(id);
            }
         }
      }
      return out;
   }

   /** A player left: drop their requests both ways and stop their countdown. */
   public void clearRequests(Player player) {
      UUID id = player.getUniqueId();
      LinkedHashMap<UUID, Request> mine = this.requests.remove(id);
      if (mine != null) {
         mine.values().forEach(r -> {
            if (r.expiry != null) {
               r.expiry.cancel();
            }
         });
      }
      for (UUID target : new ArrayList<>(this.requests.keySet())) {
         this.remove(target, id);
      }
      this.cancelCountdown(id, null);
   }

   public void toggleAutoAccept(Player player) {
      boolean on = this.plugin.prefs().toggle(player, Keys.TPA_AUTO_ACCEPT);
      this.plugin.send(player, on ? "tpa-toggle-on" : "tpa-toggle-off");
   }

   /** Called when a waiting player takes damage. */
   public void onDamage(Player player) {
      if (this.plugin.cfg().getBoolean("tpa-settings.cancel-on-damage", true)) {
         this.cancelCountdown(player.getUniqueId(), "tpa-damaged");
      }
   }

   private void cancelCountdown(UUID id, String messageKey) {
      BukkitTask t = this.countdowns.remove(id);
      if (t != null) {
         t.cancel();
         Player p = Bukkit.getPlayer(id);
         if (p != null) {
            p.sendActionBar(Component.empty());
            if (messageKey != null) {
               this.plugin.send(p, messageKey);
            }
         }
      }
   }

   public void cancelAll() {
      for (UUID id : new ArrayList<>(this.countdowns.keySet())) {
         this.cancelCountdown(id, null);
      }
      for (LinkedHashMap<UUID, Request> m : this.requests.values()) {
         for (Request r : m.values()) {
            if (r.expiry != null) {
               r.expiry.cancel();
            }
         }
      }
      this.requests.clear();
   }

   /** p1 = the one who asked, p2 = the one who was asked. */
   private void startTeleport(Player p1, Player p2, boolean here) {
      final Player teleporter = here ? p2 : p1;
      final Player destination = here ? p1 : p2;
      final int cooldown = Math.max(0, this.plugin.cfg().getInt("tpa-settings.cooldown-seconds", 5));
      final Location startLoc = teleporter.getLocation();
      this.cancelCountdown(teleporter.getUniqueId(), null);
      if (cooldown > 0) {
         this.plugin.send(teleporter, "tpa-starting", "target", destination.getName(), "time", cooldown);
      }
      BukkitRunnable run = new BukkitRunnable() {
         int time = cooldown;

         @Override
         public void run() {
            if (!teleporter.isOnline() || !destination.isOnline()) {
               TpaManager.this.countdowns.remove(teleporter.getUniqueId());
               this.cancel();
               return;
            }
            Location now = teleporter.getLocation();
            if (!now.getWorld().equals(startLoc.getWorld()) || now.distanceSquared(startLoc) > 1.0) {
               TpaManager.this.cancelCountdown(teleporter.getUniqueId(), "tpa-moved");
               return;
            }
            if (this.time > 0) {
               teleporter.sendActionBar(TpaManager.this.plugin.styled("tpa-actionbar", "target", destination.getName(), "time", this.time));
               TpaManager.this.plugin.playAudio(teleporter, "tpa-tick");
               this.time--;
               return;
            }
            TpaManager.this.countdowns.remove(teleporter.getUniqueId());
            this.cancel();
            if (!TpaManager.this.worldAllowed(teleporter, destination)) {
               TpaManager.this.plugin.send(teleporter, "tpa-other-world");
               return;
            }
            teleporter.sendActionBar(Component.empty());
            teleporter.teleportAsync(destination.getLocation(), PlayerTeleportEvent.TeleportCause.COMMAND).thenAccept(ok -> {
               if (ok) {
                  TpaManager.this.plugin.playAudio(teleporter, "tpa-success");
               }
            });
         }
      };
      this.countdowns.put(teleporter.getUniqueId(), run.runTaskTimer(this.plugin, 0L, 20L));
   }

   public boolean isCountingDown(UUID id) {
      return this.countdowns.containsKey(id);
   }
}

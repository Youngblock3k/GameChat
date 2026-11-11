package me.ryxer.gyxerchat.managers;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.ryxer.gyxerchat.GyxerChat;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Mutes, saved in mutes.yml (same layout as version 1, plus the player's name). Safe to read from the chat thread. */
public class MuteManager {
   public record Mute(long expiry, String reason, String name) {
      public boolean expired() {
         return this.expiry != -1L && System.currentTimeMillis() > this.expiry;
      }
   }

   private final GyxerChat plugin;
   private final File file;
   private final Map<UUID, Mute> mutes = new ConcurrentHashMap<>();

   public MuteManager(GyxerChat plugin) {
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), "mutes.yml");
      this.loadMutes();
   }

   /** durationMs -1 = forever. */
   public void setMute(UUID uuid, String name, String reason, long durationMs) {
      long expiry = durationMs < 0L ? -1L : System.currentTimeMillis() + durationMs;
      this.mutes.put(uuid, new Mute(expiry, reason == null || reason.isBlank() ? "No reason provided." : reason, name));
      this.saveMutes();
   }

   public boolean removeMute(UUID uuid) {
      boolean had = this.mutes.remove(uuid) != null;
      if (had) {
         this.saveMutes();
      }
      return had;
   }

   public boolean isMuted(UUID uuid) {
      Mute m = this.mutes.get(uuid);
      if (m == null) {
         return false;
      }
      if (m.expired()) {
         this.mutes.remove(uuid, m);
         return false;
      }
      return true;
   }

   public Mute get(UUID uuid) {
      return this.isMuted(uuid) ? this.mutes.get(uuid) : null;
   }

   public long getExpiry(UUID uuid) {
      Mute m = this.mutes.get(uuid);
      return m == null ? -1L : m.expiry();
   }

   public String getReason(UUID uuid) {
      Mute m = this.mutes.get(uuid);
      return m == null ? "No reason provided." : m.reason();
   }

   public Set<UUID> getAllMutedUUIDs() {
      this.mutes.entrySet().removeIf(e -> e.getValue().expired());
      return Set.copyOf(this.mutes.keySet());
   }

   public void loadMutes() {
      this.mutes.clear();
      if (!this.file.exists()) {
         return;
      }
      YamlConfiguration data = YamlConfiguration.loadConfiguration(this.file);
      ConfigurationSection sec = data.getConfigurationSection("mutes");
      if (sec == null) {
         return;
      }
      for (String key : sec.getKeys(false)) {
         try {
            UUID uuid = UUID.fromString(key);
            Mute m = new Mute(sec.getLong(key + ".expiry", -1L), sec.getString(key + ".reason", "No reason provided."), sec.getString(key + ".name"));
            if (!m.expired()) {
               this.mutes.put(uuid, m);
            }
         } catch (IllegalArgumentException e) {
            this.plugin.getLogger().warning("mutes.yml: skipped bad entry " + key);
         }
      }
   }

   public void saveMutes() {
      YamlConfiguration data = new YamlConfiguration();
      for (Map.Entry<UUID, Mute> e : this.mutes.entrySet()) {
         String base = "mutes." + e.getKey();
         data.set(base + ".expiry", e.getValue().expiry());
         data.set(base + ".reason", e.getValue().reason());
         if (e.getValue().name() != null) {
            data.set(base + ".name", e.getValue().name());
         }
      }
      try {
         data.save(this.file);
      } catch (IOException ex) {
         this.plugin.getLogger().warning("Could not save mutes.yml: " + ex.getMessage());
      }
   }
}

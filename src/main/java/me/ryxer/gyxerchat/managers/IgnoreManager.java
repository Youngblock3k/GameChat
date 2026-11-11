package me.ryxer.gyxerchat.managers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.ryxer.gyxerchat.GyxerChat;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Who ignores whom, saved in ignores.yml right away (version 1 only saved on shutdown). */
public class IgnoreManager {
   private final GyxerChat plugin;
   private final File file;
   private final Map<UUID, Set<UUID>> ignores = new ConcurrentHashMap<>();

   public IgnoreManager(GyxerChat plugin) {
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), "ignores.yml");
      this.loadIgnores();
   }

   public boolean isIgnoring(UUID ignorer, UUID target) {
      Set<UUID> s = this.ignores.get(ignorer);
      return s != null && s.contains(target);
   }

   public List<UUID> getIgnored(UUID ignorer) {
      Set<UUID> s = this.ignores.get(ignorer);
      return s == null ? List.of() : new ArrayList<>(s);
   }

   /** Returns true when the target is now ignored. */
   public boolean toggleIgnore(UUID ignorer, UUID target) {
      Set<UUID> s = this.ignores.computeIfAbsent(ignorer, k -> ConcurrentHashMap.newKeySet());
      boolean now;
      if (s.remove(target)) {
         now = false;
      } else {
         s.add(target);
         now = true;
      }
      this.saveIgnores();
      return now;
   }

   public void loadIgnores() {
      this.ignores.clear();
      if (!this.file.exists()) {
         return;
      }
      YamlConfiguration data = YamlConfiguration.loadConfiguration(this.file);
      ConfigurationSection sec = data.getConfigurationSection("ignores");
      if (sec == null) {
         return;
      }
      for (String key : sec.getKeys(false)) {
         try {
            Set<UUID> set = ConcurrentHashMap.newKeySet();
            for (String s : sec.getStringList(key)) {
               set.add(UUID.fromString(s));
            }
            this.ignores.put(UUID.fromString(key), set);
         } catch (IllegalArgumentException e) {
            this.plugin.getLogger().warning("ignores.yml: skipped bad entry " + key);
         }
      }
   }

   public void saveIgnores() {
      YamlConfiguration data = new YamlConfiguration();
      for (Map.Entry<UUID, Set<UUID>> e : this.ignores.entrySet()) {
         if (e.getValue().isEmpty()) {
            continue;
         }
         List<String> list = new ArrayList<>();
         for (UUID u : e.getValue()) {
            list.add(u.toString());
         }
         data.set("ignores." + e.getKey(), list);
      }
      try {
         data.save(this.file);
      } catch (IOException ex) {
         this.plugin.getLogger().warning("Could not save ignores.yml: " + ex.getMessage());
      }
   }
}

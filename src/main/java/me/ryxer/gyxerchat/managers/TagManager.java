package me.ryxer.gyxerchat.managers;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import me.ryxer.gyxerchat.GyxerChat;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Chat tags, saved in tags.yml. IDs are always lowercase. */
public class TagManager {
   private final GyxerChat plugin;
   private final File file;
   private final Map<String, String> tags = new ConcurrentHashMap<>();

   public TagManager(GyxerChat plugin) {
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), "tags.yml");
      this.loadTags();
   }

   public static boolean validId(String id) {
      return id != null && id.matches("[A-Za-z0-9_-]{1,32}");
   }

   public void createTag(String id, String display) {
      this.tags.put(id.toLowerCase(Locale.ROOT), display);
      this.saveTags();
   }

   public boolean deleteTag(String id) {
      boolean had = this.tags.remove(id.toLowerCase(Locale.ROOT)) != null;
      this.saveTags();
      return had;
   }

   /** Read-only view, sorted by ID. */
   public Map<String, String> getTags() {
      return java.util.Collections.unmodifiableMap(this.tags);
   }

   public Map<String, String> sorted() {
      return new TreeMap<>(this.tags);
   }

   public void loadTags() {
      this.tags.clear();
      if (!this.file.exists()) {
         return;
      }
      YamlConfiguration data = YamlConfiguration.loadConfiguration(this.file);
      ConfigurationSection sec = data.getConfigurationSection("tags");
      if (sec == null) {
         return;
      }
      for (String key : sec.getKeys(false)) {
         String display = sec.getString(key);
         if (display != null) {
            this.tags.put(key.toLowerCase(Locale.ROOT), display);
         }
      }
   }

   public void saveTags() {
      YamlConfiguration data = new YamlConfiguration();
      for (Map.Entry<String, String> e : this.sorted().entrySet()) {
         data.set("tags." + e.getKey(), e.getValue());
      }
      try {
         data.save(this.file);
      } catch (IOException ex) {
         this.plugin.getLogger().warning("Could not save tags.yml: " + ex.getMessage());
      }
   }
}

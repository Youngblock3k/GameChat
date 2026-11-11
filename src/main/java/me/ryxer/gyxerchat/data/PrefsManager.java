package me.ryxer.gyxerchat.data;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.ryxer.gyxerchat.GyxerChat;
import me.ryxer.gyxerchat.Keys;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Player settings live in the player's own data (same keys as version 1). A copy is kept in memory so the
 * chat thread can read it safely without touching the player object.
 */
public final class PrefsManager {
   /** A player's settings. Fields are replaced, never edited, so other threads always see a complete value. */
   public static final class Prefs {
      public volatile String nickname;
      public volatile String tag;
      public volatile String tagId;
      public volatile String chatColor;
      public final Set<String> flags = ConcurrentHashMap.newKeySet();

      public boolean has(String flag) {
         return this.flags.contains(flag);
      }
   }

   private static final Prefs EMPTY = new Prefs();
   private final GyxerChat plugin;
   private final Map<UUID, Prefs> cache = new ConcurrentHashMap<>();

   public PrefsManager(GyxerChat plugin) {
      this.plugin = plugin;
   }

   /** Main thread: read the player's saved data into memory. */
   public Prefs load(Player player) {
      PersistentDataContainer pdc = player.getPersistentDataContainer();
      Keys k = this.plugin.keys();
      Prefs p = new Prefs();
      p.nickname = pdc.get(k.nickname, PersistentDataType.STRING);
      p.tag = pdc.get(k.chatTag, PersistentDataType.STRING);
      p.tagId = pdc.get(k.chatTagId, PersistentDataType.STRING);
      p.chatColor = pdc.get(k.chatColor, PersistentDataType.STRING);
      for (String f : Keys.FLAGS) {
         if (pdc.has(k.flag(f), PersistentDataType.BYTE)) {
            p.flags.add(f);
         }
      }
      this.cache.put(player.getUniqueId(), p);
      return p;
   }

   public void unload(UUID id) {
      this.cache.remove(id);
   }

   /** Any thread. Never null. */
   public Prefs get(UUID id) {
      return this.cache.getOrDefault(id, EMPTY);
   }

   public Prefs get(Player player) {
      Prefs p = this.cache.get(player.getUniqueId());
      return p != null ? p : (player.isOnline() && org.bukkit.Bukkit.isPrimaryThread() ? this.load(player) : EMPTY);
   }

   private Prefs edit(Player player) {
      Prefs p = this.cache.get(player.getUniqueId());
      return p != null ? p : this.load(player);
   }

   /** Main thread. Turns an on/off setting around; returns true when it is now stored ("on"). */
   public boolean toggle(Player player, String flag) {
      boolean now = !this.edit(player).has(flag);
      this.setFlag(player, flag, now);
      return now;
   }

   public void setFlag(Player player, String flag, boolean on) {
      Prefs p = this.edit(player);
      if (on) {
         player.getPersistentDataContainer().set(this.plugin.keys().flag(flag), PersistentDataType.BYTE, (byte)1);
         p.flags.add(flag);
      } else {
         player.getPersistentDataContainer().remove(this.plugin.keys().flag(flag));
         p.flags.remove(flag);
      }
   }

   public void setNickname(Player player, String nick) {
      this.edit(player).nickname = nick;
      this.store(player, this.plugin.keys().nickname, nick);
   }

   public void setTag(Player player, String id, String display) {
      Prefs p = this.edit(player);
      p.tagId = id;
      p.tag = display;
      this.store(player, this.plugin.keys().chatTagId, id);
      this.store(player, this.plugin.keys().chatTag, display);
   }

   public void setChatColor(Player player, String color) {
      this.edit(player).chatColor = color;
      this.store(player, this.plugin.keys().chatColor, color);
   }

   private void store(Player player, org.bukkit.NamespacedKey key, String value) {
      if (value == null) {
         player.getPersistentDataContainer().remove(key);
      } else {
         player.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
      }
   }
}

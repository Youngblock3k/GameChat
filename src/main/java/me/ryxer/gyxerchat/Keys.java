package me.ryxer.gyxerchat;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Player data keys. The names are the same as in version 1, so saved nicknames, tags and settings keep working. */
public final class Keys {
   public final NamespacedKey nickname;
   public final NamespacedKey chatTag;
   public final NamespacedKey chatTagId;
   public final NamespacedKey chatColor;
   public final NamespacedKey menuValue;

   // On/off settings from /settings (a stored byte means "on")
   public static final String CHAT_DISABLED = "chat_disabled";
   public static final String PMS_DISABLED = "pms_disabled";
   public static final String MENTION_SOUND_DISABLED = "mention_sound_disabled";
   public static final String JOINLEAVE_DISABLED = "joinleave_disabled";
   public static final String BROADCASTS_DISABLED = "broadcasts_disabled";
   public static final String CHATGAMES_DISABLED = "chatgames_disabled";
   public static final String TPA_DISABLED = "tpa_disabled";
   public static final String TPAHERE_DISABLED = "tpahere_disabled";
   public static final String TPA_AUTO_ACCEPT = "tpatoggle";
   public static final String[] FLAGS = {
      CHAT_DISABLED, PMS_DISABLED, MENTION_SOUND_DISABLED, JOINLEAVE_DISABLED, BROADCASTS_DISABLED,
      CHATGAMES_DISABLED, TPA_DISABLED, TPAHERE_DISABLED, TPA_AUTO_ACCEPT
   };

   private final Plugin plugin;

   Keys(Plugin plugin) {
      this.plugin = plugin;
      this.nickname = new NamespacedKey(plugin, "nickname");
      this.chatTag = new NamespacedKey(plugin, "chattag");
      this.chatTagId = new NamespacedKey(plugin, "chattag_id");
      this.chatColor = new NamespacedKey(plugin, "chatcolor");
      this.menuValue = new NamespacedKey(plugin, "menu_value");
   }

   public NamespacedKey flag(String name) {
      return new NamespacedKey(this.plugin, name);
   }
}

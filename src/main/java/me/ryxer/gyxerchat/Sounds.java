package me.ryxer.gyxerchat;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.Locale;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

/** Finds a sound by its old name (ENTITY_VILLAGER_YES) or its id (minecraft:entity.villager.yes). */
final class Sounds {
   static final Key NONE = Key.key("gyxerchat", "none");

   private Sounds() {
   }

   static Key find(String name) {
      String n = name.trim();
      if (n.indexOf(':') >= 0 || n.indexOf('.') >= 0) {
         try {
            return Key.key(n.toLowerCase(Locale.ROOT));
         } catch (Exception e) {
            return NONE;
         }
      }
      String wanted = n.toUpperCase(Locale.ROOT);
      try {
         Registry<Sound> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.SOUND_EVENT);
         for (Sound s : registry) {
            NamespacedKey key = registry.getKey(s);
            if (key != null && key.getKey().replace('.', '_').toUpperCase(Locale.ROOT).equals(wanted)) {
               return key;
            }
         }
      } catch (Throwable ignored) {
      }
      GyxerChat.getInstance().getLogger().warning("Unknown sound in config.yml: " + name);
      return NONE;
   }
}

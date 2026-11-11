package me.ryxer.gyxerchat.utils;

import com.destroystokyo.paper.profile.PlayerProfile;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Finds a player by name without freezing the server. Version 1 used Bukkit.getOfflinePlayer(name), which can
 * block the main thread on a web lookup and gives a fake player for typos.
 */
public final class Lookup {
   private Lookup() {
   }

   /** Calls back on the main thread with the player, or null if nobody with that name ever existed. */
   public static void player(Plugin plugin, String name, Consumer<OfflinePlayer> callback) {
      Player online = Bukkit.getPlayerExact(name);
      if (online != null) {
         callback.accept(online);
         return;
      }
      OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
      if (cached != null) {
         callback.accept(cached);
         return;
      }
      if (!name.matches("[A-Za-z0-9_]{1,16}")) {
         callback.accept(null);
         return;
      }
      Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
         OfflinePlayer found = null;
         try {
            PlayerProfile profile = Bukkit.createProfile(name);
            if (profile.complete(false) && profile.getId() != null) {
               OfflinePlayer op = Bukkit.getOfflinePlayer(profile.getId());
               // offline-mode servers make up an ID for any name, so there only real visitors count
               if (Bukkit.getOnlineMode() || op.hasPlayedBefore()) {
                  found = op;
               }
            }
         } catch (Throwable ignored) {
         }
         OfflinePlayer result = found;
         if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
         }
      });
   }

   /** Best name we know for a player. */
   public static String name(OfflinePlayer p, String fallback) {
      String n = p.getName();
      return n != null ? n : fallback;
   }
}

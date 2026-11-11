package me.ryxer.gyxerchat.listeners;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.ryxer.gyxerchat.GyxerChat;
import me.ryxer.gyxerchat.Keys;
import me.ryxer.gyxerchat.data.PrefsManager;
import me.ryxer.gyxerchat.managers.MuteManager;
import me.ryxer.gyxerchat.utils.ChatFilters;
import me.ryxer.gyxerchat.utils.Durations;
import me.ryxer.gyxerchat.utils.MessageBuilder;
import me.ryxer.gyxerchat.utils.Text;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Public chat. Paper runs this on a chat thread, so everything here only reads thread-safe data
 * (the config copy, the settings cache, concurrent maps) and hands game-changing work to the main thread.
 */
public class ChatListener implements Listener {
   private record Last(String text, long time) {
   }

   private final GyxerChat plugin;
   private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
   private final Map<UUID, Last> lastMessage = new ConcurrentHashMap<>();

   public ChatListener(GyxerChat plugin) {
      this.plugin = plugin;
   }

   public void forget(UUID id) {
      this.cooldowns.remove(id);
      this.lastMessage.remove(id);
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onChat(AsyncChatEvent event) {
      Player player = event.getPlayer();
      FileConfiguration cfg = this.plugin.cfg();
      boolean admin = player.hasPermission("everlynx.chat.admin");
      String text = Text.plain(event.message());

      if (cfg.getBoolean("chat-settings.block-unicode", true) && !admin) {
         text = ChatFilters.removeBlockedUnicode(text, cfg.getString("chat-settings.allowed-characters", ""));
         if (text.isBlank()) {
            event.setCancelled(true);
            this.plugin.send(player, "blocked-characters");
            return;
         }
      }

      if (this.plugin.getStaffChatPlayers().contains(player.getUniqueId())) {
         event.setCancelled(true);
         this.plugin.sendStaffChat(player, text);
         return;
      }
      if (this.plugin.isGlobalChatMuted() && !admin) {
         event.setCancelled(true);
         player.sendMessage(this.plugin.styled("chat-locked"));
         return;
      }
      MuteManager.Mute mute = this.plugin.getMuteManager().get(player.getUniqueId());
      if (mute != null) {
         event.setCancelled(true);
         String time = mute.expiry() == -1L ? this.plugin.rawMsg("time-forever") : Durations.format(mute.expiry() - System.currentTimeMillis());
         player.sendMessage(this.plugin.styled("you-are-muted", "reason", mute.reason(), "time", time));
         return;
      }

      long now = System.currentTimeMillis();
      if (!admin) {
         long cooldown = cfg.getLong("chat-settings.cooldown-ms", 1000L);
         Long last = this.cooldowns.get(player.getUniqueId());
         if (last != null && now - last < cooldown) {
            event.setCancelled(true);
            if (cfg.getBoolean("auto-punish.enabled", true)) {
               this.plugin.getViolationManager().addStrike(player, "Spamming");
            } else {
               this.plugin.send(player, "chat-cooldown");
            }
            return;
         }
         long repeatMs = cfg.getLong("chat-settings.repeat-block-seconds", 20L) * 1000L;
         Last prev = this.lastMessage.get(player.getUniqueId());
         if (repeatMs > 0 && prev != null && now - prev.time() < repeatMs && prev.text().equalsIgnoreCase(text.trim())) {
            event.setCancelled(true);
            this.plugin.send(player, "chat-repeat");
            return;
         }
      }
      this.cooldowns.put(player.getUniqueId(), now);
      this.lastMessage.put(player.getUniqueId(), new Last(text.trim(), now));

      if (!admin && cfg.getBoolean("chat-settings.anti-caps-enabled", true)
         && ChatFilters.isShouting(text, cfg.getInt("chat-settings.anti-caps-min-length", 5), cfg.getInt("chat-settings.anti-caps-percent", 70))) {
         text = ChatFilters.calmDown(text);
      }
      if (cfg.getBoolean("chat-settings.swear-filter-enabled", true)) {
         String censored = ChatFilters.censor(text, this.plugin.blockedWords());
         if (censored != null) {
            text = censored;
            this.plugin.getViolationManager().addStrike(player, "Using blocked language");
         }
      }

      // From here on the message is really sent.
      this.plugin.getGameManager().checkAnswer(player, Text.strip(text));

      List<String> mentioned = new ArrayList<>();
      if (cfg.getBoolean("chat-settings.mentions-enabled", true)) {
         for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(player) || !ChatFilters.mentionPattern(target.getName()).matcher(text).find()) {
               continue;
            }
            mentioned.add(target.getName());
            PrefsManager.Prefs tp = this.plugin.prefs().get(target.getUniqueId());
            if (!tp.has(Keys.MENTION_SOUND_DISABLED) && !tp.has(Keys.CHAT_DISABLED)
               && !this.plugin.getIgnoreManager().isIgnoring(target.getUniqueId(), player.getUniqueId())) {
               this.plugin.playAudio(target, "mention");
            }
         }
      }

      PrefsManager.Prefs prefs = this.plugin.prefs().get(player.getUniqueId());
      boolean codes = cfg.getBoolean("chat-settings.allow-color-codes", true) && player.hasPermission("gyxerchat.chat.colors");
      Component message = MessageBuilder.build(text, prefs.chatColor, codes, mentioned, cfg.getString("chat-settings.mention-color", "&b"));

      this.plugin.getDiscordManager().sendChatMessage(player, Text.plain(Text.playerText(text, codes)));

      Component name = this.plugin.displayName(player);
      Component tag = this.plugin.tagOf(player);
      Component playerComp = name;
      if (cfg.getBoolean("interactive-chat.enabled", true)) {
         String hover = this.plugin.papi(player, String.join("\n", cfg.getStringList("interactive-chat.hover-text")));
         Component hoverComp = Text.render(hover, false, Map.of(
            "player", name,
            "health", Component.text(Math.round(player.getHealth())),
            "ping", Component.text(player.getPing()),
            "world", Component.text(player.getWorld().getName()),
            "tag", tag == null ? Text.parse("&7None") : tag));
         String click = cfg.getString("interactive-chat.click-command", "/msg {player} ").replace("{player}", player.getName());
         playerComp = name.hoverEvent(HoverEvent.showText(hoverComp)).clickEvent(ClickEvent.suggestCommand(click));
      }
      String format = this.plugin.papi(player, cfg.getString("chat-format", "{tag}&7{player} &8» &f{message}"));
      Component line = Text.render(format, cfg.getBoolean("small-caps", true), Map.of(
         "player", playerComp,
         "tag", tag == null ? Component.empty() : tag.append(Component.space()),
         "message", message));

      event.viewers().removeIf(viewer -> this.hides(viewer, player));
      event.renderer(ChatRenderer.viewerUnaware((source, sourceDisplayName, msg) -> line));
   }

   private boolean hides(Audience viewer, Player sender) {
      if (viewer instanceof Player p) {
         return this.plugin.getIgnoreManager().isIgnoring(p.getUniqueId(), sender.getUniqueId())
            || this.plugin.prefs().get(p.getUniqueId()).has(Keys.CHAT_DISABLED);
      }
      return false;
   }
}

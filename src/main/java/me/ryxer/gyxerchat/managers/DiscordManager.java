package me.ryxer.gyxerchat.managers;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import me.ryxer.gyxerchat.GyxerChat;
import me.ryxer.gyxerchat.Keys;
import me.ryxer.gyxerchat.utils.Text;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.utils.messages.MessageRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * The Discord bot. It connects on its own thread, so the server never waits for Discord (version 1 froze the
 * server until Discord answered). Every method here is safe to call even when the bot is off.
 */
public class DiscordManager extends ListenerAdapter {
   private static final int MAX_DISCORD = 1900;
   private final GyxerChat plugin;
   private volatile JDA jda;
   private volatile TextChannel chatChannel;
   private volatile TextChannel consoleChannel;
   private volatile TextChannel staffChannel;
   private volatile TextChannel logChannel;
   private volatile boolean stopped;
   private Thread connector;
   private Object appender;
   private BukkitTask flushTask;
   private final BlockingQueue<String> consoleQueue = new ArrayBlockingQueue<>(2000);

   public DiscordManager(GyxerChat plugin) {
      this.plugin = plugin;
   }

   private FileConfiguration cfg() {
      return this.plugin.cfg();
   }

   public boolean isConnected() {
      JDA j = this.jda;
      return j != null && j.getStatus() == JDA.Status.CONNECTED && !this.stopped;
   }

   public void connect() {
      if (!this.cfg().getBoolean("discord.enabled", false)) {
         return;
      }
      String token = this.cfg().getString("discord.bot-token", "").trim();
      if (token.isEmpty() || token.equals("YOUR_BOT_TOKEN_HERE")) {
         this.plugin.getLogger().warning("Discord Bot enabled but token is invalid!");
         return;
      }
      if (this.cfg().getBoolean("discord.console-commands.enabled", false) && this.allowedUsers().isEmpty() && this.allowedRoles().isEmpty()) {
         this.plugin.getLogger().warning("discord.console-commands is on, but no allowed-user-ids / allowed-role-ids are set, so nobody can run commands from Discord.");
      }
      MessageRequest.setDefaultMentions(EnumSet.noneOf(Message.MentionType.class));
      this.connector = new Thread(() -> {
         try {
            JDA built = JDABuilder.createDefault(token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
               .disableCache(EnumSet.allOf(CacheFlag.class))
               .setMemberCachePolicy(MemberCachePolicy.NONE)
               .addEventListeners(this)
               .build();
            this.jda = built;
            built.awaitReady();
            if (this.stopped) {
               built.shutdownNow();
               return;
            }
            this.chatChannel = this.channel(built, "discord.chat-channel-id");
            this.consoleChannel = this.channel(built, "discord.console-channel-id");
            this.staffChannel = this.channel(built, "discord.staff-channel-id");
            this.logChannel = this.channel(built, "discord.log-channel-id");
            this.plugin.getLogger().info("Discord Bot Connected Successfully as " + built.getSelfUser().getName() + "!");
            this.plugin.runSync(this::afterConnect);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
         } catch (Throwable e) {
            this.plugin.getLogger().severe("Failed to connect Discord Bot: " + e.getMessage());
            JDA j = this.jda;
            if (j != null) {
               j.shutdownNow();
            }
            this.jda = null;
         }
      }, "GyxerChat-Discord");
      this.connector.setDaemon(true);
      this.connector.start();
   }

   private void afterConnect() {
      if (this.stopped || !this.plugin.isEnabled()) {
         return;
      }
      if (this.consoleChannel != null && this.cfg().getBoolean("discord.console-enabled", true)) {
         this.setupConsoleLogger();
      }
      String start = this.cfg().getString("discord.formats.server-start", "");
      if (!start.isBlank()) {
         this.sendChatMessage(start);
      }
   }

   private TextChannel channel(JDA j, String path) {
      String id = this.cfg().getString(path, "").trim();
      if (id.isEmpty() || id.chars().allMatch(c -> c == '0')) {
         return null;
      }
      try {
         TextChannel ch = j.getTextChannelById(id);
         if (ch == null) {
            this.plugin.getLogger().warning("Discord: channel " + id + " (" + path + ") not found. Is the bot in that server?");
         }
         return ch;
      } catch (NumberFormatException e) {
         this.plugin.getLogger().warning("Discord: " + path + " is not a valid channel ID.");
         return null;
      }
   }

   private List<String> allowedUsers() {
      return this.cfg().getStringList("discord.console-commands.allowed-user-ids");
   }

   private List<String> allowedRoles() {
      return this.cfg().getStringList("discord.console-commands.allowed-role-ids");
   }

   // ------------------------------------------------------------------ console mirror

   private void setupConsoleLogger() {
      try {
         ConsoleLogAppender a = new ConsoleLogAppender(this.consoleQueue);
         a.start();
         org.apache.logging.log4j.Logger root = org.apache.logging.log4j.LogManager.getRootLogger();
         if (root instanceof org.apache.logging.log4j.core.Logger core) {
            core.addAppender(a);
            this.appender = a;
         } else {
            this.plugin.getLogger().warning("Discord console mirror is not supported on this server.");
            return;
         }
      } catch (Throwable t) {
         this.plugin.getLogger().warning("Discord console mirror could not start: " + t);
         return;
      }
      this.flushTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this.plugin, this::flushConsole, 40L, 40L);
   }

   private void flushConsole() {
      TextChannel ch = this.consoleChannel;
      if (!this.isConnected() || ch == null || this.consoleQueue.isEmpty()) {
         return;
      }
      StringBuilder sb = new StringBuilder();
      String line;
      int sent = 0;
      while ((line = this.consoleQueue.poll()) != null && sent < 5) {
         line = line.replace("```", "`​``");
         if (line.length() > MAX_DISCORD) {
            line = line.substring(0, MAX_DISCORD);
         }
         if (sb.length() + line.length() + 1 > MAX_DISCORD) {
            ch.sendMessage("```\n" + sb + "\n```").queue(null, e -> { });
            sent++;
            sb.setLength(0);
         }
         sb.append(line).append('\n');
      }
      if (sb.length() > 0) {
         ch.sendMessage("```\n" + sb + "\n```").queue(null, e -> { });
      }
   }

   // ------------------------------------------------------------------ Minecraft -> Discord

   private boolean chatOn() {
      return this.isConnected() && this.chatChannel != null && this.cfg().getBoolean("discord.chat-enabled", true);
   }

   private static String cut(String s) {
      return s.length() > MAX_DISCORD ? s.substring(0, MAX_DISCORD) + "…" : s;
   }

   /** A finished line (already in Discord format) for the chat channel. */
   public void sendChatMessage(String message) {
      if (this.chatOn()) {
         this.chatChannel.sendMessage(cut(Text.strip(message))).queue(null, e -> { });
      }
   }

   /** A player's chat message. */
   public void sendChatMessage(Player player, String message) {
      if (!this.chatOn()) {
         return;
      }
      String prefix = Text.strip(this.plugin.papi(player, "%vault_prefix%")).trim();
      if (prefix.equals("%vault_prefix%")) {
         prefix = "";
      }
      String path = prefix.isEmpty() ? "discord.formats.mc-to-discord-no-prefix" : "discord.formats.mc-to-discord";
      String format = this.plugin.papi(player, this.cfg().getString(path, "**{prefix} {player}** » {message}"));
      String out = Text.strip(format.replace("{prefix}", MarkdownSanitizer.escape(prefix))
            .replace("{player}", MarkdownSanitizer.escape(this.plugin.getPlayerDisplayName(player)))
            .replace("{tag}", this.tagText(player)))
         .replace("{message}", MarkdownSanitizer.escape(message));
      this.chatChannel.sendMessage(cut(out)).queue(null, e -> { });
   }

   /** Join/quit/death lines. */
   public void sendEvent(Player player, String path, String def) {
      if (!this.chatOn()) {
         return;
      }
      String format = this.plugin.papi(player, this.cfg().getString(path, def));
      if (format.isBlank()) {
         return;
      }
      String out = Text.strip(format.replace("{player}", MarkdownSanitizer.escape(this.plugin.getPlayerDisplayName(player))).replace("{tag}", this.tagText(player)));
      this.chatChannel.sendMessage(cut(out)).queue(null, e -> { });
   }

   private String tagText(Player player) {
      Component tag = this.plugin.tagOf(player);
      String t = tag == null ? "" : Text.plain(tag).trim();
      return t.isEmpty() ? "" : MarkdownSanitizer.escape(t) + " ";
   }

   public void sendStaff(Player player, String message) {
      TextChannel ch = this.staffChannel;
      if (!this.isConnected() || ch == null) {
         return;
      }
      String format = this.cfg().getString("discord.formats.staff-mc-to-discord", "**[Staff] {player}** » {message}");
      String name = player == null ? "Console" : this.plugin.getPlayerDisplayName(player);
      ch.sendMessage(cut(format.replace("{player}", MarkdownSanitizer.escape(name)).replace("{message}", MarkdownSanitizer.escape(message)))).queue(null, e -> { });
   }

   /** Punishment logs: log channel, or the console channel. */
   public void sendLog(String title, String message) {
      if (!this.isConnected()) {
         return;
      }
      TextChannel ch = this.logChannel != null ? this.logChannel : this.consoleChannel;
      if (ch != null) {
         ch.sendMessage(cut("**" + title + "**\n" + MarkdownSanitizer.escape(Text.strip(message)))).queue(null, e -> { });
      } else {
         this.plugin.getLogger().info("[Log] " + title + ": " + message.replace('\n', ' '));
      }
   }

   // ------------------------------------------------------------------ Discord -> Minecraft

   @Override
   public void onMessageReceived(MessageReceivedEvent event) {
      if (event.getAuthor().isBot() || event.isWebhookMessage() || !event.isFromGuild() || this.stopped) {
         return;
      }
      String channelId = event.getChannel().getId();
      TextChannel console = this.consoleChannel;
      TextChannel staff = this.staffChannel;
      TextChannel chat = this.chatChannel;
      if (console != null && channelId.equals(console.getId())) {
         this.handleConsoleCommand(event);
      } else if (staff != null && channelId.equals(staff.getId())) {
         String format = this.cfg().getString("discord.formats.staff-discord-to-mc", "&8[&cStaff-DS&8] &c{author} &8» &e{message}");
         Component line = Text.render(format, false, java.util.Map.of("author", Component.text(this.author(event)), "message", Component.text(this.content(event))));
         this.plugin.runSync(() -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
               if (p.hasPermission("everlynx.chat.staff")) {
                  p.sendMessage(line);
               }
            }
            Bukkit.getConsoleSender().sendMessage(line);
         });
      } else if (chat != null && channelId.equals(chat.getId()) && this.cfg().getBoolean("discord.chat-enabled", true)) {
         String format = this.cfg().getString("discord.formats.discord-to-mc", "&8[&9Discord&8] &b{author} &8» &7{message}");
         Component line = Text.render(format, false, java.util.Map.of("author", Component.text(this.author(event)), "message", Component.text(this.content(event))));
         this.plugin.runSync(() -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
               if (!this.plugin.prefs().get(p.getUniqueId()).has(Keys.CHAT_DISABLED)) {
                  p.sendMessage(line);
               }
            }
            Bukkit.getConsoleSender().sendMessage(line);
         });
      }
   }

   private String author(MessageReceivedEvent event) {
      Member m = event.getMember();
      return m != null ? m.getEffectiveName() : event.getAuthor().getName();
   }

   private String content(MessageReceivedEvent event) {
      String text = event.getMessage().getContentDisplay().replace('\n', ' ').replace('§', '&');
      if (!event.getMessage().getAttachments().isEmpty()) {
         text = (text + " [attachment]").trim();
      }
      return text.length() > 256 ? text.substring(0, 256) + "…" : text;
   }

   private void handleConsoleCommand(MessageReceivedEvent event) {
      if (!this.cfg().getBoolean("discord.console-commands.enabled", false)) {
         return;
      }
      boolean allowed = this.allowedUsers().contains(event.getAuthor().getId());
      Member m = event.getMember();
      if (!allowed && m != null) {
         List<String> roles = this.allowedRoles();
         for (Role r : m.getRoles()) {
            if (roles.contains(r.getId())) {
               allowed = true;
               break;
            }
         }
      }
      String cmd = event.getMessage().getContentRaw().trim();
      if (cmd.startsWith("/")) {
         cmd = cmd.substring(1);
      }
      if (!allowed) {
         event.getMessage().reply("You are not allowed to run console commands.").queue(null, e -> { });
         this.plugin.getLogger().warning("Discord user " + event.getAuthor().getName() + " (" + event.getAuthor().getId() + ") tried to run: " + cmd);
         return;
      }
      if (cmd.isEmpty()) {
         return;
      }
      String run = cmd;
      this.plugin.getLogger().info("Discord user " + event.getAuthor().getName() + " ran: " + run);
      this.plugin.runSync(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), run));
   }

   // ------------------------------------------------------------------ stop

   /** serverStop = true when the server/plugin is shutting down (sends the "offline" line). */
   public void shutdown(boolean serverStop) {
      this.stopped = true;
      if (this.flushTask != null) {
         this.flushTask.cancel();
         this.flushTask = null;
      }
      if (this.appender != null) {
         try {
            org.apache.logging.log4j.Logger root = org.apache.logging.log4j.LogManager.getRootLogger();
            if (root instanceof org.apache.logging.log4j.core.Logger core) {
               ConsoleLogAppender a = (ConsoleLogAppender)this.appender;
               core.removeAppender(a);
               a.stop();
            }
         } catch (Throwable ignored) {
         }
         this.appender = null;
      }
      JDA j = this.jda;
      this.jda = null;
      if (this.connector != null && this.connector.isAlive() && j == null) {
         this.connector.interrupt();
      }
      if (j == null) {
         return;
      }
      try {
         String stop = this.cfg().getString("discord.formats.server-stop", "");
         if (serverStop && this.chatChannel != null && !stop.isBlank() && j.getStatus() == JDA.Status.CONNECTED) {
            this.chatChannel.sendMessage(cut(Text.strip(stop))).timeout(3L, TimeUnit.SECONDS).complete();
         }
      } catch (Throwable ignored) {
      }
      j.shutdown();
      try {
         if (!j.awaitShutdown(Duration.ofSeconds(5L))) {
            j.shutdownNow();
         }
      } catch (InterruptedException e) {
         j.shutdownNow();
         Thread.currentThread().interrupt();
      }
   }

   /** Copies console lines into a queue. Never blocks the logging thread. */
   private static final class ConsoleLogAppender extends org.apache.logging.log4j.core.appender.AbstractAppender {
      private final BlockingQueue<String> queue;

      ConsoleLogAppender(BlockingQueue<String> queue) {
         super("GyxerChatConsoleAppender", null, null, false, org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY);
         this.queue = queue;
      }

      @Override
      public void append(org.apache.logging.log4j.core.LogEvent event) {
         String logger = event.getLoggerName();
         if (logger != null && (logger.contains("gyxerchat.libs") || logger.contains("dv8tion"))) {
            return;
         }
         String msg = event.getMessage() == null ? null : event.getMessage().getFormattedMessage();
         if (msg == null || msg.isBlank()) {
            return;
         }
         msg = msg.replaceAll("\u001b\\[[;\\d]*m", "").replaceAll("§[0-9a-fk-orx]", "");
         this.queue.offer(msg);
      }
   }
}

package me.ryxer.gyxerchat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import me.clip.placeholderapi.PlaceholderAPI;
import me.ryxer.gyxerchat.commands.ChatCommands;
import me.ryxer.gyxerchat.commands.TpaCommands;
import me.ryxer.gyxerchat.data.PrefsManager;
import me.ryxer.gyxerchat.listeners.ChatListener;
import me.ryxer.gyxerchat.listeners.MenuListener;
import me.ryxer.gyxerchat.listeners.PlayerListener;
import me.ryxer.gyxerchat.managers.BroadcastManager;
import me.ryxer.gyxerchat.managers.DiscordManager;
import me.ryxer.gyxerchat.managers.GameManager;
import me.ryxer.gyxerchat.managers.IgnoreManager;
import me.ryxer.gyxerchat.managers.MenuManager;
import me.ryxer.gyxerchat.managers.MuteManager;
import me.ryxer.gyxerchat.managers.TagManager;
import me.ryxer.gyxerchat.managers.TpaManager;
import me.ryxer.gyxerchat.managers.ViolationManager;
import me.ryxer.gyxerchat.utils.ChatFilters;
import me.ryxer.gyxerchat.utils.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class GyxerChat extends JavaPlugin {
   private static final Pattern PAPI_TOKEN = Pattern.compile("%[A-Za-z0-9_]+(?:_[A-Za-z0-9_:.-]+)?%");
   private static GyxerChat instance;
   private Keys keys;
   private PrefsManager prefs;
   private MuteManager muteManager;
   private MenuManager menuManager;
   private IgnoreManager ignoreManager;
   private GameManager gameManager;
   private DiscordManager discordManager;
   private ViolationManager violationManager;
   private TagManager tagManager;
   private BroadcastManager broadcastManager;
   private TpaManager tpaManager;
   private Economy econ;
   private volatile boolean globalChatMuted = false;
   /** A read-only copy of config.yml. The chat runs on its own thread and only reads this copy. */
   private volatile FileConfiguration snapshot = new YamlConfiguration();
   private volatile List<Pattern> blockedWords = List.of();
   private final Map<String, Key> soundKeys = new ConcurrentHashMap<>();
   private final Set<UUID> staffChatPlayers = ConcurrentHashMap.newKeySet();
   private final Set<UUID> socialSpyPlayers = ConcurrentHashMap.newKeySet();
   private final Map<UUID, UUID> recentMessages = new ConcurrentHashMap<>();

   @Override
   public void onEnable() {
      instance = this;
      this.keys = new Keys(this);
      this.saveDefaultConfig();
      this.reloadConfig();
      this.addMissingConfigKeys();
      this.refreshSnapshot();
      this.setupEconomy();
      boolean papi = this.hasPapi();
      Bukkit.getConsoleSender().sendMessage(Text.AMPERSAND.deserialize(String.join("\n",
         "&b",
         "&b  &l  ____                             &3 ____  _           _   ",
         "&b  &l / ___|_   ___  _____ _ __        &3/ ___|| |__   __ _| |_  ",
         "&b  &l| |  _| | | \\ \\/ / _ \\ '__|  &f&l--- &3| |   | '_ \\ / _` | __|",
         "&b  &l| |_| | |_| |>  <  __/ |     &f&l--- &3| |___| | | | (_| | |_ ",
         "&b  &l \\____|\\__, /_/\\_\\___|_|         &3 \\____|_| |_|\\__,_|\\__|",
         "&b  &l       |___/                       &f&lElite v" + this.getPluginMeta().getVersion(),
         "&7",
         "&8[&bGyxerChat&8] &7Initializing core systems...",
         "&8[&bGyxerChat&8] &7PlaceholderAPI: " + (papi ? "&aHooked" : "&cNot Found"),
         "&8[&bGyxerChat&8] &7Vault Economy: " + (this.econ != null ? "&aFound" : "&cNot Found"),
         "&8[&bGyxerChat&8] &fSuccessfully Loaded!",
         "&b")));
      this.prefs = new PrefsManager(this);
      this.muteManager = new MuteManager(this);
      this.ignoreManager = new IgnoreManager(this);
      this.tagManager = new TagManager(this);
      this.violationManager = new ViolationManager(this);
      this.menuManager = new MenuManager(this);
      this.tpaManager = new TpaManager(this);
      this.gameManager = new GameManager(this);
      this.broadcastManager = new BroadcastManager(this);
      this.discordManager = new DiscordManager(this);
      for (Player p : Bukkit.getOnlinePlayers()) {
         this.prefs.load(p);
      }
      this.getServer().getPluginManager().registerEvents(new ChatListener(this), this);
      this.getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
      this.getServer().getPluginManager().registerEvents(new MenuListener(), this);
      ChatCommands cmds = new ChatCommands(this);
      for (String cmd : ChatCommands.COMMANDS) {
         this.bind(cmd, cmds);
      }
      TpaCommands tpaCmds = new TpaCommands(this);
      for (String cmd : TpaCommands.COMMANDS) {
         this.bind(cmd, tpaCmds);
      }
      this.gameManager.start();
      this.broadcastManager.start();
      this.discordManager.connect();
   }

   private void bind(String name, Object handler) {
      PluginCommand c = this.getCommand(name);
      if (c != null) {
         c.setExecutor((org.bukkit.command.CommandExecutor)handler);
         c.setTabCompleter((org.bukkit.command.TabCompleter)handler);
      }
   }

   @Override
   public void onDisable() {
      if (this.menuManager != null) {
         this.menuManager.closeAll();
      }
      if (this.tpaManager != null) {
         this.tpaManager.cancelAll();
      }
      if (this.gameManager != null) {
         this.gameManager.stop();
      }
      if (this.broadcastManager != null) {
         this.broadcastManager.stop();
      }
      if (this.discordManager != null) {
         this.discordManager.shutdown(true);
      }
      if (this.muteManager != null) {
         this.muteManager.saveMutes();
      }
      if (this.ignoreManager != null) {
         this.ignoreManager.saveIgnores();
      }
      if (this.tagManager != null) {
         this.tagManager.saveTags();
      }
      this.getLogger().info("GyxerChat disabled.");
   }

   /** /chatreload and the admin menu button: reload the config and restart every part. */
   public void reloadAll() {
      this.reloadConfig();
      this.addMissingConfigKeys();
      this.refreshSnapshot();
      this.setupEconomy();
      this.muteManager.loadMutes();
      this.ignoreManager.loadIgnores();
      this.tagManager.loadTags();
      this.broadcastManager.reload();
      this.gameManager.reload();
      this.reloadDiscord();
   }

   public void reloadBroadcaster() {
      this.broadcastManager.reload();
   }

   public void reloadDiscord() {
      this.discordManager.shutdown(false);
      this.discordManager = new DiscordManager(this);
      this.discordManager.connect();
   }

   /** Adds settings that are new in this version to an older config.yml (keeps everything the owner changed). */
   private void addMissingConfigKeys() {
      Configuration defaults = this.getConfig().getDefaults();
      if (defaults == null) {
         return;
      }
      FileConfiguration cfg = this.getConfig();
      boolean changed = false;
      for (String key : defaults.getKeys(true)) {
         if (key.startsWith("chat-games.games.") || defaults.isConfigurationSection(key) || cfg.isSet(key)) {
            continue;
         }
         cfg.set(key, defaults.get(key));
         try {
            List<String> comments = defaults.getComments(key);
            if (!comments.isEmpty()) {
               cfg.setComments(key, comments);
            }
         } catch (Throwable ignored) {
         }
         changed = true;
      }
      if (cfg.getInt("config-version", 1) < 2) {
         cfg.set("config-version", 2);
         changed = true;
      }
      if (changed) {
         this.saveConfig();
         this.getLogger().info("Added new settings to config.yml.");
      }
   }

   /** Call after changing the config on the main thread. */
   public void refreshSnapshot() {
      YamlConfiguration copy = new YamlConfiguration();
      try {
         copy.loadFromString(this.getConfig().saveToString());
      } catch (InvalidConfigurationException e) {
         this.getLogger().warning("Could not copy config: " + e.getMessage());
      }
      Configuration defaults = this.getConfig().getDefaults();
      if (defaults != null) {
         copy.setDefaults(defaults);
      }
      this.snapshot = copy;
      this.blockedWords = ChatFilters.compileBlockedWords(copy.getStringList("chat-settings.blocked-words"));
      this.soundKeys.clear();
   }

   /** Settings (thread-safe read-only copy). */
   public FileConfiguration cfg() {
      return this.snapshot;
   }

   public List<Pattern> blockedWords() {
      return this.blockedWords;
   }

   public void toggleConfigOption(String path) {
      this.getConfig().set(path, !this.cfg().getBoolean(path, true));
      this.saveConfig();
      this.refreshSnapshot();
   }

   private void setupEconomy() {
      this.econ = null;
      if (this.getServer().getPluginManager().getPlugin("Vault") == null) {
         return;
      }
      RegisteredServiceProvider<Economy> rsp = this.getServer().getServicesManager().getRegistration(Economy.class);
      if (rsp != null) {
         this.econ = rsp.getProvider();
      }
   }

   public boolean hasPapi() {
      return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
   }

   public String papi(Player player, String text) {
      if (text == null) {
         return "";
      }
      if (text.indexOf('%') < 0) {
         return text;
      }
      if (player != null && this.hasPapi()) {
         try {
            return PlaceholderAPI.setPlaceholders(player, text);
         } catch (Throwable t) {
            return text;
         }
      }
      // without PlaceholderAPI, "%luckperms_prefix%" would be shown as it is: leave it out instead
      return PAPI_TOKEN.matcher(text).replaceAll("").replaceAll("^\\s+", "");
   }

   // ---------------------------------------------------------------- sounds

   /** Plays a sound from the "sounds" section. Safe from any thread. */
   public void playAudio(Player player, String soundType) {
      if (player == null) {
         return;
      }
      if (!Bukkit.isPrimaryThread()) {
         this.runSync(() -> this.playAudio(player, soundType));
         return;
      }
      String name = this.cfg().getString("sounds." + soundType);
      if (name == null || name.isBlank() || name.equalsIgnoreCase("none") || !player.isOnline()) {
         return;
      }
      Key key = this.soundKeys.computeIfAbsent(name, Sounds::find);
      if (key != Sounds.NONE) {
         player.playSound(net.kyori.adventure.sound.Sound.sound(key, net.kyori.adventure.sound.Sound.Source.MASTER, 1.0F, 1.0F));
      }
   }

   public void runSync(Runnable r) {
      if (Bukkit.isPrimaryThread()) {
         r.run();
      } else if (this.isEnabled()) {
         Bukkit.getScheduler().runTask(this, r);
      }
   }

   // ---------------------------------------------------------------- messages

   private static Map<String, Component> values(Object... kv) {
      Map<String, Component> map = new HashMap<>();
      for (int i = 0; i + 1 < kv.length; i += 2) {
         String key = String.valueOf(kv[i]);
         if (key.startsWith("{") && key.endsWith("}")) {
            key = key.substring(1, key.length() - 1);
         }
         Object v = kv[i + 1];
         map.put(key, v instanceof Component c ? c : Component.text(String.valueOf(v)));
      }
      return map;
   }

   public String rawMsg(String path) {
      return this.cfg().getString("messages." + path, "&cMessage 'messages." + path + "' is missing.");
   }

   /** A message from config "messages" with the plugin prefix. Values: "key", value, ... (value = text or component). */
   public Component msg(String path, Object... kv) {
      return this.getParsedMsg(true, path, kv);
   }

   public Component getParsedMsg(boolean usePrefix, String path, Object... kv) {
      String template = (usePrefix ? this.cfg().getString("prefix", "") : "") + this.rawMsg(path);
      return Text.render(template, false, values(kv));
   }

   /** Same, but the message words use small caps when "small-caps" is on (join/quit/death, staff chat, games...). */
   public Component styled(String path, Object... kv) {
      return this.styledTemplate(this.rawMsg(path), kv);
   }

   public Component styledTemplate(String template, Object... kv) {
      return Text.render(template, this.cfg().getBoolean("small-caps", true), values(kv));
   }

   public void send(CommandSender to, String path, Object... kv) {
      to.sendMessage(this.msg(path, kv));
   }

   /** Staff chat line to all staff, the console and the Discord staff channel. Safe from any thread. */
   public void sendStaffChat(CommandSender from, String text) {
      Component name = from instanceof Player p ? this.displayName(p) : Component.text(from.getName());
      Component line = this.styled("staff-chat-format", "player", name, "message", Text.playerText(text, true));
      for (Player p : Bukkit.getOnlinePlayers()) {
         if (p.hasPermission("everlynx.chat.staff")) {
            p.sendMessage(line);
         }
      }
      Bukkit.getConsoleSender().sendMessage(line);
      this.discordManager.sendStaff(from instanceof Player p ? p : null, Text.strip(text));
   }

   // ---------------------------------------------------------------- names

   public Component displayName(Player player) {
      String nick = this.prefs.get(player.getUniqueId()).nickname;
      if (nick == null || nick.isEmpty()) {
         return Component.text(player.getName());
      }
      return nick.indexOf('§') >= 0 ? Text.legacySection(nick) : Text.AMPERSAND.deserialize(nick);
   }

   public String getPlayerDisplayName(Player player) {
      return Text.plain(this.displayName(player));
   }

   /** Applies a player's nickname to the tab list and to other plugins that read the display name. */
   public void applyNick(Player player) {
      String nick = this.prefs.get(player.getUniqueId()).nickname;
      if (nick == null || nick.isEmpty()) {
         player.displayName(null);
         player.playerListName(null);
      } else {
         Component c = this.displayName(player);
         player.displayName(c);
         player.playerListName(c);
      }
   }

   /** The player's active tag, or null. Tags deleted with /deletetag disappear. */
   public Component tagOf(Player player) {
      PrefsManager.Prefs p = this.prefs.get(player.getUniqueId());
      String display = p.tag;
      if (p.tagId != null) {
         String current = this.tagManager.getTags().get(p.tagId);
         if (current == null) {
            return null;
         }
         display = current;
      }
      if (display == null || display.isEmpty()) {
         return null;
      }
      return Text.parse(display);
   }

   // ---------------------------------------------------------------- getters

   public static GyxerChat getInstance() {
      return instance;
   }

   public Keys keys() {
      return this.keys;
   }

   public PrefsManager prefs() {
      return this.prefs;
   }

   public Economy getEconomy() {
      return this.econ;
   }

   public MuteManager getMuteManager() {
      return this.muteManager;
   }

   public MenuManager getMenuManager() {
      return this.menuManager;
   }

   public IgnoreManager getIgnoreManager() {
      return this.ignoreManager;
   }

   public GameManager getGameManager() {
      return this.gameManager;
   }

   public DiscordManager getDiscordManager() {
      return this.discordManager;
   }

   public ViolationManager getViolationManager() {
      return this.violationManager;
   }

   public TagManager getTagManager() {
      return this.tagManager;
   }

   public BroadcastManager getBroadcastManager() {
      return this.broadcastManager;
   }

   public TpaManager getTpaManager() {
      return this.tpaManager;
   }

   public boolean isGlobalChatMuted() {
      return this.globalChatMuted;
   }

   public void setGlobalChatMuted(boolean muted) {
      this.globalChatMuted = muted;
   }

   public Set<UUID> getStaffChatPlayers() {
      return this.staffChatPlayers;
   }

   public Set<UUID> getSocialSpyPlayers() {
      return this.socialSpyPlayers;
   }

   public Map<UUID, UUID> getRecentMessages() {
      return this.recentMessages;
   }
}

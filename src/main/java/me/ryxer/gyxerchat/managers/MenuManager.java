package me.ryxer.gyxerchat.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import me.ryxer.gyxerchat.GyxerChat;
import me.ryxer.gyxerchat.Keys;
import me.ryxer.gyxerchat.menu.Menu;
import me.ryxer.gyxerchat.utils.Durations;
import me.ryxer.gyxerchat.utils.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/** All chest menus: /chatadmin, /settings, /chatcolor, /tags. */
public class MenuManager {
   private static final int[] INNER = innerSlots();
   private final GyxerChat plugin;

   public MenuManager(GyxerChat plugin) {
      this.plugin = plugin;
   }

   private static int[] innerSlots() {
      int[] s = new int[28];
      int n = 0;
      for (int row = 1; row <= 4; row++) {
         for (int col = 1; col <= 7; col++) {
            s[n++] = row * 9 + col;
         }
      }
      return s;
   }

   private String title(String path, String def) {
      return this.plugin.cfg().getString(path, def);
   }

   private static ItemStack item(Material m, String name, String... lore) {
      return Menu.item(m, name, lore);
   }

   private void ok(Player p) {
      this.plugin.playAudio(p, "success");
   }

   private void fail(Player p) {
      this.plugin.playAudio(p, "fail");
   }

   public void closeAll() {
      for (Player p : Bukkit.getOnlinePlayers()) {
         if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu) {
            p.closeInventory();
         }
      }
   }

   /** A list over several pages (28 per page) with back / previous / next buttons. */
   private record Entry(ItemStack item, Consumer<Player> action) {
   }

   private void openList(Player viewer, String title, List<Entry> entries, int page, Consumer<Player> back, java.util.function.IntConsumer reopen) {
      int pages = Math.max(1, (entries.size() + INNER.length - 1) / INNER.length);
      int pg = Math.max(0, Math.min(page, pages - 1));
      Menu menu = new Menu(6, pages > 1 ? title + " (" + (pg + 1) + "/" + pages + ")" : title).borders();
      for (int i = 0; i < INNER.length; i++) {
         int idx = pg * INNER.length + i;
         if (idx >= entries.size()) {
            break;
         }
         Entry e = entries.get(idx);
         menu.set(INNER[i], e.item(), e.action());
      }
      if (pg > 0) {
         menu.set(45, item(Material.SPECTRAL_ARROW, "&ePrevious Page"), p -> reopen.accept(pg - 1));
      }
      if (pg < pages - 1) {
         menu.set(53, item(Material.SPECTRAL_ARROW, "&eNext Page"), p -> reopen.accept(pg + 1));
      }
      menu.set(49, item(Material.ARROW, "&cBack", "&7Go back"), back);
      menu.open(viewer);
   }

   private static ItemStack head(OfflinePlayer target, String name, String... lore) {
      ItemStack head = Menu.item(Material.PLAYER_HEAD, name, lore);
      if (head.getItemMeta() instanceof SkullMeta meta) {
         meta.setOwningPlayer(target);
         head.setItemMeta(meta);
      }
      return head;
   }

   // ------------------------------------------------------------------ admin

   public void openMainMenu(Player player) {
      Menu m = new Menu(5, this.title("gui.main.title", "GyxerChat Admin")).borders();
      m.set(20, item(Material.PLAYER_HEAD, this.title("gui.main.manage-players", "&bManage Players"), "&7View online players"), p -> this.openPlayerListMenu(p, 0));
      m.set(22, item(Material.COMPARATOR, this.title("gui.main.settings", "&eSystem Settings"), "&7Toggle Discord, Games, etc."), this::openSettingsMenu);
      m.set(24, item(Material.BARRIER, this.title("gui.main.muted-players", "&cMuted Players"), "&7View all active mutes"), p -> this.openMutedListMenu(p, 0));
      m.set(29, item(Material.NAME_TAG, this.title("gui.main.manage-tags", "&dManage Tags"), "&7Delete custom chat tags"), p -> this.openAdminTagsMenu(p, 0));
      m.set(31, item(Material.WRITABLE_BOOK, "&aStart Chat Game", "&7Ask a chat game question now"), p -> {
         this.plugin.getGameManager().startNewGame();
         this.ok(p);
         p.closeInventory();
      });
      m.set(33, item(Material.COMMAND_BLOCK, "&bReload Config", "&7Apply changes from config.yml"), p -> {
         p.closeInventory();
         this.plugin.reloadAll();
         this.ok(p);
         this.plugin.send(p, "reload-success");
      });
      m.set(40, item(Material.DARK_OAK_DOOR, this.title("gui.main.close", "&cClose Menu"), "&7Exit GUI"), Player::closeInventory);
      m.open(player);
   }

   private ItemStack toggleItem(Material mat, String name, String path) {
      boolean on = this.plugin.cfg().getBoolean(path, true);
      return item(mat, name, "&7Status: " + (on ? "&aEnabled" : "&cDisabled"), "&eClick to Toggle");
   }

   public void openSettingsMenu(Player player) {
      Menu m = new Menu(6, this.title("gui.settings.title", "GyxerChat - Settings")).borders();
      this.toggle(m, 11, Material.BLUE_WOOL, "&bDiscord Bot", "discord.enabled", true);
      this.toggle(m, 13, Material.OAK_SIGN, "&9Discord Chat Sync", "discord.chat-enabled", false);
      this.toggle(m, 15, Material.COMMAND_BLOCK, "&eDiscord Console Sync", "discord.console-enabled", true);
      this.toggle(m, 20, Material.OAK_DOOR, "&aJoin/Leave Msgs", "custom-messages.join-leave-enabled", false);
      String chatStatus = this.plugin.isGlobalChatMuted() ? "&cMuted" : "&aOpen";
      m.set(22, item(Material.BEACON, "&dGlobal Chat", "&7Status: " + chatStatus, "&eClick to Toggle"), p -> {
         this.setChatLock(p.getName(), !this.plugin.isGlobalChatMuted());
         this.ok(p);
         this.openSettingsMenu(p);
      });
      this.toggle(m, 24, Material.SKELETON_SKULL, "&cDeath Msgs", "custom-messages.death-enabled", false);
      this.toggle(m, 29, Material.PAPER, "&fAnti-Caps Filter", "chat-settings.anti-caps-enabled", false);
      this.toggle(m, 31, Material.CHEST_MINECART, "&eChat Games", "chat-games.enabled", false);
      this.toggle(m, 33, Material.JUKEBOX, "&dAuto-Broadcaster", "auto-broadcaster.enabled", false);
      this.toggle(m, 39, Material.SHIELD, "&cSwear Filter", "chat-settings.swear-filter-enabled", false);
      this.toggle(m, 41, Material.ENDER_PEARL, "&bTPA System", "tpa-settings.enabled", false);
      m.set(49, item(Material.ARROW, "&cBack", "&7Return to main menu"), this::openMainMenu);
      m.open(player);
   }

   private void toggle(Menu m, int slot, Material mat, String name, String path, boolean restartDiscord) {
      m.set(slot, this.toggleItem(mat, name, path), p -> {
         this.plugin.toggleConfigOption(path);
         if (restartDiscord) {
            this.plugin.reloadDiscord();
         }
         if (path.startsWith("auto-broadcaster")) {
            this.plugin.reloadBroadcaster();
         }
         this.ok(p);
         this.openSettingsMenu(p);
      });
   }

   public void setChatLock(String by, boolean locked) {
      this.plugin.setGlobalChatMuted(locked);
      Bukkit.broadcast(this.plugin.getParsedMsg(false, locked ? "chat-lock-on" : "chat-lock-off", "player", by));
   }

   public void openPlayerListMenu(Player admin, int page) {
      List<Entry> entries = new ArrayList<>();
      for (Player target : Bukkit.getOnlinePlayers()) {
         String mute = this.plugin.getMuteManager().isMuted(target.getUniqueId()) ? "&cMuted" : "&aNot Muted";
         UUID id = target.getUniqueId();
         entries.add(new Entry(head(target, "&e" + target.getName(), "&7Status: " + mute, "&7Ping: &a" + target.getPing() + "ms", "&eClick to manage!"), p -> {
            Player t = Bukkit.getPlayer(id);
            if (t == null) {
               this.fail(p);
               this.openPlayerListMenu(p, page);
            } else {
               this.openManagePlayerMenu(p, t);
            }
         }));
      }
      this.openList(admin, this.title("gui.players.title", "GyxerChat - Players"), entries, page, this::openMainMenu, pg -> this.openPlayerListMenu(admin, pg));
   }

   public void openMutedListMenu(Player admin, int page) {
      List<Entry> entries = new ArrayList<>();
      for (UUID uuid : this.plugin.getMuteManager().getAllMutedUUIDs()) {
         MuteManager.Mute mute = this.plugin.getMuteManager().get(uuid);
         if (mute == null) {
            continue;
         }
         OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);
         String name = target.getName() != null ? target.getName() : mute.name() != null ? mute.name() : uuid.toString().substring(0, 8);
         String time = mute.expiry() == -1L ? "forever" : Durations.format(mute.expiry() - System.currentTimeMillis());
         entries.add(new Entry(head(target, "&c" + name, "&7Reason: &f" + mute.reason(), "&7Time left: &f" + time, "&aClick to unmute!"), p -> {
            this.plugin.getMuteManager().removeMute(uuid);
            this.ok(p);
            this.plugin.send(p, "unmute-success", "target", name);
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
               this.plugin.send(online, "unmuted-notify");
            }
            this.openMutedListMenu(p, page);
         }));
      }
      this.openList(admin, this.title("gui.muted.title", "GyxerChat - Muted"), entries, page, this::openMainMenu, pg -> this.openMutedListMenu(admin, pg));
   }

   public void openManagePlayerMenu(Player admin, Player target) {
      UUID id = target.getUniqueId();
      Menu m = new Menu(5, "Managing: " + target.getName()).borders();
      m.set(13, head(target, "&b" + target.getName() + "'s Info", "&7Health: &c" + Math.round(target.getHealth()), "&7Ping: &a" + target.getPing() + "ms"), null);
      if (this.plugin.getMuteManager().isMuted(id)) {
         m.set(20, item(Material.LIME_DYE, "&aUnmute Player", "&7Allow them to speak"), p -> this.withOnline(p, id, t -> {
            this.plugin.getMuteManager().removeMute(id);
            this.ok(p);
            this.plugin.send(p, "unmute-success", "target", t.getName());
            this.plugin.send(t, "unmuted-notify");
            this.openManagePlayerMenu(p, t);
         }));
      } else {
         m.set(20, item(Material.RED_DYE, "&cMute Player", "&7Stop them from speaking"), p -> this.withOnline(p, id, t -> {
            this.plugin.getMuteManager().setMute(id, t.getName(), "Muted by GUI", -1L);
            this.ok(p);
            this.plugin.send(p, "mute-success", "target", t.getName(), "reason", "GUI");
            this.plugin.send(t, "muted-notify", "time", this.plugin.rawMsg("time-forever"), "reason", "Muted by GUI");
            this.plugin.getDiscordManager().sendLog("Chat Punishment", "Type: Mute\nAdmin: " + p.getName() + "\nTarget: " + t.getName() + "\nReason: GUI");
            this.openManagePlayerMenu(p, t);
         }));
      }
      m.set(22, item(Material.NAME_TAG, "&dManage Tag", "&7Click to alter their tag"), p -> this.withOnline(p, id, t -> this.openPlayerTagMenu(p, t, 0)));
      m.set(24, item(Material.PAINTING, "&bManage Chat Color", "&7Open the color selector for them."), p -> this.withOnline(p, id, t -> this.openChatColorMenu(p, t, "none")));
      m.set(40, item(Material.ARROW, "&cBack", "&7Return to player list"), p -> this.openPlayerListMenu(p, 0));
      m.open(admin);
   }

   private void withOnline(Player admin, UUID id, Consumer<Player> action) {
      Player t = Bukkit.getPlayer(id);
      if (t == null) {
         this.fail(admin);
         this.plugin.send(admin, "player-not-found");
         this.openPlayerListMenu(admin, 0);
      } else {
         action.accept(t);
      }
   }

   public void openAdminTagsMenu(Player admin, int page) {
      List<Entry> entries = new ArrayList<>();
      for (Map.Entry<String, String> tag : this.plugin.getTagManager().sorted().entrySet()) {
         String id = tag.getKey();
         entries.add(new Entry(Menu.named(new ItemStack(Material.NAME_TAG), this.tagName(tag.getValue()),
            List.of(Text.item("&7ID: " + id), Text.item("&cClick to globally delete!"))), p -> {
               this.plugin.getTagManager().deleteTag(id);
               this.ok(p);
               this.plugin.send(p, "tag-deleted", "id", id);
               this.openAdminTagsMenu(p, page);
            }));
      }
      this.openList(admin, this.title("gui.admin-tags.title", "GyxerChat - Admin Tags"), entries, page, this::openMainMenu, pg -> this.openAdminTagsMenu(admin, pg));
   }

   private Component tagName(String display) {
      return Text.parse(display).decorationIfAbsent(net.kyori.adventure.text.format.TextDecoration.ITALIC, net.kyori.adventure.text.format.TextDecoration.State.FALSE);
   }

   public void openPlayerTagMenu(Player admin, Player target, int page) {
      UUID tid = target.getUniqueId();
      List<Entry> entries = new ArrayList<>();
      for (Map.Entry<String, String> tag : this.plugin.getTagManager().sorted().entrySet()) {
         String id = tag.getKey();
         entries.add(new Entry(Menu.named(new ItemStack(Material.NAME_TAG), this.tagName(tag.getValue()),
            List.of(Text.item("&7ID: " + id), Text.item("&aClick to force equip."))), p -> this.withOnline(p, tid, t -> {
               String display = this.plugin.getTagManager().getTags().get(id);
               if (display == null) {
                  this.fail(p);
                  return;
               }
               this.plugin.prefs().setTag(t, id, display);
               this.plugin.send(p, "tag-equipped", "tag", Text.parse(display));
               this.ok(p);
               this.openManagePlayerMenu(p, t);
            })));
      }
      this.openList(admin, "Select Tag: " + target.getName(), entries, page, p -> this.withOnline(p, tid, t -> this.openManagePlayerMenu(p, t)),
         pg -> this.openPlayerTagMenu(admin, target, pg));
      // remove-tag button next to "back"
      if (admin.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu menu) {
         menu.set(48, item(Material.RED_BED, "&cRemove Tag", "&7Clear player's current tag."), p -> this.withOnline(p, tid, t -> {
            this.plugin.prefs().setTag(t, null, null);
            this.plugin.send(p, "tag-removed");
            this.ok(p);
            this.openManagePlayerMenu(p, t);
         }));
      }
   }

   // ------------------------------------------------------------------ player menus

   private ItemStack personalToggle(Player player, Material mat, String name, String flag, boolean flagMeansOff) {
      boolean stored = this.plugin.prefs().get(player).has(flag);
      boolean on = flagMeansOff != stored;
      return item(mat, name, "&7Status: " + (on ? "&aEnabled" : "&cDisabled"), "&eClick to Toggle");
   }

   private void personal(Menu m, Player player, int slot, Material mat, String name, String flag, boolean flagMeansOff) {
      m.set(slot, this.personalToggle(player, mat, name, flag, flagMeansOff), p -> {
         this.plugin.prefs().toggle(p, flag);
         this.ok(p);
         this.openPlayerSettingsMenu(p);
      });
   }

   public void openPlayerSettingsMenu(Player player) {
      Menu m = new Menu(5, "GyxerChat - My Settings").borders();
      this.personal(m, player, 10, Material.PAPER, "&fGlobal Chat Visibility", Keys.CHAT_DISABLED, true);
      this.personal(m, player, 11, Material.WRITABLE_BOOK, "&dPrivate Messages", Keys.PMS_DISABLED, true);
      this.personal(m, player, 12, Material.BELL, "&eMention Sounds", Keys.MENTION_SOUND_DISABLED, true);
      this.personal(m, player, 13, Material.OAK_DOOR, "&aJoin/Leave Messages", Keys.JOINLEAVE_DISABLED, true);
      this.personal(m, player, 14, Material.JUKEBOX, "&6See Auto Broadcasts", Keys.BROADCASTS_DISABLED, true);
      this.personal(m, player, 15, Material.CHEST_MINECART, "&bSee Chat Games", Keys.CHATGAMES_DISABLED, true);
      this.personal(m, player, 21, Material.ENDER_PEARL, "&3Receive TPA Requests", Keys.TPA_DISABLED, true);
      this.personal(m, player, 22, Material.ENDER_EYE, "&5Receive TPA Here Requests", Keys.TPAHERE_DISABLED, true);
      this.personal(m, player, 23, Material.BEACON, "&aAuto-Accept TPA", Keys.TPA_AUTO_ACCEPT, false);
      m.set(29, item(Material.PAINTING, "&dMy Chat Color", "&7Click to customize your text color"), p -> this.openChatColorMenu(p, p, "none"));
      m.set(30, item(Material.NAME_TAG, "&bMy Chat Tags", "&7Click to equip an unlocked tag"), p -> this.openTagsMenu(p, 0));
      m.set(31, item(Material.WITHER_SKELETON_SKULL, "&cIgnored Players", "&7View people you have blocked"), p -> this.openIgnoredPlayersMenu(p, 0));
      if (player.hasPermission("gyxerchat.nick")) {
         m.set(32, item(Material.OAK_SIGN, "&aNickname Tools", "&7Command: &e/nick <name>", "&7Click here to &cReset &7your nick!"), p -> {
            this.plugin.prefs().setNickname(p, null);
            this.plugin.applyNick(p);
            this.plugin.send(p, "nick-reset");
            this.ok(p);
            p.closeInventory();
         });
      }
      m.set(40, item(Material.DARK_OAK_DOOR, "&cClose Menu", "&7Exit GUI"), Player::closeInventory);
      m.open(player);
   }

   public void openIgnoredPlayersMenu(Player player, int page) {
      List<Entry> entries = new ArrayList<>();
      for (UUID uuid : this.plugin.getIgnoreManager().getIgnored(player.getUniqueId())) {
         OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);
         String name = target.getName() != null ? target.getName() : "Unknown";
         entries.add(new Entry(head(target, "&c" + name, "&7Click to &aUn-ignore&7!"), p -> {
            if (this.plugin.getIgnoreManager().isIgnoring(p.getUniqueId(), uuid)) {
               this.plugin.getIgnoreManager().toggleIgnore(p.getUniqueId(), uuid);
               this.plugin.send(p, "ignore-remove", "target", name);
               this.ok(p);
            }
            this.openIgnoredPlayersMenu(p, page);
         }));
      }
      this.openList(player, "GyxerChat - Ignored", entries, page, this::openPlayerSettingsMenu, pg -> this.openIgnoredPlayersMenu(player, pg));
   }

   /** gradientState: "none" = pick one color, "start" = pick the first gradient color, "#RRGGBB" = pick the second. */
   public void openChatColorMenu(Player viewer, Player target, String gradientState) {
      UUID tid = target.getUniqueId();
      boolean admin = viewer.hasPermission("everlynx.chat.admin");
      String titleAppend = gradientState.equals("start") ? " (Start Color)" : gradientState.startsWith("#") ? " (End Color)" : "";
      Menu m = new Menu(6, "GyxerChat - Chat Color" + titleAppend).borders();
      int i = 0;
      for (ChatColors color : ChatColors.values()) {
         boolean hasPerm = admin || viewer.hasPermission("gyxerchat.chatcolor." + color.perm);
         String status = hasPerm ? "&aUnlocked! Click to select." : "&cLocked. No permission.";
         m.set(INNER[i++], item(color.material, color.code + color.displayName, status), p -> this.withOnline(p, tid, t -> {
            if (!hasPerm) {
               this.fail(p);
               return;
            }
            if (gradientState.equals("none")) {
               this.plugin.prefs().setChatColor(t, color.code);
               this.plugin.send(p, "color-set", "color", color.displayName);
               this.ok(p);
               p.closeInventory();
            } else if (gradientState.equals("start")) {
               this.openChatColorMenu(p, t, color.hex);
            } else {
               this.plugin.prefs().setChatColor(t, "gradient:" + gradientState + ":" + color.hex);
               this.plugin.send(p, "color-gradient-set");
               this.ok(p);
               p.closeInventory();
            }
         }));
      }
      if (gradientState.equals("none")) {
         boolean hasGrad = admin || viewer.hasPermission("gyxerchat.chatcolor.gradient");
         m.set(48, item(Material.MAGMA_CREAM, "&d&lCreate Gradient", "&7Blend two colors together.", hasGrad ? "&aClick to start building!" : "&cLocked."), p -> {
            if (!hasGrad) {
               this.plugin.send(p, "no-permission");
               this.fail(p);
            } else {
               this.withOnline(p, tid, t -> this.openChatColorMenu(p, t, "start"));
            }
         });
      }
      m.set(49, item(Material.WATER_BUCKET, "&cReset Color", "&7Return to default white."), p -> this.withOnline(p, tid, t -> {
         this.plugin.prefs().setChatColor(t, null);
         this.plugin.send(p, "color-reset", "target", t.getName());
         this.ok(p);
         p.closeInventory();
      }));
      m.set(50, item(Material.ARROW, "&cClose Menu", "&7Exit GUI"), Player::closeInventory);
      m.open(viewer);
   }

   public void openTagsMenu(Player player, int page) {
      List<Entry> entries = new ArrayList<>();
      boolean admin = player.hasPermission("everlynx.chat.admin");
      for (Map.Entry<String, String> tag : this.plugin.getTagManager().sorted().entrySet()) {
         String id = tag.getKey();
         boolean hasPerm = admin || player.hasPermission("tag.equip." + id);
         ItemStack it = Menu.named(new ItemStack(hasPerm ? Material.NAME_TAG : Material.BARRIER), this.tagName(tag.getValue()),
            List.of(Text.item("&7ID: " + id), Text.item(hasPerm ? "&aUnlocked! Click to equip." : "&cLocked. You lack permission.")));
         entries.add(new Entry(it, p -> {
            String display = this.plugin.getTagManager().getTags().get(id);
            if (display == null) {
               this.fail(p);
               this.openTagsMenu(p, page);
            } else if (hasPerm) {
               this.plugin.prefs().setTag(p, id, display);
               this.plugin.send(p, "tag-equipped", "tag", Text.parse(display));
               this.ok(p);
               p.closeInventory();
            } else {
               this.fail(p);
               this.plugin.send(p, "tag-no-permission", "permission", "tag.equip." + id);
            }
         }));
      }
      this.openList(player, "GyxerChat - Tags", entries, page, Player::closeInventory, pg -> this.openTagsMenu(player, pg));
      if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu menu) {
         menu.set(48, item(Material.RED_BED, "&cRemove Tag", "&7Click to clear your active tag."), p -> {
            this.plugin.prefs().setTag(p, null, null);
            this.plugin.send(p, "tag-removed");
            this.ok(p);
            p.closeInventory();
         });
         menu.set(49, item(Material.ARROW, "&cClose Menu", "&7Exit GUI"), Player::closeInventory);
      }
   }

   public enum ChatColors {
      DARK_RED("Dark Red", "dark_red", "&4", "#AA0000", Material.RED_TERRACOTTA),
      RED("Red", "red", "&c", "#FF5555", Material.RED_DYE),
      GOLD("Orange", "orange", "&6", "#FFAA00", Material.ORANGE_DYE),
      YELLOW("Yellow", "yellow", "&e", "#FFFF55", Material.YELLOW_DYE),
      DARK_GREEN("Dark Green", "dark_green", "&2", "#00AA00", Material.GREEN_TERRACOTTA),
      GREEN("Green", "green", "&a", "#55FF55", Material.LIME_DYE),
      AQUA("Aqua", "aqua", "&b", "#55FFFF", Material.LIGHT_BLUE_DYE),
      DARK_AQUA("Dark Aqua", "dark_aqua", "&3", "#00AAAA", Material.CYAN_DYE),
      DARK_BLUE("Dark Blue", "dark_blue", "&1", "#0000AA", Material.BLUE_TERRACOTTA),
      BLUE("Blue", "blue", "&9", "#5555FF", Material.BLUE_DYE),
      LIGHT_PURPLE("Pink", "pink", "&d", "#FF55FF", Material.PINK_DYE),
      DARK_PURPLE("Purple", "purple", "&5", "#AA00AA", Material.PURPLE_DYE),
      WHITE("White", "white", "&f", "#FFFFFF", Material.WHITE_DYE),
      GRAY("Light Gray", "light_gray", "&7", "#AAAAAA", Material.LIGHT_GRAY_DYE),
      DARK_GRAY("Dark Gray", "dark_gray", "&8", "#555555", Material.GRAY_DYE),
      BLACK("Black", "black", "&0", "#000000", Material.BLACK_DYE);

      public final String displayName;
      public final String perm;
      public final String code;
      public final String hex;
      public final Material material;

      ChatColors(String displayName, String perm, String code, String hex, Material material) {
         this.displayName = displayName;
         this.perm = perm;
         this.code = code;
         this.hex = hex;
         this.material = material;
      }
   }
}

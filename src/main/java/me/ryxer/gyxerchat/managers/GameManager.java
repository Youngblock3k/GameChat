package me.ryxer.gyxerchat.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import me.ryxer.gyxerchat.GyxerChat;
import me.ryxer.gyxerchat.Keys;
import me.ryxer.gyxerchat.utils.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** Chat games: a question every few minutes, the first right answer wins money. */
public class GameManager {
   private record Game(String answer, int reward, long id) {
   }

   private final GyxerChat plugin;
   private final AtomicReference<Game> current = new AtomicReference<>();
   private BukkitTask timer;
   private BukkitTask timeout;
   private long counter;

   public GameManager(GyxerChat plugin) {
      this.plugin = plugin;
   }

   public void start() {
      this.stop();
      long interval = Math.max(10L, this.plugin.cfg().getLong("chat-games.interval-seconds", 300L)) * 20L;
      this.timer = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
         if (this.plugin.cfg().getBoolean("chat-games.enabled", true)
            && Bukkit.getOnlinePlayers().size() >= this.plugin.cfg().getInt("chat-games.min-players", 1)) {
            this.startNewGame();
         }
      }, Math.min(1200L, interval), interval);
   }

   public void stop() {
      if (this.timer != null) {
         this.timer.cancel();
         this.timer = null;
      }
      this.endTimeout();
      this.current.set(null);
   }

   public void reload() {
      this.start();
   }

   private void endTimeout() {
      if (this.timeout != null) {
         this.timeout.cancel();
         this.timeout = null;
      }
   }

   public boolean isRunning() {
      return this.current.get() != null;
   }

   public void startNewGame() {
      ConfigurationSection games = this.plugin.cfg().getConfigurationSection("chat-games.games");
      if (games == null || games.getKeys(false).isEmpty()) {
         this.plugin.getLogger().warning("No chat games found in config.yml under 'chat-games.games'!");
         return;
      }
      List<String> keys = new ArrayList<>(games.getKeys(false));
      String key = keys.get(ThreadLocalRandom.current().nextInt(keys.size()));
      String question = games.getString(key + ".question", "&cError: Missing Question");
      String answer = games.getString(key + ".ans", "");
      if (answer.isBlank()) {
         this.plugin.getLogger().warning("Chat game " + key + " has no answer ('ans').");
         return;
      }
      Game game = new Game(Text.strip(answer).trim(), games.getInt(key + ".prize", 100), ++this.counter);
      this.current.set(game);
      this.announce(this.plugin.styled("game-start", "task", Text.parse(question)));
      this.endTimeout();
      long seconds = this.plugin.cfg().getLong("chat-games.answer-timeout-seconds", 60L);
      if (seconds > 0) {
         this.timeout = Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (this.current.compareAndSet(game, null)) {
               this.announce(this.plugin.styled("game-timeout", "answer", game.answer()));
            }
         }, seconds * 20L);
      }
   }

   private void announce(Component msg) {
      for (Player p : Bukkit.getOnlinePlayers()) {
         if (!this.plugin.prefs().get(p.getUniqueId()).has(Keys.CHATGAMES_DISABLED)) {
            p.sendMessage(msg);
         }
      }
      Bukkit.getConsoleSender().sendMessage(msg);
   }

   /** Called from the chat thread for every message that is really sent. Only one player can win. */
   public boolean checkAnswer(Player p, String msg) {
      Game game = this.current.get();
      if (game == null || !msg.trim().equalsIgnoreCase(game.answer())) {
         return false;
      }
      if (!this.current.compareAndSet(game, null)) {
         return false;
      }
      this.plugin.runSync(() -> {
         this.endTimeout();
         int reward = game.reward();
         if (this.plugin.cfg().getBoolean("chat-games.use-vault", true) && this.plugin.getEconomy() != null) {
            this.plugin.getEconomy().depositPlayer(p, reward);
         } else {
            String cmd = this.plugin.cfg().getString("chat-games.reward-command", "eco give {player} {reward}")
               .replace("{player}", p.getName()).replace("{reward}", String.valueOf(reward));
            if (!cmd.isBlank()) {
               Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.startsWith("/") ? cmd.substring(1) : cmd);
            }
         }
         this.announce(this.plugin.styled("game-win", "player", p.getName(), "reward", reward, "answer", game.answer()));
         this.plugin.playAudio(p, "success");
      });
      return true;
   }
}

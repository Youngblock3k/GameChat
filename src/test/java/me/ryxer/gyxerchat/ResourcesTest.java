package me.ryxer.gyxerchat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Every message the code asks for exists in config.yml, and every command in the code is in plugin.yml. */
class ResourcesTest {
   private static YamlConfiguration load(String name) throws IOException {
      try (InputStream in = ResourcesTest.class.getClassLoader().getResourceAsStream(name)) {
         assertNotNull(in, name);
         return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
      }
   }

   @Test
   void allMessageKeysExist() throws IOException {
      YamlConfiguration cfg = load("config.yml");
      Pattern call = Pattern.compile("(?:send\\([^,]+,|msg\\(|styled\\(|rawMsg\\(|getParsedMsg\\((?:true|false),\\s*(?:[^,]*\\?\\s*)?)\\s*\"([a-z0-9-]+)\"");
      Pattern ternary = Pattern.compile("\\?\\s*\"([a-z0-9-]+)\"\\s*:\\s*\"([a-z0-9-]+)\"");
      TreeSet<String> missing = new TreeSet<>();
      int found = 0;
      try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
         for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
            String src = Files.readString(f);
            List<String> keys = new ArrayList<>();
            Matcher m = call.matcher(src);
            while (m.find()) {
               keys.add(m.group(1));
            }
            Matcher t = ternary.matcher(src);
            while (t.find()) {
               keys.add(t.group(1));
               keys.add(t.group(2));
            }
            for (String k : keys) {
               found++;
               if (!cfg.isSet("messages." + k) && !isSettingPath(k)) {
                  missing.add(k + " (" + f.getFileName() + ")");
               }
            }
         }
      }
      assertTrue(found > 60, "found only " + found + " message uses");
      assertEquals(new TreeSet<>(), missing);
   }

   private static boolean isSettingPath(String k) {
      // ternaries that are not messages (sound names, config paths)
      return List.of("success", "fail", "click", "mention", "warn", "clear-chat", "tpa-tick", "tpa-success").contains(k)
         || k.startsWith("discord.") || k.endsWith("-no-prefix");
   }

   @Test
   void commandsAreRegistered() throws IOException {
      YamlConfiguration plugin = load("plugin.yml");
      for (String c : me.ryxer.gyxerchat.commands.ChatCommands.COMMANDS) {
         assertTrue(plugin.isConfigurationSection("commands." + c), c);
      }
      for (String c : me.ryxer.gyxerchat.commands.TpaCommands.COMMANDS) {
         assertTrue(plugin.isConfigurationSection("commands." + c), c);
      }
   }

   @Test
   void defaultConfigIsComplete() throws IOException {
      YamlConfiguration cfg = load("config.yml");
      assertEquals(2, cfg.getInt("config-version"));
      assertTrue(cfg.getConfigurationSection("chat-games.games").getKeys(false).size() >= 4);
      assertTrue(cfg.getStringList("discord.console-commands.allowed-user-ids").isEmpty());
      assertEquals(false, cfg.getBoolean("discord.console-commands.enabled"));
      for (String s : cfg.getConfigurationSection("sounds").getKeys(false)) {
         assertTrue(cfg.getString("sounds." + s).matches("[A-Z_]+"), s);
      }
   }
}

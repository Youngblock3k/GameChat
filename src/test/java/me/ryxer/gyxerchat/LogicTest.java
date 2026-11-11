package me.ryxer.gyxerchat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import me.ryxer.gyxerchat.utils.ChatFilters;
import me.ryxer.gyxerchat.utils.Durations;
import me.ryxer.gyxerchat.utils.FontUtil;
import me.ryxer.gyxerchat.utils.MessageBuilder;
import me.ryxer.gyxerchat.utils.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

class LogicTest {
   @Test
   void durations() {
      assertEquals(600_000L, Durations.parse("10m"));
      assertEquals(5_400_000L, Durations.parse("1h30m"));
      assertEquals(7 * 86_400_000L, Durations.parse("7d"));
      assertEquals(604_800_000L, Durations.parse("1w"));
      assertEquals(-1L, Durations.parse("abc"));
      assertEquals(-1L, Durations.parse("10x"));
      assertEquals(-1L, Durations.parse("10mfoo"));
      assertEquals(-1L, Durations.parse("0m"));
      assertEquals(-1L, Durations.parse("99999999999999999999d"));
      assertEquals("1h 30m", Durations.format(5_400_000L));
      assertEquals("10m", Durations.format(600_000L));
      assertEquals("45s", Durations.format(44_100L));
      assertEquals("2d 3h", Durations.format(2 * 86_400_000L + 3 * 3_600_000L + 5_000L));
   }

   @Test
   void smallCapsLeavesCodesAlone() {
      assertEquals("ʜᴇʟʟᴏ", FontUtil.toSmallCaps("Hello"));
      assertEquals("&aʜɪ &#FFAA00ᴏᴋ", FontUtil.toSmallCaps("&aHi &#FFAA00ok"));
      assertEquals("<red>ʜɪ</red> {player}", FontUtil.toSmallCaps("<red>Hi</red> {player}"));
      assertEquals("§cᴀ", FontUtil.toSmallCaps("§cA"));
   }

   @Test
   void legacyAndMiniMessageMix() {
      Component c = Text.parse("&aGreen <bold>bold</bold> &#FF00AAhex");
      assertEquals("Green bold hex", Text.plain(c));
      assertEquals("", Text.strip("&a&l"));
      assertEquals("Hi there", Text.strip("<gradient:#ff0000:#0000ff>Hi</gradient> &cthere"));
      // a color code ends bold, like vanilla
      Component bold = Text.parse("&lA&cB");
      TextComponent b = find(bold, "B");
      assertNotNull(b);
      assertEquals(NamedTextColor.RED, b.color());
      assertEquals(TextDecoration.State.FALSE, b.decoration(TextDecoration.BOLD));
   }

   @Test
   void placeholdersCannotInject() {
      Component out = Text.render("&7{player} » {message}", true, Map.of(
         "player", Component.text("Steve"),
         "message", Component.text("<click:run_command:/op me>hi</click>")));
      assertEquals("Steve » <click:run_command:/op me>hi</click>", Text.plain(out).replace("»", "»"));
      assertFalse(hasClick(out));
      // unknown placeholders stay as written
      assertEquals("{nope}", Text.plain(Text.render("{nope}", false, Map.of())));
      // small caps only change the template's own words
      assertEquals("ᴊᴏɪɴᴇᴅ Steve", Text.plain(Text.render("joined {player}", true, Map.of("player", Component.text("Steve")))));
   }

   @Test
   void playerTextOnlyLegacy() {
      assertEquals("<red>x", Text.plain(Text.playerText("<red>x", true)));
      Component colored = Text.playerText("&cRed", true);
      assertEquals("Red", Text.plain(colored));
      assertEquals("&cRed", Text.plain(Text.playerText("&cRed", false)));
   }

   @Test
   void unicodeFilter() {
      assertEquals("Hallo Grüße ä é ß", ChatFilters.removeBlockedUnicode("Hallo Grüße ä é ß", ""));
      assertEquals("abc", ChatFilters.removeBlockedUnicode("a‮b\u0000c😀", ""));
      assertEquals("I ❤ you", ChatFilters.removeBlockedUnicode("I ❤ you", "❤"));
      assertEquals("", ChatFilters.removeBlockedUnicode("กข", ""));
   }

   @Test
   void caps() {
      assertTrue(ChatFilters.isShouting("HELLO EVERYONE", 5, 70));
      assertFalse(ChatFilters.isShouting("Hello Everyone", 5, 70));
      assertFalse(ChatFilters.isShouting("OK", 5, 70));
      assertFalse(ChatFilters.isShouting("12345 !!!", 5, 70));
      assertEquals("Hello everyone", ChatFilters.calmDown("HELLO EVERYONE"));
   }

   @Test
   void swearFilter() {
      List<Pattern> p = ChatFilters.compileBlockedWords(List.of("shit", "a.b", ""));
      assertEquals("oh *** man", ChatFilters.censor("oh shit man", p));
      assertEquals("oh *** man", ChatFilters.censor("oh S h-I_t man", p));
      assertNull(ChatFilters.censor("nice weather", p));
      assertNull(ChatFilters.censor("axb", p));
      assertEquals("***", ChatFilters.censor("a.b", p));
   }

   @Test
   void mentions() {
      Pattern p = ChatFilters.mentionPattern("Steve");
      assertTrue(p.matcher("hi steve!").find());
      assertTrue(p.matcher("@Steve hi").find());
      assertFalse(p.matcher("Steven hi").find());
      assertFalse(p.matcher("mail@steve").find());
   }

   @Test
   void messageBuilderSolidColorAndMention() {
      Component c = MessageBuilder.build("hi @steve and bob", "&c", false, List.of("Steve"), "&b");
      assertEquals("hi @Steve and bob", Text.plain(c));
      assertEquals(NamedTextColor.RED, c.color());
      TextComponent mention = find(c, "@Steve");
      assertNotNull(mention);
      assertEquals(NamedTextColor.AQUA, mention.color());
   }

   @Test
   void messageBuilderGradient() {
      Component c = MessageBuilder.build("&lab cd", "gradient:#FF0000:#0000FF", true, List.of(), "&b");
      assertEquals("ab cd", Text.plain(c));
      assertEquals(TextColor.color(0xFF0000), find(c, "a").color());
      assertEquals(TextColor.color(0x0000FF), find(c, "d").color());
      // a broken saved gradient falls back to white
      assertEquals("x", Text.plain(MessageBuilder.build("x", "gradient:nope", false, List.of(), "&b")));
   }

   @Test
   void styleOf() {
      assertEquals(NamedTextColor.GOLD, MessageBuilder.styleOf("&6").color());
      assertEquals(TextColor.color(0x12AB34), MessageBuilder.styleOf("&#12AB34").color());
      assertEquals(NamedTextColor.WHITE, MessageBuilder.styleOf("junk").color());
   }

   private static TextComponent find(Component c, String content) {
      if (c instanceof TextComponent t && t.content().equals(content)) {
         return t;
      }
      for (Component child : c.children()) {
         TextComponent f = find(child, content);
         if (f != null) {
            // children inherit the parent's color when they have none
            if (f.color() == null && c.color() != null) {
               return f.color(c.color());
            }
            return f;
         }
      }
      return null;
   }

   private static boolean hasClick(Component c) {
      if (c.clickEvent() != null && c.clickEvent().action() == ClickEvent.Action.RUN_COMMAND) {
         return true;
      }
      for (Component child : c.children()) {
         if (hasClick(child)) {
            return true;
         }
      }
      return false;
   }
}

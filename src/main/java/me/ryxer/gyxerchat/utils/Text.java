package me.ryxer.gyxerchat.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * All text in GyxerChat goes through here. Config strings may mix classic codes (&a, &l, &#FF00AA)
 * and MiniMessage tags (<red>, <gradient:...>). Text typed by players is never read as MiniMessage.
 */
public final class Text {
   public static final MiniMessage MM = MiniMessage.miniMessage();
   /** Classic & codes incl. &#RRGGBB and &x&R&R&G&G&B&B. Can't create click/hover events, so it is safe for player text. */
   public static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.builder()
      .character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
   private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.builder()
      .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
   private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z0-9_-]+)}");
   private static final Pattern X_HEX = Pattern.compile("[&§][xX]((?:[&§][0-9a-fA-F]){6})");
   private static final Pattern HASH_HEX = Pattern.compile("[&§]#([0-9a-fA-F]{6})");
   private static final Pattern CODE = Pattern.compile("[&§]([0-9a-fA-Fk-oK-OrR])");
   private static final String[] COLORS = {
      "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
      "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"
   };
   private static final String RESET_DECORATIONS = "<!obfuscated><!bold><!strikethrough><!underlined><!italic>";

   private Text() {
   }

   /** Turns classic color codes into MiniMessage tags. A color code also ends bold/italic/..., just like in vanilla. */
   public static String legacyToMini(String s) {
      if (s == null || s.isEmpty()) {
         return "";
      }
      Matcher x = X_HEX.matcher(s);
      StringBuilder sb = new StringBuilder();
      while (x.find()) {
         String hex = x.group(1).replaceAll("[&§]", "");
         x.appendReplacement(sb, Matcher.quoteReplacement(RESET_DECORATIONS + "<#" + hex + ">"));
      }
      x.appendTail(sb);
      Matcher h = HASH_HEX.matcher(sb.toString());
      sb = new StringBuilder();
      while (h.find()) {
         h.appendReplacement(sb, Matcher.quoteReplacement(RESET_DECORATIONS + "<#" + h.group(1) + ">"));
      }
      h.appendTail(sb);
      Matcher c = CODE.matcher(sb.toString());
      sb = new StringBuilder();
      while (c.find()) {
         char code = Character.toLowerCase(c.group(1).charAt(0));
         String rep = switch (code) {
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> RESET_DECORATIONS + "<" + COLORS[Character.digit(code, 16)] + ">";
         };
         c.appendReplacement(sb, Matcher.quoteReplacement(rep));
      }
      c.appendTail(sb);
      return sb.toString();
   }

   /** A config string (classic codes and/or MiniMessage) as a component. */
   public static Component parse(String s) {
      return MM.deserialize(legacyToMini(s));
   }

   /**
    * Fills a config template. {key} placeholders become the given components, so player names and
    * messages can never inject formatting. Small caps (if on) only touch the template's own words.
    */
   public static Component render(String template, boolean smallCaps, Map<String, Component> values) {
      if (template == null) {
         template = "";
      }
      String t = smallCaps ? FontUtil.toSmallCaps(template) : template;
      List<TagResolver> resolvers = new ArrayList<>();
      Matcher m = PLACEHOLDER.matcher(t);
      StringBuilder sb = new StringBuilder();
      while (m.find()) {
         String key = m.group(1);
         Component value = values.get(key);
         if (value == null) {
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
         } else {
            String tag = "gx_" + key.replace('-', '_');
            m.appendReplacement(sb, Matcher.quoteReplacement("<" + tag + ">"));
            resolvers.add(Placeholder.component(tag, value));
         }
      }
      m.appendTail(sb);
      return MM.deserialize(legacyToMini(sb.toString()), TagResolver.resolver(resolvers));
   }

   /** Text a player typed: classic & codes only if allowed, never MiniMessage. */
   public static Component playerText(String raw, boolean allowCodes) {
      return allowCodes ? AMPERSAND.deserialize(raw) : Component.text(raw);
   }

   /** Item names: plain white-ish text without the default italic. */
   public static Component item(String s) {
      return parse(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
   }

   public static String plain(Component c) {
      return PlainTextComponentSerializer.plainText().serialize(c);
   }

   /** Removes &/§ codes, hex codes and MiniMessage tags: what the text looks like without colors. */
   public static String strip(String s) {
      if (s == null) {
         return "";
      }
      return plain(parse(s));
   }

   /** Old saved values (nicknames/tags from version 1) may contain § codes. */
   public static Component legacySection(String s) {
      return SECTION.deserialize(s);
   }
}

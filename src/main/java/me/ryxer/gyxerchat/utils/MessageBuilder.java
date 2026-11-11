package me.ryxer.gyxerchat.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyFormat;

/** Turns what a player typed into the colored chat component (chat color or gradient, plus highlighted @mentions). */
public final class MessageBuilder {
   private record Range(int start, int end, String name) {
   }

   private MessageBuilder() {
   }

   /**
    * @param colorSetting saved chat color: "&c", "&#FF00AA" or "gradient:#AA0000:#5555FF" (null = white)
    * @param mentionNames online player names that were mentioned (found with {@link ChatFilters#mentionPattern})
    */
   public static Component build(String text, String colorSetting, boolean allowCodes, List<String> mentionNames, String mentionColor) {
      String setting = colorSetting == null || colorSetting.isBlank() ? "&f" : colorSetting;
      TextColor[] gradient = gradient(setting);
      String body = text;
      if (gradient != null && allowCodes) {
         body = Text.plain(Text.AMPERSAND.deserialize(body));
      }
      List<Range> ranges = mentions(body, mentionNames);
      if (gradient != null) {
         return gradientText(body, gradient[0], gradient[1], ranges, mentionColor);
      }
      TextComponent.Builder root = Component.text().style(styleOf(setting));
      int pos = 0;
      for (Range r : ranges) {
         if (r.start() > pos) {
            root.append(Text.playerText(body.substring(pos, r.start()), allowCodes));
         }
         root.append(mention(r.name(), mentionColor));
         pos = r.end();
      }
      if (pos < body.length()) {
         root.append(Text.playerText(body.substring(pos), allowCodes));
      }
      return root.build();
   }

   private static Component mention(String name, String mentionColor) {
      return Text.parse((mentionColor == null ? "&b" : mentionColor) + "@" + name);
   }

   private static List<Range> mentions(String body, List<String> names) {
      List<Range> all = new ArrayList<>();
      if (names != null) {
         for (String n : names) {
            Matcher m = ChatFilters.mentionPattern(n).matcher(body);
            while (m.find()) {
               all.add(new Range(m.start(), m.end(), n));
            }
         }
      }
      all.sort(Comparator.comparingInt(Range::start).thenComparingInt(r -> -(r.end() - r.start())));
      List<Range> out = new ArrayList<>();
      int last = -1;
      for (Range r : all) {
         if (r.start() >= last) {
            out.add(r);
            last = r.end();
         }
      }
      return out;
   }

   static TextColor[] gradient(String setting) {
      if (!setting.startsWith("gradient:")) {
         return null;
      }
      String[] parts = setting.split(":");
      if (parts.length != 3) {
         return null;
      }
      TextColor a = TextColor.fromHexString(parts[1]);
      TextColor b = TextColor.fromHexString(parts[2]);
      return a == null || b == null ? null : new TextColor[]{a, b};
   }

   private static Component gradientText(String body, TextColor start, TextColor end, List<Range> ranges, String mentionColor) {
      int count = (int)body.codePoints().filter(cp -> cp != ' ').count();
      TextComponent.Builder root = Component.text();
      int step = 0;
      int ri = 0;
      int i = 0;
      while (i < body.length()) {
         if (ri < ranges.size() && ranges.get(ri).start() == i) {
            Range r = ranges.get(ri++);
            root.append(mention(r.name(), mentionColor));
            step += (int)body.substring(r.start(), r.end()).codePoints().filter(cp -> cp != ' ').count();
            i = r.end();
            continue;
         }
         int cp = body.codePointAt(i);
         String ch = new String(Character.toChars(cp));
         if (cp == ' ') {
            root.append(Component.text(ch));
         } else {
            float ratio = count > 1 ? (float)step / (count - 1) : 0.0F;
            root.append(Component.text(ch, TextColor.lerp(ratio, start, end)));
            step++;
         }
         i += Character.charCount(cp);
      }
      return root.build();
   }

   /** The look of a saved color like "&c", "&l&b" or "&#FF00AA". */
   public static Style styleOf(String codes) {
      Style.Builder style = Style.style();
      boolean any = false;
      for (int i = 0; i + 1 < codes.length(); i++) {
         char c = codes.charAt(i);
         if (c != '&' && c != '§') {
            continue;
         }
         if (codes.charAt(i + 1) == '#' && i + 8 <= codes.length()) {
            TextColor hex = TextColor.fromHexString(codes.substring(i + 1, i + 8));
            if (hex != null) {
               style.color(hex);
               any = true;
               i += 7;
               continue;
            }
         }
         LegacyFormat f = LegacyComponentSerializer.parseChar(codes.charAt(i + 1));
         if (f != null) {
            if (f.color() != null) {
               style.color(f.color());
               any = true;
            } else if (f.decoration() != null) {
               style.decoration(f.decoration(), true);
            }
            i++;
         }
      }
      if (!any) {
         style.color(NamedTextColor.WHITE);
      }
      return style.build();
   }
}

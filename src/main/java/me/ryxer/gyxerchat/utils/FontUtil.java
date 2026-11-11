package me.ryxer.gyxerchat.utils;

/** Small caps letters (ᴀʙᴄ). Leaves color codes, hex codes, MiniMessage tags, {placeholders} and %placeholders% alone. */
public final class FontUtil {
   private static final String NORMAL_LOWER = "abcdefghijklmnopqrstuvwxyz";
   private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀѕᴛᴜᴠᴡхʏᴢ";

   private FontUtil() {
   }

   public static String toSmallCaps(String input) {
      if (input == null) {
         return "";
      }
      StringBuilder sb = new StringBuilder(input.length());
      int n = input.length();
      for (int i = 0; i < n; i++) {
         char c = input.charAt(i);
         if ((c == '&' || c == '§') && i + 1 < n) {
            char next = input.charAt(i + 1);
            if (next == '#' && isHex(input, i + 2, 6)) {
               sb.append(input, i, i + 8);
               i += 7;
               continue;
            }
            if ("0123456789abcdefklmnorxABCDEFKLMNORX".indexOf(next) >= 0) {
               sb.append(c).append(next);
               i++;
               continue;
            }
         }
         int skipTo = -1;
         if (c == '<') {
            skipTo = input.indexOf('>', i);
         } else if (c == '{') {
            skipTo = input.indexOf('}', i);
         }
         if (skipTo > i) {
            sb.append(input, i, skipTo + 1);
            i = skipTo;
            continue;
         }
         int index = NORMAL_LOWER.indexOf(Character.toLowerCase(c));
         sb.append(index != -1 && c < 128 ? SMALL_CAPS.charAt(index) : c);
      }
      return sb.toString();
   }

   private static boolean isHex(String s, int from, int len) {
      if (from + len > s.length()) {
         return false;
      }
      for (int i = from; i < from + len; i++) {
         if (Character.digit(s.charAt(i), 16) < 0) {
            return false;
         }
      }
      return true;
   }
}

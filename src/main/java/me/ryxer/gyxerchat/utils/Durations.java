package me.ryxer.gyxerchat.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** "1d2h30m" <-> milliseconds. */
public final class Durations {
   private static final Pattern PART = Pattern.compile("(\\d+)(w|d|h|m|s)");
   private static final Pattern WHOLE = Pattern.compile("((\\d+)(w|d|h|m|s))+");

   private Durations() {
   }

   /** Milliseconds, or -1 when the text is not a valid time like 10m, 1h30m, 7d. */
   public static long parse(String time) {
      if (time == null) {
         return -1L;
      }
      String t = time.toLowerCase();
      if (!WHOLE.matcher(t).matches()) {
         return -1L;
      }
      long total = 0L;
      Matcher m = PART.matcher(t);
      while (m.find()) {
         long val;
         try {
            val = Long.parseLong(m.group(1));
         } catch (NumberFormatException e) {
            return -1L;
         }
         long unit = switch (m.group(2)) {
            case "w" -> 604_800_000L;
            case "d" -> 86_400_000L;
            case "h" -> 3_600_000L;
            case "m" -> 60_000L;
            default -> 1_000L;
         };
         if (val > Long.MAX_VALUE / unit / 2) {
            return -1L;
         }
         total += val * unit;
      }
      return total > 0L ? total : -1L;
   }

   /** 5400000 -> "1h 30m". Rounds up to whole seconds. */
   public static String format(long ms) {
      long s = Math.max(1L, (ms + 999L) / 1000L);
      long d = s / 86400L;
      s %= 86400L;
      long h = s / 3600L;
      s %= 3600L;
      long m = s / 60L;
      s %= 60L;
      StringBuilder sb = new StringBuilder();
      if (d > 0) sb.append(d).append("d ");
      if (h > 0) sb.append(h).append("h ");
      if (m > 0) sb.append(m).append("m ");
      if (s > 0 && d == 0) sb.append(s).append("s ");
      return sb.toString().trim();
   }
}

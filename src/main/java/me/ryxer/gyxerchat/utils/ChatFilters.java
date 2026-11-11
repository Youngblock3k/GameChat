package me.ryxer.gyxerchat.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** The chat filters as plain functions (no server needed, so they are unit tested). */
public final class ChatFilters {
   private ChatFilters() {
   }

   /** Keeps normal keyboard text, letters of any Latin alphabet (ä, é, ß...) and the extra allowed characters. */
   public static String removeBlockedUnicode(String text, String allowed) {
      StringBuilder sb = new StringBuilder(text.length());
      text.codePoints().forEach(cp -> {
         boolean ok = (cp >= 0x20 && cp <= 0x7E)
            || (Character.isLetter(cp) && Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN)
            || (allowed != null && allowed.indexOf(cp) >= 0);
         if (ok) {
            sb.appendCodePoint(cp);
         }
      });
      return sb.toString();
   }

   /** True when at least {percent}% of the letters are capitals. Only letters count, not spaces or numbers. */
   public static boolean isShouting(String text, int minLength, int percent) {
      int letters = 0;
      int upper = 0;
      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (Character.isLetter(c)) {
            letters++;
            if (Character.isUpperCase(c)) {
               upper++;
            }
         }
      }
      return letters >= Math.max(1, minLength) && upper * 100 >= percent * letters;
   }

   /** "HELLO EVERYONE" -> "Hello everyone". */
   public static String calmDown(String text) {
      if (text.isEmpty()) {
         return text;
      }
      String lower = text.toLowerCase();
      return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
   }

   /** One pattern per blocked word that also catches "f u c k", "f.u.c.k" and "FUCK". */
   public static List<Pattern> compileBlockedWords(List<String> words) {
      List<Pattern> out = new ArrayList<>();
      for (String w : words) {
         if (w == null || w.isBlank()) {
            continue;
         }
         StringBuilder re = new StringBuilder();
         int[] cps = w.trim().codePoints().toArray();
         for (int i = 0; i < cps.length; i++) {
            if (i > 0) {
               re.append("[\\s\\W_]*");
            }
            re.append(Pattern.quote(new String(Character.toChars(cps[i]))));
         }
         out.add(Pattern.compile(re.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
      }
      return out;
   }

   /** Returns the censored text, or null when nothing was blocked. */
   public static String censor(String text, List<Pattern> patterns) {
      String result = text;
      boolean hit = false;
      for (Pattern p : patterns) {
         var m = p.matcher(result);
         if (m.find()) {
            hit = true;
            result = m.replaceAll("***");
         }
      }
      return hit ? result : null;
   }

   /** Finds the word "name" or "@name" (any case) in the text. */
   public static Pattern mentionPattern(String name) {
      return Pattern.compile("(?<![\\w@])@?" + Pattern.quote(name) + "(?!\\w)", Pattern.CASE_INSENSITIVE);
   }
}

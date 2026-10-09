package viewer.ops;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;

/**
 * A quick, offline estimate of how hard a password is to guess, plus a generator.
 *
 * <p>This is a heuristic (length, variety, common words, obvious runs), not a guarantee. It exists to steer
 * people away from "123456" and "password", not to certify strength.</p>
 */
public final class PasswordStrength {

    public enum Level { EMPTY, WEAK, FAIR, GOOD, STRONG }

    public record Result(Level level, int score, String hint) { }

    private static final Set<String> COMMON = Set.of("password", "123456", "12345678", "123456789", "qwerty", "qwertyuiop",
            "letmein", "welcome", "admin", "iloveyou", "abc123", "111111", "000000", "monkey", "dragon", "secret",
            "readora", "pdf", "passw0rd", "p@ssw0rd", "changeme", "login", "master", "hello", "football", "baseball");

    private PasswordStrength() { }

    public static Result evaluate(char[] pw) {
        if (pw == null || pw.length == 0) return new Result(Level.EMPTY, 0, "");
        String s = new String(pw);
        int len = s.length();
        boolean lower = false, upper = false, digit = false, symbol = false;
        for (char c : pw) {
            if (Character.isLowerCase(c)) lower = true;
            else if (Character.isUpperCase(c)) upper = true;
            else if (Character.isDigit(c)) digit = true;
            else symbol = true;
        }
        int variety = (lower ? 1 : 0) + (upper ? 1 : 0) + (digit ? 1 : 0) + (symbol ? 1 : 0);
        int score = Math.min(len, 16) * 4 + variety * 9;                      // up to 64 + 36

        String lowerS = s.toLowerCase(Locale.ROOT);
        boolean common = COMMON.contains(lowerS);
        if (!common) for (String w : COMMON) if (w.length() >= 6 && lowerS.contains(w)) { common = true; break; }
        boolean allSame = s.chars().distinct().count() == 1;
        boolean run = isRun(s);

        String hint;
        if (common || allSame || run) { score = Math.min(score, 15); hint = "This is a very common pattern. Avoid it."; }
        else if (len < 8) { score = Math.min(score, 30); hint = "Use at least 8 characters."; }
        else if (variety < 2) { score = Math.min(score, 45); hint = "Mix in capitals, numbers or symbols."; }
        else if (len < 12) { score = Math.min(score, 70); hint = "Longer is stronger: aim for 12 or more."; }
        else hint = score >= 75 ? "Strong." : "Good. A few more characters would make it stronger.";

        Level level = score < 25 ? Level.WEAK : score < 50 ? Level.FAIR : score < 75 ? Level.GOOD : Level.STRONG;
        if (level == Level.STRONG && !hint.equals("Strong.") ) hint = "Strong.";
        return new Result(level, Math.min(100, score), hint);
    }

    /** True for ascending/descending runs such as "abcdefgh" or "87654321". */
    private static boolean isRun(String s) {
        if (s.length() < 5) return false;
        int step = s.charAt(1) - s.charAt(0);
        if (Math.abs(step) != 1) return false;
        for (int i = 1; i < s.length(); i++) if (s.charAt(i) - s.charAt(i - 1) != step) return false;
        return true;
    }

    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz", UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ",
            DIGITS = "23456789", SYMBOLS = "!@#$%^&*-_=+?";

    /** A random password of {@code length} characters with at least one of each kind (no look-alikes such as 0/O, 1/l). */
    public static char[] generate(int length) {
        SecureRandom r = new SecureRandom();
        int n = Math.max(12, length);
        String all = LOWER + UPPER + DIGITS + SYMBOLS;
        char[] out = new char[n];
        String[] must = {LOWER, UPPER, DIGITS, SYMBOLS};
        for (int i = 0; i < n; i++) out[i] = i < must.length ? must[i].charAt(r.nextInt(must[i].length())) : all.charAt(r.nextInt(all.length()));
        for (int i = n - 1; i > 0; i--) {                                      // Fisher-Yates shuffle
            int j = r.nextInt(i + 1);
            char t = out[i]; out[i] = out[j]; out[j] = t;
        }
        return out;
    }
}

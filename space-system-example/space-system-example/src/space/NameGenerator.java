package space;

import java.util.Random;

/** Pronounceable alien names from syllables. Uses the system's own random, so names are deterministic too. */
public final class NameGenerator {
    private static final String[] START = {"ka", "zar", "ne", "vo", "ta", "ri", "sa", "do", "ke", "mi", "lu", "gra", "the", "ox",
            "ze", "ba", "ul", "yu", "shi", "ra", "po", "to", "an", "el", "is", "or", "qua", "ve", "ar", "ji", "go", "ha", "cy", "dra"};
    private static final String[] MIDDLE = {"ra", "ne", "li", "ta", "go", "ri", "ma", "ka", "do", "se", "ru", "ve", "la", "zo", "ti", "na"};
    private static final String[] END = {"k", "n", "x", "th", "r", "s", "ra", "on", "ia", "us", "ar", "ek", "is", "um", "ae", "or", "ul", "ix"};
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private NameGenerator() {}

    public static String name(Random r) {
        StringBuilder sb = new StringBuilder(START[r.nextInt(START.length)]);
        int middles = r.nextInt(3) == 0 ? 0 : 1;
        if (r.nextInt(5) == 0) middles++;
        for (int i = 0; i < middles; i++) sb.append(MIDDLE[r.nextInt(MIDDLE.length)]);
        sb.append(END[r.nextInt(END.length)]);
        sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
        return sb.toString();
    }

    public static String roman(int n) {
        return n >= 1 && n <= ROMAN.length ? ROMAN[n - 1] : Integer.toString(n);
    }
}

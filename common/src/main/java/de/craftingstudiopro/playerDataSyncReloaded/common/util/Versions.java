package de.craftingstudiopro.playerDataSyncReloaded.common.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Version parsing shared by the Paper update checker, the Paper handler choice and the Fabric bootstrap. */
public final class Versions {
    private static final Pattern LEADING = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
    private static final Pattern DIGITS = Pattern.compile("(\\d+)");

    private Versions() {
    }

    /** alpha 0, beta 1, rc/pre/snapshot 2, everything else (Stable, Release, no suffix) 3. */
    public static int channelRank(String version) {
        String v = version.toLowerCase(Locale.ROOT);
        if (v.contains("alpha")) return 0;
        if (v.contains("beta")) return 1;
        if (v.contains("-rc") || v.contains(".rc") || v.contains("pre") || v.contains("snapshot")) return 2;
        return 3;
    }

    public static boolean isPreRelease(String version) {
        return channelRank(version) < 3;
    }

    /**
     * Compares plugin versions like {@code 26.12-Alpha.2}, {@code 26.11-Stable} or {@code 26.10-ALPHA.001}:
     * first the numbers before the dash, then the channel (alpha &lt; beta &lt; rc &lt; stable), then the
     * last number after the dash, so {@code ALPHA.001} equals {@code Alpha.1}.
     */
    public static int compare(String a, String b) {
        int[] na = leadingNumbers(a);
        int[] nb = leadingNumbers(b);
        for (int i = 0; i < Math.max(na.length, nb.length); i++) {
            int x = i < na.length ? na[i] : 0;
            int y = i < nb.length ? nb[i] : 0;
            if (x != y) return Integer.compare(x, y);
        }
        int channel = Integer.compare(channelRank(a), channelRank(b));
        if (channel != 0) return channel;
        return Integer.compare(suffixNumber(a), suffixNumber(b));
    }

    private static int[] leadingNumbers(String version) {
        String[] parts = version.trim().split("-", 2)[0].split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String digits = parts[i].replaceAll("[^0-9]", "");
            out[i] = digits.isEmpty() ? 0 : Integer.parseInt(digits);
        }
        return out;
    }

    private static int suffixNumber(String version) {
        String[] dash = version.trim().split("-", 2);
        if (dash.length < 2) return 0;
        Matcher m = DIGITS.matcher(dash[1]);
        int last = 0;
        while (m.find()) {
            last = Integer.parseInt(m.group(1));
        }
        return last;
    }

    /** major, minor, patch of a Minecraft version such as {@code 1.21.4-R0.1-SNAPSHOT} or {@code 26.2}; null if unreadable. */
    public static int[] parseMinecraft(String version) {
        Matcher m = LEADING.matcher(version == null ? "" : version.trim());
        if (!m.find()) {
            return null;
        }
        return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                m.group(3) == null ? 0 : Integer.parseInt(m.group(3))};
    }

    /** Compares a parsed Minecraft version with major.minor.patch. */
    public static int compareMinecraft(int[] v, int major, int minor, int patch) {
        if (v[0] != major) return Integer.compare(v[0], major);
        if (v[1] != minor) return Integer.compare(v[1], minor);
        return Integer.compare(v[2], patch);
    }

    /**
     * Paper handler package for a Bukkit version: the newest handler at or below it. Null below 1.20.
     */
    public static String paperHandlerFor(String bukkitVersion) {
        int[] v = parseMinecraft(bukkitVersion);
        if (v == null || compareMinecraft(v, 26, 2, 0) >= 0) return "v26_2";
        if (compareMinecraft(v, 1, 21, 4) >= 0) return "v26_1";
        if (compareMinecraft(v, 1, 21, 0) >= 0) return "v1_21_R1";
        if (compareMinecraft(v, 1, 20, 0) >= 0) return "v1_20_R1";
        return null;
    }

    /**
     * Fabric adapter for a Minecraft version: only the versions each adapter was compiled against.
     * 1.20.2+ and 1.21.5+ changed the networking and NBT methods the 1.20 and 1.21 adapters call.
     */
    public static String fabricAdapterFor(String minecraft) {
        if (minecraft == null) return null;
        if (minecraft.matches("26\\.2(\\..*|-.*)?")) return "v26_2_R1";
        if (minecraft.matches("26\\.1(\\..*|-.*)?")) return "v26_1_R1";
        if (minecraft.equals("1.21.4")) return "v1_21_R1";
        if (minecraft.equals("1.20.1")) return "v1_20_R1";
        return null;
    }
}

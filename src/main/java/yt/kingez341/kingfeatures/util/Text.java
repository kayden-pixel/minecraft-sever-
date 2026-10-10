package yt.kingez341.kingfeatures.util;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;

public final class Text {
    private Text() {}

    public static String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    public static List<String> color(List<String> values) {
        List<String> out = new ArrayList<>();
        if (values != null) {
            for (String value : values) out.add(color(value));
        }
        return out;
    }

    public static String strip(String value) {
        return ChatColor.stripColor(color(value));
    }
}

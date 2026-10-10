package yt.kingez341.kingfeatures.troll;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.Locale;

public enum TrollAction {
    FAKE_LIGHTNING("Fake Lightning", Material.LIGHTNING_ROD, "Strike harmless lightning at the target", "lightning", "thunder", "shock"),
    LAUNCH("Launch", Material.FIREWORK_ROCKET, "Throw the target into the air", "launch", "yeet", "air"),
    SPIN("Spin Cycle", Material.COMPASS, "Spin the target around for a few seconds", "spin", "rotate", "dizzy"),
    CREEPER_SCARE("Creeper Scare", Material.CREEPER_HEAD, "Play a creeper fuse behind the target", "creeper", "scare", "fuse"),
    DARKNESS("Darkness", Material.ENDER_EYE, "Give temporary darkness", "dark", "blind", "vision"),
    FREEZE("Freeze", Material.PACKED_ICE, "Temporarily slow the target almost completely", "freeze", "slow", "ice"),
    FAKE_DEATH("Fake Death", Material.SKELETON_SKULL, "Show a fake death screen-style title", "death", "dead", "rip"),
    INVENTORY_SHUFFLE("Inventory Shuffle", Material.BUNDLE, "Shuffle hotbar slots without deleting items", "inventory", "shuffle", "hotbar"),
    SOUND_SPAM("Sound Burst", Material.NOTE_BLOCK, "Play a short burst of confusing sounds", "sound", "noise", "music"),
    GHOST_HIT("Ghost Hit", Material.IRON_SWORD, "Play hurt feedback without dealing damage", "hit", "hurt", "ghost"),
    FAKE_DIAMONDS("Fake Diamonds", Material.DIAMOND, "Show a fake diamond discovery message", "diamond", "ore", "fake"),
    UPSIDE_DOWN("Upside Down", Material.NAME_TAG, "Flip the target's view with a smooth pitch prank", "upside", "view", "camera");

    private final String displayName;
    private final Material icon;
    private final String description;
    private final String[] keywords;

    TrollAction(String displayName, Material icon, String description, String... keywords) {
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
        this.keywords = keywords;
    }

    public String displayName() { return displayName; }
    public Material icon() { return icon; }
    public String description() { return description; }

    public boolean matches(String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.toLowerCase(Locale.ROOT).trim();
        if (displayName.toLowerCase(Locale.ROOT).contains(q) || name().toLowerCase(Locale.ROOT).contains(q)) return true;
        return Arrays.stream(keywords).anyMatch(k -> k.contains(q) || q.contains(k));
    }
}

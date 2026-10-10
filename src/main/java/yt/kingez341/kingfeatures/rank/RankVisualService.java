package yt.kingez341.kingfeatures.rank;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import yt.kingez341.kingfeatures.KingFeaturesPlugin;
import yt.kingez341.kingfeatures.ui.GuiItems;
import yt.kingez341.kingfeatures.util.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Permission-free rank rendering. OmniPanel remains authoritative for the
 * player's rank; KingFeatures only turns the API result into a visual badge.
 */
public final class RankVisualService implements Listener {
    private static final Key RANK_FONT = Key.key("kingfeatures", "ranks");

    private static final LinkedHashMap<String, RankDefinition> RANKS = new LinkedHashMap<>();
    static {
        add(new RankDefinition("owner", "Owner", '\uF341', Material.NETHER_STAR, List.of("owner")));
        add(new RankDefinition("co_owner", "Co-Owner", '\uF342', Material.BLAZE_POWDER, List.of("coowner", "co-owner", "co_owner", "co owner")));
        add(new RankDefinition("ceo", "CEO", '\uF343', Material.GOLD_BLOCK, List.of("ceo")));
        add(new RankDefinition("manager", "Manager", '\uF344', Material.CLOCK, List.of("manager")));
        add(new RankDefinition("admin", "Admin", '\uF345', Material.REDSTONE_TORCH, List.of("admin", "administrator")));
        add(new RankDefinition("builder", "Builder", '\uF346', Material.BRICKS, List.of("builder")));
        add(new RankDefinition("developer", "Developer", '\uF347', Material.REPEATER, List.of("developer", "dev")));
        add(new RankDefinition("moderator", "Moderator", '\uF348', Material.SHIELD, List.of("moderator", "mod")));
        add(new RankDefinition("helper", "Helper", '\uF349', Material.LIME_DYE, List.of("helper")));
        add(new RankDefinition("mvp", "MVP", '\uF34A', Material.AMETHYST_SHARD, List.of("mvp", "mvp+", "mvpplus")));
        add(new RankDefinition("vip", "VIP", '\uF34B', Material.GOLD_INGOT, List.of("vip", "vip+", "vipplus")));
        add(new RankDefinition("youtuber", "YouTuber", '\uF34C', Material.RED_DYE, List.of("youtuber", "youtube", "media", "creator")));
        add(new RankDefinition("member", "Member", '\uF34D', Material.IRON_INGOT, List.of("member", "default", "player")));
    }

    private static final int[] RANK_SLOTS = {10,11,12,13,14,15,16,19,20,21,22,23,24};

    private final KingFeaturesPlugin plugin;
    private final Map<UUID, AppliedState> applied = new LinkedHashMap<>();
    private BukkitTask refreshTask;
    private volatile String status = "not-started";

    public RankVisualService(KingFeaturesPlugin plugin) {
        this.plugin = plugin;
    }

    private static void add(RankDefinition definition) {
        RANKS.put(definition.key(), definition);
    }

    public void start() {
        stopTaskOnly();
        ensureDefaults();
        if (!plugin.getConfig().getBoolean("rank-visuals.enabled", true)) {
            restoreAll();
            status = "disabled";
            return;
        }

        long period = Math.max(10L, plugin.getConfig().getLong("rank-visuals.refresh-ticks", 20L));
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 5L, period);
        for (Player player : Bukkit.getOnlinePlayers()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> apply(player), 10L);
        }
        updateStatus();
    }

    public void stop() {
        stopTaskOnly();
        restoreAll();
        status = "stopped";
    }

    private void stopTaskOnly() {
        if (refreshTask != null) refreshTask.cancel();
        refreshTask = null;
    }

    public String status() {
        return status;
    }

    public void openGui(Player player) {
        VisualHolder holder = new VisualHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, Text.color("&d&lRANK VISUALS &8• &fOmniPanel"));
        holder.inventory = inv;
        render(inv, holder);
        player.openInventory(inv);
    }

    public void refreshAll() {
        if (!plugin.getConfig().getBoolean("rank-visuals.enabled", true)) {
            restoreAll();
            updateStatus();
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) apply(player);
        updateStatus();
    }

    public void apply(Player player) {
        if (player == null || !player.isOnline()) return;
        if (plugin.getOmniPanel() == null || !plugin.getOmniPanel().isConnected()) {
            restore(player);
            return;
        }
        if (!plugin.getConfig().getBoolean("rank-visuals.enabled", true)) {
            restore(player);
            return;
        }

        String key = resolveRankKey(player);
        RankDefinition definition = RANKS.get(key);
        if (definition == null || !isRankEnabled(key)) {
            restore(player);
            return;
        }

        AppliedState state = applied.computeIfAbsent(player.getUniqueId(), ignored -> new AppliedState());
        Component currentList = player.playerListName();
        Component currentDisplay = player.displayName();

        if (state.lastList != null && currentList != null && !currentList.equals(state.lastList)) state.baseList = currentList;
        if (state.lastDisplay != null && currentDisplay != null && !currentDisplay.equals(state.lastDisplay)) state.baseDisplay = currentDisplay;
        if (state.baseList == null) state.baseList = currentList;
        if (state.baseDisplay == null) state.baseDisplay = currentDisplay;

        Component visual = Component.text(String.valueOf(definition.glyph()))
                .font(RANK_FONT)
                .color(NamedTextColor.WHITE)
                .append(Component.space())
                .append(Component.text(player.getName()));

        if (plugin.getConfig().getBoolean("rank-visuals.contexts.player-list", true)) {
            player.playerListName(visual);
            state.lastList = visual;
        } else {
            restoreList(player, state);
        }

        if (plugin.getConfig().getBoolean("rank-visuals.contexts.display-name", true)) {
            player.displayName(visual);
            state.lastDisplay = visual;
        } else {
            restoreDisplay(player, state);
        }
        state.rankKey = key;
    }

    public String resolveRankKey(Player player) {
        if (player == null) return "member";
        String raw = plugin.getOmniPanel() == null ? "" : plugin.getOmniPanel().resolvePlaceholder(player, "%omnipanel_primary_rank_plain%");
        if (raw == null || raw.isBlank()) {
            raw = plugin.getOmniPanel() == null ? "" : plugin.getOmniPanel().resolvePlaceholder(player, "%omnipanel_rank_plain%");
        }
        String normalized = normalize(raw);
        if (normalized.isBlank()) return "member";

        for (RankDefinition definition : RANKS.values()) {
            for (String alias : definition.aliases()) {
                String a = normalize(alias);
                if (normalized.equals(a)) return definition.key();
            }
        }
        String bestKey = null;
        int bestLength = -1;
        for (RankDefinition definition : RANKS.values()) {
            for (String alias : definition.aliases()) {
                String a = normalize(alias);
                if (!a.isBlank() && normalized.contains(a) && a.length() > bestLength) {
                    bestLength = a.length();
                    bestKey = definition.key();
                }
            }
        }
        return bestKey == null ? "member" : bestKey;
    }

    public boolean isRankEnabled(String key) {
        return plugin.getConfig().getBoolean("rank-visuals.enabled-ranks." + key, true);
    }

    public void setRankEnabled(String key, boolean enabled) {
        if (!RANKS.containsKey(key)) return;
        plugin.getConfig().set("rank-visuals.enabled-ranks." + key, enabled);
        plugin.saveConfig();
        refreshAll();
    }

    public void setAllRanks(boolean enabled) {
        for (String key : RANKS.keySet()) plugin.getConfig().set("rank-visuals.enabled-ranks." + key, enabled);
        plugin.saveConfig();
        refreshAll();
    }

    public void toggleContext(String context) {
        String path = "rank-visuals.contexts." + context;
        plugin.getConfig().set(path, !plugin.getConfig().getBoolean(path, true));
        plugin.saveConfig();
        refreshAll();
    }

    public void toggleGlobal() {
        boolean next = !plugin.getConfig().getBoolean("rank-visuals.enabled", true);
        plugin.getConfig().set("rank-visuals.enabled", next);
        plugin.saveConfig();
        if (next) start();
        else {
            restoreAll();
            updateStatus();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> apply(event.getPlayer()), 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        applied.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof VisualHolder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getRawSlot();
        String rank = holder.rankBySlot.get(slot);
        if (rank != null) {
            setRankEnabled(rank, !isRankEnabled(rank));
            render(holder.inventory, holder);
            return;
        }

        switch (slot) {
            case 28 -> { toggleGlobal(); render(holder.inventory, holder); }
            case 30 -> { toggleContext("player-list"); render(holder.inventory, holder); }
            case 32 -> { toggleContext("display-name"); render(holder.inventory, holder); }
            case 34 -> { setAllRanks(true); render(holder.inventory, holder); }
            case 36 -> { setAllRanks(false); render(holder.inventory, holder); }
            case 40 -> {
                if (plugin.isPackAvailable()) plugin.getPackManager().send(player);
                player.sendMessage(Text.color("&aRank resource-pack refresh requested."));
            }
            case 53 -> player.closeInventory();
            default -> { }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof VisualHolder) event.setCancelled(true);
    }

    private void render(Inventory inv, VisualHolder holder) {
        holder.rankBySlot.clear();
        ItemStack filler = GuiItems.filler(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        int index = 0;
        for (RankDefinition definition : RANKS.values()) {
            int slot = RANK_SLOTS[index++];
            boolean enabled = isRankEnabled(definition.key());
            inv.setItem(slot, GuiItems.item(definition.icon(),
                    (enabled ? "&a&l" : "&c&l") + definition.display(),
                    List.of(
                            enabled ? "&aVisual badge enabled" : "&cVisual badge disabled",
                            "&7OmniPanel remains the rank authority.",
                            "&7No rank permission is created or checked.",
                            "",
                            "&eClick to toggle"
                    ), enabled));
            holder.rankBySlot.put(slot, definition.key());
        }

        boolean global = plugin.getConfig().getBoolean("rank-visuals.enabled", true);
        boolean list = plugin.getConfig().getBoolean("rank-visuals.contexts.player-list", true);
        boolean display = plugin.getConfig().getBoolean("rank-visuals.contexts.display-name", true);
        inv.setItem(28, toggleItem(Material.BEACON, "Rank Visual System", global, "Master switch"));
        inv.setItem(30, toggleItem(Material.PLAYER_HEAD, "Player List / TAB", list, "Shows image badge beside the player name"));
        inv.setItem(32, toggleItem(Material.NAME_TAG, "Display / Chat Name", display, "Uses the visual badge in the Paper display name"));
        inv.setItem(34, GuiItems.item(Material.LIME_CONCRETE, "&a&lEnable All Ranks", "&7Turns every rank visual on."));
        inv.setItem(36, GuiItems.item(Material.RED_CONCRETE, "&c&lDisable All Ranks", "&7Turns every rank visual off."));
        inv.setItem(40, GuiItems.item(Material.PAINTING, "&d&lRefresh Resource Pack",
                "&7Ask the pack distributor to rebuild/resend.", "&7Pack: &f" + plugin.packStatus()));
        inv.setItem(49, GuiItems.item(Material.WRITABLE_BOOK, "&d&lHow it works",
                "&7Ranks come directly from the OmniPanel API.",
                "&7The images live in KingFeatures' resource pack.",
                "&7No new LuckPerms or Bukkit permissions are used",
                "&7to decide which badge a player receives."));
        inv.setItem(53, GuiItems.item(Material.BARRIER, "&c&lClose", "&7Close Rank Visual settings."));
    }

    private ItemStack toggleItem(Material material, String title, boolean enabled, String detail) {
        return GuiItems.item(material,
                (enabled ? "&a&l" : "&c&l") + title,
                List.of(
                        enabled ? "&aEnabled" : "&cDisabled",
                        "&7" + detail,
                        "",
                        "&eClick to toggle"
                ), enabled);
    }

    private void restore(Player player) {
        AppliedState state = applied.remove(player.getUniqueId());
        if (state == null) return;
        restoreList(player, state);
        restoreDisplay(player, state);
    }

    private void restoreList(Player player, AppliedState state) {
        Component current = player.playerListName();
        if (state.lastList != null && (current == null || current.equals(state.lastList))) {
            player.playerListName(state.baseList);
        }
        state.lastList = null;
    }

    private void restoreDisplay(Player player, AppliedState state) {
        Component current = player.displayName();
        if (state.lastDisplay != null && current.equals(state.lastDisplay) && state.baseDisplay != null) {
            player.displayName(state.baseDisplay);
        }
        state.lastDisplay = null;
    }

    private void restoreAll() {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player player : online) restore(player);
        applied.clear();
    }

    private void ensureDefaults() {
        ConfigurationSection ranks = plugin.getConfig().getConfigurationSection("rank-visuals.enabled-ranks");
        boolean changed = false;
        for (String key : RANKS.keySet()) {
            String path = "rank-visuals.enabled-ranks." + key;
            if (ranks == null || !plugin.getConfig().contains(path)) {
                plugin.getConfig().set(path, true);
                changed = true;
            }
        }
        if (changed) plugin.saveConfig();
    }

    private void updateStatus() {
        int enabled = 0;
        for (String key : RANKS.keySet()) if (isRankEnabled(key)) enabled++;
        String api = plugin.getOmniPanel() != null && plugin.getOmniPanel().isConnected() ? "connected" : "waiting-for-omnipanel";
        status = (plugin.getConfig().getBoolean("rank-visuals.enabled", true) ? "ready" : "disabled")
                + " api=" + api + " ranks=" + enabled + "/" + RANKS.size()
                + " list=" + plugin.getConfig().getBoolean("rank-visuals.contexts.player-list", true)
                + " display=" + plugin.getConfig().getBoolean("rank-visuals.contexts.display-name", true);
    }

    private static String normalize(String input) {
        if (input == null) return "";
        String clean = Text.strip(input).toLowerCase(Locale.ROOT).trim();
        clean = clean.replace('[', ' ').replace(']', ' ')
                .replace('_', ' ').replace('-', ' ')
                .replace('+', ' ');
        clean = clean.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        return clean;
    }

    private record RankDefinition(String key, String display, char glyph, Material icon, List<String> aliases) { }

    private static final class AppliedState {
        private Component baseList;
        private Component baseDisplay;
        private Component lastList;
        private Component lastDisplay;
        private String rankKey;
    }

    private static final class VisualHolder implements InventoryHolder {
        private final Map<Integer, String> rankBySlot = new LinkedHashMap<>();
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
}

package yt.kingez341.kingfeatures.ranks;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import yt.kingez341.kingfeatures.KingFeaturesPlugin;
import yt.kingez341.kingfeatures.util.Text;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Permissionless rank visual bridge.
 *
 * OmniPanel remains the source of truth for the player's rank. KingFeatures only
 * decorates OmniPanel's existing active-rank visual layer with a resource-pack
 * glyph. No LuckPerms group or permission is created, granted or removed.
 */
public final class RankVisualService implements Listener {
    private static final String TITLE = "KingFeatures • Rank Visuals";

    private record RankDef(String key, String label, char glyph, Material material, int slot) {}

    private static final List<RankDef> RANKS = List.of(
            new RankDef("owner", "Owner", '\uE101', Material.NETHER_STAR, 10),
            new RankDef("co_owner", "Co-Owner", '\uE102', Material.TOTEM_OF_UNDYING, 11),
            new RankDef("ceo", "CEO", '\uE103', Material.GOLD_BLOCK, 12),
            new RankDef("manager", "Manager", '\uE104', Material.COMMAND_BLOCK, 13),
            new RankDef("admin", "Admin", '\uE105', Material.REDSTONE_BLOCK, 14),
            new RankDef("builder", "Builder", '\uE106', Material.BRICKS, 15),
            new RankDef("developer", "Developer", '\uE107', Material.REPEATING_COMMAND_BLOCK, 16),
            new RankDef("moderator", "Moderator", '\uE108', Material.SHIELD, 19),
            new RankDef("helper", "Helper", '\uE109', Material.LIME_DYE, 20),
            new RankDef("mvp", "MVP", '\uE10A', Material.AMETHYST_SHARD, 21),
            new RankDef("vip", "VIP", '\uE10B', Material.PINK_DYE, 22),
            new RankDef("youtuber", "YouTuber", '\uE10C', Material.RED_DYE, 23),
            new RankDef("member", "Member", '\uE10D', Material.IRON_INGOT, 24)
    );

    private static final Map<String, RankDef> BY_KEY = new LinkedHashMap<>();
    static {
        for (RankDef rank : RANKS) BY_KEY.put(rank.key(), rank);
    }

    private final KingFeaturesPlugin plugin;
    private final NamespacedKey appliedKey;
    private final Map<UUID, String> applied = new ConcurrentHashMap<>();
    private BukkitTask refreshTask;
    private volatile String status = "not-started";

    public RankVisualService(KingFeaturesPlugin plugin) {
        this.plugin = plugin;
        this.appliedKey = new NamespacedKey(plugin, "omnipanel_rank_visual_applied");
    }

    public void start() {
        stopTask();
        if (!plugin.getConfig().getBoolean("rank-visuals.enabled", true)) {
            status = "disabled";
            restoreAll();
            return;
        }
        long ticks = Math.max(40L, plugin.getConfig().getLong("rank-visuals.refresh-ticks", 100L));
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, ticks);
        status = "ready ranks=" + enabledCount() + "/" + RANKS.size();
        Bukkit.getScheduler().runTaskLater(plugin, this::refreshAll, 30L);
    }

    public void stop() {
        stopTask();
        applied.clear();
    }

    private void stopTask() {
        if (refreshTask != null) refreshTask.cancel();
        refreshTask = null;
    }

    public String status() {
        return status;
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(new RankGuiHolder(), 54, Component.text(TITLE));
        fill(inventory);
        player.openInventory(inventory);
    }

    public String visualFor(Player player) {
        String plain = resolvePlainRank(player);
        String key = normalizeRank(plain);
        RankDef rank = BY_KEY.get(key);
        if (rank == null || !isEnabled(rank.key())) return plain == null ? "" : plain;
        return rank.glyph() + "";
    }

    public void refreshAll() {
        if (!plugin.getConfig().getBoolean("rank-visuals.enabled", true)) return;
        for (Player player : Bukkit.getOnlinePlayers()) refresh(player);
        status = "ready ranks=" + enabledCount() + "/" + RANKS.size() + " online=" + Bukkit.getOnlinePlayers().size();
    }

    public void refresh(Player player) {
        if (player == null || !player.isOnline()) return;
        String plain = resolvePlainRank(player);
        String rankKey = normalizeRank(plain);
        RankDef def = BY_KEY.get(rankKey);
        if (def == null || !isEnabled(rankKey)) {
            restoreIfApplied(player);
            return;
        }

        String previous = applied.get(player.getUniqueId());
        boolean marked = player.getPersistentDataContainer().has(appliedKey, PersistentDataType.BYTE);
        if (rankKey.equals(previous) && marked) return;

        if (applyOmniPanelVisual(player, def, plain == null || plain.isBlank() ? def.label() : plain)) {
            applied.put(player.getUniqueId(), rankKey);
            player.getPersistentDataContainer().set(appliedKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    private boolean applyOmniPanelVisual(Player player, RankDef def, String displayName) {
        Plugin omni = Bukkit.getPluginManager().getPlugin("OmniPanel");
        if (omni == null || !omni.isEnabled()) {
            status = "waiting-for-omnipanel";
            return false;
        }
        try {
            Method method = findMethod(omni.getClass(), "applyRankVisual", 7);
            if (method == null) throw new NoSuchMethodException("applyRankVisual/7");
            method.setAccessible(true);
            method.invoke(omni, player, String.valueOf(def.glyph()), displayName, 0L, true, "kingfeatures:rankvisual", def.key());
            return true;
        } catch (Throwable throwable) {
            status = "apply-error:" + rootMessage(throwable);
            plugin.getLogger().warning("Could not apply OmniPanel rank visual for " + player.getName() + ": " + rootMessage(throwable));
            return false;
        }
    }

    private void restoreIfApplied(Player player) {
        boolean marked = player.getPersistentDataContainer().has(appliedKey, PersistentDataType.BYTE);
        if (!marked && !applied.containsKey(player.getUniqueId())) return;

        Plugin omni = Bukkit.getPluginManager().getPlugin("OmniPanel");
        if (omni != null && omni.isEnabled()) {
            try {
                Method clear = findMethod(omni.getClass(), "clearAppliedRank", 4);
                if (clear != null) {
                    clear.setAccessible(true);
                    clear.invoke(omni, player.getUniqueId(), false, "KingFeatures", "rank_visual_restore");
                }
                Method sync = findMethod(omni.getClass(), "syncRankVisualFromLuckPermsIfMissing", 1);
                if (sync != null) {
                    sync.setAccessible(true);
                    sync.invoke(omni, player);
                }
                Method refresh = findMethod(omni.getClass(), "refreshPlayerRankVisuals", 1);
                if (refresh != null) {
                    refresh.setAccessible(true);
                    refresh.invoke(omni, player);
                }
            } catch (Throwable throwable) {
                plugin.getLogger().warning("Could not restore OmniPanel rank visual for " + player.getName() + ": " + rootMessage(throwable));
            }
        }
        applied.remove(player.getUniqueId());
        player.getPersistentDataContainer().remove(appliedKey);
    }

    private void restoreAll() {
        for (Player player : Bukkit.getOnlinePlayers()) restoreIfApplied(player);
    }

    private String resolvePlainRank(Player player) {
        if (plugin.getOmniPanel() == null) return "";
        String rank = plugin.getOmniPanel().resolvePlaceholder(player, "rank_plain");
        return rank == null ? "" : rank.trim();
    }

    private boolean isEnabled(String key) {
        return plugin.getConfig().getBoolean("rank-visuals.ranks." + key, true);
    }

    private int enabledCount() {
        int count = 0;
        for (RankDef rank : RANKS) if (isEnabled(rank.key())) count++;
        return count;
    }

    private void setEnabled(String key, boolean enabled) {
        plugin.getConfig().set("rank-visuals.ranks." + key, enabled);
        plugin.saveConfig();
        Bukkit.getScheduler().runTask(plugin, this::refreshAll);
    }

    private void fill(Inventory inventory) {
        ItemStack pane = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, pane);

        for (RankDef rank : RANKS) {
            boolean enabled = isEnabled(rank.key());
            List<String> lore = List.of(
                    "&7OmniPanel rank: &f" + rank.label(),
                    "&7Badge glyph: &fU+" + Integer.toHexString(rank.glyph()).toUpperCase(Locale.ROOT),
                    "&7Status: " + (enabled ? "&aENABLED" : "&cDISABLED"),
                    "",
                    "&7No LuckPerms permission is added or changed.",
                    Bukkit.getOnlinePlayers().stream().anyMatch(Player::isOp) ? "" : "",
                    "&eOP: click to toggle globally"
            );
            inventory.setItem(rank.slot(), item(rank.material(), (enabled ? "&a" : "&c") + rank.label(), lore));
        }

        inventory.setItem(40, item(Material.BEACON, "&d&lOmniPanel Rank Visuals", List.of(
                "&7Rank source: &fOmniPanel API",
                "&7Permissions: &fnone added",
                "&7Enabled: &f" + enabledCount() + "/" + RANKS.size(),
                "",
                "&7The badge texture comes from the KingFeatures pack."
        )));
        inventory.setItem(48, item(Material.LIME_DYE, "&aEnable All", List.of("&7OP only. No permission node is used.")));
        inventory.setItem(50, item(Material.RED_DYE, "&cDisable All", List.of("&7OP only. Restores OmniPanel/LuckPerms visuals.")));
        inventory.setItem(53, item(Material.BARRIER, "&cClose", List.of()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> refresh(event.getPlayer()), 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        applied.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RankGuiHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        if (slot == 53) {
            player.closeInventory();
            return;
        }
        if (!player.isOp()) {
            player.sendMessage(Text.color("&eRank visuals are automatic. Only server operators can change the global rank list; no permission node is used."));
            return;
        }

        if (slot == 48 || slot == 50) {
            boolean enable = slot == 48;
            for (RankDef rank : RANKS) plugin.getConfig().set("rank-visuals.ranks." + rank.key(), enable);
            plugin.saveConfig();
            if (!enable) restoreAll(); else refreshAll();
            fill(event.getInventory());
            player.sendMessage(Text.color(enable ? "&aEnabled badge visuals for every mapped OmniPanel rank." : "&cDisabled all KingFeatures rank badges and restored OmniPanel visuals."));
            return;
        }

        for (RankDef rank : RANKS) {
            if (rank.slot() != slot) continue;
            boolean next = !isEnabled(rank.key());
            setEnabled(rank.key(), next);
            if (!next) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    String key = normalizeRank(resolvePlainRank(online));
                    if (rank.key().equals(key)) restoreIfApplied(online);
                }
            }
            fill(event.getInventory());
            player.sendMessage(Text.color((next ? "&aEnabled " : "&cDisabled ") + rank.label() + " rank visual."));
            return;
        }
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            meta.setLore(lore.stream().map(Text::color).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String normalizeRank(String value) {
        if (value == null) return "";
        String cleaned = value.replaceAll("(?i)§[0-9A-FK-ORX]", "")
                .toLowerCase(Locale.ROOT)
                .replace("co owner", "co_owner")
                .replace("co-owner", "co_owner")
                .replace("youtube", "youtuber")
                .replaceAll("[^a-z0-9_]", "");
        return switch (cleaned) {
            case "coowner" -> "co_owner";
            case "mod" -> "moderator";
            case "dev" -> "developer";
            case "yt", "youtube" -> "youtuber";
            default -> cleaned;
        };
    }

    private static Method findMethod(Class<?> type, String name, int count) {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            for (Method method : cursor.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == count) return method;
            }
        }
        return null;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) cursor = cursor.getCause();
        String message = cursor.getMessage();
        return message == null || message.isBlank() ? cursor.getClass().getSimpleName() : message.replace('\n', ' ');
    }

    private static final class RankGuiHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}

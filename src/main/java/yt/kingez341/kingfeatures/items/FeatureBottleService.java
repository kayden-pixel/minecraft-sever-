package yt.kingez341.kingfeatures.items;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import yt.kingez341.kingfeatures.KingFeaturesPlugin;
import yt.kingez341.kingfeatures.economy.UltimateEconomyBridge;
import yt.kingez341.kingfeatures.util.Text;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class FeatureBottleService implements Listener {
    private final KingFeaturesPlugin plugin;
    private final UltimateEconomyBridge economy;
    private final NamespacedKey itemIdKey;
    private final NamespacedKey schemaKey;
    private final NamespacedKey expiresAtKey;
    private final NamespacedKey sourceKey;

    public FeatureBottleService(KingFeaturesPlugin plugin, UltimateEconomyBridge economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.itemIdKey = new NamespacedKey(plugin, "item_id");
        this.schemaKey = new NamespacedKey(plugin, "item_schema");
        this.expiresAtKey = new NamespacedKey(plugin, "redeem_until");
        this.sourceKey = new NamespacedKey(plugin, "item_source");
    }

    public double price() {
        return plugin.getConfig().getDouble("bottle.price", 255.0D);
    }

    public ItemStack createBottle(boolean activateWindow, String source) {
        ItemStack stack = new ItemStack(Material.POTION);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(Text.color(plugin.getConfig().getString("bottle.display-name", "&6&lKing's Favor Bottle")));

        List<String> lore = new ArrayList<>(Text.color(plugin.getConfig().getStringList("bottle.lore")));
        if (activateWindow) {
            long expires = redeemUntil();
            lore.add(Text.color("&8Expires: &7" + formatRemaining(expires - System.currentTimeMillis())));
        } else {
            lore.add(Text.color("&8Crate/Shop template - timer starts only on direct purchase."));
        }
        meta.setLore(lore);

        int modelData = plugin.getConfig().getInt("bottle.custom-model-data", 341255);
        if (modelData > 0) {
            try {
                meta.setCustomModelData(modelData);
            } catch (Throwable ignored) {
                // Custom model data is cosmetic. PDC remains the authoritative item identity.
            }
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(itemIdKey, PersistentDataType.STRING, plugin.getConfig().getString("bottle.id", "kings_favor"));
        pdc.set(schemaKey, PersistentDataType.INTEGER, 1);
        pdc.set(expiresAtKey, PersistentDataType.LONG, activateWindow ? redeemUntil() : 0L);
        pdc.set(sourceKey, PersistentDataType.STRING, source == null ? "unknown" : source);
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isFeatureBottle(ItemStack item) {
        if (item == null || item.getType() != Material.POTION || !item.hasItemMeta()) return false;
        String id = item.getItemMeta().getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
        return id != null && id.equalsIgnoreCase(plugin.getConfig().getString("bottle.id", "kings_favor"));
    }

    public BuyResult buy(Player player) {
        if (!plugin.getConfig().getBoolean("bottle.enabled", true)) return new BuyResult(false, "Bottle system is disabled.");
        if (!economy.isConnected() && !economy.connect()) return new BuyResult(false, "Ultimate Economy Plus is unavailable.");
        if (player.getInventory().firstEmpty() < 0) return new BuyResult(false, "Your inventory is full.");

        double price = price();
        UltimateEconomyBridge.MoneyResult result = economy.withdraw(player, price);
        if (!result.success()) return new BuyResult(false, "Purchase declined: " + result.message());

        ItemStack bottle = createBottle(true, "uep_purchase");
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(bottle);
        if (!leftovers.isEmpty()) {
            economy.deposit(player, price);
            return new BuyResult(false, "Delivery failed; your " + price + " was refunded.");
        }

        return new BuyResult(true, "Bought King's Favor for " + trim(price) + ". Balance: " + trim(result.balance()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!isFeatureBottle(item)) return;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        long expiresAt = pdc.getOrDefault(expiresAtKey, PersistentDataType.LONG, 0L);
        if (expiresAt > 0L && System.currentTimeMillis() > expiresAt) {
            event.getPlayer().sendMessage(Text.color("&cThat King's Favor bottle expired before it was redeemed."));
            return;
        }

        int hours = Math.max(1, plugin.getConfig().getInt("bottle.hero-of-the-village-hours", 24));
        int amplifier = Math.max(0, plugin.getConfig().getInt("bottle.amplifier", 0));
        long calculatedTicks = Duration.ofHours(hours).toSeconds() * 20L;
        int ticks = (int) Math.min(Integer.MAX_VALUE, calculatedTicks);
        event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, ticks, amplifier, false, true, true), true);
        event.getPlayer().sendMessage(Text.color("&6King's Favor activated! &eHero of the Village &7is active for &f" + hours + "h&7."));
        plugin.audit(event.getPlayer().getName() + " consumed a King's Favor bottle (" + hours + "h Hero of the Village)");
    }

    private long redeemUntil() {
        int hours = Math.max(1, plugin.getConfig().getInt("bottle.redeem-window-hours", 5));
        return System.currentTimeMillis() + Duration.ofHours(hours).toMillis();
    }

    private static String formatRemaining(long millis) {
        if (millis <= 0) return "expired";
        long minutes = Math.max(1, Duration.ofMillis(millis).toMinutes());
        long hours = minutes / 60;
        long remainder = minutes % 60;
        return hours > 0 ? hours + "h " + remainder + "m" : remainder + "m";
    }

    private static String trim(double value) {
        if (Math.rint(value) == value) return Long.toString((long) value);
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    public record BuyResult(boolean success, String message) {}
}

package yt.kingez341.kingfeatures.troll;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import yt.kingez341.kingfeatures.KingFeaturesPlugin;
import yt.kingez341.kingfeatures.ui.GuiItems;
import yt.kingez341.kingfeatures.util.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class TrollLabGui implements Listener {
    private static final int[] CONTENT = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final KingFeaturesPlugin plugin;
    private final TrollExecutor executor;
    private final NamespacedKey targetKey;
    private final NamespacedKey actionKey;
    private final ModernDialogSearch searchDialog;
    private BukkitTask animation;
    private int phase;

    public TrollLabGui(KingFeaturesPlugin plugin, TrollExecutor executor) {
        this.plugin = plugin;
        this.executor = executor;
        this.targetKey = new NamespacedKey(plugin, "troll_target");
        this.actionKey = new NamespacedKey(plugin, "troll_action");
        this.searchDialog = new ModernDialogSearch(plugin, this);
    }

    public void start() {
        stop();
        long period = Math.max(2L, plugin.getConfig().getLong("troll-lab.animation-period-ticks", 6L));
        animation = Bukkit.getScheduler().runTaskTimer(plugin, this::animateOpenMenus, period, period);
    }

    public void stop() {
        if (animation != null) animation.cancel();
        animation = null;
    }

    public void openTargets(Player player) {
        LabHolder holder = new LabHolder(View.TARGETS, null, "");
        Inventory inventory = Bukkit.createInventory(holder, 54, Text.color("&5&lTROLL LAB &8• &dSelect Player"));
        holder.inventory = inventory;
        renderFrame(inventory);

        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        for (int i = 0; i < Math.min(CONTENT.length, online.size()); i++) {
            Player target = online.get(i);
            ItemStack icon = GuiItems.item(Material.PLAYER_HEAD, "&d" + target.getName(), List.of(
                    "&7Click to open Troll Lab actions.",
                    "",
                    "&8UUID: " + target.getUniqueId().toString().substring(0, 8)
            ), target.equals(player));
            ItemMeta meta = icon.getItemMeta();
            meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, target.getUniqueId().toString());
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT[i], icon);
        }
        inventory.setItem(49, GuiItems.item(Material.ENDER_EYE, "&d&lLive Targets", "&7Online: &f" + online.size(), "&8Click a player above."));
        player.openInventory(inventory);
    }

    public void openActions(Player player, UUID targetId, String query) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            player.sendMessage(Text.color("&cThat player is no longer online."));
            openTargets(player);
            return;
        }
        String normalized = query == null ? "" : query.trim();
        LabHolder holder = new LabHolder(View.ACTIONS, targetId, normalized);
        Inventory inventory = Bukkit.createInventory(holder, 54, Text.color("&5&lTROLL LAB &8• &d" + target.getName()));
        holder.inventory = inventory;
        renderFrame(inventory);

        List<TrollAction> actions = new ArrayList<>();
        for (TrollAction action : TrollAction.values()) if (action.matches(normalized)) actions.add(action);
        for (int i = 0; i < Math.min(CONTENT.length, actions.size()); i++) {
            TrollAction action = actions.get(i);
            ItemStack icon = GuiItems.item(action.icon(), "&d&l" + action.displayName(), List.of(
                    "&7" + action.description(),
                    "",
                    "&fTarget: &d" + target.getName(),
                    "&aClick to run"
            ), false);
            ItemMeta meta = icon.getItemMeta();
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action.name());
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT[i], icon);
        }

        inventory.setItem(45, GuiItems.item(Material.ARROW, "&f&lBack", "&7Choose another player."));
        inventory.setItem(49, GuiItems.item(Material.SPYGLASS, "&d&lSearch Trolls",
                normalized.isBlank() ? "&7Search all Troll Lab actions." : "&7Filter: &f" + normalized,
                "&8Uses Paper's Dialog input."));
        inventory.setItem(53, GuiItems.item(Material.BARRIER, "&c&lClose", "&7Close Troll Lab."));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof LabHolder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        if (holder.view == View.TARGETS) {
            String uuid = clicked.hasItemMeta() ? clicked.getItemMeta().getPersistentDataContainer().get(targetKey, PersistentDataType.STRING) : null;
            if (uuid == null) return;
            try {
                openActions(player, UUID.fromString(uuid), "");
            } catch (IllegalArgumentException ignored) {}
            return;
        }

        int slot = event.getSlot();
        if (slot == 45) {
            openTargets(player);
            return;
        }
        if (slot == 49) {
            searchDialog.open(player, holder.targetId, holder.query);
            return;
        }
        if (slot == 53) {
            player.closeInventory();
            return;
        }

        String actionName = clicked.hasItemMeta() ? clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING) : null;
        if (actionName == null || holder.targetId == null) return;
        Player target = Bukkit.getPlayer(holder.targetId);
        if (target == null) {
            player.sendMessage(Text.color("&cTarget went offline."));
            openTargets(player);
            return;
        }
        try {
            TrollAction action = TrollAction.valueOf(actionName.toUpperCase(Locale.ROOT));
            TrollExecutor.Result result = executor.execute(player, target, action);
            player.sendMessage(Text.color((result.success() ? "&a" : "&c") + result.message()));
            if (result.success()) openActions(player, holder.targetId, holder.query);
        } catch (IllegalArgumentException ignored) {}
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof LabHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        // Holder state is inventory-owned; nothing global to leak or clean up.
    }

    private void animateOpenMenus() {
        phase++;
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (!(top.getHolder() instanceof LabHolder)) continue;
            animateFrame(top);
        }
    }

    private void renderFrame(Inventory inventory) {
        animateFrame(inventory);
    }

    private void animateFrame(Inventory inventory) {
        Material[] panes = {Material.PURPLE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE, Material.PINK_STAINED_GLASS_PANE};
        for (int slot = 0; slot < 54; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            boolean border = row == 0 || row == 5 || col == 0 || col == 8;
            if (!border) continue;
            if (slot == 45 || slot == 49 || slot == 53) continue;
            Material material = panes[Math.floorMod(slot + phase, panes.length)];
            inventory.setItem(slot, GuiItems.filler(material, "&8✦"));
        }
    }

    private enum View { TARGETS, ACTIONS }

    private static final class LabHolder implements InventoryHolder {
        private final View view;
        private final UUID targetId;
        private final String query;
        private Inventory inventory;

        private LabHolder(View view, UUID targetId, String query) {
            this.view = view;
            this.targetId = targetId;
            this.query = query;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}

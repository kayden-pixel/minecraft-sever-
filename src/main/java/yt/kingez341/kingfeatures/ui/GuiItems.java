package yt.kingez341.kingfeatures.ui;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import yt.kingez341.kingfeatures.util.Text;

import java.util.ArrayList;
import java.util.List;

public final class GuiItems {
    private GuiItems() {}

    public static ItemStack item(Material material, String name, List<String> lore, boolean glow) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(Text.color(name));
        meta.setLore(Text.color(lore));
        if (glow) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack item(Material material, String name, String... lore) {
        return item(material, name, List.of(lore), false);
    }

    public static ItemStack filler(Material material, String name) {
        return item(material, name, List.of(), false);
    }

    public static List<String> lines(String... lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) out.add(line);
        return out;
    }
}

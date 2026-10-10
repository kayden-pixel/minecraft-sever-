package yt.kingez341.kingfeatures.troll;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import yt.kingez341.kingfeatures.KingFeaturesPlugin;
import yt.kingez341.kingfeatures.util.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TrollExecutor {
    private final KingFeaturesPlugin plugin;
    private final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public TrollExecutor(KingFeaturesPlugin plugin) {
        this.plugin = plugin;
    }

    public Result execute(Player actor, Player target, TrollAction action) {
        if (target == null || !target.isOnline()) return new Result(false, "Target is offline.");
        if (!plugin.getConfig().getBoolean("troll-lab.allow-self-target", true) && actor.getUniqueId().equals(target.getUniqueId())) {
            return new Result(false, "Self-targeting is disabled.");
        }
        long now = System.currentTimeMillis();
        long cooldown = Math.max(0, plugin.getConfig().getLong("troll-lab.action-cooldown-seconds", 2)) * 1000L;
        long next = cooldowns.getOrDefault(actor.getUniqueId(), 0L);
        if (now < next) return new Result(false, "Troll Lab is cooling down for " + Math.max(1, (next - now + 999) / 1000) + "s.");
        cooldowns.put(actor.getUniqueId(), now + cooldown);

        switch (action) {
            case FAKE_LIGHTNING -> target.getWorld().strikeLightningEffect(target.getLocation());
            case LAUNCH -> target.setVelocity(new Vector(0.0, 1.65, 0.0));
            case SPIN -> spin(target);
            case CREEPER_SCARE -> {
                target.playSound(target.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0f, 1.0f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (target.isOnline()) target.playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);
                }, 35L);
            }
            case DARKNESS -> target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20 * 8, 0, false, false, true), true);
            case FREEZE -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 6, 9, false, false, true), true);
            case FAKE_DEATH -> {
                target.sendTitle(Text.color("&4&lYOU DIED"), Text.color("&7...nah, Troll Lab got you 😭"), 5, 45, 10);
                target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_DEATH, 1.0f, 1.0f);
            }
            case INVENTORY_SHUFFLE -> shuffleHotbar(target);
            case SOUND_SPAM -> soundBurst(target);
            case GHOST_HIT -> {
                target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.85f);
                target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1.0, 0), 5, 0.35, 0.45, 0.35, 0.0);
            }
            case FAKE_DIAMONDS -> {
                target.sendMessage(Text.color("&b&l+ 12 DIAMONDS &7were added to your inventory!"));
                target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (target.isOnline()) target.sendMessage(Text.color("&8Troll Lab: &7yeah... about those diamonds 👀"));
                }, 40L);
            }
            case UPSIDE_DOWN -> flipView(target);
        }

        plugin.audit("TROLL actor=" + actor.getName() + " target=" + target.getName() + " action=" + action.name());
        return new Result(true, "Ran " + action.displayName() + " on " + target.getName() + ".");
    }

    private void spin(Player target) {
        new BukkitRunnable() {
            int ticks;
            @Override public void run() {
                if (!target.isOnline() || ticks >= 40) { cancel(); return; }
                Location location = target.getLocation();
                location.setYaw(location.getYaw() + 36.0f);
                target.teleport(location);
                ticks += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void flipView(Player target) {
        Location original = target.getLocation();
        Location changed = original.clone();
        changed.setPitch(original.getPitch() > 0 ? -89.0f : 89.0f);
        target.teleport(changed);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!target.isOnline()) return;
            Location current = target.getLocation();
            current.setPitch(original.getPitch());
            target.teleport(current);
        }, 45L);
    }

    private static void shuffleHotbar(Player target) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = target.getInventory().getItem(slot);
            items.add(item == null ? null : item.clone());
        }
        Collections.shuffle(items);
        for (int slot = 0; slot < 9; slot++) target.getInventory().setItem(slot, items.get(slot));
        target.updateInventory();
    }

    private void soundBurst(Player target) {
        Sound[] sounds = {Sound.BLOCK_NOTE_BLOCK_BELL, Sound.BLOCK_NOTE_BLOCK_PLING, Sound.ENTITY_ENDERMAN_STARE, Sound.ENTITY_CHICKEN_AMBIENT};
        for (int i = 0; i < sounds.length; i++) {
            Sound sound = sounds[i];
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (target.isOnline()) target.playSound(target.getLocation(), sound, 0.8f, 1.1f);
            }, i * 5L);
        }
    }

    public record Result(boolean success, String message) {}
}

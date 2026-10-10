package yt.kingez341.kingfeatures.pack;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;
import yt.kingez341.kingfeatures.KingFeaturesPlugin;
import yt.kingez341.kingfeatures.util.Text;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

public final class ResourcePackManager implements Listener {
    private static final UUID PACK_ID = UUID.fromString("34100000-0000-4000-8000-000000000255");

    private enum DeliveryMode {
        NONE,
        RESOURCEPACKMANAGER,
        DIRECT_REMOTE
    }

    private final KingFeaturesPlugin plugin;
    private Path localPack;
    private String localSha1;
    private String remoteUrl;
    private String remoteSha1;
    private DeliveryMode deliveryMode = DeliveryMode.NONE;
    private String status = "not-started";

    public ResourcePackManager(KingFeaturesPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        deliveryMode = DeliveryMode.NONE;
        localPack = null;
        localSha1 = null;
        remoteUrl = "";
        remoteSha1 = "";

        if (!plugin.getConfig().getBoolean("resource-pack.enabled", true)) {
            status = "disabled";
            return;
        }

        try {
            localPack = discoverPack();
            if (localPack != null && Files.isRegularFile(localPack)) {
                localSha1 = sha1(localPack);
            }
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not inspect local resource pack: " + rootMessage(throwable));
        }

        remoteUrl = clean(plugin.getConfig().getString("resource-pack.remote-url", ""));
        remoteSha1 = clean(plugin.getConfig().getString("resource-pack.remote-sha1", "")).toLowerCase(Locale.ROOT);
        if (!remoteSha1.matches("[0-9a-f]{40}")) remoteSha1 = "";

        if (plugin.getConfig().getBoolean("resource-pack.resourcepackmanager.enabled", true) && registerWithResourcePackManager()) {
            deliveryMode = DeliveryMode.RESOURCEPACKMANAGER;
            return;
        }

        if (!remoteUrl.isBlank()) {
            deliveryMode = DeliveryMode.DIRECT_REMOTE;
            status = "direct-remote:" + remoteUrl + (remoteSha1.isBlank() ? " hash=none" : " sha1=" + remoteSha1);
            plugin.getLogger().info("Resource pack delivery using direct remote URL fallback.");
            return;
        }

        if (localPack != null && Files.isRegularFile(localPack)) {
            status = "local-pack-found-but-no-distributor:" + localPack.getFileName();
            plugin.getLogger().warning("Found resource pack " + localPack + " but ResourcePackManager is not available and resource-pack.remote-url is blank. The old port-8124 self-host was removed because it is unreliable on managed hosts. Install ResourcePackManager or configure an HTTPS remote URL.");
        } else {
            status = "no-pack-found";
            plugin.getLogger().warning("No KingFeatures/OmniPanel resource pack found. Pack delivery is idle.");
        }
    }

    public void stop() {
        deliveryMode = DeliveryMode.NONE;
    }

    public String status() {
        return status;
    }

    public Path localPack() {
        return localPack;
    }

    public void send(Player player) {
        if (!plugin.getConfig().getBoolean("resource-pack.enabled", true)) return;

        if (deliveryMode == DeliveryMode.RESOURCEPACKMANAGER) {
            boolean reload = reloadResourcePackManager();
            player.sendMessage(Text.color(reload
                    ? "&aResourcePackManager is rebuilding/resending the merged server pack."
                    : "&eResourcePackManager owns pack delivery; reconnect if the pack does not refresh."));
            return;
        }

        if (deliveryMode != DeliveryMode.DIRECT_REMOTE || remoteUrl.isBlank()) return;

        try {
            boolean required = plugin.getConfig().getBoolean("resource-pack.required", true);
            String promptText = Text.strip(plugin.getConfig().getString("resource-pack.prompt", "SkyStrike resource pack"));
            Component prompt = Component.text(promptText);

            if (!remoteSha1.isBlank()) {
                player.setResourcePack(PACK_ID, remoteUrl, remoteSha1, prompt, required);
            } else {
                player.setResourcePack(PACK_ID, remoteUrl, (byte[]) null, prompt, required);
            }
            plugin.getLogger().info("Sent KingFeatures remote resource-pack request to " + player.getName() + ".");
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Resource pack send failed for " + player.getName() + ": " + rootMessage(throwable));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (deliveryMode != DeliveryMode.DIRECT_REMOTE) return;
        long delay = Math.max(1L, plugin.getConfig().getLong("resource-pack.send-delay-ticks", 20L));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) send(event.getPlayer());
        }, delay);
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) return;
        status = "direct-remote:last=" + event.getStatus().name().toLowerCase(Locale.ROOT) + ":player=" + event.getPlayer().getName();
        plugin.getLogger().info("KingFeatures resource pack " + event.getStatus().name().toLowerCase(Locale.ROOT) + " for " + event.getPlayer().getName());
    }

    private boolean registerWithResourcePackManager() {
        try {
            Plugin rspm = Bukkit.getPluginManager().getPlugin("ResourcePackManager");
            if (rspm == null || !rspm.isEnabled()) return false;

            ClassLoader loader = rspm.getClass().getClassLoader();
            Class<?> api = Class.forName("com.magmaguy.resourcepackmanager.api.ResourcePackManagerAPI", true, loader);

            if (localPack != null && Files.isRegularFile(localPack)) {
                Path pluginsDir = plugin.getDataFolder().toPath().getParent().toAbsolutePath().normalize();
                Path pack = localPack.toAbsolutePath().normalize();
                if (!pack.startsWith(pluginsDir)) {
                    status = "rspm-local-pack-outside-plugins:" + pack;
                    plugin.getLogger().warning("ResourcePackManager requires a path under plugins/. Pack is outside that directory: " + pack);
                    return false;
                }

                String relative = pluginsDir.relativize(pack).toString().replace('\\', '/');
                Method register = api.getMethod(
                        "registerLocalResourcePack",
                        String.class,
                        String.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        String.class
                );
                register.invoke(null, "KingFeatures", relative, false, true, true, "kingfeatures reload");
                status = "resourcepackmanager:local=" + relative + " sha1=" + localSha1;
                plugin.getLogger().info("Registered local KingFeatures pack with ResourcePackManager: " + relative);
            } else if (!remoteUrl.isBlank()) {
                Method register = api.getMethod(
                        "registerRemoteResourcePack",
                        String.class,
                        String.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        String.class
                );
                register.invoke(null, "KingFeatures", remoteUrl, false, true, true, "kingfeatures reload");
                status = "resourcepackmanager:remote=" + remoteUrl;
                plugin.getLogger().info("Registered remote KingFeatures pack with ResourcePackManager.");
            } else {
                return false;
            }

            if (plugin.getConfig().getBoolean("resource-pack.resourcepackmanager.reload-after-register", true)) {
                Bukkit.getScheduler().runTaskLater(plugin, this::reloadResourcePackManager, 20L);
            }
            return true;
        } catch (Throwable throwable) {
            status = "resourcepackmanager-error:" + throwable.getClass().getSimpleName() + ":" + rootMessage(throwable);
            plugin.getLogger().warning("ResourcePackManager integration failed; trying fallback delivery: " + rootMessage(throwable));
            return false;
        }
    }

    private boolean reloadResourcePackManager() {
        try {
            Plugin rspm = Bukkit.getPluginManager().getPlugin("ResourcePackManager");
            if (rspm == null || !rspm.isEnabled()) return false;
            Class<?> api = Class.forName("com.magmaguy.resourcepackmanager.api.ResourcePackManagerAPI", true, rspm.getClass().getClassLoader());
            Method reload = api.getMethod("reloadResourcePack");
            if (!Modifier.isStatic(reload.getModifiers())) return false;
            reload.invoke(null);
            return true;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("ResourcePackManager reload failed: " + rootMessage(throwable));
            return false;
        }
    }

    private Path discoverPack() {
        Path configured = plugin.getDataFolder().toPath()
                .resolve(plugin.getConfig().getString("resource-pack.local-file", "resourcepack/KingFeatures-Pack.zip"))
                .normalize();
        if (Files.isRegularFile(configured)) return configured;

        if (plugin.getConfig().getBoolean("resource-pack.auto-import-omnipanel-rank-pack", true)) {
            Path serverRoot = plugin.getDataFolder().toPath().getParent().getParent();
            String relative = plugin.getConfig().getString(
                    "resource-pack.omnipanel-rank-pack-path",
                    "plugins/OmniPanel/rank-pack/Kingez341YT-Rank-Pack-1.21.11.zip"
            );
            Path omni = serverRoot.resolve(relative).normalize();
            if (Files.isRegularFile(omni)) {
                plugin.getLogger().info("Auto-detected OmniPanel rank pack: " + omni);
                return omni;
            }
        }
        return null;
    }

    private static String sha1(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) cursor = cursor.getCause();
        String message = cursor.getMessage();
        return message == null || message.isBlank() ? cursor.getClass().getSimpleName() : message.replace('\n', ' ');
    }
}

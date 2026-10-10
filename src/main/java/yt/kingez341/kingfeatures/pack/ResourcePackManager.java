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

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipFile;

public final class ResourcePackManager implements Listener {
    private static final UUID PACK_ID = UUID.fromString("34100000-0000-4000-8000-000000000255");
    private static final String BUNDLED_B64_PREFIX = "rankpack/KingFeatures-Rank-Visuals.zip.b64.part";
    private static final int BUNDLED_PARTS = 21;
    private static final String BUNDLED_NAME = "KingFeatures-Rank-Visuals.zip";

    private enum DeliveryMode { NONE, RESOURCEPACKMANAGER, DIRECT_REMOTE }

    private final KingFeaturesPlugin plugin;
    private Path localPack;
    private String localSha1;
    private String remoteUrl;
    private String remoteSha1;
    private DeliveryMode deliveryMode = DeliveryMode.NONE;
    private String status = "not-started";

    public ResourcePackManager(KingFeaturesPlugin plugin) { this.plugin = plugin; }

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
            if (localPack != null && Files.isRegularFile(localPack)) localSha1 = sha1(localPack);
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
            plugin.getLogger().warning("Found resource pack " + localPack + " but ResourcePackManager is not available and resource-pack.remote-url is blank. Install ResourcePackManager or configure an HTTPS remote URL.");
        } else {
            status = "no-pack-found";
            plugin.getLogger().warning("No KingFeatures resource pack found. Pack delivery is idle.");
        }
    }

    public void stop() { deliveryMode = DeliveryMode.NONE; }
    public String status() { return status; }
    public Path localPack() { return localPack; }

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
            Component prompt = Component.text(Text.strip(plugin.getConfig().getString("resource-pack.prompt", "SkyStrike resource pack")));
            if (!remoteSha1.isBlank()) player.setResourcePack(PACK_ID, remoteUrl, remoteSha1, prompt, required);
            else player.setResourcePack(PACK_ID, remoteUrl, (byte[]) null, prompt, required);
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
                Path pluginsDir = plugin.getDataFolder().toPath().toAbsolutePath().normalize().getParent();
                Path pack = localPack.toAbsolutePath().normalize();
                if (pluginsDir == null || !pack.startsWith(pluginsDir)) {
                    status = "rspm-local-pack-outside-plugins:" + pack;
                    plugin.getLogger().warning("ResourcePackManager requires a path under plugins/. Pack is outside that directory: " + pack);
                    return false;
                }
                String relative = pluginsDir.relativize(pack).toString().replace('\\', '/');
                Method register = api.getMethod("registerLocalResourcePack", String.class, String.class,
                        boolean.class, boolean.class, boolean.class, String.class);
                register.invoke(null, "KingFeatures", relative, false, true, true, "kingfeatures reload");
                status = "resourcepackmanager:local=" + relative + " sha1=" + localSha1;
                plugin.getLogger().info("Registered KingFeatures rank visuals with ResourcePackManager: " + relative);
            } else if (!remoteUrl.isBlank()) {
                Method register = api.getMethod("registerRemoteResourcePack", String.class, String.class,
                        boolean.class, boolean.class, boolean.class, String.class);
                register.invoke(null, "KingFeatures", remoteUrl, false, true, true, "kingfeatures reload");
                status = "resourcepackmanager:remote=" + remoteUrl;
                plugin.getLogger().info("Registered remote KingFeatures pack with ResourcePackManager.");
            } else return false;

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

    private Path discoverPack() throws Exception {
        Path dataFolder = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        String configuredName = clean(plugin.getConfig().getString("resource-pack.local-file", "resourcepack/" + BUNDLED_NAME));
        Path configured = dataFolder.resolve(configuredName.isBlank() ? "resourcepack/" + BUNDLED_NAME : configuredName).normalize();

        boolean bundled = plugin.getConfig().getBoolean("resource-pack.bundled-rank-visuals.enabled", true);
        if (bundled && configured.getFileName() != null && BUNDLED_NAME.equalsIgnoreCase(configured.getFileName().toString())) {
            Path extracted = extractBundledRankPack(configured);
            if (extracted != null) return extracted;
        }
        if (Files.isRegularFile(configured)) return configured;

        if (bundled) {
            Path extracted = extractBundledRankPack(dataFolder.resolve("resourcepack").resolve(BUNDLED_NAME));
            if (extracted != null) return extracted;
        }

        if (plugin.getConfig().getBoolean("resource-pack.auto-import-omnipanel-rank-pack", true)) {
            Path pluginsDir = dataFolder.getParent();
            Path serverRoot = pluginsDir == null ? Path.of(".").toAbsolutePath().normalize() : pluginsDir.getParent();
            if (serverRoot == null) serverRoot = Path.of(".").toAbsolutePath().normalize();
            String relative = plugin.getConfig().getString("resource-pack.omnipanel-rank-pack-path",
                    "plugins/OmniPanel/rank-pack/Kingez341YT-Rank-Pack-1.21.11.zip");
            Path omni = serverRoot.resolve(relative).toAbsolutePath().normalize();
            if (Files.isRegularFile(omni)) {
                plugin.getLogger().info("Using legacy OmniPanel rank pack fallback: " + omni);
                return omni;
            }
        }
        return null;
    }

    private Path extractBundledRankPack(Path output) throws Exception {
        StringBuilder encodedText = new StringBuilder(42000);
        for (int part = 1; part <= BUNDLED_PARTS; part++) {
            String path = BUNDLED_B64_PREFIX + part;
            InputStream input = plugin.getResource(path);
            if (input == null) {
                plugin.getLogger().warning("Bundled rank pack resource is missing from the KingFeatures JAR: " + path);
                return null;
            }
            try (input) {
                encodedText.append(new String(input.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\\s+", ""));
            }
        }

        byte[] bytes = Base64.getDecoder().decode(encodedText.toString());
        Files.createDirectories(output.getParent());
        Path temp = output.resolveSibling(output.getFileName() + ".tmp");
        Files.write(temp, bytes);
        try (ZipFile ignored = new ZipFile(temp.toFile())) { }

        boolean changed = !Files.isRegularFile(output) || !sha256(output).equals(sha256(temp));
        if (changed) {
            try {
                Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicUnavailable) {
                Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING);
            }
            plugin.getLogger().info("Installed bundled OmniPanel rank visual pack: " + output);
        } else {
            Files.deleteIfExists(temp);
        }
        return output;
    }

    private static String sha1(Path path) throws Exception { return digest(path, "SHA-1"); }
    private static String sha256(Path path) throws Exception { return digest(path, "SHA-256"); }

    private static String digest(Path path, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    private static String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) cursor = cursor.getCause();
        String message = cursor.getMessage();
        return message == null || message.isBlank() ? cursor.getClass().getSimpleName() : message.replace('\n', ' ');
    }
}

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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ResourcePackManager implements Listener {
    private static final UUID PACK_ID = UUID.fromString("34100000-0000-4000-8000-000000000255");
    private static final int EMBEDDED_PART_COUNT = 6;
    private static final String EMBEDDED_PACK_NAME = "KingFeatures-Rank-Pack.zip";
    private static final String EMBEDDED_SHA1 = "6afdaefa9da8fb98c38b481e125c6b9ebce9bf1c";
    private static final String BUILTIN_PACK_URL = "https://raw.githubusercontent.com/kayden-pixel/minecraft-sever-/pack-host/KingFeatures-Rank-Pack.zip";

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
    private boolean builtinRemote;
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
        builtinRemote = false;

        if (!plugin.getConfig().getBoolean("resource-pack.enabled", true)) {
            status = "disabled";
            return;
        }

        try {
            localPack = ensureEmbeddedPack();
            localSha1 = sha1(localPack);
            plugin.getLogger().info("KingFeatures embedded rank pack ready: " + localPack + " sha1=" + localSha1);
        } catch (Throwable throwable) {
            status = "embedded-pack-error:" + rootMessage(throwable);
            plugin.getLogger().warning("Could not prepare embedded KingFeatures rank pack: " + rootMessage(throwable));
        }

        String configuredUrl = clean(plugin.getConfig().getString("resource-pack.remote-url", ""));
        String configuredSha1 = clean(plugin.getConfig().getString("resource-pack.remote-sha1", "")).toLowerCase(Locale.ROOT);
        if (!configuredSha1.matches("[0-9a-f]{40}")) configuredSha1 = "";

        if (!configuredUrl.isBlank()) {
            remoteUrl = configuredUrl;
            remoteSha1 = configuredSha1;
        } else {
            remoteUrl = BUILTIN_PACK_URL;
            remoteSha1 = EMBEDDED_SHA1;
            builtinRemote = true;
        }

        if (plugin.getConfig().getBoolean("resource-pack.resourcepackmanager.enabled", true)
                && localPack != null
                && Files.isRegularFile(localPack)
                && registerWithResourcePackManager()) {
            deliveryMode = DeliveryMode.RESOURCEPACKMANAGER;
            return;
        }

        if (!remoteUrl.isBlank()) {
            deliveryMode = DeliveryMode.DIRECT_REMOTE;
            status = (builtinRemote ? "direct-builtin-host:" : "direct-remote:") + remoteUrl
                    + (remoteSha1.isBlank() ? " hash=none" : " sha1=" + remoteSha1);
            plugin.getLogger().info(builtinRemote
                    ? "Resource pack delivery ready using KingFeatures built-in hosted fallback. No manual pack install is required."
                    : "Resource pack delivery ready using configured remote URL.");
            return;
        }

        status = "no-delivery-path";
        plugin.getLogger().warning("KingFeatures has no usable resource-pack delivery path.");
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
            String promptText = Text.strip(plugin.getConfig().getString("resource-pack.prompt", "SkyStrike rank visuals"));
            Component prompt = Component.text(promptText);

            if (!remoteSha1.isBlank()) {
                player.setResourcePack(PACK_ID, remoteUrl, remoteSha1, prompt, required);
            } else {
                player.setResourcePack(PACK_ID, remoteUrl, (byte[]) null, prompt, required);
            }
            plugin.getLogger().info("Sent KingFeatures rank-pack request to " + player.getName()
                    + " via " + (builtinRemote ? "built-in host" : "configured URL") + ".");
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
        status = (builtinRemote ? "direct-builtin-host" : "direct-remote")
                + ":last=" + event.getStatus().name().toLowerCase(Locale.ROOT)
                + ":player=" + event.getPlayer().getName();
        plugin.getLogger().info("KingFeatures resource pack " + event.getStatus().name().toLowerCase(Locale.ROOT)
                + " for " + event.getPlayer().getName());
    }

    private boolean registerWithResourcePackManager() {
        try {
            Plugin rspm = Bukkit.getPluginManager().getPlugin("ResourcePackManager");
            if (rspm == null || !rspm.isEnabled()) return false;

            ClassLoader loader = rspm.getClass().getClassLoader();
            Class<?> api = Class.forName("com.magmaguy.resourcepackmanager.api.ResourcePackManagerAPI", true, loader);

            Path pluginsDir = plugin.getDataFolder().toPath().toAbsolutePath().normalize().getParent();
            Path pack = localPack.toAbsolutePath().normalize();
            if (pluginsDir == null || !pack.startsWith(pluginsDir)) {
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
            status = "resourcepackmanager:embedded=" + relative + " sha1=" + localSha1;
            plugin.getLogger().info("Registered embedded KingFeatures rank pack with ResourcePackManager: " + relative);

            if (plugin.getConfig().getBoolean("resource-pack.resourcepackmanager.reload-after-register", true)) {
                Bukkit.getScheduler().runTaskLater(plugin, this::reloadResourcePackManager, 20L);
            }
            return true;
        } catch (Throwable throwable) {
            status = "resourcepackmanager-error:" + throwable.getClass().getSimpleName() + ":" + rootMessage(throwable);
            plugin.getLogger().warning("ResourcePackManager integration failed; using KingFeatures direct fallback: " + rootMessage(throwable));
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

    private Path ensureEmbeddedPack() throws Exception {
        Path folder = plugin.getDataFolder().toPath().toAbsolutePath().normalize().resolve("resourcepack");
        Files.createDirectories(folder);
        Path target = folder.resolve(EMBEDDED_PACK_NAME);

        if (Files.isRegularFile(target)) {
            String existingSha1 = sha1(target);
            if (EMBEDDED_SHA1.equalsIgnoreCase(existingSha1) && isResourcePackZip(target)) {
                return target;
            }
            plugin.getLogger().info("Replacing outdated KingFeatures embedded rank pack (sha1=" + existingSha1 + ").");
        }

        StringBuilder encoded = new StringBuilder(46000);
        ClassLoader loader = plugin.getClass().getClassLoader();
        for (int i = 0; i < EMBEDDED_PART_COUNT; i++) {
            String resource = String.format(Locale.ROOT, "embedded/KingFeatures-Rank-Pack.part%02d", i);
            try (InputStream input = loader.getResourceAsStream(resource)) {
                if (input == null) throw new IllegalStateException("Missing embedded pack resource " + resource);
                encoded.append(new String(input.readAllBytes(), StandardCharsets.US_ASCII).trim());
            }
        }

        byte[] bytes = Base64.getDecoder().decode(encoded.toString());
        String decodedSha1 = sha1(bytes);
        if (!EMBEDDED_SHA1.equalsIgnoreCase(decodedSha1)) {
            throw new IllegalStateException("Embedded rank pack checksum mismatch: expected " + EMBEDDED_SHA1 + " got " + decodedSha1);
        }

        Path temp = folder.resolve(EMBEDDED_PACK_NAME + ".tmp");
        Files.write(temp, bytes);
        if (!isResourcePackZip(temp)) {
            Files.deleteIfExists(temp);
            throw new IllegalStateException("Embedded rank pack is not a valid resource-pack ZIP");
        }

        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private static boolean isResourcePackZip(Path path) {
        boolean mcmeta = false;
        boolean font = false;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(path))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if ("pack.mcmeta".equals(name)) mcmeta = true;
                if ("assets/minecraft/font/default.json".equals(name)) font = true;
                if (mcmeta && font) return true;
            }
        } catch (Throwable ignored) {
            return false;
        }
        return false;
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

    private static String sha1(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        return HexFormat.of().formatHex(digest.digest(bytes));
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

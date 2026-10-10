package yt.kingez341.kingfeatures.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import yt.kingez341.kingfeatures.KingFeaturesPlugin;
import yt.kingez341.kingfeatures.util.Text;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;

public final class ResourcePackManager implements Listener {
    private static final UUID PACK_ID = UUID.fromString("34100000-0000-4000-8000-000000000255");

    private final KingFeaturesPlugin plugin;
    private HttpServer server;
    private Path localPack;
    private String sha1;
    private String status = "not-started";

    public ResourcePackManager(KingFeaturesPlugin plugin) { this.plugin = plugin; }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("resource-pack.enabled", true)) { status = "disabled"; return; }
        String remote = String.valueOf(plugin.getConfig().getString("resource-pack.remote-url", "")).trim();
        if (!remote.isBlank()) { status = "remote:" + remote; return; }
        localPack = discoverPack();
        if (localPack == null || !Files.isRegularFile(localPack)) {
            status = "no-pack-found";
            plugin.getLogger().warning("No KingFeatures/local OmniPanel rank resource pack found. Auto-send is idle until a pack exists or resource-pack.remote-url is set.");
            return;
        }
        try {
            sha1 = sha1(localPack);
            if (plugin.getConfig().getBoolean("resource-pack.self-host.enabled", true)) startHttpServer();
            else status = "local-pack-found-self-host-disabled";
        } catch (Exception exception) {
            status = "error:" + exception.getClass().getSimpleName();
            plugin.getLogger().warning("Could not prepare resource pack: " + exception.getMessage());
        }
    }

    public void stop() { if (server != null) { server.stop(0); server = null; } }
    public String status() { return status; }
    public Path localPack() { return localPack; }

    public void send(Player player) {
        if (!plugin.getConfig().getBoolean("resource-pack.enabled", true)) return;
        String url = resolveUrl(player);
        if (url == null || url.isBlank()) return;
        try {
            String effectiveHash = sha1;
            if ((effectiveHash == null || effectiveHash.isBlank()) && localPack != null && Files.isRegularFile(localPack)) effectiveHash = sha1(localPack);
            if (effectiveHash == null || effectiveHash.isBlank()) return;
            boolean required = plugin.getConfig().getBoolean("resource-pack.required", true);
            String promptText = Text.strip(plugin.getConfig().getString("resource-pack.prompt", "SkyStrike resource pack"));
            player.setResourcePack(PACK_ID, url, effectiveHash, Component.text(promptText), required);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Resource pack send failed for " + player.getName() + ": " + throwable.getMessage());
        }
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        long delay = Math.max(1L, plugin.getConfig().getLong("resource-pack.send-delay-ticks", 20L));
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (event.getPlayer().isOnline()) send(event.getPlayer()); }, delay);
    }

    @EventHandler public void onPackStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) return;
        plugin.getLogger().info("Resource pack " + event.getStatus().name().toLowerCase(Locale.ROOT) + " for " + event.getPlayer().getName());
    }

    private Path discoverPack() {
        Path configured = plugin.getDataFolder().toPath().resolve(plugin.getConfig().getString("resource-pack.local-file", "resourcepack/KingFeatures-Pack.zip")).normalize();
        if (Files.isRegularFile(configured)) return configured;
        if (plugin.getConfig().getBoolean("resource-pack.auto-import-omnipanel-rank-pack", true)) {
            Path serverRoot = plugin.getDataFolder().toPath().getParent().getParent();
            String relative = plugin.getConfig().getString("resource-pack.omnipanel-rank-pack-path", "plugins/OmniPanel/rank-pack/Kingez341YT-Rank-Pack-1.21.11.zip");
            Path omni = serverRoot.resolve(relative).normalize();
            if (Files.isRegularFile(omni)) { plugin.getLogger().info("Auto-detected OmniPanel rank pack: " + omni); return omni; }
        }
        return null;
    }

    private void startHttpServer() throws IOException {
        String bind = plugin.getConfig().getString("resource-pack.self-host.bind-address", "0.0.0.0");
        int port = plugin.getConfig().getInt("resource-pack.self-host.port", 8124);
        server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        server.createContext("/kingfeatures-pack.zip", this::handlePackRequest);
        server.setExecutor(Executors.newFixedThreadPool(2, runnable -> { Thread thread = new Thread(runnable, "KingFeatures-PackHTTP"); thread.setDaemon(true); return thread; }));
        server.start();
        status = "self-hosted:" + bind + ":" + port + " sha1=" + sha1;
    }

    private void handlePackRequest(HttpExchange exchange) throws IOException {
        if (localPack == null || !Files.isRegularFile(localPack)) { exchange.sendResponseHeaders(404, -1); exchange.close(); return; }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) && !"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) { exchange.sendResponseHeaders(405, -1); exchange.close(); return; }
        long size = Files.size(localPack);
        exchange.getResponseHeaders().set("Content-Type", "application/zip");
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=300");
        exchange.getResponseHeaders().set("ETag", '"' + sha1 + '"');
        exchange.sendResponseHeaders(200, "HEAD".equalsIgnoreCase(exchange.getRequestMethod()) ? -1 : size);
        if (!"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            try (OutputStream output = exchange.getResponseBody()) { Files.copy(localPack, output); }
        } else exchange.close();
    }

    private String resolveUrl(Player player) {
        String remote = String.valueOf(plugin.getConfig().getString("resource-pack.remote-url", "")).trim();
        if (!remote.isBlank()) return remote;
        if (server == null) return null;
        String host = String.valueOf(plugin.getConfig().getString("resource-pack.self-host.public-host", "")).trim();
        if (host.isBlank()) host = virtualHost(player);
        if (host == null || host.isBlank()) return null;
        int port = plugin.getConfig().getInt("resource-pack.self-host.port", 8124);
        if (host.startsWith("http://") || host.startsWith("https://")) return host.replaceAll("/+$", "") + ":" + port + "/kingfeatures-pack.zip";
        int colon = host.indexOf(':');
        if (colon > 0 && host.indexOf(':', colon + 1) < 0) host = host.substring(0, colon);
        return "http://" + host + ":" + port + "/kingfeatures-pack.zip";
    }

    private static String virtualHost(Player player) {
        try {
            Method method = player.getClass().getMethod("getVirtualHost");
            Object value = method.invoke(player);
            if (value instanceof InetSocketAddress address && address.getHostString() != null) return address.getHostString();
        } catch (ReflectiveOperationException ignored) {}
        return null;
    }

    private static String sha1(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192]; int read;
            while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}

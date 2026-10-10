package yt.kingez341.kingfeatures;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import yt.kingez341.kingfeatures.command.KingFeaturesCommand;
import yt.kingez341.kingfeatures.economy.UltimateEconomyBridge;
import yt.kingez341.kingfeatures.integration.OmniPanelIntegration;
import yt.kingez341.kingfeatures.items.FeatureBottleService;
import yt.kingez341.kingfeatures.pack.ResourcePackManager;
import yt.kingez341.kingfeatures.troll.TrollExecutor;
import yt.kingez341.kingfeatures.troll.TrollLabGui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class KingFeaturesPlugin extends JavaPlugin {
    private UltimateEconomyBridge economy;
    private FeatureBottleService bottleService;
    private ResourcePackManager packManager;
    private TrollLabGui trollLab;
    private OmniPanelIntegration omniPanel;
    private final Map<String, String> moduleFailures = new LinkedHashMap<>();

    @Override
    public void onEnable() {
        moduleFailures.clear();
        safe("config", this::saveDefaultConfig);
        safe("command", this::registerRootCommand);

        safe("uep", () -> {
            economy = new UltimateEconomyBridge(this);
            economy.connect();
        });

        safe("bottles", () -> {
            if (economy == null) {
                economy = new UltimateEconomyBridge(this);
            }
            bottleService = new FeatureBottleService(this, economy);
            registerListener(bottleService);
        });

        safe("resource-pack", () -> {
            packManager = new ResourcePackManager(this);
            registerListener(packManager);
            packManager.start();
        });

        safe("troll-lab", () -> {
            TrollExecutor trollExecutor = new TrollExecutor(this);
            trollLab = new TrollLabGui(this, trollExecutor);
            registerListener(trollLab);
            trollLab.start();
        });

        safe("omnipanel", () -> {
            omniPanel = new OmniPanelIntegration(this);
            omniPanel.start();
        });

        if (moduleFailures.isEmpty()) {
            getLogger().info("KingFeatures v" + getDescription().getVersion() + " enabled successfully. UEP=" + economyStatus() + " OmniPanel=" + omniPanelStatus() + " Pack=" + packStatus());
        } else {
            getLogger().warning("KingFeatures v" + getDescription().getVersion() + " enabled in DEGRADED mode. Failed modules: " + String.join(", ", moduleFailures.keySet()));
            getLogger().warning("Run /kf status for details. The plugin remains enabled so working modules and diagnostics are still usable.");
        }
    }

    @Override
    public void onDisable() {
        safeShutdown("omnipanel", () -> { if (omniPanel != null) omniPanel.stop(); });
        safeShutdown("troll-lab", () -> { if (trollLab != null) trollLab.stop(); });
        safeShutdown("resource-pack", () -> { if (packManager != null) packManager.stop(); });
    }

    private void registerRootCommand() {
        KingFeaturesCommand command = new KingFeaturesCommand(this);
        PluginCommand root = getCommand("kingfeatures");
        if (root == null) {
            throw new IllegalStateException("kingfeatures command missing from plugin.yml");
        }
        root.setExecutor(command);
        root.setTabCompleter(command);
    }

    private void registerListener(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, this);
    }

    public void reloadKingFeatures() {
        moduleFailures.clear();
        safe("config-reload", this::reloadConfig);
        safe("uep-reload", () -> {
            if (economy == null) economy = new UltimateEconomyBridge(this);
            economy.connect();
        });
        safe("pack-reload", () -> {
            if (packManager != null) packManager.start();
        });
        safe("troll-reload", () -> {
            if (trollLab != null) trollLab.start();
        });
        safe("omnipanel-reload", () -> {
            if (omniPanel != null) omniPanel.start();
        });
    }

    public UltimateEconomyBridge getEconomy() { return economy; }
    public FeatureBottleService getBottleService() { return bottleService; }
    public ResourcePackManager getPackManager() { return packManager; }
    public TrollLabGui getTrollLab() { return trollLab; }
    public OmniPanelIntegration getOmniPanel() { return omniPanel; }

    public boolean isBottleAvailable() { return bottleService != null; }
    public boolean isPackAvailable() { return packManager != null; }
    public boolean isTrollLabAvailable() { return trollLab != null; }

    public String economyStatus() {
        return economy == null ? failureOr("uep", "unavailable") : economy.status();
    }

    public String omniPanelStatus() {
        return omniPanel == null ? failureOr("omnipanel", "unavailable") : omniPanel.status();
    }

    public String packStatus() {
        return packManager == null ? failureOr("resource-pack", "unavailable") : packManager.status();
    }

    public String trollStatus() {
        return trollLab == null ? failureOr("troll-lab", "unavailable") : "ready";
    }

    public Map<String, String> moduleFailures() {
        return Map.copyOf(moduleFailures);
    }

    public void audit(String line) {
        if (getConfig().getBoolean("troll-lab.audit-to-console", true)) getLogger().info("[Audit] " + line);
        try {
            Files.createDirectories(getDataFolder().toPath());
            Files.writeString(
                    getDataFolder().toPath().resolve("audit.log"),
                    Instant.now() + " " + line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            getLogger().warning("Could not append audit.log: " + exception.getMessage());
        }
    }

    private void safe(String module, ThrowingRunnable action) {
        try {
            action.run();
        } catch (Throwable throwable) {
            recordFailure(module, throwable);
        }
    }

    private void safeShutdown(String module, ThrowingRunnable action) {
        try {
            action.run();
        } catch (Throwable throwable) {
            getLogger().warning("Shutdown error in " + module + ": " + rootMessage(throwable));
        }
    }

    private void recordFailure(String module, Throwable throwable) {
        String detail = throwable.getClass().getSimpleName() + ": " + rootMessage(throwable);
        moduleFailures.put(module, detail);
        getLogger().severe("KingFeatures module '" + module + "' failed but the plugin will stay enabled: " + detail);
        throwable.printStackTrace();
        try {
            Files.createDirectories(getDataFolder().toPath());
            Files.writeString(
                    getDataFolder().toPath().resolve("startup-errors.log"),
                    Instant.now() + " [" + module + "] " + detail + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException ignored) {
        }
    }

    private String failureOr(String module, String fallback) {
        return moduleFailures.getOrDefault(module, fallback);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) cursor = cursor.getCause();
        String message = cursor.getMessage();
        return message == null || message.isBlank() ? cursor.getClass().getSimpleName() : message.replace('\n', ' ');
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}

package yt.kingez341.kingfeatures;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
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

public final class KingFeaturesPlugin extends JavaPlugin {
    private UltimateEconomyBridge economy;
    private FeatureBottleService bottleService;
    private ResourcePackManager packManager;
    private TrollLabGui trollLab;
    private OmniPanelIntegration omniPanel;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        economy = new UltimateEconomyBridge(this);
        economy.connect();
        bottleService = new FeatureBottleService(this, economy);
        packManager = new ResourcePackManager(this);
        TrollExecutor trollExecutor = new TrollExecutor(this);
        trollLab = new TrollLabGui(this, trollExecutor);
        omniPanel = new OmniPanelIntegration(this);

        Bukkit.getPluginManager().registerEvents(bottleService, this);
        Bukkit.getPluginManager().registerEvents(packManager, this);
        Bukkit.getPluginManager().registerEvents(trollLab, this);

        KingFeaturesCommand command = new KingFeaturesCommand(this);
        PluginCommand root = getCommand("kingfeatures");
        if (root == null) throw new IllegalStateException("kingfeatures command missing from plugin.yml");
        root.setExecutor(command);
        root.setTabCompleter(command);

        packManager.start();
        trollLab.start();
        omniPanel.start();

        getLogger().info("KingFeatures v" + getDescription().getVersion() + " enabled. UEP=" + economy.status() + " OmniPanel=" + omniPanel.status());
    }

    @Override
    public void onDisable() {
        if (omniPanel != null) omniPanel.stop();
        if (trollLab != null) trollLab.stop();
        if (packManager != null) packManager.stop();
    }

    public void reloadKingFeatures() {
        reloadConfig();
        economy.connect();
        packManager.start();
        trollLab.start();
        omniPanel.start();
    }

    public UltimateEconomyBridge getEconomy() { return economy; }
    public FeatureBottleService getBottleService() { return bottleService; }
    public ResourcePackManager getPackManager() { return packManager; }
    public TrollLabGui getTrollLab() { return trollLab; }
    public OmniPanelIntegration getOmniPanel() { return omniPanel; }

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
}

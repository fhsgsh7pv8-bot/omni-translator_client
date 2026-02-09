package org.pytenix;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.pytenix.brigde.ConnectionListener;
import org.pytenix.brigde.SpigotBridge;
import org.pytenix.gradient.GradientService;
import org.pytenix.module.ModuleService;
import org.pytenix.placeholder.PlaceholderService;
import org.pytenix.util.TaskScheduler;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;



@Getter
public class SpigotTranslator extends JavaPlugin {

    private String serverName;

    private TranslatorService translatorService;
    private SpigotBridge spigotBridge;
    private ModuleService moduleService;
    private GradientService gradientService;
    private PlaceholderService placeholderService;


    private TaskScheduler taskScheduler;



    @Getter @Setter
    ServerConfiguration serverConfiguration;

    private File configFile;

    private ObjectMapper mapper = new ObjectMapper();

    LegacyComponentSerializer legacyComponentSerializer = LegacyComponentSerializer.builder()
            .character('§')
            .extractUrls()
            .hexColors()
            .flattener(ComponentFlattener.basic())
            .build();


    Cache<UUID, GradientService.GradientInfo> cachedGradients = CacheBuilder.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS).build();




    @Override
    public void onEnable() {

        this.serverName = this.getServer().getName();
        this.taskScheduler = new TaskScheduler(this);
        this.configFile = new File(getDataFolder(), "proxy_sync_config.json");

        loadConfigFromDisk();

        this.gradientService = new GradientService();

        this.placeholderService = new PlaceholderService(this);

        this.spigotBridge = new SpigotBridge(this);
        this.translatorService = new TranslatorService(this);

        initializePattern();

        Bukkit.getPluginManager().registerEvents(new ConnectionListener(this),this);

        moduleService = new ModuleService(this);

    }



    private void loadConfigFromDisk() {
        if (!configFile.exists()) {

            getLogger().info("Keine lokale Config gefunden. Nutze Default bis Proxy sendet.");
            this.serverConfiguration = ServerConfiguration.createDefault("DEIN-TEST-KEY");
            return;
        }

        try {
            this.serverConfiguration = mapper.readValue(configFile, ServerConfiguration.class);
            getLogger().info("Lokale Config geladen (Lizenz: " + serverConfiguration.getLicenseKey() + ")");
        } catch (IOException e) {
            getLogger().severe("Konnte lokale Config nicht laden: " + e.getMessage());
            this.serverConfiguration = ServerConfiguration.createDefault("DEIN-TEST-KEY");
        }
    }


    public void applyConfigUpdate(ServerConfiguration newConfig) {
        this.serverConfiguration = newConfig;


        placeholderService.updateProtectedWords(serverConfiguration.getBlacklistedWords());



        getTaskScheduler().runAsync(() -> {
            try {
                if (!getDataFolder().exists()) getDataFolder().mkdirs();
                mapper.writeValue(configFile, newConfig);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

    }

    public void initializePattern()
    {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            placeholderService.getPlayernameProtector().addPlayer(onlinePlayer.getName().toLowerCase());
        }
    }


    @Override
    public void onDisable() {
    }
}
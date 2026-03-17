package org.pytenix;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.pytenix.brigde.ConnectionListener;
import org.pytenix.brigde.SpigotBridge;
import org.pytenix.config.ConfigService;
import org.pytenix.config.ConfigurationFile;
import org.pytenix.entity.ServerConfiguration;
import org.pytenix.listener.JoinQuitListener;
import org.pytenix.placeholder.GradientService;
import org.pytenix.module.ModuleService;
import org.pytenix.placeholder.PlaceholderService;
import org.pytenix.util.TaskScheduler;
import org.pytenix.util.TextComponentUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;



@Getter
public class SpigotTranslator extends JavaPlugin {

    private String serverName;

    private TranslatorService translatorService;

    private SpigotBridge spigotBridge;
    private ModuleService moduleService;


    private TaskScheduler taskScheduler;

    private File configFile;

    private ObjectMapper mapper = new ObjectMapper();

    @Getter
    TextComponentUtil textComponentUtil;

    LegacyComponentSerializer legacyComponentSerializer = LegacyComponentSerializer.builder()
            .character('§')
            .extractUrls()
            .hexColors()
            .flattener(ComponentFlattener.basic())
            .build();


    ConfigService configService;
    ConfigurationFile configurationFile;

    @Override
    public void onEnable() {

        this.configService = new ConfigService();


        if(!configService.exists("config.json")) {
            configService.saveConfig("config.json",new ConfigurationFile("DEIN-LIZENZ-SCHLÜSSEL"));
            System.out.println("[AITranslator] Please check in config.json for the license key!");
        }
        this.configurationFile = configService.loadConfig("config.json",ConfigurationFile.class);





        this.serverName = this.getServer().getName();
        this.taskScheduler = new TaskScheduler(this);
        this.configFile = new File(getDataFolder(), "proxy_sync_config.json");





        this.spigotBridge = new SpigotBridge(this);
        spigotBridge.setSecretKey(configurationFile.getLicenseKey());

        loadConfigFromDisk();

        this.translatorService = new TranslatorService(spigotBridge) {
            @Override
            protected CompletableFuture<String> process(UUID id, String text, String targetLang, String module) {
                return spigotBridge.translate(id,text,targetLang,module);
            }
        };

        this.textComponentUtil = new TextComponentUtil(translatorService);

        spigotBridge.initPlayernames();

        Bukkit.getPluginManager().registerEvents(new ConnectionListener(this),this);
        Bukkit.getPluginManager().registerEvents(new JoinQuitListener(this),this);

        moduleService = new ModuleService(this);

        getServer().getCommandMap().register("translator", new org.bukkit.command.Command("testmsg") {

            private final TestMessageCommand executor = new TestMessageCommand();

            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                return executor.onCommand(sender, this, commandLabel, args);
            }
        });

        getLogger().info("AITranslator Test-Modul geladen!");

    }



    private void loadConfigFromDisk() {
        if (!configFile.exists()) {

            getLogger().info("Keine lokale Config gefunden. Nutze Default bis Proxy sendet.");
            spigotBridge.setServerConfiguration(ServerConfiguration.createDefault("DEIN-LIZENZ-SCHLÜSSEL"));
            return;
        }

        try {
            spigotBridge.setServerConfiguration(mapper.readValue(configFile, ServerConfiguration.class));
        } catch (IOException e) {
            getLogger().severe("Konnte lokale Config nicht laden: " + e.getMessage());
            spigotBridge.setServerConfiguration(ServerConfiguration.createDefault("DEIN-LIZENZ-SCHLÜSSEL"));
        }
    }






    @Override
    public void onDisable() {
    }
}
package org.pytenix;


import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.Getter;
import lombok.Setter;
import org.pytenix.config.ConfigService;
import org.pytenix.config.ConfigurationFile;
import org.slf4j.Logger;

import java.io.File;

@Plugin(
        id = "translator",
        name = "TranslatorProxy",
        version = "1.0-SNAPSHOT",
        authors = {"PytenixOG"}
)
public class VelocityTranslator {

    private final ProxyServer server;
    private final Logger logger;


    @Getter
    private RestfulService restfulService;

    @Getter
    private final CaffeineCache caffeineCache;


    @Getter @Setter
    public ServerConfiguration cachedConfig;

    @Getter
    private final ProxyServer proxyServer;

    @Getter
    VelocityBridge velocityBridge;

    final ConfigService configService;
    final ConfigurationFile configurationFile;


    @Inject
    public VelocityTranslator(ProxyServer server, Logger logger) {
        this.server = server;
        this.proxyServer = server;
        this.logger = logger;
        this.caffeineCache = new CaffeineCache();

        this.configService = new ConfigService();

        if(!configService.exists("config.json")) {
            configService.saveConfig("config.json",new ConfigurationFile("DEIN-LIZENZ-SCHLÜSSEL"));
            System.out.println("[AITranslator] Please check in config.json for the license key!");
        }
        this.configurationFile = configService.loadConfig("config.json",ConfigurationFile.class);


    }




    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {

        this.velocityBridge = new VelocityBridge(this);
        velocityBridge.setSecretKey(configurationFile.getLicenseKey());

        server.getEventManager().register(this,velocityBridge );

        this.restfulService = new RestfulService(this,velocityBridge, configurationFile.getLicenseKey(),server); //CONFIG ANBINDUNG


        logger.info("Translator Proxy erfolgreich gestartet!");
    }
}
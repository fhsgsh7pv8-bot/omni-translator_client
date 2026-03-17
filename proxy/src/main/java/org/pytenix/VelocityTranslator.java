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
import org.pytenix.entity.ServerConfiguration;
import org.pytenix.listener.PlayerConnectionChangeListener;
import org.pytenix.motd.GeoService;
import org.pytenix.motd.MotDEvent;
import org.pytenix.placeholder.PlaceholderNormalizer;
import org.pytenix.placeholder.PlaceholderService;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
    GeoService geoService;

    @Getter
    private RestfulService restfulService;

    @Getter
    private final CaffeineCache caffeineCache;

    @Getter
    String remoteAddress = "ws.omni-translate.me";


    @Getter
    private final ProxyServer proxyServer;

    @Getter
    VelocityBridge velocityBridge;

    final ConfigService configService;
    final ConfigurationFile configurationFile;


    @Getter
    TranslatorService translatorService;

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


        this.geoService = new GeoService(this, configurationFile.getLicenseKey(), server);

        server.getEventManager().register(this,velocityBridge );

        this.restfulService = new RestfulService(this,velocityBridge, configurationFile.getLicenseKey(),server); //CONFIG ANBINDUNG



        this.translatorService = new TranslatorService(velocityBridge) {
            @Override
            protected CompletableFuture<String> process(UUID id, String text, String targetLang, String module) {
                return restfulService.sendTranslationRequest(id,text,targetLang,module);
            }
        };

        server.getEventManager().register(this, new MotDEvent(this));
        server.getEventManager().register(this, new PlayerConnectionChangeListener(this));

        logger.info("Translator Proxy erfolgreich gestartet!");
    }
}
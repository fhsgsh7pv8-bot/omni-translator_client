package org.pytenix;


import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;

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


    @Inject
    public VelocityTranslator(ProxyServer server, Logger logger) {
        this.server = server;
        this.proxyServer = server;
        this.logger = logger;
        this.caffeineCache = new CaffeineCache();

    }




    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {

        this.velocityBridge = new VelocityBridge(this);
        server.getEventManager().register(this,velocityBridge );

        this.restfulService = new RestfulService(this,velocityBridge,"DEIN-TEST-KEY",server); //CONFIG ANBINDUNG


        logger.info("Translator Proxy erfolgreich gestartet!");
    }
}
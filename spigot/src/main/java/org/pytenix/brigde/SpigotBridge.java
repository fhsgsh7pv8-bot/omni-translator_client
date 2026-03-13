package org.pytenix.brigde;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.pytenix.AdvancedTranslationBridge;
import org.pytenix.entity.ServerConfiguration;
import org.pytenix.SpigotTranslator;
import org.pytenix.placeholder.GradientService;
import org.pytenix.proto.generated.NetworkPackets;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SpigotBridge extends AdvancedTranslationBridge {
    private final SpigotTranslator plugin;
    private static final String CHANNEL = "translator:main";


    public SpigotBridge(SpigotTranslator plugin) {
        this.plugin = plugin;
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL,
                (ch, player, msg) -> this.onReceiveRaw(msg, null));


        ScheduledExecutorService flushScheduler = Executors.newSingleThreadScheduledExecutor();

        flushScheduler.scheduleAtFixedRate(() -> {
            if (!Bukkit.getOnlinePlayers().isEmpty()) {
                this.flush();
            }
        }, 10, 10, TimeUnit.MILLISECONDS);




    }


    @Override
    public void initPlayernames() {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            getPlaceholderService().getPlayernameProtector().addPlayer(onlinePlayer.getName().toLowerCase());
        }
    }

    @Override
    protected void handleConfigRequest(String originServer) {

    }

    @Override
    protected void onConfigUpdate(ServerConfiguration serverConfiguration) {


        plugin.getTaskScheduler().runAsync(() -> {


                try {
                    if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
                    plugin.getMapper().writeValue(plugin.getConfigFile(), serverConfiguration);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            plugin.getLogger().info("Config-Update vom Proxy empfangen und angewendet.");

    }

    @Override
    protected void dispatchRaw(byte[] data, String originServer) {
        //EXTRA SO; DAMIT DER TRAFFIC GLEICHMÄ?IG AUFGETEILT WIRD!
        Bukkit.getOnlinePlayers().stream().findAny().ifPresent(p -> p.sendPluginMessage(plugin, CHANNEL, data));
    }



    @Override
    protected void handleFullResultPackage(NetworkPackets.TranslationBatchResult batch) {
        super.handleResponses(batch);
    }

    @Override
    protected void handleFullRequestPackage(NetworkPackets.TranslationBatchRequest batch) {
        // Spigot empfängt keine Requests, es schickt sie nur.
    }


}
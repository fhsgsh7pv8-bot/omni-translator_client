package org.pytenix.brigde;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerUnregisterChannelEvent;
import org.pytenix.AdvancedTranslationBridge;
import org.pytenix.entity.ServerConfiguration;
import org.pytenix.SpigotTranslator;
import org.pytenix.placeholder.GradientService;
import org.pytenix.proto.generated.NetworkPackets;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

public class SpigotBridge extends AdvancedTranslationBridge implements Listener {
    private final SpigotTranslator plugin;
    private static final String CHANNEL = "translator:main";

    private final Set<Player> availablePlayers = ConcurrentHashMap.newKeySet();

    private final Queue<byte[]> packetQueue = new ConcurrentLinkedQueue<>();


    public SpigotBridge(SpigotTranslator plugin) {
        this.plugin = plugin;
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL,
                (ch, player, msg) -> this.onReceiveRaw(msg, null));


        Bukkit.getPluginManager().registerEvents(this, plugin);

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

        // Der Flush-Scheduler (baut die Batches zusammen)
        scheduler.scheduleAtFixedRate(() -> {
            if (!availablePlayers.isEmpty()) {
                this.flush();
            }
        }, 10, 10, TimeUnit.MILLISECONDS);

        scheduler.scheduleAtFixedRate(this::drainQueue, 10, 10, TimeUnit.MILLISECONDS);
    }



    @EventHandler
    public void onChannelRegister(PlayerRegisterChannelEvent event) {
        if (event.getChannel().equals(CHANNEL)) {
            availablePlayers.add(event.getPlayer());
            drainQueue();
        }
    }

    @EventHandler
    public void onChannelUnregister(PlayerUnregisterChannelEvent event) {
        if (event.getChannel().equals(CHANNEL)) {
            availablePlayers.remove(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        availablePlayers.remove(event.getPlayer());
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
        packetQueue.add(data);

        drainQueue();
    }

    private void drainQueue() {
        if (availablePlayers.isEmpty() || packetQueue.isEmpty()) {
            return;
        }

        Player carrier = availablePlayers.stream().findAny().orElse(null);
        if (carrier == null) return;

        byte[] packet;
        while ((packet = packetQueue.poll()) != null) {
            try {
                carrier.sendPluginMessage(plugin, CHANNEL, packet);
            } catch (Exception e) {
                packetQueue.add(packet);
                availablePlayers.remove(carrier);
                break;
            }
        }
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
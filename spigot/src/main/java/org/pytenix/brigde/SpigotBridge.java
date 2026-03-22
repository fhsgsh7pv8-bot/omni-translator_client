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


    private boolean hasConfig;

    public SpigotBridge(SpigotTranslator plugin) {
        this.plugin = plugin;
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL,
                (ch, player, msg) -> this.onReceiveRaw(msg, null));


        Bukkit.getPluginManager().registerEvents(this, plugin);

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!availablePlayers.isEmpty()) {
                // 1. Flush: Sammelt alle Einzel-Requests aus der deduplicationQueue zu EINEM Batch
                // und wirft das fertige byte[] in die packetQueue (via dispatchRaw)
                this.flush();

                // 2. Drain: Nimmt die fertigen Batches aus der packetQueue und sendet sie ab
                this.drainQueue();
            }
        }, 1L, 1L); // Startet nach 1 Tick, wiederholt sich jeden Tick
    }



    @EventHandler
    public void onChannelRegister(PlayerRegisterChannelEvent event) {
        if (event.getChannel().equals(CHANNEL)) {

            if(!hasConfig)
                sendConfigRequest(event.getPlayer());

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


        hasConfig = true;
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

    public void sendConfigRequest(Player player)
    {
        NetworkPackets.ConfigRequestPacket req = NetworkPackets.ConfigRequestPacket.newBuilder().setTimestamp(System.currentTimeMillis()).build();
        NetworkPackets.PacketWrapper wrapper = NetworkPackets.PacketWrapper.newBuilder().setConfigRequest(req).build();
            player.sendPluginMessage(plugin, CHANNEL, wrapper.toByteArray());

    }
    @Override
    protected void dispatchRaw(byte[] data, String originServer) {
        packetQueue.add(data);
    }

    private void drainQueue() {
        if (availablePlayers.isEmpty() || packetQueue.isEmpty() || !hasConfig) {
            return;
        }

        // Wir schnappen uns einen Spieler als "Carrier" für den Plugin-Channel
        Player carrier = availablePlayers.stream().findAny().orElse(null);
        if (carrier == null) return;

        byte[] packet;
        while ((packet = packetQueue.poll()) != null) {
            try {
                // Das ist jetzt 100% sicher, da es über den Bukkit-Main-Thread Scheduler aufgerufen wird!
                carrier.sendPluginMessage(plugin, CHANNEL, packet);
            } catch (Exception e) {
                // Wenn das Senden fehlschlägt (z.B. Spieler ist genau in dem Moment geleaved),
                // packen wir das Paket zurück in die Queue und entfernen den kaputten Carrier.
                packetQueue.add(packet);
                availablePlayers.remove(carrier);
                break; // Schleife abbrechen, der nächste Tick versucht es mit einem neuen Carrier
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
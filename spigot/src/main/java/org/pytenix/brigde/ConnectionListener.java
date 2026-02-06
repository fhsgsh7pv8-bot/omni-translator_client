package org.pytenix.brigde;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.pytenix.SpigotTranslator;

public class ConnectionListener implements Listener {
    private final SpigotTranslator plugin;
    private boolean requested = false;

    public ConnectionListener(SpigotTranslator plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!requested) {
            plugin.getLogger().info("Erster Spieler erkannt. Fordere Sync vom Proxy an...");

             plugin.getTaskScheduler().runAsyncLater(() -> {
                plugin.getSpigotBridge().sendConfigRequestProto();
            },10);

            requested = true;
        }
    }
}
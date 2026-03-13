package org.pytenix.listener;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.pytenix.AdvancedTranslationBridge;
import org.pytenix.SpigotTranslator;

public class JoinQuitListener implements Listener {

    final SpigotTranslator spigotTranslator;
    final AdvancedTranslationBridge translationBridge;


    public JoinQuitListener(SpigotTranslator plugin)
    {
        this.spigotTranslator = plugin;
        this.translationBridge = plugin.getSpigotBridge();

    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event)
    {
        this.translationBridge.getPlaceholderService().getPlayernameProtector().addPlayer(event.getPlayer().getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event)
    {
        this.translationBridge.getPlaceholderService().getPlayernameProtector().removePlayer(event.getPlayer().getName());
    }
}

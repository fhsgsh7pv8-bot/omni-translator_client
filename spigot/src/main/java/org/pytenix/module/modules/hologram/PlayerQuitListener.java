package org.pytenix.module.modules.hologram;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    final HologramModule hologramModule;

    public PlayerQuitListener(HologramModule hologramModule)
    {
        this.hologramModule = hologramModule;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event)
    {
        hologramModule.getPlayerTranslationCache().invalidate(event.getPlayer().getUniqueId());
    }




}

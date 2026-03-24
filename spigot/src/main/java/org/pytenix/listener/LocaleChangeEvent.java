package org.pytenix.listener;


import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.pytenix.SpigotTranslator;

//ONLY FOR DEMO SERVER
public class LocaleChangeEvent implements Listener {

    public SpigotTranslator translator;

    public LocaleChangeEvent(SpigotTranslator translator)
    {
        this.translator = translator;
    }

    @EventHandler
    public void onLocaleChange(PlayerLocaleChangeEvent event)
    {
        Player player = event.getPlayer();

        final Location originalLocation = player.getLocation().clone();

        Location refreshLocation = originalLocation.clone().add(0, 0, 200);
        player.teleport(refreshLocation);

        translator.getTaskScheduler().runSyncLater(() -> {

            player.teleport(originalLocation);
            player.sendMessage("§a[Omni] §7Language updated!");

        }, 3); // 3 Ticks warten

    }
}

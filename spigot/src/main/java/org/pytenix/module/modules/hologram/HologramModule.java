package org.pytenix.module.modules.hologram;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.pytenix.SpigotTranslator;
import org.pytenix.module.TranslatorModule;
import org.pytenix.module.modules.player.ChatPacketListener;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Getter
public class HologramModule extends TranslatorModule {

    public final Cache<UUID, Cache<Component, Component>> playerTranslationCache = CacheBuilder.newBuilder()
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();




    public HologramModule(SpigotTranslator spigotTranslator) {
        super(spigotTranslator, "hologram");

        Bukkit.getPluginManager().registerEvents(new PlayerQuitListener(this),getSpigotTranslator());

        PacketEvents.getAPI().getEventManager().registerListener(new EntityPacketListener(this),
                PacketListenerPriority.HIGHEST);
    }



}

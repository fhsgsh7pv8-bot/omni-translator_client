package org.pytenix.module.modules.hologram;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnLivingEntity;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.github.retrooper.packetevents.adventure.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.pytenix.module.modules.player.LiveChatModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class EntityPacketListener implements PacketListener, Listener {


    final HologramModule hologramModule;



    public EntityPacketListener(HologramModule hologramModule)
    {
        this.hologramModule = hologramModule;


    }



    private Cache<Component, Component> getHologramCache(String locale) {
        try {
            return hologramModule.getPlayerTranslationCache().get(locale, () -> CacheBuilder.newBuilder()
                    .expireAfterWrite(2, TimeUnit.MINUTES)
                    .maximumSize(1000)
                    .build());
        } catch (ExecutionException e) {
            e.printStackTrace();
            return null;
        }
    }


    @Override
    public void onPacketSend(PacketSendEvent event) {

        if(!hologramModule.isActive())
            return;

        if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
            WrapperPlayServerEntityMetadata wrapper = new WrapperPlayServerEntityMetadata(event);
            processHologram(event,event.getUser(), wrapper.getEntityId(), wrapper.getEntityMetadata());
        } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
         //   WrapperPlayServerSpawnEntity wrapper = new WrapperPlayServerSpawnEntity(event);
            //TODO:
        } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_LIVING_ENTITY) {
            WrapperPlayServerSpawnLivingEntity wrapper = new WrapperPlayServerSpawnLivingEntity(event);
            processHologram(event,event.getUser(), wrapper.getEntityId(), wrapper.getEntityMetadata());
        }
    }
    private void processHologram(PacketSendEvent event, com.github.retrooper.packetevents.protocol.player.User user, int entityId, List<EntityData<?>> dataList) {
        if (dataList == null || dataList.isEmpty()) return;

        final Player player = Bukkit.getPlayer(user.getUUID());

        if(!hologramModule.checkIfNeed(event.getUser().getUUID()))
            return;

        Cache<Component, Component> personalCache = getHologramCache(player.getLocale());
        if (personalCache == null) return;

        List<EntityData<?>> newMetadataList = new ArrayList<>();
        List<EntityData<?>> toTranslateAsync = new ArrayList<>();
        boolean packetModified = false;

        // ==========================================================
        // PHASE 1: SYNCHRONER CACHE CHECK (Kein Delay, kein Flickering)
        // ==========================================================
        for (EntityData data : dataList) {
            Object value = data.getValue();
            Component originalComponent = null;
            boolean wasOptional = false;

            // Extrahiere die Component
            if (value instanceof Optional<?> opt) {
                if (opt.isPresent() && opt.get() instanceof Component comp) {
                    originalComponent = comp;
                    wasOptional = true;
                }
            } else if (value instanceof Component comp) {
                originalComponent = comp;
                wasOptional = false;
            }

            if (originalComponent != null) {
                Component cachedTranslation = personalCache.getIfPresent(originalComponent);

                if (cachedTranslation != null) {
                    // 🎯 CACHE HIT: Paket sofort austauschen!
                    Object newValue = wasOptional ? Optional.of(cachedTranslation) : cachedTranslation;
                    EntityData newData = new EntityData(data.getIndex(), data.getType(), newValue);
                    newMetadataList.add(newData);
                    packetModified = true;
                } else {
                    newMetadataList.add(data);
                    toTranslateAsync.add(data);
                }
            } else {
                newMetadataList.add(data);
            }
        }

        if (packetModified) {
            dataList.clear();
            dataList.addAll(newMetadataList);
            event.markForReEncode(true);
        }

        if (!toTranslateAsync.isEmpty()) {
            CompletableFuture.runAsync(() -> {
                for (EntityData dataToTranslate : toTranslateAsync) {
                    Object value = dataToTranslate.getValue();
                    Component originalComponent = null;
                    boolean wasOptional = false;

                    if (value instanceof Optional<?> opt) {
                        originalComponent = (Component) opt.get();
                        wasOptional = true;
                    } else {
                        originalComponent = (Component) value;
                    }

                    String legacyText = hologramModule.getSpigotTranslator().getLegacyComponentSerializer().serialize(originalComponent);

                    if (!legacyText.trim().isEmpty()) {
                        final Component keyComponent = originalComponent;
                        final boolean isOptionalFinal = wasOptional;

                        translateHologramLine(player, legacyText)
                                .thenAccept(translatedComponent -> {
                                    if (translatedComponent == null) return;

                                    // 1. Für die Zukunft in den Cache legen
                                    personalCache.put(keyComponent, translatedComponent);

                                    // 2. Ein Update-Paket (Fake) schicken, damit der Spieler es jetzt sieht
                                    Object newValue = isOptionalFinal ? Optional.of(translatedComponent) : translatedComponent;
                                    EntityData newDataUpdate = new EntityData(dataToTranslate.getIndex(), dataToTranslate.getType(), newValue);

                                    List<EntityData<?>> singleUpdateList = new ArrayList<>();
                                    singleUpdateList.add(newDataUpdate);

                                    // Sendet das Paket still an den Spieler
                                    sendUpdatePacket(user, entityId, singleUpdateList);
                                });
                    }
                }
            });
        }
    }
    private CompletableFuture<Component> translateHologramLine(Player player, String text) {
        if (player == null) return CompletableFuture.completedFuture(null);
        String lang = player.getLocale();

       return hologramModule.translate(text, lang)
                .thenApply(translatedString -> {
                    return hologramModule.getSpigotTranslator().getLegacyComponentSerializer().deserialize(translatedString);
                });
    }

    private void sendUpdatePacket(User user, int entityId, List<EntityData<?>> newData) {
        if (newData.isEmpty()) return;


        if (Bukkit.getPlayer(user.getUUID()) == null) return;

        WrapperPlayServerEntityMetadata updatePacket = new WrapperPlayServerEntityMetadata(

                entityId,
                newData
        );



       PacketEvents.getAPI().getPlayerManager().sendPacketSilently(Bukkit.getPlayer(user.getUUID()), updatePacket);
    }


}

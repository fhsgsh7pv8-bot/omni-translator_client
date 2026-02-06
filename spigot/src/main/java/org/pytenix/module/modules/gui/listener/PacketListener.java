package org.pytenix.module.modules.gui.listener;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.pytenix.module.modules.gui.InventoryModule;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PacketListener implements com.github.retrooper.packetevents.event.PacketListener {


    private final InventoryModule inventoryModule;

    private final Set<String> activeTranslations = ConcurrentHashMap.newKeySet();
   private final Map<UUID, Integer> latestStateIdMap = new ConcurrentHashMap<>();

    public PacketListener(InventoryModule inventoryModule)
    {
        this.inventoryModule = inventoryModule;
    }


    @Override
    public void onPacketSend(PacketSendEvent event) {

        if (event.isCancelled()) return;

        if(!inventoryModule.isActive())
            return;

        if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
            if(!inventoryModule.isActive())
                return;

            handleWindowItems(event);
        }
       else if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
            if(!inventoryModule.isActive())
                return;

           handleSetSlot(event);
        }
    }

    public void handleWindowItems(PacketSendEvent event)
    {
        WrapperPlayServerWindowItems wrapper = new WrapperPlayServerWindowItems(event);
        latestStateIdMap.put(event.getUser().getUUID(), wrapper.getStateId());
        if (wrapper.getWindowId() == 0) {
            return;
        }
        com.github.retrooper.packetevents.protocol.player.User user = event.getUser();
        if (user == null) return;



        String lockKey = user.getUUID().toString() + ":" + wrapper.getWindowId() + ":" + wrapper.getStateId();

      if (activeTranslations.contains(lockKey)) {
            return;
        }
        activeTranslations.add(lockKey);
        {


            Player player = org.bukkit.Bukkit.getPlayer(user.getUUID());

           if (player == null) {
                return;
            }


            try {


                int windowId = wrapper.getWindowId();
                int stateId = wrapper.getStateId();


                List<ItemStack> bukkitItems = wrapper.getItems().stream()
                        .map(SpigotConversionUtil::toBukkitItemStack)
                        .collect(Collectors.toList());



                ItemStack carriedItem = wrapper.getCarriedItem()
                        .map(SpigotConversionUtil::toBukkitItemStack)
                        .orElse(null);

                String locale = player.getLocale();



              CompletableFuture.runAsync(() -> {
                    try {
                        translateAndSendUpdate(player, windowId, stateId, bukkitItems, carriedItem, locale);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

            } catch (Exception e) {
                System.out.println("Fehler beim Lesen des Pakets: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void handleSetSlot(PacketSendEvent event) {

        WrapperPlayServerSetSlot wrapper = new WrapperPlayServerSetSlot(event);

        if (wrapper.getWindowId() == 0) {
            return;
        }

        com.github.retrooper.packetevents.protocol.player.User user = event.getUser();
        if (user == null) return;


        Player player = org.bukkit.Bukkit.getPlayer(user.getUUID());

        if (player == null) {
            return;
        }


        event.setCancelled(true);


        int windowId = wrapper.getWindowId();
        int stateId = wrapper.getStateId();
        int slot = wrapper.getSlot();
        final ItemStack item = SpigotConversionUtil.toBukkitItemStack(wrapper.getItem()).clone();
        String locale = player.getLocale();


        inventoryModule.translateItem(item, locale).thenAccept(translatedItem -> {
            if (!player.isOnline()) return;

            Integer lastId = latestStateIdMap.get(player.getUniqueId());
            if (lastId != null && stateId < lastId)

            if(player.getOpenInventory() == null || player.getOpenInventory().getTopInventory() == null ||
                    !player.getOpenInventory().getTopInventory().getItem(slot).isSimilar(item))
               return;



            WrapperPlayServerSetSlot updatePacket = new WrapperPlayServerSetSlot(
                    windowId,
                    stateId,
                    slot,
                    SpigotConversionUtil.fromBukkitItemStack(translatedItem)
            );


            PacketEvents.getAPI().getPlayerManager().sendPacketSilently(player, updatePacket);
        });
    }

    private void sendUpdateToClient(Player player, int windowId, int stateId, List<ItemStack> items, ItemStack carriedItem) {
        List<com.github.retrooper.packetevents.protocol.item.ItemStack> peItems = items.stream()
                .map(SpigotConversionUtil::fromBukkitItemStack)
                .collect(Collectors.toList());

        com.github.retrooper.packetevents.protocol.item.ItemStack peCarriedItem =
                SpigotConversionUtil.fromBukkitItemStack(carriedItem);

        WrapperPlayServerWindowItems updatePacket = new WrapperPlayServerWindowItems(
                windowId,
                stateId,
                peItems,
                peCarriedItem
        );


        Integer lastId = latestStateIdMap.get(player.getUniqueId());
        if (lastId != null && stateId < lastId) {
            return;
        }

        PacketEvents.getAPI().getPlayerManager().sendPacketSilently(player, updatePacket);
    }
    private void translateAndSendUpdate(Player player, int windowId, int stateId, List<ItemStack> items, ItemStack carriedItem, String locale) {
      int topInventorySize = items.size() - 36;

        List<ItemStack> batchToTranslate = new ArrayList<>();


        //TODO: WEITERMACHEN; BATCHING NOCH BESSER MACHEN; MIT CHECKS OB GLEICHER GEFILTERTER TEXT MEHRMALS IN QUEUE IST
        //         WIE IN INTERNE API EIN BATCHING SYSTEM VIA PLUGINMESSAGE CHANNEL
        //          AUF PACKET RATELIMITING ACHTEN, AMBESTEN 1 BATCH = 1 INVENTAR
        //             UND KP OB MAN IN NACHINEIN WENN MAN AUF MEHRERE BATCHES EIN INVENTAR VERTEILT ES UPDATEN KANN
        
        //TODO: BEIM ECONOMY SHOP; WERDEN MANCHE SHOPITEMS SPÄTER NACHGELADEN, HEI?T SET_SLOT WEITER MACHEN
        //
       for (int i = 0; i < topInventorySize; i++) {
            ItemStack item = items.get(i);
            if (item != null && item.hasItemMeta() && (item.getItemMeta().displayName() != null || item.getItemMeta().lore() != null)) {

                batchToTranslate.add(item);
            } else {

                batchToTranslate.add(null);
            }
        }


        batchToTranslate.add(carriedItem);


        inventoryModule.translateInventoryBatch(batchToTranslate, locale).thenAccept(translatedBatch -> {
            if (!player.isOnline()) return;



            ItemStack translatedCarried = translatedBatch.get(translatedBatch.size() - 1);
            List<ItemStack> translatedItems = new ArrayList<>();




            for (int i = 0; i < topInventorySize; i++) {
                ItemStack original = items.get(i);
                ItemStack translated = translatedBatch.get(i);

                translatedItems.add(translated != null ? translated : original);
            }
            Integer lastId = latestStateIdMap.get(player.getUniqueId());
            if (lastId != null && stateId < lastId) {
                activeTranslations.remove(player.getUniqueId() + ":" + windowId + ":" + stateId);
                return;
            }
            sendUpdateToClient(player, windowId, stateId, translatedItems, translatedCarried);
            activeTranslations.remove(player.getUniqueId() + ":" + windowId + ":" + stateId);
        });
    }


}

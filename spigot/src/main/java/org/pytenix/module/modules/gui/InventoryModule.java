package org.pytenix.module.modules.gui;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.pytenix.SpigotTranslator;
import org.pytenix.module.TranslatorModule;
import org.pytenix.module.modules.gui.listener.PacketListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class InventoryModule extends TranslatorModule {


    LegacyComponentSerializer legacyComponentSerializer;

    public InventoryModule(SpigotTranslator spigotTranslator) {
        super(spigotTranslator,"gui");

        this.legacyComponentSerializer = this.getSpigotTranslator().getLegacyComponentSerializer();

        PacketEvents.getAPI().getEventManager().registerListener(new PacketListener(this),
                PacketListenerPriority.NORMAL);
    }






    public CompletableFuture<ItemStack> translateItem(ItemStack item, String targetLanguage) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta() || item.getItemMeta() == null) {
            return CompletableFuture.completedFuture(item);
        }


        ItemStack clonedItem = item.clone();
        ItemMeta meta = clonedItem.getItemMeta();

        List<CompletableFuture<?>> allFutures = new ArrayList<>();

        final CompletableFuture<String> nameFuture = meta.hasDisplayName()
                ? translate(legacyComponentSerializer.serialize(meta.displayName()), targetLanguage)
                : CompletableFuture.completedFuture(null);

        if (meta.hasDisplayName()) {
            allFutures.add(nameFuture);
        }

        List<CompletableFuture<String>> loreFutures = new ArrayList<>();

        if (meta.hasLore() && meta.lore() != null) {
            StringBuilder currentBlock = new StringBuilder();

            for (Component component : meta.lore()) {
                String serialized = legacyComponentSerializer.serialize(component);

                if (serialized.trim().isEmpty() || serialized.matches("^§[0-9a-fk-or]+$")) {

                    if (!currentBlock.isEmpty()) {
                        CompletableFuture<String> blockFuture = translate(currentBlock.toString(), targetLanguage);
                        loreFutures.add(blockFuture);
                        allFutures.add(blockFuture);
                        currentBlock.setLength(0);
                    }

                    loreFutures.add(CompletableFuture.completedFuture(serialized));

                } else {

                    if (!currentBlock.isEmpty()) {
                        currentBlock.append("\n");
                    }
                    currentBlock.append(serialized);
                }
            }


            if (!currentBlock.isEmpty()) {
                CompletableFuture<String> lastBlockFuture = translate(currentBlock.toString(), targetLanguage);
                loreFutures.add(lastBlockFuture);
                allFutures.add(lastBlockFuture);
            }
        }


            //String a = meta.lore().stream().map(legacyComponentSerializer::serialize).collect(Collectors.joining("\n"));
         //   CompletableFuture<String> future = translate(a,targetLanguage);
           // loreFutures = future;
          //  allFutures.add(future);




        return CompletableFuture.allOf(allFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    if (meta.hasDisplayName()) {
                        String translatedName = nameFuture.join();
                        if (translatedName != null) {
                            meta.displayName(legacyComponentSerializer.deserialize(translatedName));
                        }
                    }


                    if (!loreFutures.isEmpty()) {
                        List<Component> newLore = new ArrayList<>();

                        for (CompletableFuture<String> future : loreFutures) {
                            String result = future.join();

                            for (String line : result.split("\n")) {
                                newLore.add(legacyComponentSerializer.deserialize(line));
                            }
                        }
                        meta.lore(newLore);
                    }

                    clonedItem.setItemMeta(meta);
                    return clonedItem;
                });
    }
    public CompletableFuture<List<ItemStack>> translateInventoryBatch(List<ItemStack> items, String targetLanguage) {
        if (items == null || items.isEmpty()) {
            return CompletableFuture.completedFuture(items);
        }


        List<CompletableFuture<ItemStack>> itemFutures = items.stream()
                .map(item -> translateItem( item, targetLanguage))
                .toList();


        return CompletableFuture.allOf(itemFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> itemFutures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList())
                );
    }


}

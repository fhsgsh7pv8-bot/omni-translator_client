package org.pytenix.module.modules.player;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.pytenix.SpigotTranslator;
import org.pytenix.util.TaskScheduler;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AsyncPlayerChatListener implements Listener {


    final LiveChatModule liveChatModule;
    final SpigotTranslator spigotTranslator;
    final TaskScheduler taskScheduler;

    public AsyncPlayerChatListener(LiveChatModule liveChatModule) {
        this.liveChatModule = liveChatModule;
        this.spigotTranslator = liveChatModule.getSpigotTranslator();
        this.taskScheduler = spigotTranslator.getTaskScheduler();
        Bukkit.getPluginManager().registerEvents(this, spigotTranslator);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        if (!liveChatModule.isActive()) return;

        Player sender = event.getPlayer();
        Component originalMessage = event.message();

        String rawJson = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(event.originalMessage());
        String rawJson2 = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(event.message());
        System.out.println("ORIGINAL MESSAGE: " + rawJson);
        System.out.println("MESSAGE: " + rawJson2);

        String senderLang = sender.getLocale().split("_")[0].toLowerCase();

        Map<String, List<Player>> languageGroups = new HashMap<>();

        for (Audience audience : event.viewers()) {
            if (audience instanceof Player p && !p.getUniqueId().equals(sender.getUniqueId())) {

                String targetLang = p.getLocale().split("_")[0].toLowerCase();

                // 2. DER KOSTEN-KILLER: Gleiche Sprache? -> Direkt senden, kein API Call!
                //HIER AMBESTEN NICHT AUF DEFAULTLANGUAGE VERLASSEN!!
                if (targetLang.equals(senderLang)) {
                    Component rendered = event.renderer().render(sender, sender.displayName(), originalMessage, p);
                    liveChatModule.sendSystemMessage(p, rendered);
                    continue;
                }

                languageGroups.computeIfAbsent(targetLang, k -> new ArrayList<>()).add(p);
            }
        }

        event.viewers().clear();
        event.viewers().add(sender);

        if (languageGroups.isEmpty()) return;

        languageGroups.forEach((targetLang, groupMembers) -> {

            spigotTranslator.getTextComponentUtil().translateComplexMessage(originalMessage, targetLang, liveChatModule.getModuleName())
                    .orTimeout(5, TimeUnit.SECONDS)
                    .whenComplete((translatedText, ex) -> {

                        Component finalText = (ex == null && translatedText != null) ? translatedText : originalMessage;

                        for (Player recipient : groupMembers) {
                            if (!recipient.isOnline()) continue;

                            Component finalRendered = event.renderer().render(sender, sender.displayName(), finalText, recipient);
                            liveChatModule.sendSystemMessage(recipient, finalRendered);
                        }
                    });
        });
    }



    private Component replaceContent(Component base, String original, Component translated) {

        if (translated == null) return base;

        try {
            return base.replaceText(config -> {
                config.matchLiteral(original);
                config.replacement(translated);
                config.once();
            });
        } catch (Exception e) {

            return base.append(translated);
        }
    }
         /*
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {


        if(!liveChatModule.isActive())
            return;

        ChatRendererCache.set(event.getPlayer().getUniqueId(), event.renderer());

         */
/*

if(!liveChatModule.isActive())
            return;
        Player sender = event.getPlayer();

        Set<Player> actualRecipients = Bukkit.getOnlinePlayers().stream()
                .filter(p -> event.viewers().contains(p))
                .filter(p -> !p.getUniqueId().equals(sender.getUniqueId()))
                .collect(Collectors.toSet());

        event.setCancelled(true);

        Component originalMessage = event.message();
        String rawText = spigotTranslator.getLegacyComponentSerializer().serialize(originalMessage);
        ChatRenderer currentRenderer = event.renderer();



        liveChatModule.deliverToOne(sender, currentRenderer.render(sender, sender.displayName(), originalMessage, sender));

        if (actualRecipients.isEmpty()) return;

        Map<String, List<Player>> languageGroups = actualRecipients.stream()
                .collect(Collectors.groupingBy(Player::getLocale));


        languageGroups.forEach((locale, recipients) -> liveChatModule.translate(rawText, locale)
                .orTimeout(5, TimeUnit.SECONDS)
                .handle((translatedText, ex) -> {
                     if (ex != null || translatedText == null) {
                         liveChatModule.deliver(sender, originalMessage, originalMessage, recipients, currentRenderer);
                    } else {

                         Component translated = spigotTranslator.getLegacyComponentSerializer().deserialize(translatedText);

                         liveChatModule.deliver(sender, originalMessage, translated, recipients, currentRenderer);
                    }

                    return null;
                }));


    }
 */

}

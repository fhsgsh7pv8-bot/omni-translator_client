package org.pytenix.module.modules.player;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.pytenix.SpigotTranslator;
import org.pytenix.util.TaskScheduler;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        String rawText = PlainTextComponentSerializer.plainText().serialize(originalMessage);

        Set<Player> recipients = new HashSet<>();
        for (Object audience : event.viewers()) {
            if (audience instanceof Player p && !((Player) audience).getUniqueId().equals(sender.getUniqueId())) {
                recipients.add(p);
            }
        }


        event.viewers().clear();
        event.viewers().add(sender);


        Map<String, List<Player>> languageGroups = recipients.stream()
                .collect(Collectors.groupingBy((p) -> p.getLocale().toLowerCase()));


        languageGroups.forEach((targetLang, groupMembers) -> {


            liveChatModule.translate(rawText, targetLang)
                    .orTimeout(5, TimeUnit.SECONDS)
                    .handle((translatedText, ex) -> {

                        String finalText = (ex == null && translatedText != null) ? translatedText : rawText;


                        for (Player recipient : groupMembers) {

                            taskScheduler.runForEntity(recipient, () -> {
                                if (!recipient.isOnline()) return;

                                Component rendered = event.renderer().render(sender, sender.displayName(), originalMessage, recipient);


                                Component finalMessage = replaceContent(rendered, rawText, finalText);


                                liveChatModule.sendSystemMessage(recipient, finalMessage);
                            });
                        }
                        return null;
                    });
        });
    }


    private Component replaceContent(Component base, String original, String translated) {

        if (translated == null || translated.isEmpty()) return base;

        try {
            return base.replaceText(config -> {
                config.matchLiteral(original);
                config.replacement(translated);
                config.once();
            });
        } catch (Exception e) {

            return base.append(Component.text(" (" + translated + ")"));
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

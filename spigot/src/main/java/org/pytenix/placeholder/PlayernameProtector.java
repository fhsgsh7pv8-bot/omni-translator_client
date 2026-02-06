package org.pytenix.placeholder;

import io.papermc.paper.event.player.PlayerNameEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.conversations.PlayerNamePrompt;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.pytenix.SpigotTranslator;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlayernameProtector implements Listener {


        private final Set<String> onlinePlayerNames = ConcurrentHashMap.newKeySet();


        private static final Pattern ULTIMATE_PATTERN = Pattern.compile("((?:[&§][0-9a-fk-or])*)([a-zA-Z0-9_]+)");




    final SpigotTranslator spigotTranslator;

    public PlayernameProtector(SpigotTranslator spigotTranslator)
    {



        this.spigotTranslator = spigotTranslator;

        Bukkit.getPluginManager().registerEvents(this,spigotTranslator);
    }


@EventHandler
public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        addPlayer(event.getPlayer().getName());

}
    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        removePlayer(event.getPlayer().getName());

    }


        public void addPlayer(String name) {
            if (name != null) {
                onlinePlayerNames.add(name.toLowerCase());
            }
        }

        public void removePlayer(String name) {
            if (name != null) {
                onlinePlayerNames.remove(name.toLowerCase());
            }
        }


        public ProtectionResult maskNames(String text) {
            if (text == null || text.isBlank() || onlinePlayerNames.isEmpty()) {
                return new ProtectionResult(text, Collections.emptyMap());
            }

            StringBuilder sb = new StringBuilder();

            Matcher matcher = ULTIMATE_PATTERN.matcher(text);

            Map<String, String> replacements = new HashMap<>();
            int counter = 0;

            while (matcher.find()) {
                String colorPrefix = matcher.group(1);
                String potentialName = matcher.group(2);


                if (onlinePlayerNames.contains(potentialName.toLowerCase())) {

                    String placeholder = "{P" + counter + "}";
                    replacements.put(placeholder, potentialName);


                    String replacement = (colorPrefix != null ? colorPrefix : "") + placeholder;

                    matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                    counter++;
                } else {

                    matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
                }
            }
            matcher.appendTail(sb);

            return new ProtectionResult(sb.toString(), replacements);
        }


        public String restoreNames(String translatedText, Map<String, String> replacements) {
            if (replacements.isEmpty()) return translatedText;

            String result = translatedText;

            for (Map.Entry<String, String> entry : replacements.entrySet()) {
               result = result.replace(entry.getKey(), entry.getValue());
            }
            return result;
        }


        public record ProtectionResult(String maskedText, Map<String, String> replacements) {}
    }


package org.pytenix.placeholder;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import lombok.Getter;
import org.pytenix.SpigotTranslator;
import org.pytenix.placeholder.impl.ExtendedPlaceholder;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PlaceholderService {

    final SpigotTranslator spigotTranslator;

    public TreeMap<Integer, ExtendedPlaceholder> registeredPlaceholders;

    private Pattern atomicPattern;
    private List<ExtendedPlaceholder> indexedPlaceholders;


    //NOTE: KI GENERATED/IMPROVED
    public final Pattern PRICE_PATTERN = Pattern.compile("\\d+(?:[.,]\\d+)*");
    public final Pattern SYSTEM_PROTECTION_PATTERN = Pattern.compile("(?:\\{[a-zA-Z]\\d+\\})|(?:\\[#[A-Z]+-\\d+#\\])");

    @Getter
    final PlayernameProtector playernameProtector;
    Cache<UUID, PlayernameProtector.ProtectionResult> cachedNames = CacheBuilder.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS).build();

    public final Pattern COLOR_PATTERN = Pattern.compile("(?:§x(?:§[0-9a-fA-F]){6})|(?:§[0-9a-fA-Fk-orK-OR])");
    public PlaceholderService(SpigotTranslator spigotTranslator) {
        this.registeredPlaceholders = new TreeMap<>();
        this.spigotTranslator = spigotTranslator;
        this.playernameProtector = new PlayernameProtector(spigotTranslator);

        registerPlaceholder(0, new ExtendedPlaceholder("SKIP", () -> SYSTEM_PROTECTION_PATTERN));
        registerPlaceholder(1, new ExtendedPlaceholder("C", () -> COLOR_PATTERN));
        registerPlaceholder(10, new ExtendedPlaceholder("N", () -> PRICE_PATTERN));
    }

    public boolean registerPlaceholder(int priority, ExtendedPlaceholder placeholder) {
        if (registeredPlaceholders.containsKey(priority)) {
            System.out.println("Could not register Placeholder: " + placeholder.getClass().getSimpleName() + " Priority already registered!");
            return false;
        }
        registeredPlaceholders.put(priority, placeholder);

        rebuildAtomicPattern();
        return true;
    }


    public void rebuildAtomicPattern() {
        this.indexedPlaceholders = new ArrayList<>(registeredPlaceholders.values());

        String combinedRegex = indexedPlaceholders.stream()
                .filter(ph -> ph.getPattern() != null)

                .map(ph -> "(" + ph.getPattern().pattern() + ")")
                .collect(Collectors.joining("|"));

        if (!combinedRegex.isEmpty()) {
            this.atomicPattern = Pattern.compile(combinedRegex);
        } else {
            this.atomicPattern = null;
        }
    }


    public String toPlaceholders(UUID id, String text) {
        if (atomicPattern == null || text == null || text.isEmpty()) return text;



        PlayernameProtector.ProtectionResult result = playernameProtector.maskNames(text);
        if (!result.replacements().isEmpty()) {
            cachedNames.put(id, result);
            text = result.maskedText();
        }

        StringBuilder sb = new StringBuilder();
        Matcher matcher = atomicPattern.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            sb.append(text, lastEnd, matcher.start());

            boolean matched = false;

            for (int i = 0; i < indexedPlaceholders.size(); i++) {

                if (i + 1 <= matcher.groupCount() && matcher.group(i + 1) != null) {
                    ExtendedPlaceholder ph = indexedPlaceholders.get(i);
                    String originalValue = matcher.group(i + 1);

                    if (ph.placeholder().equals("SKIP")) {
                        sb.append(originalValue);
                        matched = true;
                        break;
                    }
                    // -----------------------------

                    Map<Integer, String> playerCache = ph.cachedValues().asMap()
                            .computeIfAbsent(id, k -> new HashMap<>());

                    int contentId = playerCache.size();
                    playerCache.put(contentId, originalValue);

                    sb.append("{").append(ph.placeholder()).append(contentId).append("}");
                    matched = true;
                    break;
                }
            }

            if (!matched) {
                sb.append(matcher.group());
            }
            lastEnd = matcher.end();
        }
        sb.append(text.substring(lastEnd));

        return sb.toString();
    }

    public String fromPlaceholders(UUID id, String text) {
        List<ExtendedPlaceholder> reverseList = new ArrayList<>(registeredPlaceholders.values());
        Collections.reverse(reverseList);

        for (ExtendedPlaceholder ph : reverseList) {
           if(ph.placeholder().equals("SKIP")) continue;

            Map<Integer, String> cache = ph.cachedValues().getIfPresent(id);
            if (cache == null || cache.isEmpty()) continue;

            for (Map.Entry<Integer, String> entry : cache.entrySet()) {

                String token = "{" + ph.placeholder() + entry.getKey() + "}";
                text = text.replace(token, entry.getValue());
            }
        }


        if (cachedNames.getIfPresent(id) != null) {
            text = playernameProtector.restoreNames(text, cachedNames.getIfPresent(id).replacements());
            cachedNames.invalidate(id);
        }

        return text;
    }
}
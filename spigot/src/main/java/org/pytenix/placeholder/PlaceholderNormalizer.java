package org.pytenix.placeholder;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.pytenix.gradient.GradientService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaceholderNormalizer {
    private final Pattern CODE_PATTERN = Pattern.compile("(?:\\{C\\d+\\})+");
    private final Pattern pattern = Pattern.compile("\\{C\\d+\\}");

    Cache<UUID, NormalizationResult> cachedNormalized = CacheBuilder.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS).build();


    public record NormalizationResult(String cleanedText, Map<String, String> mappings) {}



    public String normalizeText(UUID uuid, String text)
    {

        NormalizationResult normalizationResult = normalize(text);
        cachedNormalized.put(uuid, normalizationResult);
        return normalizationResult.cleanedText();


    }

    public String denormalizeText(UUID uuid, String text)
    {
        if(cachedNormalized.getIfPresent(uuid) == null)
            return null;

        return denormalize(text,cachedNormalized.getIfPresent(uuid).mappings);

    }
    private String denormalize(String normalizedText, Map<String, String> mappings) {
        if (normalizedText == null || mappings == null || mappings.isEmpty()) {
            return normalizedText;
        }


        Matcher matcher = pattern.matcher(normalizedText);

        StringBuilder sb = new StringBuilder(normalizedText.length() + 50);

        while (matcher.find()) {
            String placeholder = matcher.group();


            String originalSequence = mappings.get(placeholder);

            if (originalSequence != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(originalSequence));
            } else {
                matcher.appendReplacement(sb, placeholder);
            }
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    private NormalizationResult normalize(String text) {
        if (text == null || !text.contains("{C")) {
            return new NormalizationResult(text, Collections.emptyMap());
        }

        Matcher matcher = CODE_PATTERN.matcher(text);

        if (!matcher.find()) {
            return new NormalizationResult(text, Collections.emptyMap());
        }

        Map<String, String> translationMap = new HashMap<>();
        StringBuilder sb = new StringBuilder(text.length());
        int counter = 0;

        matcher.reset();

        while (matcher.find()) {
            String originalGroup = matcher.group();

            String newPlaceholder = "{C" + counter + "}";

            translationMap.put(newPlaceholder, originalGroup);
            matcher.appendReplacement(sb, newPlaceholder);

            counter++;
        }
        matcher.appendTail(sb);

        return new NormalizationResult(sb.toString(), translationMap);
    }


}

package org.pytenix.placeholder.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.pytenix.placeholder.BasePlaceholder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExtendedPlaceholder implements BasePlaceholder {



    Cache<UUID, Map<Integer, String>> cachedValues = CacheBuilder.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS).build();


    String placeHolderKey;
    Supplier<Pattern> patternSupplier;

    public ExtendedPlaceholder(String placeholderKey, Supplier<Pattern> patternSupplier)
    {
        this.placeHolderKey = placeholderKey;
        this.patternSupplier = patternSupplier;
    }


    public String placeholder() {
        return placeHolderKey;
    }

    public Cache<UUID, Map<Integer, String>> cachedValues() {
        return cachedValues;
    }


    /*
    @Override
    public String toPlaceholder(UUID id, String text) {
        StringBuilder sb = new StringBuilder();

        {
            Matcher matcher = patternSupplier.get().matcher(text);
            HashMap<Integer, String> foundNames = new HashMap<>();


            int counter = 0;
            int lastEnd = 0;

            while (matcher.find()) {

                sb.append(text, lastEnd, matcher.start());

                String name = matcher.group();
                foundNames.put(counter, name);
                sb.append("{").append(placeholder()).append("-").append(counter).append("}");

                lastEnd = matcher.end();
                counter++;
            }
            sb.append(text.substring(lastEnd));
            cachedValues().put(id, foundNames);
        }

        return sb.toString();
    }

    @Override
    public String fromPlaceholder(UUID id, String text) {
        Map<Integer, String> values = cachedValues().getIfPresent(id);

        if (values != null && !values.isEmpty()) {
            for (Map.Entry<Integer, String> integerStringEntry : values.entrySet()) {
                text = text.replace("{"+placeholder()+"-" + integerStringEntry.getKey() + "}", integerStringEntry.getValue());
            }
        }

        return text;
    }

     */

    @Override
    public Pattern getPattern() {
        return patternSupplier.get();
    }
}

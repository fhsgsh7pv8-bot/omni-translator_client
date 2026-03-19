package org.pytenix.util;


import com.google.gson.Gson;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.pytenix.TranslatorService;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class TextComponentUtil {


    private final TranslatorService translatorService;
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacySection();

    public TextComponentUtil(TranslatorService translatorService) {
        this.translatorService = translatorService;
    }

    private static class TranslationContext {
        int clickIndex = 0;
        int hoverIndex = 0;
        final Map<Integer, ClickEvent> clicks = new HashMap<>();
        final Map<Integer, Component> hovers = new HashMap<>();
    }

    public CompletableFuture<Component> translateComplexMessage(Component originalComponent, String lang, String module) {
        long started = System.currentTimeMillis();
        TranslationContext ctx = new TranslationContext();

        Component taggedComponent = injectTags(originalComponent, ctx);

        String mainPayload = legacySerializer.serialize(taggedComponent);

        UUID mainBatchId = UUID.randomUUID();
        Map<Integer, CompletableFuture<String>> hoverFutures = new HashMap<>();

        for (Map.Entry<Integer, Component> entry : ctx.hovers.entrySet()) {
            int id = entry.getKey();
            UUID hoverBatchId = UUID.randomUUID();

            String legacyHover = legacySerializer.serialize(entry.getValue());
            String preparedHover = translatorService.preparePayload(hoverBatchId, legacyHover);

            hoverFutures.put(id, translatorService.processAndRestore(hoverBatchId, preparedHover, lang, module, started));
        }

        String preparedMain = translatorService.preparePayload(mainBatchId, mainPayload);
        CompletableFuture<String> mainFuture = translatorService.processAndRestore(mainBatchId, preparedMain, lang, module, started);

        return mainFuture.thenCombineAsync(
                CompletableFuture.allOf(hoverFutures.values().toArray(new CompletableFuture[0])),
                (translatedMainText, v) -> {

                    Component finalComponent = legacySerializer.deserialize(translatedMainText);

                    finalComponent = finalComponent.replaceText(TextReplacementConfig.builder()
                            .match("<H(\\d+)>(.*?)</H\\1>")
                            .replacement((matchResult, builder) -> {
                                int id = Integer.parseInt(matchResult.group(1));
                                String translatedWord = matchResult.group(2);

                                // Den übersetzten Hover-Text holen und deserialisieren
                                String translatedHoverText = hoverFutures.get(id).join();
                                Component hoverComp = legacySerializer.deserialize(translatedHoverText);

                                return legacySerializer.deserialize(translatedWord)
                                        .hoverEvent(HoverEvent.showText(hoverComp));
                            })
                            .build());

                    finalComponent = finalComponent.replaceText(TextReplacementConfig.builder()
                            .match("<A(\\d+)>(.*?)</A\\1>")
                            .replacement((matchResult, builder) -> {
                                int id = Integer.parseInt(matchResult.group(1));
                                String translatedWord = matchResult.group(2);
                                ClickEvent originalClick = ctx.clicks.get(id);

                                return legacySerializer.deserialize(translatedWord)
                                        .clickEvent(originalClick);
                            })
                            .build());

                    return finalComponent;
                });
    }

    private Component injectTags(Component c, TranslationContext ctx) {
        List<Component> newChildren = new ArrayList<>();
        for (Component child : c.children()) {
            newChildren.add(injectTags(child, ctx));
        }

        Component modified = c.children(newChildren);

        if (modified instanceof TextComponent tc) {
            ClickEvent click = tc.clickEvent();
            HoverEvent<?> hover = tc.hoverEvent();

            if (click != null || hover != null) {
                String content = tc.content();
                if (!content.isEmpty()) {
                    if (click != null) {
                        int id = ctx.clickIndex++;
                        ctx.clicks.put(id, click);
                        content = "<A" + id + ">" + content + "</A" + id + ">";
                    }
                    if (hover != null) {
                        int id = ctx.hoverIndex++;
                        ctx.hovers.put(id, (Component) hover.value());
                        content = "<H" + id + ">" + content + "</H" + id + ">";
                    }
                    modified = tc.content(content).clickEvent(null).hoverEvent(null);
                }
            }
        }
        return modified;
    }
}

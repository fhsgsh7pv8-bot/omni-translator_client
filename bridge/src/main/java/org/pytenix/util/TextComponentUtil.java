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


    public record FoundEvent(String text, ClickEvent click, HoverEvent<?> hover) {}

    final TranslatorService translatorService;

    public TextComponentUtil(TranslatorService translatorService)
    {
        this.translatorService = translatorService;
    }

    public void printAllEvents(Component component, String realMessage) {


        var mm = MiniMessage.miniMessage();


            int a = 0;
            int b = 0;

        HashMap<Integer,String> toTranslate = new HashMap<>();

        for (FoundEvent allEvent : getAllEvents(component)) {



            if(allEvent.click() != null)
            {
                realMessage = realMessage.replace(allEvent.text, "<A"+a+">"+ allEvent.text+"</A"+a+">");
                System.out.println("TEXT: " + allEvent.text() + " | " + allEvent.click.value() + " | " + allEvent.click.action().name() + " | " + allEvent.click.examinableName());
                a++;
            }
            else if(allEvent.hover != null){
                Component hoverComp = (Component) allEvent.hover().value();

                System.out.println("TEXT: " + allEvent.text() + " | " +  LegacyComponentSerializer.legacySection().serialize(hoverComp) + " | " + allEvent.hover.examinableName());
                toTranslate.put(b, LegacyComponentSerializer.legacySection().serialize(hoverComp));
                realMessage = realMessage.replace(allEvent.text, "<H"+b+">"+ allEvent.text+"</H"+b+">");
                b++;
            }


        }

        System.out.println("Edited: " + realMessage);
        toTranslate.forEach((integer, s) ->
        {
            System.out.println("TAG: " + integer + " TEXT: " + s);
        });

    }

    public CompletableFuture<Component> translateComplexMessage(Component originalComponent, String realMessage, String lang, String module) {

        long started = System.currentTimeMillis();

        UUID mainBatchId = UUID.randomUUID();
        String preparedMainMessage = realMessage;

        Map<Integer, String> hoversToTranslate = new HashMap<>();
        Map<Integer, UUID> hoverBatchIds = new HashMap<>();
        Map<Integer, ClickEvent> clickEventsOriginal = new HashMap<>();

        int hIndex = 0;
        int aIndex = 0;

        for (FoundEvent event : getAllEvents(originalComponent)) {
            if (event.click() != null) {
                preparedMainMessage = preparedMainMessage.replaceFirst(Pattern.quote(event.text()), "<A" + aIndex + ">" + event.text() + "</A" + aIndex + ">");
                clickEventsOriginal.put(aIndex, event.click());
                aIndex++;
            } else if (event.hover() != null) {
                Component hoverComp = (Component) event.hover().value();
                String legacyHover = LegacyComponentSerializer.legacySection().serialize(hoverComp);

                UUID hoverBatchId = UUID.randomUUID();
                String preparedHover = translatorService.preparePayload(hoverBatchId, legacyHover);

                hoversToTranslate.put(hIndex, preparedHover);
                hoverBatchIds.put(hIndex, hoverBatchId);

                preparedMainMessage = preparedMainMessage.replaceFirst(Pattern.quote(event.text()), "<H" + hIndex + ">" + event.text() + "</H" + hIndex + ">");
                hIndex++;
            }
        }

        preparedMainMessage = translatorService.preparePayload(mainBatchId, preparedMainMessage);
        CompletableFuture<String> mainTranslationFuture = translatorService.processAndRestore(mainBatchId, preparedMainMessage, lang, module, started);

        Map<Integer, CompletableFuture<String>> hoverFutures = new HashMap<>();
        for (Map.Entry<Integer, String> entry : hoversToTranslate.entrySet()) {
            int id = entry.getKey();
            hoverFutures.put(id, translatorService.processAndRestore(hoverBatchIds.get(id), entry.getValue(), lang, module, started));
        }

        return mainTranslationFuture.thenCombineAsync(CompletableFuture.allOf(hoverFutures.values().toArray(new CompletableFuture[0])), (translatedMainText, v) -> {

            Component finalComponent = LegacyComponentSerializer.legacySection().deserialize(translatedMainText);

            finalComponent = finalComponent.replaceText(TextReplacementConfig.builder()
                    .match("<H(\\d+)>(.*?)</H\\1>")
                    .replacement((matchResult, builder) -> {
                        int id = Integer.parseInt(matchResult.group(1));
                        String translatedWord = matchResult.group(2);

                        String translatedHoverText = hoverFutures.get(id).join();
                        Component hoverComp = LegacyComponentSerializer.legacySection().deserialize(translatedHoverText);

                        return LegacyComponentSerializer.legacySection().deserialize(translatedWord)
                                .hoverEvent(HoverEvent.showText(hoverComp));
                    })
                    .build());

            finalComponent = finalComponent.replaceText(TextReplacementConfig.builder()
                    .match("<A(\\d+)>(.*?)</A\\1>")
                    .replacement((matchResult, builder) -> {
                        int id = Integer.parseInt(matchResult.group(1));
                        String translatedWord = matchResult.group(2);
                        ClickEvent originalClick = clickEventsOriginal.get(id);

                        return LegacyComponentSerializer.legacySection().deserialize(translatedWord)
                                .clickEvent(originalClick);
                    })
                    .build());

            return finalComponent;
        });
    }

    public List<FoundEvent> getAllEvents(Component root) {
        List<FoundEvent> foundEvents = new ArrayList<>();
        searchRecursive(root, foundEvents);
        return foundEvents;
    }

    private void searchRecursive(Component node, List<FoundEvent> list) {
        ClickEvent click = node.clickEvent();
        HoverEvent<?> hover = node.hoverEvent();

        if (click != null || hover != null) {
            String content = "";
            if (node instanceof TextComponent tc) {
                content = tc.content();
            }
            list.add(new FoundEvent(content, click, hover));
        }

         for (Component child : node.children()) {
            searchRecursive(child, list);
        }
    }

}

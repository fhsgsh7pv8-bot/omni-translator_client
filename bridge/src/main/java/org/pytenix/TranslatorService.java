package org.pytenix;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.pytenix.placeholder.GradientService;
import org.pytenix.placeholder.PlaceholderService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public abstract class TranslatorService {



    final AdvancedTranslationBridge translationBridge;
    final GradientService gradientService;
    final PlaceholderService placeholderService;




    public TranslatorService(AdvancedTranslationBridge translationBridge) {


        this.translationBridge = translationBridge;
        this.gradientService = translationBridge.getGradientService();
        this.placeholderService = translationBridge.getPlaceholderService();


    }


    protected abstract CompletableFuture<String> process(UUID id, String text, String targetLang, String module);




    public CompletableFuture<String> translate(String text, String lang,String module) {

        final  long started = System.currentTimeMillis();
        if (text == null || text.isBlank())
            return CompletableFuture.completedFuture(text);


        UUID id = UUID.randomUUID();
        List<String> processedLines = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        List<UUID> lineUuids = new ArrayList<>();

        for (String line : lines) {
            UUID lineId = UUID.randomUUID();

            String cleanText = handleGradient(lineId, line);

            String maskedText = placeholderService.toPlaceholders(lineId, cleanText);

            processedLines.add(maskedText);
           lineUuids.add(lineId);
        }

        translationBridge.getCachedReferences().put(id, lineUuids);
        String finalPayload = String.join("\n", processedLines);

        return process(id, finalPayload, lang, module)
                .thenApplyAsync(s -> {
                    System.out.println("TRANSLATE " + s + " TOOK " + (System.currentTimeMillis()-started) + "ms");
                    return translationBridge.handlePlaceholders(id, s);
                });

    }

    public String handleGradient(UUID uuid, String text)
    {
        GradientService.ExtractionResult extractionResult = gradientService.stripAndAnalyze(text);
        if (extractionResult.info().isGradient()) {
            gradientService.cachedGradients.put(uuid, extractionResult.info());
            return extractionResult.cleanText();
        }
        return text;
    }

}

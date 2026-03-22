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




    public String preparePayload(UUID batchId, String text) {
        List<String> processedLines = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        List<UUID> lineUuids = new ArrayList<>();

        for (String line : lines) {
            UUID lineId = UUID.randomUUID();
            String cleanText = handleGradient(lineId, line); // Gradient raus!
            String maskedText = placeholderService.toPlaceholders(lineId, cleanText);
            processedLines.add(maskedText);
            lineUuids.add(lineId);
        }

        translationBridge.getCachedReferences().put(batchId, lineUuids);
        return String.join("\n", processedLines);
    }

    public CompletableFuture<String> processAndRestore(UUID batchId, String payload, String lang, String module, long started) {
        return process(batchId, payload, lang, module)
                .thenApplyAsync(s -> {
                     return translationBridge.handlePlaceholders(batchId, s); // Gradients & Farben zurück!
                });
    }

    // 3. Deine normale translate-Methode (bleibt unverändert für normale Chat-Nachrichten!)
    public CompletableFuture<String> translate(String text, String lang, String module) {
        if (text == null || text.isBlank()) return CompletableFuture.completedFuture(text);

        long started = System.currentTimeMillis();
        UUID batchId = UUID.randomUUID();

        String prepared = preparePayload(batchId, text);
        return processAndRestore(batchId, prepared, lang, module, started);
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

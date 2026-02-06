package org.pytenix;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.pytenix.brigde.SpigotBridge;
import org.pytenix.gradient.GradientService;
import org.pytenix.placeholder.PlaceholderService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class TranslatorService {



    final SpigotTranslator spigotTranslator;
    final SpigotBridge spigotBridge;
    final GradientService gradientService;
    final PlaceholderService placeholderService;


    public Cache<UUID, List<UUID>> cachedReferences = CacheBuilder.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS).build();

    public TranslatorService(SpigotTranslator spigotTranslator) {


        this.spigotTranslator = spigotTranslator;
        this.spigotBridge = spigotTranslator.getSpigotBridge();
        this.gradientService = spigotTranslator.getGradientService();
        this.placeholderService = spigotTranslator.getPlaceholderService();


    }




    public CompletableFuture<String> fakeTranslateAsync(String message, String lang,String module) {
        return translate(message, lang,module);
    }

    public CompletableFuture<String> translate(String text, String lang,String module) {



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

        cachedReferences.put(id, lineUuids);
        String finalPayload = String.join("\n", processedLines);

        return spigotBridge.translate(id, finalPayload, lang, module)
                .thenApplyAsync(s -> spigotBridge.handlePlaceholders(id, s));

    }

    public String handleGradient(UUID uuid, String text)
    {
        GradientService.ExtractionResult extractionResult = gradientService.stripAndAnalyze(text);
        if (extractionResult.info().isGradient()) {
            spigotTranslator.cachedGradients.put(uuid, extractionResult.info());
            return extractionResult.cleanText();
        }
        return text;
    }

}

package org.pytenix.module;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.pytenix.SpigotTranslator;
import org.pytenix.TranslatorService;
import org.pytenix.entity.ServerConfiguration;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class TranslatorModule {


    @Getter
    final SpigotTranslator spigotTranslator;

    @Getter
    final String moduleName;


    @Getter
    final TranslatorService translatorService;

    public TranslatorModule(SpigotTranslator spigotTranslator, String moduleName)
    {

        this.spigotTranslator = spigotTranslator;
        this.moduleName = moduleName;

        this.translatorService = spigotTranslator.getTranslatorService();
    }




    public boolean isActive()
    {
        return spigotTranslator.getSpigotBridge().getServerConfiguration().getModules().getOrDefault(moduleName,true);
    }

    public boolean checkIfNeed(UUID playerUUID)
    {

        if(getServerConfiguration() == null || getServerConfiguration().getDefaultLanguage() == null)
            return true;

        return !Bukkit.getPlayer(playerUUID).getLocale().startsWith(getServerConfiguration().getDefaultLanguage());
    }

    public ServerConfiguration getServerConfiguration()
    {
        return spigotTranslator.getSpigotBridge().getServerConfiguration();
    }


    public CompletableFuture<String> translate(String text, String locale)
    {
        String cached = spigotTranslator.getCaffeineCache().get(text, locale);

        if(cached != null)
            return CompletableFuture.completedFuture(cached);

        return translatorService.translate(text, locale, this.moduleName).whenComplete((result, throwable) -> {

            if (throwable == null && result != null) {
                spigotTranslator.getCaffeineCache().set(text, locale, result);
            }

        });
    }






}

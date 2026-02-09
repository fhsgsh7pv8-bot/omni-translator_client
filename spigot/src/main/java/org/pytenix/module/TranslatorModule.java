package org.pytenix.module;

import lombok.Getter;
import org.pytenix.SpigotTranslator;
import org.pytenix.TranslatorService;

import java.util.concurrent.CompletableFuture;

public abstract class TranslatorModule {


    @Getter
    final SpigotTranslator spigotTranslator;
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
        return spigotTranslator.getServerConfiguration().getModules().getOrDefault(moduleName,true);
    }



    public CompletableFuture<String> translate(String text, String locale)
    {
        return translatorService.translate(text, locale, this.moduleName);
    }






}

package org.pytenix.module;

import org.pytenix.SpigotTranslator;
import org.pytenix.module.modules.chat.PluginChatModule;
import org.pytenix.module.modules.gui.InventoryModule;
import org.pytenix.module.modules.hologram.HologramModule;
import org.pytenix.module.modules.player.LiveChatModule;

import java.util.ArrayList;
import java.util.List;

public class ModuleService {


    final SpigotTranslator spigotTranslator;

    final List<TranslatorModule> modules;

    public ModuleService(SpigotTranslator spigotTranslator)
    {
        this.spigotTranslator = spigotTranslator;
        this.modules = new ArrayList<>();

        registerModule(new InventoryModule(spigotTranslator));
        registerModule(new PluginChatModule(spigotTranslator));
        registerModule(new LiveChatModule(spigotTranslator));
        registerModule(new HologramModule(spigotTranslator));
    }




    public void registerModule(TranslatorModule translatorModule)
    {
        modules.add(translatorModule);
    }



}

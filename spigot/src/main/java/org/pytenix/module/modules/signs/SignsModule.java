package org.pytenix.module.modules.signs;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import org.pytenix.SpigotTranslator;
import org.pytenix.module.TranslatorModule;
import org.pytenix.module.modules.gui.listener.PacketListener;

public class SignsModule extends TranslatorModule {


    //NUR BEIM PLATZIEREN / UPDATEN WIRD ES ÜBERSETZT
    //BEIM ANSCHAUEN GEHT NICHT; MÜSSE MAN MAP_CHUNK AUSEINANDER NEHMEN (LASTIG)

    public SignsModule(SpigotTranslator spigotTranslator) {
        super(spigotTranslator, "signs");


        PacketEvents.getAPI().getEventManager().registerListener(new SignViewListener(this),
                PacketListenerPriority.NORMAL);

    }



}

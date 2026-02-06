package org.pytenix.module.modules.chat;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import lombok.Getter;
import org.pytenix.SpigotTranslator;
import org.pytenix.module.TranslatorModule;

@Getter
public class PluginChatModule extends TranslatorModule {

    MessageSequencer messageSequencer;

    public PluginChatModule(SpigotTranslator spigotTranslator) {
        super(spigotTranslator, "plugin_chat");

        this.messageSequencer = new MessageSequencer(this);

        PacketEvents.getAPI().getEventManager().registerListener(new SystemPacketListener(this),
                PacketListenerPriority.HIGHEST);

    }


}

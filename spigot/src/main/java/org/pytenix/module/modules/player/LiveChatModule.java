package org.pytenix.module.modules.player;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import io.papermc.paper.chat.ChatRenderer;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.checkerframework.checker.units.qual.C;
import org.pytenix.SpigotTranslator;
import org.pytenix.module.TranslatorModule;
import org.pytenix.module.modules.chat.MessageSequencer;
import org.pytenix.module.modules.chat.SystemPacketListener;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class LiveChatModule extends TranslatorModule {




    public LiveChatModule(SpigotTranslator spigotTranslator) {
        super(spigotTranslator, "live_chat");


        new AsyncPlayerChatListener(this);

        //   PacketEvents.getAPI().getEventManager().registerListener(new ChatPacketListener(this),
        //        PacketListenerPriority.HIGHEST);
    }




    public void sendSystemMessage(Player player, Component content) {

        WrapperPlayServerSystemChatMessage systemPacket = new WrapperPlayServerSystemChatMessage(
                false,
                content
        );

        PacketEvents.getAPI().getPlayerManager().sendPacketSilently(player, systemPacket);
    }

}

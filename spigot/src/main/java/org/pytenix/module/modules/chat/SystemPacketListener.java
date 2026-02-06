package org.pytenix.module.modules.chat;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.chat.message.ChatMessage;
import com.github.retrooper.packetevents.protocol.chat.message.ChatMessage_v1_19_1;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChatMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.pytenix.SpigotTranslator;

import java.util.UUID;

public class SystemPacketListener implements PacketListener {



    final PluginChatModule pluginChatModule;
    final MessageSequencer messageSequencer;

    public SystemPacketListener(PluginChatModule pluginChatModule)
    {
        this.pluginChatModule = pluginChatModule;
        this.messageSequencer = pluginChatModule.getMessageSequencer();

    }


    @Override
    public void onPacketSend(PacketSendEvent event) {

        if (event.getPacketType() == PacketType.Play.Server.SYSTEM_CHAT_MESSAGE) {
            if(event.isCancelled())
                return;

            if(!pluginChatModule.isActive())
                return;

            WrapperPlayServerSystemChatMessage packet = new WrapperPlayServerSystemChatMessage(event);
            Component messageComponent = packet.getMessage();
            boolean isOverlay = packet.isOverlay();

            Player player = org.bukkit.Bukkit.getPlayer(event.getUser().getUUID());
            if (player == null) return;



            String rawTextWithColors = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().serialize(messageComponent);


            event.setCancelled(true);


            final UUID uuid = player.getUniqueId();

            messageSequencer.translateWithOrder(uuid,rawTextWithColors,player.getLocale(),isOverlay);



        }
    }

}
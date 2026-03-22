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
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.pytenix.SpigotTranslator;
import org.pytenix.util.TextComponentUtil;

import java.util.UUID;

public class SystemPacketListener implements PacketListener {



    final PluginChatModule pluginChatModule;
    final MessageSequencer messageSequencer;

    final TextComponentUtil textComponentUtil;

    public SystemPacketListener(PluginChatModule pluginChatModule)
    {
        this.pluginChatModule = pluginChatModule;
        this.messageSequencer = pluginChatModule.getMessageSequencer();
        this.textComponentUtil = new TextComponentUtil(pluginChatModule.getTranslatorService());

    }




    @Override
    public void onPacketSend(PacketSendEvent event) {

        if (event.getPacketType() == PacketType.Play.Server.SYSTEM_CHAT_MESSAGE) {
            if(event.isCancelled())
                return;

            if(!pluginChatModule.isActive())
                return;

            Player player = org.bukkit.Bukkit.getPlayer(event.getUser().getUUID());

            if (player == null)
                return;

            final UUID uuid = player.getUniqueId();

            //TODO: WAS IS MIT MSG NACHRICHTEN??
            if(!pluginChatModule.checkIfNeed(uuid))
                return;

            WrapperPlayServerSystemChatMessage packet = new WrapperPlayServerSystemChatMessage(event);
            Component messageComponent = packet.getMessage();
            boolean isOverlay = packet.isOverlay();







            //String rawTextWithColors = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
         //           .legacySection().serialize(messageComponent);


            event.setCancelled(true);
            messageSequencer.translateWithOrder(uuid,messageComponent,LegacyComponentSerializer.legacySection().serialize(messageComponent) ,player.getLocale(),isOverlay);



        }
    }

}
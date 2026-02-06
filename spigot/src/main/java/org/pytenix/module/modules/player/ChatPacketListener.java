package org.pytenix.module.modules.player;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.chat.message.*;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChatMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import com.google.gson.Gson;
import io.papermc.paper.chat.ChatRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

public class ChatPacketListener implements PacketListener {


    final LiveChatModule liveChatModule;

    public ChatPacketListener(LiveChatModule liveChatModule)
    {
        this.liveChatModule = liveChatModule;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {

        if(!liveChatModule.isActive())
            return;

        /*
        if (event.getPacketType() == PacketType.Play.Server.CHAT_MESSAGE) {
            WrapperPlayServerChatMessage chatWrapper = new WrapperPlayServerChatMessage(event);
            ChatMessage message = chatWrapper.getMessage();

            final UUID senderUUID = MessageVersion.getSenderUuid(message);
            Component originalRawContent = message.getChatContent();

            if (senderUUID == null || originalRawContent == null) return;
            if (senderUUID.equals(event.getUser().getUUID())) return;




            Player receiver = Bukkit.getPlayer(event.getUser().getUUID());
            Player sender = Bukkit.getPlayer(senderUUID);

            if (sender == null || receiver == null) return;

            ChatRenderer renderer = ChatRendererCache.get(senderUUID);

            if (renderer == null) renderer = ChatRenderer.defaultRenderer();


            String plainText = liveChatModule.getSpigotTranslator().getLegacyComponentSerializer().serialize(originalRawContent);
            String targetLang = receiver.getLocale();


            ChatRenderer finalRenderer = renderer;


            event.setCancelled(true);


            liveChatModule.translate(plainText, targetLang)
                    .orTimeout(5, TimeUnit.SECONDS)
                    .handle((translatedText, ex) -> {

                        Component renderedMessage = finalRenderer.render(
                                sender,
                                sender.displayName(),
                                originalRawContent,
                                receiver
                        );

                        if (translatedText != null && ex == null) {

                            var serializer = liveChatModule.getSpigotTranslator().getLegacyComponentSerializer();


                            String fullChatString = serializer.serialize(renderedMessage);

                            String searchString = serializer.serialize(originalRawContent);

                            int lastIndex = fullChatString.lastIndexOf(searchString);

                            if (lastIndex != -1) {

                                String before = fullChatString.substring(0, lastIndex);
                                String after = fullChatString.substring(lastIndex + searchString.length());

                                String newChatString = before + translatedText + after;

                                renderedMessage = serializer.deserialize(newChatString);
                            } else {

                                // System.out.println("Could not match original text in renderer output!");
                            }
                        }


                        sendSystemMessage(receiver, renderedMessage);

                        return null;
                    });

        }

         */
    }


    public void sendSystemMessage(Player player, Component content) {

        WrapperPlayServerSystemChatMessage systemPacket = new WrapperPlayServerSystemChatMessage(
                false,
                content
        );

        PacketEvents.getAPI().getPlayerManager().sendPacketSilently(player, systemPacket);
    }

    public enum MessageVersion {


        A(ChatMessage_v1_16.class, ChatMessage_v1_16::getSenderUUID),
        B(ChatMessage_v1_19.class, ChatMessage_v1_19::getSenderUUID),
        C(ChatMessage_v1_19_1.class, ChatMessage_v1_19_1::getSenderUUID),
        D(ChatMessage_v1_19_3.class, ChatMessage_v1_19_3::getSenderUUID),
        E(ChatMessage_v1_21_5.class, ChatMessage_v1_21_5::getSenderUUID);


        final Class<?> clazz;

       final Function<Object, UUID> uuidExtractor;

        <T> MessageVersion(Class<T> clazz, Function<T, UUID> specificExtractor) {
            this.clazz = clazz;

            this.uuidExtractor = obj -> specificExtractor.apply(clazz.cast(obj));
        }

         public static UUID getSenderUuid(ChatMessage message) {
            for (MessageVersion version : values()) {
                if (version.clazz.isInstance(message)) {
                   return version.uuidExtractor.apply(message);
                }
            }
            return null;
        }
    }
}

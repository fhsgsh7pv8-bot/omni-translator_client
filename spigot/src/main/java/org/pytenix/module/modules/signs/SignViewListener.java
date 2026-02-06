package org.pytenix.module.modules.signs;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.nbt.*;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockEntityData;
import io.github.retrooper.packetevents.adventure.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SignViewListener implements PacketListener {


    private static final Pattern QUOTE_PATTERN = Pattern.compile("^[\"']+|[\"']+$");

    private final SignsModule signsModule;

    public SignViewListener(SignsModule signsModule)
    {
        this.signsModule = signsModule;
    }

    @Override
    public void onPacketSend(PacketSendEvent event)
    {

        if (event.getConnectionState() !=  com.github.retrooper.packetevents.protocol.ConnectionState.PLAY) return;

       /*
        String name = event.getPacketType().getName().toLowerCase();
        if (name.contains("keepalive") || name.contains("rotation") || name.contains("position")
                || name.contains("sound_effect")|| name.contains("entity_velocity")
        || name.contains("entity_relative_move") || name.contains("entity_head_look")
        || name.contains("entity_relative_move_and_rotation")) return;

        System.out.println("[DEBUG] Sende Paket: " + name);


        if (event.getPacketType() == PacketType.Play.Server.BLOCK_ENTITY_DATA) {
            WrapperPlayServerBlockEntityData packet = new WrapperPlayServerBlockEntityData(event);
            System.out.println(" >>> BLOCK_ENTITY_DATA gefunden! Type: " + packet.getType() + " @ " + packet.getPosition());
        }

        */


       // if(!signsModule.isActive())   return;
        if(event.getPacketType() != PacketType.Play.Server.BLOCK_ENTITY_DATA) return;

        WrapperPlayServerBlockEntityData packet = new WrapperPlayServerBlockEntityData(event);

        int type = packet.getType();
        if (type != 9 && type != 7) return;

        System.out.println("SIGN DETECTED!");

        User user = event.getUser();
        Player player = org.bukkit.Bukkit.getPlayer(user.getUUID()); //SAFER

        if(player == null) return;


        event.setCancelled(true);

        NBTCompound nbtCompound = packet.getNBT();
        if(nbtCompound == null) return;

        boolean isSign = nbtCompound.contains("front_text") || nbtCompound.contains("messages") || nbtCompound.contains("Text1");
        if (!isSign)
            return;

        signsModule.getSpigotTranslator().getTaskScheduler().runAsync(() ->
        {
            CompletableFuture<Void> front =  processSignSide(nbtCompound,"front_text",player);
            CompletableFuture<Void> back =  processSignSide(nbtCompound,"back_text",player);


            CompletableFuture.allOf(front,back).join();


            WrapperPlayServerBlockEntityData newPacket = new WrapperPlayServerBlockEntityData(
                    packet.getPosition(),
                    packet.getBlockEntityType(),
                    nbtCompound
            );


            event.getUser().sendPacketSilently(newPacket);

        });


    }
    private CompletableFuture<Void> processSignSide(NBTCompound root, String sideKey, Player player) {
        if (!root.contains(sideKey)) return CompletableFuture.completedFuture(null);

        final String locale = player.getLocale();
        final NBTCompound side = root.getCompoundTagOrNull(sideKey);

        if (side == null || !side.contains("messages")) return CompletableFuture.completedFuture(null);

        final NBTList<NBTString> messages = side.getStringListTagOrNull("messages");


        List<String> plainLines = new ArrayList<>();
        boolean hasContent = false;


        for (NBTString tag : messages.getTags()) {
            String rawText = tag.getValue();
            String plainMessage = "";

            if (rawText != null && !rawText.isEmpty() && !rawText.equalsIgnoreCase("null") && !rawText.equals("{}")) {
                try {
                    Component component;

                    try {
                        component = GsonComponentSerializer.gson().deserialize(rawText);
                    } catch (Exception e) {

                        component = signsModule.getSpigotTranslator().getLegacyComponentSerializer().deserialize(rawText);
                    }
                    plainMessage = signsModule.getSpigotTranslator().getLegacyComponentSerializer().serialize(component);
                } catch (Exception ignored) {
                    plainMessage = rawText;
                }
            }

            plainLines.add(plainMessage);
            if (!plainMessage.trim().isEmpty()) {
                hasContent = true;
            }
        }

        if (!hasContent) return CompletableFuture.completedFuture(null);

        String joinedText = String.join("\n", plainLines);

        return signsModule.translate(joinedText, locale).thenAccept(translatedBlock -> {

            String[] translatedLines = translatedBlock.split("\\R");

            NBTList<NBTString> newMessages = new NBTList<>(NBTType.STRING);

            for (int i = 0; i < messages.size(); i++) {
                String translatedLineRaw;

                if (i < translatedLines.length) {
                    translatedLineRaw = translatedLines[i];
                } else {
                    translatedLineRaw = "";


                }

                translatedLineRaw = cleanTranslatedText(translatedLineRaw);

                Component c = signsModule.getSpigotTranslator().getLegacyComponentSerializer().deserialize(translatedLineRaw);
                String json = GsonComponentSerializer.gson().serialize(c);


                //TODO:
                newMessages.addTag(new NBTString(json));
            }

            side.setTag("messages", newMessages);
        });
    }



    private String cleanTranslatedText(String input) {
        if (input == null) return "";
        String text = input.trim();
        text = text.replace("```", "").replace("`", "");
        text = QUOTE_PATTERN.matcher(text).replaceAll("");
        text = text.replace("\"","");
        return text;
    }


}

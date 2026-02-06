package org.pytenix.module.modules.chat;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.pytenix.TranslatorService;

import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class MessageSequencer implements Listener {

    final PluginChatModule pluginChatModule;


    private final Map<UUID, Queue<QueuedMessage>> userQueues = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();


    public MessageSequencer(PluginChatModule pluginChatModule) {

        this.pluginChatModule = pluginChatModule;
        Bukkit.getPluginManager().registerEvents(this, pluginChatModule.getSpigotTranslator());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event)
    {
        cleanup(event.getPlayer().getUniqueId());
    }

    public void translateWithOrder(UUID uuid, String text, String locale, boolean isOverlay) {
        Queue<QueuedMessage> queue = userQueues.computeIfAbsent(uuid, k -> new ConcurrentLinkedQueue<>());

        QueuedMessage msg = new QueuedMessage(text, isOverlay);
        queue.add(msg);

        pluginChatModule.translate(text, locale).thenAccept(translated -> {
          completeMessage(uuid, msg, translated);
        });

        scheduler.schedule(() -> {
            if (msg.translatedText.get() == null) {
                msg.translatedText.set(text);
                System.out.println("[Sequencer] Timeout für: " + text);
                processQueue(uuid);
            }
        }, 6, TimeUnit.SECONDS);
    }

    private void completeMessage(UUID uuid, QueuedMessage msg, String translated) {
        if (msg.translatedText.compareAndSet(null, translated)) {
            processQueue(uuid);
        }
    }

    private void processQueue(UUID uuid) {
        Queue<QueuedMessage> queue = userQueues.get(uuid);
        if (queue == null) return;

        synchronized (queue) {
            while (!queue.isEmpty()) {
                QueuedMessage head = queue.peek();
                String textToSend = head.translatedText.get();

                if (textToSend != null) {
                    boolean success = sendPacket(uuid, textToSend, head.isOverlay);

                    if (success) {
                        queue.poll();
                    } else {
                        scheduler.schedule(() -> processQueue(uuid), 500, TimeUnit.MILLISECONDS);
                        break;
                    }
                } else {
                    break;
                }
            }
        }
    }

    private boolean sendPacket(UUID uuid, String text, boolean isOverlay) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return false;

        try {
            Component comp = serializer.deserialize(text);
            WrapperPlayServerSystemChatMessage packet = new WrapperPlayServerSystemChatMessage(isOverlay, comp);

            PacketEvents.getAPI().getPlayerManager().getUser(player).sendPacketSilently(packet);
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    public void cleanup(UUID uuid) {
        userQueues.remove(uuid);
    }

    private static class QueuedMessage {
        final String originalText;
        final boolean isOverlay;
        final AtomicReference<String> translatedText = new AtomicReference<>(null);

        QueuedMessage(String originalText, boolean isOverlay) {
            this.originalText = originalText;
            this.isOverlay = isOverlay;
        }
    }
}
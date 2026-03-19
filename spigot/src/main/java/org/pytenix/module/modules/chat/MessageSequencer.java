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
import org.pytenix.util.TextComponentUtil;

import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class MessageSequencer implements Listener {

    final PluginChatModule pluginChatModule;

    private final Map<UUID, Queue<QueuedMessage>> userQueues = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final TextComponentUtil textComponentUtil;

    public MessageSequencer(PluginChatModule pluginChatModule) {
        this.textComponentUtil = pluginChatModule.getSpigotTranslator().getTextComponentUtil();
        this.pluginChatModule = pluginChatModule;
        Bukkit.getPluginManager().registerEvents(this, pluginChatModule.getSpigotTranslator());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cleanup(event.getPlayer().getUniqueId());
    }

    public void translateWithOrder(UUID uuid, Component component, String realMessage, String locale, boolean isOverlay) {
        Queue<QueuedMessage> queue = userQueues.computeIfAbsent(uuid, k -> new ConcurrentLinkedQueue<>());

        QueuedMessage msg = new QueuedMessage(component, isOverlay);
        queue.add(msg);

        ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
            if (msg.translatedComponent.compareAndSet(null, component)) {
                System.out.println("[Sequencer] ⏳ API Hard-Timeout (6s)! Sende Original: " + LegacyComponentSerializer.legacySection().serialize(component));
                processQueue(uuid);
            }
        }, 15, TimeUnit.SECONDS);

        // 2. Asynchrone Übersetzung mit ERROR-HANDLING (Der Gamechanger)
        textComponentUtil.translateComplexMessage(component, locale, pluginChatModule.getModuleName())
                .whenComplete((translatedComponent, throwable) -> {
                    timeoutTask.cancel(false);

                    if (throwable != null) {
                        System.err.println("[Sequencer] ❌ Interner Fehler bei der Übersetzung! Stau wird verhindert.");
                        throwable.printStackTrace();
                        completeMessage(uuid, msg, component);
                    } else {
                        completeMessage(uuid, msg, translatedComponent);
                    }
                });
    }

    private void completeMessage(UUID uuid, QueuedMessage msg, Component translatedComponent) {
        if (msg.translatedComponent.compareAndSet(null, translatedComponent)) {
            processQueue(uuid);
        }
    }

    private void processQueue(UUID uuid) {
        Queue<QueuedMessage> queue = userQueues.get(uuid);
        if (queue == null) return;

        synchronized (queue) {
            while (!queue.isEmpty()) {
                QueuedMessage head = queue.peek();
                Component compToSend = head.translatedComponent.get();

                if (compToSend != null) {
                    boolean success = sendPacket(uuid, compToSend, head.isOverlay);

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

    private boolean sendPacket(UUID uuid, Component comp, boolean isOverlay) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return false;

        try {

            WrapperPlayServerSystemChatMessage packet = new WrapperPlayServerSystemChatMessage(isOverlay, comp);
            PacketEvents.getAPI().getPlayerManager().getUser(player).sendPacketSilently(packet);
            return true;
        } catch (Exception e) {
            pluginChatModule.getSpigotTranslator().getLogger().log(java.util.logging.Level.SEVERE, "Failed to send packet", e);
            return true;
        }
    }

    public void cleanup(UUID uuid) {
        userQueues.remove(uuid);
    }


    private static class QueuedMessage {
        final Component originalComponent;
        final boolean isOverlay;
        final AtomicReference<Component> translatedComponent = new AtomicReference<>(null);

        QueuedMessage(Component originalComponent, boolean isOverlay) {
            this.originalComponent = originalComponent;
            this.isOverlay = isOverlay;
        }
    }
}
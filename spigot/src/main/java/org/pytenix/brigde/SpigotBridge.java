package org.pytenix.brigde;

import org.bukkit.Bukkit;
import org.pytenix.AdvancedTranslationBridge;
import org.pytenix.ServerConfiguration;
import org.pytenix.SpigotTranslator;
import org.pytenix.UuidUtil;
import org.pytenix.gradient.GradientService;
import org.pytenix.proto.generated.NetworkPackets;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SpigotBridge extends AdvancedTranslationBridge {
    private final SpigotTranslator plugin;
    private static final String CHANNEL = "translator:main";


    public SpigotBridge(SpigotTranslator plugin) {
        this.plugin = plugin;
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL,
                (ch, player, msg) -> this.onReceiveRaw(msg, null));


        ScheduledExecutorService flushScheduler = Executors.newSingleThreadScheduledExecutor();

        flushScheduler.scheduleAtFixedRate(() -> {
            if (!Bukkit.getOnlinePlayers().isEmpty()) {
                this.flush();
            }
        }, 10, 10, TimeUnit.MILLISECONDS);




    }



    @Override
    protected void handleConfigRequest(String originServer) {}

    @Override
    protected void handleConfigUpdate(NetworkPackets.ServerConfiguration packet) {


        plugin.getTaskScheduler().runAsync(() -> {

            ServerConfiguration update = new ServerConfiguration();


            update.setModules(new HashMap<>(packet.getModulesMap()));

            update.setBlacklistedWords(new HashSet<>(packet.getWordsList()));

            plugin.applyConfigUpdate(update);

            plugin.getLogger().info("Config-Update vom Proxy empfangen und angewendet.");
        });

    }

    @Override
    protected void dispatchRaw(byte[] data, String originServer) {
        //EXTRA SO; DAMIT DER TRAFFIC GLEICHMÄ?IG AUFGETEILT WIRD!
        Bukkit.getOnlinePlayers().stream().findAny().ifPresent(p -> p.sendPluginMessage(plugin, CHANNEL, data));
    }



    @Override
    protected void handleFullResultPackage(NetworkPackets.TranslationBatchResult batch) {
        super.handleResponses(batch);
    }

    @Override
    protected void handleFullRequestPackage(NetworkPackets.TranslationBatchRequest batch) {
        // Spigot empfängt keine Requests, es schickt sie nur.
    }

    @Override
    public String handlePlaceholders(UUID uuid, String result) {

        List<UUID> lineIds = plugin.getTranslatorService().cachedReferences.getIfPresent(uuid);


        if (lineIds == null || lineIds.isEmpty()) {

            return result;
        }

        String[] translatedLines = result.split("\n", -1);
        List<String> finalLines = new ArrayList<>();

        for (int i = 0; i < lineIds.size(); i++) {
            UUID lineUuid = lineIds.get(i);

            String currentLine = (i < translatedLines.length) ? translatedLines[i] : "";

            if (plugin.getPlaceholderService() != null) {
                currentLine = plugin.getPlaceholderService().fromPlaceholders(lineUuid, currentLine);
            }

            if (plugin.getGradientService() != null) {
                GradientService.GradientInfo gradientInfo = plugin.getCachedGradients().getIfPresent(lineUuid);
                if (gradientInfo != null && gradientInfo.isGradient()) {
                    currentLine = plugin.getGradientService().applyGradient(currentLine, gradientInfo);
                    plugin.getCachedGradients().invalidate(lineUuid);
                }
            }

            finalLines.add(currentLine);
        }


        plugin.getTranslatorService().cachedReferences.invalidate(uuid);


        return String.join("\n", finalLines);
    }
}
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

public class SpigotBridge extends AdvancedTranslationBridge {
    private final SpigotTranslator plugin;
    private static final String CHANNEL = "translator:main";

    public SpigotBridge(SpigotTranslator plugin) {
        this.plugin = plugin;
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL,
                (ch, player, msg) -> this.onReceiveRaw(msg, null));


        plugin.getTaskScheduler().runTimerAsync( this::flush, 1, 1);

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
        Bukkit.getOnlinePlayers().stream().findAny().ifPresent(p -> p.sendPluginMessage(plugin, CHANNEL, data));
    }

    @Override
    protected void handleFullPackage(NetworkPackets.TranslationBatch batch) {

        if (batch.getResultsCount() != batch.getRequestsCount()) {
            return;
        }


        for (int i = 0; i < batch.getRequestsCount(); i++) {

            UUID requestId = UuidUtil.fromByteString(batch.getRequests(i).getRequestId());

            String rawTranslation = batch.getResults(i);


            List<CompletableFuture<String>> subs = this.pendingRequests.get(requestId);

            if (subs != null) {
                subs.forEach(stringCompletableFuture -> stringCompletableFuture.complete(rawTranslation));
            }
        }
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
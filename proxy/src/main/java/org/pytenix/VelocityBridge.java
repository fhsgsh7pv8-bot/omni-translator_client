package org.pytenix;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.pytenix.entity.ServerConfiguration;
import org.pytenix.proto.generated.NetworkPackets;
import org.pytenix.proto.generated.NetworkPackets.TranslationRequest;
import org.pytenix.util.UuidUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class VelocityBridge extends AdvancedTranslationBridge {
    private final VelocityTranslator proxy;
    private final ChannelIdentifier identifier = MinecraftChannelIdentifier.from("translator:main");

    private final Map<String, ConcurrentLinkedQueue<NetworkPackets.TranslationResult>> outgoingResults = new ConcurrentHashMap<>();

    private final ScheduledExecutorService flushScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("Velocity-Flush-Worker");
        thread.setDaemon(true);
        return thread;
    });

    private final ExecutorService apiExecutor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("Translation-API-Worker");
        thread.setDaemon(true);
        return thread;
    });

    public VelocityBridge(VelocityTranslator proxy) {
        this.proxy = proxy;
        proxy.getProxyServer().getChannelRegistrar().register(identifier);

        flushScheduler.scheduleAtFixedRate(this::flushOutgoingResults, 10, 10, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        flushScheduler.shutdown();
        apiExecutor.shutdown();
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(identifier)) return;

        if (event.getSource() instanceof ServerConnection connection) {
            String serverName = connection.getServerInfo().getName();
            this.onReceiveRaw(event.getData(), serverName);
        }
    }

    @Override
    protected void dispatchRaw(byte[] data, String originServer) {
        if (originServer == null) {
            System.err.println("Fehler: Kein Origin-Server für Antwort angegeben!");
            return;
        }

        proxy.getProxyServer().getServer(originServer).ifPresent(server ->
                server.sendPluginMessage(identifier, data));
    }

    @Override
    protected void handleFullRequestPackage(NetworkPackets.TranslationBatchRequest batch) {
        processRequestBatch(batch);
    }

    @Override
    protected void handleFullResultPackage(NetworkPackets.TranslationBatchResult batch) {
        // Wird ignoriert. Velocity empfängt keine Results.
    }

    private void processRequestBatch(NetworkPackets.TranslationBatchRequest batch) {
        String originServer = batch.getOriginServer();

        ConcurrentLinkedQueue<NetworkPackets.TranslationResult> serverQueue = outgoingResults.computeIfAbsent(originServer, k -> new ConcurrentLinkedQueue<>());

        for (TranslationRequest req : batch.getRequestsList()) {
            UUID id = UuidUtil.fromByteString(req.getRequestId());
            String text = req.getText();
            String lang = req.getTargetLang();

            String cached = proxy.getCaffeineCache().get(text, lang);

            if (cached != null) {
                serverQueue.add(NetworkPackets.TranslationResult.newBuilder()
                        .setRequestId(req.getRequestId())
                        .setResult(cached)
                        .build());
            } else {
                proxy.getRestfulService()
                        .sendTranslationRequest(id, text, lang, req.getModule())
                        .thenAcceptAsync(translatedText -> {
                            String finalString = (isSuccessfull(translatedText) && !translatedText.equals(text)) ? translatedText : text;

                            serverQueue.add(NetworkPackets.TranslationResult.newBuilder()
                                    .setRequestId(req.getRequestId())
                                    .setResult(finalString)
                                    .build());
                        }, apiExecutor);
            }
        }
    }

    private void flushOutgoingResults() {
        for (Map.Entry<String, ConcurrentLinkedQueue<NetworkPackets.TranslationResult>> entry : outgoingResults.entrySet()) {
            String serverName = entry.getKey();
            ConcurrentLinkedQueue<NetworkPackets.TranslationResult> queue = entry.getValue();

            if (queue.isEmpty()) continue;

            List<NetworkPackets.TranslationResult> toSend = new ArrayList<>();
            NetworkPackets.TranslationResult result;

            int count = 0;
            while ((result = queue.poll()) != null && count < 500) {
                toSend.add(result);
                count++;
            }

            if (!toSend.isEmpty()) {
                NetworkPackets.TranslationBatchResult responseBatch = NetworkPackets.TranslationBatchResult.newBuilder()
                        .setOriginServer(serverName)
                        .addAllResults(toSend)
                        .build();

                sendResultBatch(responseBatch, serverName);
            }
        }
    }

    public boolean isSuccessfull(String string) {
        return string != null && !string.equalsIgnoreCase("TIMEOUT") && !string.startsWith("ERROR");
    }

    @Override
    protected void initPlayernames() {
        for(Player player : proxy.getProxyServer().getAllPlayers())
        {
            getPlaceholderService().getPlayernameProtector().addPlayer(player.getUsername().toLowerCase());
        }
    }

    @Override
    protected void handleConfigRequest(String originServer) {
        if (getServerConfiguration() == null) return;
        NetworkPackets.ServerConfiguration packet = convertToProto(getServerConfiguration());
        sendConfigProto(packet, originServer);
    }

    @Override
    protected void onConfigUpdate(ServerConfiguration configuration) {

    }



    private NetworkPackets.ServerConfiguration convertToProto(ServerConfiguration javaConfig) {
        NetworkPackets.ServerConfiguration.Builder builder = NetworkPackets.ServerConfiguration.newBuilder();
        if (javaConfig.getModules() != null) builder.putAllModules(javaConfig.getModules());
        if (javaConfig.getBlacklistedWords() != null) builder.addAllWords(javaConfig.getBlacklistedWords());
        return builder.build();
    }

    public void broadcastConfigUpdate(ServerConfiguration javaConfig) {
        setServerConfiguration(javaConfig);

        NetworkPackets.ServerConfiguration packet = convertToProto(javaConfig);
        for (RegisteredServer server : proxy.getProxyServer().getAllServers()) {
            sendConfigProto(packet, server.getServerInfo().getName());
        }
    }
}
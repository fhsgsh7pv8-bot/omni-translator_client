package org.pytenix;

import com.google.protobuf.ByteString;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.pytenix.proto.generated.NetworkPackets;
import org.pytenix.proto.generated.NetworkPackets.Chunk;
import org.pytenix.proto.generated.NetworkPackets.PacketWrapper;
import org.pytenix.proto.generated.NetworkPackets.TranslationBatch;
import org.pytenix.proto.generated.NetworkPackets.TranslationRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class VelocityBridge extends AdvancedTranslationBridge {
    private final VelocityTranslator proxy;
    private final ChannelIdentifier identifier = MinecraftChannelIdentifier.from("translator:main");


    private final ExecutorService apiExecutor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("Translation-API-Worker");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    protected void handleConfigUpdate(NetworkPackets.ConfigPacket configPacket) {
    }

    @Override
    protected void handleConfigRequest(String originServer) {
        if (proxy.getCachedConfig() == null) {
            System.out.println("Spigot Server " + originServer + " will Config, aber Proxy hat noch keine.");
            return;
        }

        NetworkPackets.ConfigPacket packet = convertToProto(proxy.getCachedConfig());
        sendConfigProto(packet, originServer);
        System.out.println("Cached Config an startenden Server gesendet: " + originServer);
    }

    public VelocityBridge(VelocityTranslator proxy) {
        this.proxy = proxy;
        proxy.getProxyServer().getChannelRegistrar().register(identifier);
    }

    @Override
    protected String handlePlaceholders(UUID uuid, String result) {
        return result;
    }

    private NetworkPackets.ConfigPacket convertToProto(ConfigUpdate javaConfig) {
        NetworkPackets.ConfigPacket.Builder builder = NetworkPackets.ConfigPacket.newBuilder()
                .setLicenseKey(javaConfig.getLicenseKey() == null ? "" : javaConfig.getLicenseKey());

        if (javaConfig.getModules() != null) {
            builder.putAllModules(javaConfig.getModules());
        }
        if(javaConfig.getBlacklistedWords() != null)
        {
            builder.addAllWords(javaConfig.getBlacklistedWords());
        }
        return builder.build();
    }

    public void broadcastConfigUpdate(ConfigUpdate javaConfig) {
        proxy.setCachedConfig(javaConfig);

        NetworkPackets.ConfigPacket packet = convertToProto(javaConfig);

        for (RegisteredServer server : proxy.getProxyServer().getAllServers()) {
            sendConfigProto(packet, server.getServerInfo().getName());
        }
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
    protected void handleFullPackage(TranslationBatch batch) {
        if (batch.getResultsCount() == 0) {
            processRequestBatch(batch);
        } else {
            super.handleResponses(batch);
        }
    }

    private void processRequestBatch(TranslationBatch batch) {
        List<CompletableFuture<String>> futures = new ArrayList<>();

        for (TranslationRequest req : batch.getRequestsList()) {
            UUID id = UuidUtil.fromByteString(req.getRequestId());
            String text = req.getText();
            String lang = req.getTargetLang();

            String cached = proxy.getCaffeineCache().get(text, lang);

            if (cached != null) {
                futures.add(CompletableFuture.completedFuture(cached));
            } else {

                CompletableFuture<String> apiCall = proxy.getRestfulService()
                        .sendTranslationRequest(id, text, lang,req.getModule())
                        .thenApply(response -> {
                            String translatedText = response.getTranslatedText();

                            if (isSuccessfull(translatedText) && !translatedText.equals(text)) {

                                proxy.getCaffeineCache().set(text, lang, translatedText);
                                return translatedText;
                            } else {

                                return text;
                            }
                        });
                futures.add(apiCall);
            }
        }


        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenAcceptAsync(unused -> {

            List<String> results = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            TranslationBatch responseBatch = batch.toBuilder()
                    .addAllResults(results)
                    .build();


            sendResponse(responseBatch);

        }, apiExecutor);
    }


    private void sendResponse(TranslationBatch batch) {
        byte[] data = batch.toByteArray();
        String targetServer = batch.getOriginServer();

        if (data.length < 30000) {

            PacketWrapper wrapper = PacketWrapper.newBuilder().setBatch(batch).build();
            dispatchRaw(wrapper.toByteArray(), targetServer);
        } else {

            sendChunkedResponse(data, targetServer);
        }
    }


    private void sendChunkedResponse(byte[] data, String targetServer) {
        String transmissionId = UUID.randomUUID().toString();
        int maxChunkSize = 29000;
        int totalParts = (int) Math.ceil((double) data.length / maxChunkSize);

        for (int i = 0; i < totalParts; i++) {
            int start = i * maxChunkSize;
            int end = Math.min(data.length, start + maxChunkSize);

            ByteString chunkData = ByteString.copyFrom(data, start, end - start);

            Chunk chunk = Chunk.newBuilder()
                    .setTransmissionId(transmissionId)
                    .setPartIndex(i)
                    .setTotalParts(totalParts)
                    .setData(chunkData)
                    .build();

            PacketWrapper wrapper = PacketWrapper.newBuilder().setChunk(chunk).build();
            dispatchRaw(wrapper.toByteArray(), targetServer);
        }
    }

    public boolean isSuccessfull(String string) {
        return string != null && !string.equalsIgnoreCase("TIMEOUT") && !string.startsWith("ERROR");
    }
}
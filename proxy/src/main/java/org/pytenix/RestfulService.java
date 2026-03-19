package org.pytenix;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import org.pytenix.entity.ServerConfiguration;
import org.pytenix.proto.generated.NetworkPackets;
import org.pytenix.util.UuidUtil;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

public class RestfulService {

    private WebSocket webSocket;
    private final String url;
    private final String apiKey;
    private int reconnectAttempts = 0;
    private final ProxyServer proxyServer;
    private final VelocityTranslator velocityTranslator;
    private final VelocityBridge velocityBridge;

    private final ConcurrentHashMap<UUID, CompletableFuture<String>> queue = new ConcurrentHashMap<>();
    private record QueuedRequest(NetworkPackets.TranslationRequest request, CompletableFuture<String> future, UUID originalId) {}
    private final List<QueuedRequest> pendingRequests = new ArrayList<>();
    private ScheduledTask flushTask = null;



    private static final int MAX_BATCH_SIZE = 25;
    private static final long MAX_WAIT_TIME_MS = 20;

    public RestfulService(VelocityTranslator velocityTranslator, VelocityBridge velocityBridge, String apiKey, ProxyServer proxyServer) {
        this.velocityTranslator = velocityTranslator;
        this.velocityBridge = velocityBridge;
        this.proxyServer = proxyServer;
        this.apiKey = apiKey;
        this.url = "wss://"+velocityTranslator.getRemoteAddress()+"/";

        connect();
    }

    public void connect() {
        HttpClient.newBuilder()
                .executor(Executors.newCachedThreadPool())
                .build()
                .newWebSocketBuilder()

                .header("X-API-KEY", apiKey)
                .buildAsync(URI.create(url), new WebSocketListener())
                .thenAccept(ws -> {
                    this.webSocket = ws;
                    this.reconnectAttempts = 0;
                    System.out.println("[OmniTranslator] Verbindung zum API-Backend erfolgreich hergestellt.");


                    sendTranslationRequest(UUID.randomUUID(),"This is a Test!","de_de","live_chat").thenAcceptAsync(translationResponse ->
                    {

                    });

                }).exceptionally(ex -> {
                    System.err.println("============= WEB-SOCKET CRASH =============");
                    ex.printStackTrace();
                    System.err.println("============================================");
                    scheduleReconnect();
                    return null;
                });
    }

    private void scheduleReconnect() {
        reconnectAttempts++;
        long waitTime = Math.min((long) Math.pow(2, reconnectAttempts), 60);

        System.err.println("[OmniTranslator] Verbindung fehlgeschlagen (Versuch "
                + reconnectAttempts + "). Reconnect in " + waitTime + "s...");

            proxyServer.getScheduler().buildTask(velocityTranslator, this::connect)
                .delay(waitTime, TimeUnit.SECONDS)
                .schedule();
    }


    private void handleConfigUpdate(ServerConfiguration config) {
        System.out.println("[OmniTranslator] Neue Config empfangen & wird verteilt.");

        velocityTranslator.getVelocityBridge().setServerConfiguration(config);
        velocityBridge.broadcastConfigUpdate(config);
    }


    public CompletableFuture<String> sendTranslationRequest(UUID id, String text, String lang,String module) {

        final long start = System.currentTimeMillis();
        CompletableFuture<String> future = new CompletableFuture<>();

        if (text == null || text.isBlank()) return CompletableFuture.completedFuture(text);

        // Baue das Protobuf Request-Objekt
        NetworkPackets.TranslationRequest request = NetworkPackets.TranslationRequest.newBuilder()
                .setRequestId(UuidUtil.toByteString(id))
                .setText(text)
                .setTargetLang(lang)
                .setModule(module)
                .build();

       synchronized (pendingRequests) {

            pendingRequests.add(new QueuedRequest(request, future, id));

           if (pendingRequests.size() >= MAX_BATCH_SIZE) {
                flushBatch();
            }

            else if (flushTask == null) {
                flushTask = proxyServer.getScheduler().buildTask(velocityTranslator, this::flushBatch)
                        .delay(MAX_WAIT_TIME_MS, TimeUnit.MILLISECONDS)
                        .schedule();
            }
        }


        return future.orTimeout(60, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    queue.remove(id);
                    return "TIMEOUT";
                }).thenApply(translationResponse ->
                {
                    System.out.println("TOOK " + (System.currentTimeMillis() - start) + " ms for " + text);
                    return translationResponse;
                });
    }


    private void flushBatch() {
        List<QueuedRequest> batchToProcess;


        synchronized (pendingRequests) {
            if (pendingRequests.isEmpty()) return;


            batchToProcess = new ArrayList<>(pendingRequests);
            pendingRequests.clear();


            if (flushTask != null) {
                flushTask.cancel();
                flushTask = null;
            }
        }


        sendAsProtobufBatch(batchToProcess);
    }

    private void sendAsProtobufBatch(List<QueuedRequest> batch) {
        try {

            NetworkPackets.TranslationBatchRequest.Builder batchBuilder = NetworkPackets.TranslationBatchRequest.newBuilder();



            if (webSocket != null && !webSocket.isOutputClosed()) {

                for (QueuedRequest qr : batch) {
                    batchBuilder.addRequests(qr.request());
                    queue.put(qr.originalId(),qr.future());
                }


                NetworkPackets.PacketWrapper packetWrapper = NetworkPackets.PacketWrapper.newBuilder().setBatchRequest(batchBuilder.build())
                                .build();

                webSocket.sendBinary(ByteBuffer.wrap(packetWrapper.toByteArray()) , true).thenRun(() ->
                {
                    System.out.println("BATCH SENT (" + batch.size() + " Items)");
                }).exceptionally(ex -> {
                    System.err.println("BATCH SEND FAILED: " + ex.getMessage());
                    return null;
                });


            } else {
                throw new IllegalStateException("WebSocket not connected");
            }
        } catch (Exception e) {

            for (QueuedRequest qr : batch) {
                qr.future().completeExceptionally(e);
                queue.remove(qr.originalId());
            }

            e.printStackTrace();
        }
    }


    private class WebSocketListener implements WebSocket.Listener {


        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);

            try {
                buffer.write(bytes);
            } catch (Exception e) {
                e.printStackTrace();
            }

            if(last)
            {
                byte[] payload = buffer.toByteArray();
                buffer.reset();

                CompletableFuture.runAsync(() ->
                {
                    try {
                        NetworkPackets.PacketWrapper wrapper = NetworkPackets.PacketWrapper.parseFrom(payload);

                        switch (wrapper.getPayloadCase()) {
                            case BATCH_RESULT:
                                NetworkPackets.TranslationBatchResult batch = wrapper.getBatchResult();

                                for (NetworkPackets.TranslationResult translationResult : batch.getResultsList()) {

                                    String resultText = translationResult.getResult();

                                    UUID id = UuidUtil.fromByteString(translationResult.getRequestId());
                                    CompletableFuture<String> future = queue.remove(id);
                                    if (future != null) future.complete(resultText);
                                }

                                break;
                            case CONFIG, CONFIG_REQUEST:

                                NetworkPackets.ServerConfiguration serverConfiguration = wrapper.getConfig();

                                ServerConfiguration configuration = new ServerConfiguration();
                                configuration.setModules(new HashMap<>(serverConfiguration.getModulesMap()));
                                configuration.setLicenseKey(serverConfiguration.getLicenseKey());
                                configuration.setDefaultLanguage(serverConfiguration.getDefaultLanguage());
                                configuration.setBlacklistedWords(new HashSet<>(serverConfiguration.getWordsList()));

                                 handleConfigUpdate(configuration);
                                break;

                            default:
                                break;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            webSocket.request(1);
            return null;

        }


        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            System.out.println("[OmniTranslator] WebSocket geschlossen: " + reason);
            scheduleReconnect();
            return null;

        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            System.err.println("[OmniTranslator] WebSocket Fehler: " + error.getMessage());
        }
    }
}
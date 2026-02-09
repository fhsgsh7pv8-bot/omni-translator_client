package org.pytenix;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import org.pytenix.model.TranslationRequest;
import org.pytenix.model.TranslationResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

public class RestfulService {

    private WebSocket webSocket;
    private final String url;
    private final String apiKey;
    private int reconnectAttempts = 0;
    private final ProxyServer proxyServer;
    private final VelocityTranslator velocityTranslator;
    private final VelocityBridge velocityBridge;

    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final ConcurrentHashMap<UUID, CompletableFuture<TranslationResponse>> queue;


    private record QueuedRequest(TranslationRequest request, CompletableFuture<TranslationResponse> future) {}

    private final List<QueuedRequest> pendingRequests = new ArrayList<>();

    private ScheduledTask flushTask = null;



    private static final int MAX_BATCH_SIZE = 25;
    private static final long MAX_WAIT_TIME_MS = 100;

    public RestfulService(VelocityTranslator velocityTranslator, VelocityBridge velocityBridge, String apiKey, ProxyServer proxyServer) {
        this.velocityTranslator = velocityTranslator;
        this.velocityBridge = velocityBridge;
        this.proxyServer = proxyServer;
        this.apiKey = apiKey;
        this.queue = new ConcurrentHashMap<>();
        this.url = "ws://localhost:8083/v1/translate";

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



        velocityTranslator.setCachedConfig(config);

        velocityBridge.broadcastConfigUpdate(config);
    }


    public CompletableFuture<TranslationResponse> sendTranslationRequest(UUID id, String text, String lang,String module) {
        CompletableFuture<TranslationResponse> future = new CompletableFuture<>();
        TranslationRequest request = new TranslationRequest(id, text, lang, apiKey,module);

        if(text == null || text.isBlank() || text.isEmpty())
            return CompletableFuture.completedFuture(new TranslationResponse(id,text,lang));

       synchronized (pendingRequests) {

            pendingRequests.add(new QueuedRequest(request, future));

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
                    return new TranslationResponse(id, "TIMEOUT", "ERROR");
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


        sendAsJsonArray(batchToProcess);
    }

    private void sendAsJsonArray(List<QueuedRequest> batch) {
        try {

            List<TranslationRequest> requestDTOs = batch.stream().map(QueuedRequest::request).toList();
            String json = mapper.writeValueAsString(requestDTOs);

            if (webSocket != null && !webSocket.isOutputClosed()) {

                for (QueuedRequest qr : batch) {
                    queue.put(qr.request().getId(), qr.future());
                }


                webSocket.sendText(json, true).thenRun(() -> {

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
                queue.remove(qr.request().getId());
            }

            e.printStackTrace();
        }
    }


    private class WebSocketListener implements WebSocket.Listener {


        private final StringBuilder messageBuilder = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            messageBuilder.append(data);

            if (last) {
                String fullJson = messageBuilder.toString();
                messageBuilder.setLength(0);

                CompletableFuture.runAsync(() -> {
                    try {

                        JsonNode rootNode = mapper.readTree(fullJson);


                        if (rootNode.isArray()) {
                            TranslationResponse[] responses = mapper.treeToValue(rootNode, TranslationResponse[].class);
                            for (TranslationResponse res : responses) processResponse(res);
                            return;
                        }




                        if (rootNode.has("type") && rootNode.get("type").asText().equals("CONFIG_UPDATE")) {
                            ServerConfiguration config = mapper.treeToValue(rootNode, ServerConfiguration.class);
                            handleConfigUpdate(config);
                            return;
                        }


                        if (rootNode.has("id")) {
                            TranslationResponse res = mapper.treeToValue(rootNode, TranslationResponse.class);
                            processResponse(res);
                        } else {

                            ServerConfiguration config = mapper.treeToValue(rootNode, ServerConfiguration.class);
                            handleConfigUpdate(config);
                        }

                    } catch (Exception e) {
                        System.err.println("Error parsing JSON: " + fullJson);
                        e.printStackTrace();
                    }
                });
            }
            webSocket.request(1);
            return null;
        }

        private void processResponse(TranslationResponse res) {
            CompletableFuture<TranslationResponse> future = queue.remove(res.getId());
            if (future != null) {
                future.complete(res);
            }
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
package org.pytenix.motd;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import org.pytenix.*;
import org.pytenix.proto.generated.NetworkPackets;
import org.pytenix.util.UuidUtil;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

public class GeoService {

    private WebSocket webSocket;
    private final String url;
    private final String apiKey;
    private int reconnectAttempts = 0;
    private final ProxyServer proxyServer;
    private final VelocityTranslator velocityTranslator;

    private final ConcurrentHashMap<UUID, CompletableFuture<String>> queue = new ConcurrentHashMap<>();
    private record QueuedRequest(NetworkPackets.GeoRequestPacket request, CompletableFuture<String> future, UUID originalId) {}
    private final List<GeoService.QueuedRequest> pendingRequests = new ArrayList<>();
    private ScheduledTask flushTask = null;



    private static final int MAX_BATCH_SIZE = 50;
    private static final long MAX_WAIT_TIME_MS = 10;

    public GeoService(VelocityTranslator velocityTranslator, String apiKey, ProxyServer proxyServer) {
        this.velocityTranslator = velocityTranslator;
        this.proxyServer = proxyServer;
        this.apiKey = apiKey;
        this.url = "wss://"+velocityTranslator.getRemoteAddress()+"/geo";

        connect();
    }

    public void connect() {
        HttpClient.newBuilder()
                .executor(Executors.newCachedThreadPool())
                .build()
                .newWebSocketBuilder()

                .header("X-API-KEY", apiKey)
                .buildAsync(URI.create(url), new GeoService.WebSocketListener())
                .thenAccept(ws -> {
                    this.webSocket = ws;
                    this.reconnectAttempts = 0;
                    System.out.println("[OmniTranslator] Verbindung zum API-Backend erfolgreich hergestellt.");



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




    public CompletableFuture<String> sendGeoRequest(UUID id, String ipAddress) {

        final long start = System.currentTimeMillis();
        CompletableFuture<String> future = new CompletableFuture<>();

        if (ipAddress == null || ipAddress.isBlank()) return CompletableFuture.completedFuture("en_en");

        // Baue das Protobuf Request-Objekt
        NetworkPackets.GeoRequestPacket request = NetworkPackets.GeoRequestPacket.newBuilder()
                .setRequestId(UuidUtil.toByteString(id))
                .setIpAddress(ipAddress)
                .build();
        synchronized (pendingRequests) {

            pendingRequests.add(new GeoService.QueuedRequest(request, future, id));

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
                    return translationResponse;
                });
    }


    private void flushBatch() {
        List<GeoService.QueuedRequest> batchToProcess;


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

    private void sendAsProtobufBatch(List<GeoService.QueuedRequest> batch) {
        try {

            NetworkPackets.GeoBatchRequest.Builder batchBuilder = NetworkPackets.GeoBatchRequest.newBuilder();



            if (webSocket != null && !webSocket.isOutputClosed()) {

                for (GeoService.QueuedRequest qr : batch) {
                    batchBuilder.addRequests(qr.request());
                    queue.put(qr.originalId(),qr.future());
                }


                NetworkPackets.PacketWrapper packetWrapper = NetworkPackets.PacketWrapper.newBuilder()
                        .setGeoBatchRequest(batchBuilder.build())
                        .build();

                webSocket.sendBinary(ByteBuffer.wrap(packetWrapper.toByteArray()) , true).thenRun(() ->
                {
                }).exceptionally(ex -> {
                    System.err.println("BATCH SEND FAILED: " + ex.getMessage());
                    return null;
                });


            } else {
                throw new IllegalStateException("WebSocket not connected");
            }
        } catch (Exception e) {

            for (GeoService.QueuedRequest qr : batch) {
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

                        if (wrapper.getPayloadCase() == NetworkPackets.PacketWrapper.PayloadCase.GEO_BATCH_RESULT) {
                            NetworkPackets.GeoBatchResult batch = wrapper.getGeoBatchResult();

                            for (NetworkPackets.GeoResultPacket geoResult : batch.getResultsList()) {

                                String resultText = geoResult.getLanguage();

                                UUID id = UuidUtil.fromByteString(geoResult.getRequestId());
                                CompletableFuture<String> future = queue.remove(id);
                                if (future != null) future.complete(resultText);
                            }
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

package org.pytenix;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.Setter;
import org.pytenix.proto.generated.NetworkPackets;
import org.pytenix.proto.generated.NetworkPackets.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public abstract class AdvancedTranslationBridge {
     public final Map<UUID, List<CompletableFuture<String>>> pendingRequests = new ConcurrentHashMap<>();

    protected record DeduplicationKey(String text, String lang, String module) {}

    private final Map<DeduplicationKey, UUID> deduplicationQueue = new ConcurrentHashMap<>();


    private final Cache<String, Map<Integer, byte[]>> chunkAssembler = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();


    @Setter
    protected String secretKey;

    private static final int MAGIC_HEADER = 0x50595458;

    byte[] secureWrap(byte[] payload) {
        if (secretKey == null || secretKey.isEmpty()) return payload;

        long timestamp = System.currentTimeMillis();
        byte[] signature = HmacService.calculateSignature(timestamp, payload, secretKey);

       ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + Long.BYTES + Integer.BYTES + signature.length + payload.length);

        buffer.putInt(MAGIC_HEADER);
        buffer.putLong(timestamp);
        buffer.putInt(signature.length);
        buffer.put(signature);
        buffer.put(payload);

        return buffer.array();
    }


    private byte[] secureUnwrap(byte[] data) {
        if (secretKey == null || secretKey.isEmpty()) {
            System.err.println("DEBUG: Kein SecretKey gesetzt, kann nicht prüfen.");
            return null;
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(data);

            if (buffer.remaining() >= 4) {
                int potentialHeader = buffer.getInt(0);
                if (potentialHeader != MAGIC_HEADER) {
                    return data;
                }
            } else {
                return data;
            }


            buffer.getInt();


            if (buffer.remaining() < 12) {
                System.err.println("DEBUG: Paket viel zu klein (" + buffer.remaining() + " bytes). Header fehlt.");
                return null;
            }

            long timestamp = buffer.getLong();
            long diff = System.currentTimeMillis() - timestamp;


            if (Math.abs(diff) > 20000) {
                System.err.println("DEBUG: Timestamp abgelaufen! Diff: " + diff);
                return null;
            }

            int sigLen = buffer.getInt();

            if (sigLen < 0 || sigLen > 512) {
                System.err.println("DEBUG: Ungültige Signaturlänge gelesen: " + sigLen + ". Strukturfehler?");
                return null;
            }


            if (buffer.remaining() < sigLen) {
                System.err.println("DEBUG: Buffer Underflow! Erwarte " + sigLen + " Sig-Bytes, habe nur " + buffer.remaining());
                return null;
            }

            byte[] signature = new byte[sigLen];
            buffer.get(signature);


            byte[] payload = new byte[buffer.remaining()];
            buffer.get(payload);


            if (HmacService.isValid(timestamp, payload, signature, secretKey)) {
                return payload;
            } else {
                System.err.println("DEBUG: Signatur-Check fehlgeschlagen! Key falsch oder Daten manipuliert.");
                return null;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }



    public CompletableFuture<String> translate(UUID id, String text, String targetLang, String module) {
        if (text == null || text.isEmpty()) return CompletableFuture.completedFuture("");

        DeduplicationKey key = new DeduplicationKey(text, targetLang, module);
        CompletableFuture<String> future = new CompletableFuture<>();

        future.orTimeout(5, TimeUnit.SECONDS).exceptionally(ex -> text);

        synchronized (deduplicationQueue) {

            UUID requestId = deduplicationQueue.computeIfAbsent(key, k -> id);

            pendingRequests.computeIfAbsent(requestId, k -> new ArrayList<>()).add(future);
        }

        return future;
    }


    public void flush() {
        if (deduplicationQueue.isEmpty()) return;

        NetworkPackets.TranslationBatch.Builder batchBuilder = TranslationBatch.newBuilder();

        synchronized (deduplicationQueue) {
            for (Map.Entry<DeduplicationKey, UUID> entry : deduplicationQueue.entrySet()) {
                DeduplicationKey key = entry.getKey();
                UUID reqId = entry.getValue();

                batchBuilder.addRequests(TranslationRequest.newBuilder()
                        .setRequestId(UuidUtil.toByteString(reqId))
                        .setText(key.text)
                        .setTargetLang(key.lang)
                        .setModule(key.module)
                        .build());
            }
            deduplicationQueue.clear();
        }


        sendProto(batchBuilder.build());
    }


    private void sendProto(TranslationBatch batch) {
        byte[] batchBytes = batch.toByteArray();

        if (batchBytes.length < 25000) {
            PacketWrapper wrapper = PacketWrapper.newBuilder().setBatch(batch).build();

            byte[] securedData = secureWrap(wrapper.toByteArray());

            dispatchRaw(securedData, null);
        } else {
            sendChunked(batchBytes);
        }
    }

    private void sendChunked(byte[] data) {
        String transmissionId = UUID.randomUUID().toString();
        int maxChunkSize = 28000;
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

            byte[] securedData = secureWrap(wrapper.toByteArray());

            dispatchRaw(securedData, null);
        }
    }

    public void onReceiveRaw(byte[] data, String originServer) {
        try {

            byte[] cleanPayload = secureUnwrap(data);

            if (cleanPayload == null) {
                System.out.println("ABABABAB");
                return;
            }

            PacketWrapper wrapper = PacketWrapper.parseFrom(cleanPayload);

            if (wrapper.hasBatch()) {
                TranslationBatch batch = wrapper.getBatch();
                if (originServer != null) {
                    batch = batch.toBuilder().setOriginServer(originServer).build();
                }
                handleFullPackage(batch);

            } else if (wrapper.hasChunk()) {
                handleChunk(wrapper.getChunk(), originServer);
            }else if (wrapper.hasConfig()) {
                handleConfigUpdate(wrapper.getConfig());
            }else if (wrapper.hasConfigRequest()) {
                handleConfigRequest(originServer);
            }

        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
    }

    public void sendConfigRequestProto() {
        NetworkPackets.ConfigRequestPacket req = NetworkPackets.ConfigRequestPacket.newBuilder()
                .setTimestamp(System.currentTimeMillis())
                .build();

        PacketWrapper wrapper = PacketWrapper.newBuilder().setConfigRequest(req).build();


        dispatchRaw(wrapper.toByteArray(), null);
    }


    protected abstract void handleConfigRequest(String originServer);

    protected abstract void handleConfigUpdate(NetworkPackets.ServerConfiguration configPacket);

    public void sendConfigProto(NetworkPackets.ServerConfiguration packet, String targetServer) {

        PacketWrapper wrapper = PacketWrapper.newBuilder().setConfig(packet).build();
        dispatchRaw(secureWrap(wrapper.toByteArray()), targetServer);
    }

    private void handleChunk(Chunk chunk, String originServer) {
        Map<Integer, byte[]> parts;
        try {
            parts = chunkAssembler.get(chunk.getTransmissionId(), ConcurrentHashMap::new);
        } catch (ExecutionException e) {
            return;
        }
        parts.put(chunk.getPartIndex(), chunk.getData().toByteArray());

        if (parts.size() == chunk.getTotalParts()) {

            chunkAssembler.invalidate(chunk.getTransmissionId());
            byte[] fullData = assemble(parts, chunk.getTotalParts());

            try {
                TranslationBatch batch = TranslationBatch.parseFrom(fullData);
                if (originServer != null) {
                    batch = batch.toBuilder().setOriginServer(originServer).build();
                }
                handleFullPackage(batch);
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }
    }

    private byte[] assemble(Map<Integer, byte[]> parts, int total) {
        int size = parts.values().stream().mapToInt(b -> b.length).sum();
        byte[] full = new byte[size];
        int offset = 0;
        for (int i = 0; i < total; i++) {
            byte[] part = parts.get(i);
            System.arraycopy(part, 0, full, offset, part.length);
            offset += part.length;
        }
        return full;
    }


    public void handleResponses(TranslationBatch batch) {
        if (batch.getResultsCount() != batch.getRequestsCount()) return;

        for (int i = 0; i < batch.getRequestsCount(); i++) {
            TranslationRequest req = batch.getRequests(i);
            String result = batch.getResults(i);


            List<CompletableFuture<String>> futures = pendingRequests.remove(UuidUtil.fromByteString(req.getRequestId()));
            if (futures != null) {
                for (CompletableFuture<String> future : futures) {

                    future.complete(result);
                }
            }
        }
    }

    protected abstract void dispatchRaw(byte[] data, String originServer);


    protected abstract void handleFullPackage(TranslationBatch batch);
    protected abstract String handlePlaceholders(UUID uuid, String result);
}

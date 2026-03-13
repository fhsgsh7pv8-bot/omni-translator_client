package org.pytenix;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.Getter;
import lombok.Setter;
import org.pytenix.encryption.HmacService;
import org.pytenix.placeholder.GradientService;
import org.pytenix.placeholder.PlaceholderService;
import org.pytenix.proto.generated.NetworkPackets;
import org.pytenix.proto.generated.NetworkPackets.*;
import org.pytenix.util.UuidUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Getter
public abstract class AdvancedTranslationBridge {




    private final PlaceholderService placeholderService = new PlaceholderService(this);

    private final GradientService gradientService = new GradientService();

    @Setter
    protected String secretKey;




    @Setter
    private org.pytenix.entity.ServerConfiguration serverConfiguration;

    public final Map<UUID, List<CompletableFuture<String>>> pendingRequests = new ConcurrentHashMap<>();

    protected record DeduplicationKey(String text, String lang, String module) {}

    private final Map<DeduplicationKey, UUID> deduplicationQueue = new ConcurrentHashMap<>();

    private final Cache<String, Map<Integer, byte[]>> chunkAssembler = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();




    private final Cache<UUID, List<UUID>> cachedReferences = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();


    private static final int MAGIC_HEADER = 0x50595458;



    protected abstract void initPlayernames();

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
        if (secretKey == null || secretKey.isEmpty()) return data;

        try {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            if (buffer.remaining() >= 4 && buffer.getInt(0) != MAGIC_HEADER) return data;

            buffer.position(4); // Skip magic header

            if (buffer.remaining() < 12) return null;

            long timestamp = buffer.getLong();
            if (Math.abs(System.currentTimeMillis() - timestamp) > 20000) return null;

            int sigLen = buffer.getInt();
            if (sigLen < 0 || sigLen > 512 || buffer.remaining() < sigLen) return null;

            byte[] signature = new byte[sigLen];
            buffer.get(signature);

            byte[] payload = new byte[buffer.remaining()];
            buffer.get(payload);

            if (HmacService.isValid(timestamp, payload, signature, secretKey)) {
                return payload;
            }
            return null;
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

        NetworkPackets.TranslationBatchRequest.Builder batchBuilder = TranslationBatchRequest.newBuilder();

        Map<DeduplicationKey, UUID> snapshot;
        synchronized (deduplicationQueue) {
            snapshot = new HashMap<>(deduplicationQueue);
            deduplicationQueue.clear();
        }

        if (snapshot.isEmpty()) return;

        for (Map.Entry<DeduplicationKey, UUID> entry : snapshot.entrySet()) {
            DeduplicationKey key = entry.getKey();
            UUID reqId = entry.getValue();

            batchBuilder.addRequests(TranslationRequest.newBuilder()
                    .setRequestId(UuidUtil.toByteString(reqId))
                    .setText(key.text)
                    .setTargetLang(key.lang)
                    .setModule(key.module)
                    .build());
        }

        sendRequestBatch(batchBuilder.build(), null);
    }


    protected void sendRequestBatch(TranslationBatchRequest batch, String targetServer) {
        PacketWrapper wrapper = PacketWrapper.newBuilder().setBatchRequest(batch).build();
        byte[] wrapperBytes = wrapper.toByteArray();

        if (wrapperBytes.length < 25000) {
            dispatchRaw(secureWrap(wrapperBytes), targetServer);
        } else {
            sendChunked(wrapperBytes, targetServer);
        }
    }

    protected void sendResultBatch(TranslationBatchResult batch, String targetServer) {
        PacketWrapper wrapper = PacketWrapper.newBuilder().setBatchResult(batch).build();
        byte[] wrapperBytes = wrapper.toByteArray();

        if (wrapperBytes.length < 25000) {
            dispatchRaw(secureWrap(wrapperBytes), targetServer);
        } else {
            sendChunked(wrapperBytes, targetServer);
        }
    }

    private void sendChunked(byte[] data, String targetServer) {
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
            dispatchRaw(secureWrap(wrapper.toByteArray()), targetServer);
        }
    }



    public void onReceiveRaw(byte[] data, String originServer) {
        try {
            byte[] cleanPayload = secureUnwrap(data);
            if (cleanPayload == null) return;

            PacketWrapper wrapper = PacketWrapper.parseFrom(cleanPayload);
            routeWrapper(wrapper, originServer);

        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
    }


    private void routeWrapper(PacketWrapper wrapper, String originServer) {
        if (wrapper.hasBatchResult()) {
            TranslationBatchResult batch = wrapper.getBatchResult();
            if (originServer != null) batch = batch.toBuilder().setOriginServer(originServer).build();
            handleFullResultPackage(batch);

        } else if (wrapper.hasBatchRequest()) {
            TranslationBatchRequest batch = wrapper.getBatchRequest();
            if (originServer != null) batch = batch.toBuilder().setOriginServer(originServer).build();
            handleFullRequestPackage(batch);

        } else if (wrapper.hasChunk()) {
            handleChunk(wrapper.getChunk(), originServer);

        } else if (wrapper.hasConfig()) {
            handleConfigUpdate(wrapper.getConfig());

        } else if (wrapper.hasConfigRequest()) {
            handleConfigRequest(originServer);
        }
    }

    private void handleChunk(Chunk chunk, String originServer) {
        try {
            Map<Integer, byte[]> parts = chunkAssembler.get(chunk.getTransmissionId(), ConcurrentHashMap::new);
            parts.put(chunk.getPartIndex(), chunk.getData().toByteArray());

            if (parts.size() == chunk.getTotalParts()) {
                chunkAssembler.invalidate(chunk.getTransmissionId());
                byte[] fullData = assemble(parts, chunk.getTotalParts());


                PacketWrapper assembledWrapper = PacketWrapper.parseFrom(fullData);
                routeWrapper(assembledWrapper, originServer);
            }
        } catch (Exception e) {
            e.printStackTrace();
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

    protected void handleResponses(TranslationBatchResult batch) {
        for (TranslationResult translationResult : batch.getResultsList()) {
            UUID id = UuidUtil.fromByteString(translationResult.getRequestId());
            String result = translationResult.getResult();

            List<CompletableFuture<String>> futures = pendingRequests.remove(id);
            if (futures != null) {
                for (CompletableFuture<String> future : futures) {
                    future.complete(result);
                }
            }
        }
    }

    public void sendConfigRequestProto() {
        ConfigRequestPacket req = ConfigRequestPacket.newBuilder().setTimestamp(System.currentTimeMillis()).build();
        PacketWrapper wrapper = PacketWrapper.newBuilder().setConfigRequest(req).build();
        dispatchRaw(wrapper.toByteArray(), null);
    }

    public void sendConfigProto(NetworkPackets.ServerConfiguration packet, String targetServer) {
        PacketWrapper wrapper = PacketWrapper.newBuilder().setConfig(packet).build();
        dispatchRaw(secureWrap(wrapper.toByteArray()), targetServer);
    }

    protected abstract void handleConfigRequest(String originServer);



    protected abstract void onConfigUpdate(org.pytenix.entity.ServerConfiguration configuration);


    public void handleConfigUpdate(NetworkPackets.ServerConfiguration configPacket){
        org.pytenix.entity.ServerConfiguration update = new org.pytenix.entity.ServerConfiguration();

        update.setModules(new HashMap<>(configPacket.getModulesMap()));
        update.setBlacklistedWords(new HashSet<>(configPacket.getWordsList()));

        this.serverConfiguration = update;
        placeholderService.updateProtectedWords(serverConfiguration.getBlacklistedWords());


        onConfigUpdate(update);
    }

    protected abstract void dispatchRaw(byte[] data, String originServer);

    protected abstract void handleFullResultPackage(TranslationBatchResult batch);
    protected abstract void handleFullRequestPackage(TranslationBatchRequest batch);

    public String handlePlaceholders(UUID uuid, String result)
    {

        List<UUID> lineIds = cachedReferences.getIfPresent(uuid);


        if (lineIds == null || lineIds.isEmpty()) {

            return result;
        }

        //TODO MORGEN TESTEN

        String[] translatedLines = result.split("\n", -1);
        List<String> finalLines = new ArrayList<>();

        for (int i = 0; i < lineIds.size(); i++) {
            UUID lineUuid = lineIds.get(i);

            String currentLine = (i < translatedLines.length) ? translatedLines[i] : "";

            if (getPlaceholderService() != null) {
                currentLine = getPlaceholderService().fromPlaceholders(lineUuid, currentLine);
            }

            if (getGradientService() != null) {
                GradientService.GradientInfo gradientInfo = getGradientService().cachedGradients.getIfPresent(lineUuid);
                if (gradientInfo != null && gradientInfo.isGradient()) {
                    currentLine = getGradientService().applyGradient(currentLine, gradientInfo);
                    getGradientService().cachedGradients.invalidate(lineUuid);
                }
            }

            finalLines.add(currentLine);
        }


        cachedReferences.invalidate(uuid);


        return String.join("\n", finalLines);
    }
}


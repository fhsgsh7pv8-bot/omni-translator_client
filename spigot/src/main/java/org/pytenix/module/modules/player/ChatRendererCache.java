package org.pytenix.module.modules.player;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.papermc.paper.chat.ChatRenderer;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ChatRendererCache {

    private static final Cache<UUID, ChatRenderer> cache = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();

    public static void set(UUID uuid, ChatRenderer renderer) {
        cache.put(uuid, renderer);
    }

    public static ChatRenderer get(UUID uuid) {
        return cache.getIfPresent(uuid);
    }
}
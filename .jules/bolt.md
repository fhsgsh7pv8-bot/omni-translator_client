## 2023-10-27 - Cache successfully translated text in VelocityBridge
**Learning:** `VelocityBridge` queries `CaffeineCache` using `.get()` before forwarding translation requests, but it never actually stores successful responses in the cache, making the cache effectively useless and resulting in redundant `RestfulService` translation calls.
**Action:** Always verify that caches are both read from AND written to. If caching is involved, it requires full read-through/write-through lifecycle management, otherwise you're paying cache initialization overhead without the benefits.

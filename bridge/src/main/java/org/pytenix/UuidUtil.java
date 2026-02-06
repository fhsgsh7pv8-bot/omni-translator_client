package org.pytenix;

import com.google.protobuf.ByteString;

import java.nio.ByteBuffer;
import java.util.UUID;

public class UuidUtil {
    /**
     * Wandelt Java UUID -> Protobuf ByteString (extrem schnell)
     */
    public static ByteString toByteString(UUID uuid) {
        if (uuid == null) return ByteString.EMPTY;

        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());

        return ByteString.copyFrom(bb.array());
    }

    /**
     * Wandelt Protobuf ByteString -> Java UUID
     */
    public static UUID fromByteString(ByteString bytes) {
        if (bytes == null || bytes.isEmpty()) return null;

        ByteBuffer bb = bytes.asReadOnlyByteBuffer();
        long high = bb.getLong();
        long low = bb.getLong();

        return new UUID(high, low);
    }


}

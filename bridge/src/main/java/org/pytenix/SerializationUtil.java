package org.pytenix;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.io.IOException;

public class SerializationUtil {
    private static final ObjectMapper mapper = new ObjectMapper(new MessagePackFactory());

    public static byte[] serialize(Object obj) throws IOException {
        return mapper.writeValueAsBytes(obj);
    }

    public static <T> T deserialize(byte[] data, Class<T> clazz) throws IOException {
        return mapper.readValue(data, clazz);
    }
}

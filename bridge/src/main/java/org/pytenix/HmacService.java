package org.pytenix;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class HmacService {
    private static final String ALGORITHM = "HmacSHA256";


    public static byte[] calculateSignature(long timestamp, byte[] payload, String secretKey) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(keySpec);

            ByteBuffer timeBuffer = ByteBuffer.allocate(Long.BYTES);
            timeBuffer.putLong(timestamp);
            mac.update(timeBuffer.array());

            return mac.doFinal(payload);
        } catch (Exception e) {
            throw new RuntimeException("HMAC Fehler", e);
        }
    }

    public static boolean isValid(long timestamp, byte[] payload, byte[] receivedSignature, String secretKey) {
        byte[] computedSignature = calculateSignature(timestamp, payload, secretKey);
        return MessageDigest.isEqual(computedSignature, receivedSignature);
    }
}
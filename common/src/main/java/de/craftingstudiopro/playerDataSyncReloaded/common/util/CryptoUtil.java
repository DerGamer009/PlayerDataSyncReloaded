package de.craftingstudiopro.playerDataSyncReloaded.common.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class CryptoUtil {
    private static final String ALGORITHM = "AES";

    public static String encrypt(String data, String key) throws Exception {
        if (key == null || key.length() < 16) return data; // No encryption if key is invalid

        byte[] keyBytes = keyBytes(key);
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, ALGORITHM);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * The first 16 characters as UTF-8. AES takes 16, 24 or 32 bytes, so those keys stay exactly as
     * before; any other length (non-ASCII characters) made every encryption fail and is cut to 16 bytes.
     */
    private static byte[] keyBytes(String key) {
        byte[] bytes = key.substring(0, 16).getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 16 || bytes.length == 24 || bytes.length == 32) {
            return bytes;
        }
        return java.util.Arrays.copyOf(bytes, 16);
    }

    public static String decrypt(String encryptedData, String key) throws Exception {
        if (key == null || key.length() < 16) return encryptedData;

        byte[] keyBytes = keyBytes(key);
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, ALGORITHM);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);

        byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
        return new String(decrypted, StandardCharsets.UTF_8);
    }
}

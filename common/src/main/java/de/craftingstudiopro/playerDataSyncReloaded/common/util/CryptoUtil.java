package de.craftingstudiopro.playerDataSyncReloaded.common.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class CryptoUtil {
    private static final String ALGORITHM = "AES";

    public static String encrypt(String data, String key) throws Exception {
        if (key == null || key.length() < 16) return data; // No encryption if key is invalid
        
        // Ensure key is 16 bytes (AES-128)
        byte[] keyBytes = key.substring(0, 16).getBytes(StandardCharsets.UTF_8);
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
        
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        
        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public static String decrypt(String encryptedData, String key) throws Exception {
        if (key == null || key.length() < 16) return encryptedData;
        
        byte[] keyBytes = key.substring(0, 16).getBytes(StandardCharsets.UTF_8);
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
        
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        
        byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
        return new String(decrypted, StandardCharsets.UTF_8);
    }
}
